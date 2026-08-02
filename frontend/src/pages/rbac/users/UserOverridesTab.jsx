/**
 * الصلاحيات الخاصة للمستخدمين — WAAD-RBAC-PHASE-3B-USER-OVERRIDES-API-UI
 *
 * Tab 3 of /admin/users. Mirrors the visual language of the "الأدوار
 * والصلاحيات" matrix (Tab 2, RolePermissionsMatrix.jsx): a right-side user
 * column + a permission matrix grouped into category cards. Unlike Tab 2
 * (which edits a role's baseline), this tab edits per-user *exceptions* to
 * that baseline — every permission here is one of:
 *
 *   - موروث من الدور   (inherited): checked, comes from the user's role(s)
 *   - منح خاص           (grant):     checked, an active GRANT override
 *   - سحب خاص           (revoke):    unchecked, an active REVOKE override
 *     even though the role would normally grant it
 *   - بدون صلاحية       (none):      unchecked, neither role nor override
 *
 * Clicking a checkbox always flips it relative to its current *effective*
 * state — creating or deactivating exactly one override — never touches the
 * user's role or the role's own permission set. All effective state is
 * derived from live GET responses (role permissions + this user's
 * overrides), never guessed client-side, so a rejected backend call simply
 * leaves the checkbox where it was (nothing was optimistically flipped).
 */

import { useCallback, useEffect, useMemo, useState } from 'react';

import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  CircularProgress,
  Grid,
  IconButton,
  InputAdornment,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Tooltip,
  Typography
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import ClearIcon from '@mui/icons-material/Clear';
import PersonSearchIcon from '@mui/icons-material/PersonSearch';
import ShieldIcon from '@mui/icons-material/Shield';
import AddCircleIcon from '@mui/icons-material/AddCircle';
import RemoveCircleIcon from '@mui/icons-material/RemoveCircle';
import LockIcon from '@mui/icons-material/Lock';
import KeyIcon from '@mui/icons-material/Key';
import RefreshIcon from '@mui/icons-material/Refresh';

import CircularLoader from 'components/CircularLoader';
import usersService from 'services/rbac/users.service';
import rolePermissionsService from 'services/rbac/rolePermissions.service';
import userOverridesService from 'services/rbac/userOverrides.service';
import { openSnackbar } from 'api/snackbar';
import useAuth from 'hooks/useAuth';
import { RbacUiLabels, getRoleDisplayName } from 'constants/rbac';
import { GROUP_LABELS_AR, GROUP_ICONS } from './permissionGroupMeta';

// Same tiny per-role color mapping duplicated across UsersList.jsx /
// UserDetails.jsx — a UI color choice, not a permission/role definition.
const ROLE_TONE = {
  SUPER_ADMIN: 'error',
  WAAD_ADMIN: 'secondary',
  MEDICAL_REVIEWER: 'primary',
  ACCOUNTANT: 'warning',
  FINANCE_VIEWER: 'warning',
  PROVIDER_STAFF: 'info',
  EMPLOYER_ADMIN: 'success',
  DATA_ENTRY: 'default',
  BENEFICIARY: 'default'
};

const STATUS_FILTERS = [
  { value: 'all', label: 'كل الصلاحيات' },
  { value: 'inherited', label: 'موروثة من الدور' },
  { value: 'grant', label: 'ممنوحة خصيصًا' },
  { value: 'revoke', label: 'مسحوبة خصيصًا' }
];

const userRoleNames = (user) => {
  const roles = user?.roles || (user?.role ? [{ name: user.role }] : []);
  return [...new Set(roles.map((r) => (typeof r === 'string' ? r : r?.name)).filter(Boolean))];
};

const UserOverridesTab = () => {
  const { user: currentUser } = useAuth();
  const isCurrentUserWaadAdmin = currentUser?.roles?.includes('WAAD_ADMIN');

  // ---- users column ----
  const [users, setUsers] = useState([]);
  const [usersLoading, setUsersLoading] = useState(true);
  const [usersError, setUsersError] = useState(null);
  const [userSearch, setUserSearch] = useState('');
  const [selectedUser, setSelectedUser] = useState(null);
  const [permissionCounts, setPermissionCounts] = useState(new Map());

  // ---- permission catalog (shared, loaded once) ----
  const [groups, setGroups] = useState([]);
  const [catalogLoading, setCatalogLoading] = useState(true);
  const [catalogError, setCatalogError] = useState(null);

  // ---- selected user's role baseline + overrides ----
  const [roleBaseCodes, setRoleBaseCodes] = useState(new Set());
  const [overrides, setOverrides] = useState([]);
  const [matrixLoading, setMatrixLoading] = useState(false);
  const [matrixError, setMatrixError] = useState(null);

  const [statusFilter, setStatusFilter] = useState('all');
  const [pendingCodes, setPendingCodes] = useState(new Set());
  const [pendingGroups, setPendingGroups] = useState(new Set());

  const isTargetSuperAdmin = userRoleNames(selectedUser).includes('SUPER_ADMIN');

  // ============================================================
  // Load users
  // ============================================================
  const loadUsers = useCallback(async () => {
    setUsersLoading(true);
    setUsersError(null);
    try {
      const data = await usersService.getAllUsers();
      setUsers(Array.isArray(data) ? data : []);
    } catch (err) {
      setUsersError(err?.response?.data?.messageAr || err?.response?.data?.message || 'فشل تحميل قائمة المستخدمين');
      setUsers([]);
    } finally {
      setUsersLoading(false);
    }
  }, []);

  const loadCatalog = useCallback(async () => {
    setCatalogLoading(true);
    setCatalogError(null);
    try {
      const data = await rolePermissionsService.getGroupedPermissions();
      setGroups(Array.isArray(data) ? data : []);
    } catch (err) {
      setCatalogError(err?.response?.data?.messageAr || err?.response?.data?.message || 'فشل تحميل كتالوج الصلاحيات');
      setGroups([]);
    } finally {
      setCatalogLoading(false);
    }
  }, []);

  useEffect(() => {
    loadUsers();
    loadCatalog();
  }, [loadUsers, loadCatalog]);

  // Background, best-effort: effective-permission counts for the user cards.
  // Failures per-user are silently skipped (card just shows "-").
  useEffect(() => {
    if (!users.length) return undefined;
    let cancelled = false;
    (async () => {
      const results = await Promise.allSettled(
        users.map((u) => usersService.getEffectivePermissions(u.id).then((codes) => [u.id, Array.isArray(codes) ? codes.length : 0]))
      );
      if (cancelled) return;
      setPermissionCounts((prev) => {
        const next = new Map(prev);
        results.forEach((r) => {
          if (r.status === 'fulfilled') next.set(r.value[0], r.value[1]);
        });
        return next;
      });
    })();
    return () => {
      cancelled = true;
    };
  }, [users]);

  const allPermissions = useMemo(() => groups.flatMap((g) => g.permissions || []), [groups]);

  // ============================================================
  // Load selected user's role baseline + overrides
  // ============================================================
  const loadUserPermissions = useCallback(
    async (user) => {
      if (!user || userRoleNames(user).includes('SUPER_ADMIN')) return;
      setMatrixLoading(true);
      setMatrixError(null);
      try {
        const roleNames = userRoleNames(user);
        const [codeArrays, overridesData] = await Promise.all([
          Promise.all(roleNames.map((r) => rolePermissionsService.getRolePermissions(r).catch(() => []))),
          userOverridesService.getOverrides(user.id)
        ]);
        const codes = new Set(codeArrays.flat());
        const overridesList = Array.isArray(overridesData) ? overridesData : [];
        setRoleBaseCodes(codes);
        setOverrides(overridesList);

        // keep the sidebar count for this user in sync without a refetch
        const activeByCode = new Map(overridesList.filter((o) => o.active).map((o) => [o.permissionCode, o.effect]));
        let effectiveCount = 0;
        allPermissions.forEach((p) => {
          const effect = activeByCode.get(p.code);
          const checked = effect ? effect === 'GRANT' : codes.has(p.code);
          if (checked) effectiveCount += 1;
        });
        setPermissionCounts((prev) => new Map(prev).set(user.id, effectiveCount));
      } catch (err) {
        setMatrixError(err?.response?.data?.messageAr || err?.response?.data?.message || 'فشل تحميل صلاحيات المستخدم');
      } finally {
        setMatrixLoading(false);
      }
    },
    [allPermissions]
  );

  useEffect(() => {
    if (selectedUser && allPermissions.length) loadUserPermissions(selectedUser);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedUser?.id, allPermissions.length]);

  const activeOverrideByCode = useMemo(() => {
    const map = new Map();
    overrides.filter((o) => o.active).forEach((o) => map.set(o.permissionCode, o));
    return map;
  }, [overrides]);

  const computeState = useCallback(
    (code) => {
      const override = activeOverrideByCode.get(code);
      if (override) {
        return {
          checked: override.effect === 'GRANT',
          source: override.effect === 'GRANT' ? 'grant' : 'revoke',
          overrideId: override.id
        };
      }
      if (roleBaseCodes.has(code)) return { checked: true, source: 'inherited', overrideId: null };
      return { checked: false, source: 'none', overrideId: null };
    },
    [activeOverrideByCode, roleBaseCodes]
  );

  // ============================================================
  // Filtering
  // ============================================================
  const filteredUsers = useMemo(() => {
    if (!userSearch.trim()) return users;
    const term = userSearch.trim().toLowerCase();
    return users.filter(
      (u) => u.username?.toLowerCase().includes(term) || u.fullName?.toLowerCase().includes(term) || u.email?.toLowerCase().includes(term)
    );
  }, [users, userSearch]);

  const visibleGroups = useMemo(() => {
    return groups
      .map((g) => ({
        ...g,
        permissions: (g.permissions || []).filter((p) => {
          if (statusFilter === 'all') return true;
          return computeState(p.code).source === statusFilter;
        })
      }))
      .filter((g) => g.permissions.length > 0);
  }, [groups, statusFilter, computeState]);

  // ============================================================
  // Mutations
  // ============================================================
  const applyRawChange = (userId, permission, state) => {
    if (state.source === 'grant' || state.source === 'revoke') {
      return userOverridesService.deactivateOverride(userId, state.overrideId, 'إعادة إلى صلاحيات الدور');
    }
    if (state.source === 'inherited') {
      return userOverridesService.createOverride(userId, {
        permissionCode: permission.code,
        effect: 'REVOKE',
        reason: `سحب خاص: ${permission.labelAr}`
      });
    }
    return userOverridesService.createOverride(userId, {
      permissionCode: permission.code,
      effect: 'GRANT',
      reason: `منح خاص: ${permission.labelAr}`
    });
  };

  const errorMessage = (err) => {
    const status = err?.response?.status;
    if (status === 403) return RbacUiLabels.superAdminProtected;
    return err?.response?.data?.messageAr || err?.response?.data?.message || 'فشل تحديث الصلاحية';
  };

  const handleToggle = async (permission) => {
    if (!selectedUser || pendingCodes.has(permission.code)) return;
    if (isCurrentUserWaadAdmin && permission.criticalSecurity) return;
    const state = computeState(permission.code);
    setPendingCodes((prev) => new Set(prev).add(permission.code));
    try {
      await applyRawChange(selectedUser.id, permission, state);
      const messages = {
        grant: 'تم إلغاء المنح الخاص — عاد للحالة الافتراضية حسب الدور',
        revoke: 'تم إلغاء السحب الخاص — عاد للحالة الافتراضية حسب الدور',
        inherited: `تم سحب "${permission.labelAr}" لهذا المستخدم`,
        none: `تم منح "${permission.labelAr}" لهذا المستخدم`
      };
      openSnackbar({ open: true, message: messages[state.source], variant: 'alert', alert: { color: 'success' } });
      await loadUserPermissions(selectedUser);
    } catch (err) {
      openSnackbar({ open: true, message: errorMessage(err), variant: 'alert', alert: { color: 'error' } });
    } finally {
      setPendingCodes((prev) => {
        const next = new Set(prev);
        next.delete(permission.code);
        return next;
      });
    }
  };

  const handleBulk = async (groupName, groupPermissions, desiredChecked) => {
    if (!selectedUser || pendingGroups.has(groupName)) return;
    const targets = groupPermissions.filter((p) => {
      if (isCurrentUserWaadAdmin && p.criticalSecurity) return false;
      return computeState(p.code).checked !== desiredChecked;
    });
    if (targets.length === 0) return;
    setPendingGroups((prev) => new Set(prev).add(groupName));
    try {
      await Promise.all(targets.map((p) => applyRawChange(selectedUser.id, p, computeState(p.code))));
      openSnackbar({
        open: true,
        message: desiredChecked ? 'تم تحديد كل صلاحيات الفئة لهذا المستخدم' : 'تم إزالة كل صلاحيات الفئة الخاصة بهذا المستخدم',
        variant: 'alert',
        alert: { color: 'success' }
      });
    } catch (err) {
      openSnackbar({ open: true, message: errorMessage(err), variant: 'alert', alert: { color: 'error' } });
    } finally {
      await loadUserPermissions(selectedUser);
      setPendingGroups((prev) => {
        const next = new Set(prev);
        next.delete(groupName);
        return next;
      });
    }
  };

  const isLoadingShell = usersLoading || catalogLoading;

  return (
    <Box>
      <Alert severity="info" sx={{ mb: '1.0rem' }}>
        هذه المصفوفة تُدير استثناءات فردية لمستخدم واحد فوق صلاحيات دوره — لا تُعدّل دور المستخدم ولا صلاحيات الدور نفسه (تلك في تبويب
        «الأدوار والصلاحيات»). لا يمكن استهداف حساب مدير النظام الأعلى (SUPER_ADMIN).
      </Alert>

      <Grid container spacing={2}>
        {/* ================= Users column ================= */}
        <Grid size={{ xs: 12, md: 3 }}>
          <TextField
            fullWidth
            size="small"
            placeholder="ابحث بالاسم أو اسم المستخدم أو البريد"
            value={userSearch}
            onChange={(e) => setUserSearch(e.target.value)}
            sx={{ mb: '0.75rem' }}
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon fontSize="small" />
                  </InputAdornment>
                ),
                endAdornment: userSearch ? (
                  <InputAdornment position="end">
                    <IconButton size="small" onClick={() => setUserSearch('')}>
                      <ClearIcon fontSize="small" />
                    </IconButton>
                  </InputAdornment>
                ) : undefined
              }
            }}
          />

          <Box sx={{ position: 'sticky', top: '1rem', maxHeight: 'calc(100vh - 14rem)', overflowY: 'auto', pr: '0.25rem' }}>
            <Stack spacing={1}>
              {usersLoading ? (
                <Box sx={{ display: 'flex', justifyContent: 'center', py: '2.0rem' }}>
                  <CircularLoader />
                </Box>
              ) : usersError ? (
                <Alert
                  severity="error"
                  action={
                    <Button color="inherit" size="small" onClick={loadUsers}>
                      إعادة المحاولة
                    </Button>
                  }
                >
                  {usersError}
                </Alert>
              ) : filteredUsers.length === 0 ? (
                <Alert severity="info">لا يوجد مستخدمون مطابقون</Alert>
              ) : (
                filteredUsers.map((u) => {
                  const active = u.id === selectedUser?.id;
                  const roles = userRoleNames(u);
                  const primaryTone = ROLE_TONE[roles[0]] || 'default';
                  const isActive = u.active !== false && u.enabled !== false;
                  const count = permissionCounts.get(u.id);
                  return (
                    <Paper
                      key={u.id}
                      onClick={() => setSelectedUser(u)}
                      elevation={active ? 3 : 0}
                      sx={{
                        p: '0.6rem 0.85rem',
                        cursor: 'pointer',
                        border: '1px solid',
                        borderColor: active ? `${primaryTone}.main` : 'divider',
                        bgcolor: active ? `${primaryTone}.lighter` : 'background.paper'
                      }}
                    >
                      <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
                        <Box sx={{ minWidth: 0 }}>
                          <Typography variant="subtitle2" fontWeight="bold" noWrap>
                            {u.fullName || u.username}
                          </Typography>
                          <Typography variant="caption" color="text.secondary" noWrap display="block">
                            {u.username}
                          </Typography>
                        </Box>
                        <Chip
                          label={isActive ? 'نشط' : 'معطل'}
                          size="small"
                          color={isActive ? 'success' : 'default'}
                          variant={isActive ? 'filled' : 'outlined'}
                          sx={{ flexShrink: 0 }}
                        />
                      </Stack>
                      <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap sx={{ mt: '0.4rem' }}>
                        {roles.map((r) => (
                          <Chip
                            key={r}
                            label={getRoleDisplayName(r, 'ar') || r}
                            size="small"
                            color={ROLE_TONE[r] || 'default'}
                            variant="light"
                          />
                        ))}
                      </Stack>
                      <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: '0.35rem' }}>
                        {count === undefined ? '…' : count} صلاحية فعلية
                      </Typography>
                    </Paper>
                  );
                })
              )}
            </Stack>
          </Box>
        </Grid>

        {/* ================= Matrix ================= */}
        <Grid size={{ xs: 12, md: 9 }}>
          {!selectedUser ? (
            <Paper variant="outlined" sx={{ p: '3rem 1.5rem', textAlign: 'center' }}>
              <PersonSearchIcon sx={{ fontSize: '2.5rem', color: 'text.disabled', mb: '0.5rem' }} />
              <Typography color="text.secondary">اختر مستخدمًا من القائمة لعرض/تعديل صلاحياته الخاصة</Typography>
            </Paper>
          ) : isTargetSuperAdmin ? (
            <Alert severity="warning" icon={<LockIcon />}>
              {RbacUiLabels.protected}: {RbacUiLabels.superAdminProtected}
            </Alert>
          ) : isLoadingShell ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: '3.0rem' }}>
              <CircularLoader />
            </Box>
          ) : catalogError ? (
            <Alert
              severity="error"
              action={
                <Button color="inherit" size="small" onClick={loadCatalog}>
                  إعادة المحاولة
                </Button>
              }
            >
              {catalogError}
            </Alert>
          ) : (
            <Stack spacing={2}>
              {/* Legend */}
              <Paper variant="outlined" sx={{ p: '0.6rem 0.85rem' }}>
                <Stack direction="row" spacing={1.5} flexWrap="wrap" useFlexGap alignItems="center">
                  <Typography variant="caption" fontWeight="bold" color="text.secondary">
                    دليل الرموز:
                  </Typography>
                  <Chip size="small" variant="outlined" icon={<ShieldIcon sx={{ fontSize: '0.9rem' }} />} label="موروث من الدور" />
                  <Chip size="small" color="success" icon={<AddCircleIcon sx={{ fontSize: '0.9rem' }} />} label="منح خاص" />
                  <Chip size="small" color="error" icon={<RemoveCircleIcon sx={{ fontSize: '0.9rem' }} />} label="سحب خاص" />
                  <Chip size="small" variant="outlined" label="بدون صلاحية" sx={{ opacity: 0.7 }} />
                </Stack>
              </Paper>

              <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap justifyContent="space-between">
                <Typography variant="subtitle1" fontWeight="bold">
                  {selectedUser.fullName || selectedUser.username}
                  <Typography component="span" variant="caption" color="text.secondary" sx={{ ml: '0.5rem' }}>
                    ({selectedUser.username})
                  </Typography>
                </Typography>
                <Stack direction="row" spacing={1} alignItems="center">
                  <TextField
                    select
                    size="small"
                    label="عرض حسب الحالة"
                    value={statusFilter}
                    onChange={(e) => setStatusFilter(e.target.value)}
                    sx={{ minWidth: '11rem' }}
                  >
                    {STATUS_FILTERS.map((f) => (
                      <MenuItem key={f.value} value={f.value}>
                        {f.label}
                      </MenuItem>
                    ))}
                  </TextField>
                  <Tooltip title="تحديث">
                    <span>
                      <IconButton size="small" onClick={() => loadUserPermissions(selectedUser)} disabled={matrixLoading}>
                        <RefreshIcon fontSize="small" />
                      </IconButton>
                    </span>
                  </Tooltip>
                </Stack>
              </Stack>

              {matrixLoading ? (
                <Box sx={{ display: 'flex', justifyContent: 'center', py: '3.0rem' }}>
                  <CircularProgress />
                </Box>
              ) : matrixError ? (
                <Alert
                  severity="error"
                  action={
                    <Button color="inherit" size="small" onClick={() => loadUserPermissions(selectedUser)}>
                      إعادة المحاولة
                    </Button>
                  }
                >
                  {matrixError}
                </Alert>
              ) : visibleGroups.length === 0 ? (
                <Alert severity="info">لا توجد صلاحيات مطابقة لهذا الفلتر</Alert>
              ) : (
                <Grid container spacing={2}>
                  {visibleGroups.map((g) => {
                    const groupPerms = g.permissions;
                    const selectedCount = groupPerms.filter((p) => computeState(p.code).checked).length;
                    const GroupIcon = GROUP_ICONS[g.groupName];
                    const groupIsPending = pendingGroups.has(g.groupName);
                    return (
                      <Grid key={g.groupName} size={{ xs: 12, lg: 6 }}>
                        <Paper variant="outlined">
                          <Stack
                            direction="row"
                            justifyContent="space-between"
                            alignItems="center"
                            sx={{ p: '0.5rem 0.75rem', bgcolor: 'action.hover' }}
                          >
                            <Stack direction="row" spacing={0.75} alignItems="center">
                              {GroupIcon && <GroupIcon sx={{ fontSize: '1.1rem', color: 'text.secondary' }} />}
                              <Typography variant="caption" fontWeight="bold">
                                {GROUP_LABELS_AR[g.groupName] || g.groupName}
                              </Typography>
                            </Stack>
                            <Stack direction="row" spacing={1} alignItems="center">
                              <Typography variant="caption" color="text.secondary">
                                {selectedCount}/{groupPerms.length}
                              </Typography>
                              {groupIsPending ? (
                                <CircularProgress size={14} />
                              ) : (
                                <Button
                                  size="small"
                                  onClick={() => handleBulk(g.groupName, groupPerms, selectedCount !== groupPerms.length)}
                                >
                                  {selectedCount === groupPerms.length ? 'إزالة الكل' : 'تحديد الكل'}
                                </Button>
                              )}
                            </Stack>
                          </Stack>
                          <Stack divider={<Box sx={{ borderBottom: '1px solid', borderColor: 'divider' }} />}>
                            {groupPerms.map((p) => {
                              const state = computeState(p.code);
                              const isPending = pendingCodes.has(p.code);
                              const lockedForActor = isCurrentUserWaadAdmin && p.criticalSecurity;
                              return (
                                <Stack
                                  key={p.code}
                                  direction="row"
                                  alignItems="flex-start"
                                  spacing={1}
                                  sx={{ p: '0.5rem 0.75rem', opacity: isPending ? 0.6 : 1 }}
                                >
                                  <Checkbox
                                    size="small"
                                    checked={state.checked}
                                    disabled={isPending || groupIsPending || lockedForActor}
                                    onChange={() => handleToggle(p)}
                                    sx={{ p: 0, mt: '2px' }}
                                  />
                                  <Box sx={{ flex: 1, minWidth: 0 }}>
                                    <Stack direction="row" spacing={0.5} alignItems="center" flexWrap="wrap" useFlexGap>
                                      <Typography variant="body2">{p.labelAr}</Typography>
                                      {state.source === 'grant' && (
                                        <Chip
                                          size="small"
                                          color="success"
                                          label="منح خاص"
                                          icon={<AddCircleIcon sx={{ fontSize: '0.8rem' }} />}
                                        />
                                      )}
                                      {state.source === 'revoke' && (
                                        <Chip
                                          size="small"
                                          color="error"
                                          label="سحب خاص"
                                          icon={<RemoveCircleIcon sx={{ fontSize: '0.8rem' }} />}
                                        />
                                      )}
                                      {state.source === 'inherited' && (
                                        <Chip
                                          size="small"
                                          variant="outlined"
                                          label="موروث"
                                          icon={<ShieldIcon sx={{ fontSize: '0.8rem' }} />}
                                        />
                                      )}
                                      {p.sensitive && (
                                        <Tooltip title={RbacUiLabels.sensitivePermission}>
                                          <KeyIcon sx={{ fontSize: '0.9rem', color: 'warning.main' }} />
                                        </Tooltip>
                                      )}
                                      {p.criticalSecurity && (
                                        <Tooltip title={lockedForActor ? 'لا يمكن لمدير وعد تعديل هذه الصلاحية' : RbacUiLabels.protected}>
                                          <LockIcon sx={{ fontSize: '0.9rem', color: 'error.main' }} />
                                        </Tooltip>
                                      )}
                                      {isPending && <CircularProgress size={12} />}
                                    </Stack>
                                    <Typography variant="caption" color="text.secondary">
                                      {p.code}
                                    </Typography>
                                  </Box>
                                </Stack>
                              );
                            })}
                          </Stack>
                        </Paper>
                      </Grid>
                    );
                  })}
                </Grid>
              )}
            </Stack>
          )}
        </Grid>
      </Grid>
    </Box>
  );
};

export default UserOverridesTab;
