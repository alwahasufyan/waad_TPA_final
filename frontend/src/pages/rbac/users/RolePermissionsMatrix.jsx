/**
 * Roles & Permissions Matrix
 * WAAD-RBAC-USERS-ROLES-UI-REDESIGN-1
 *
 * Real backend data only (RolePermissionAdminController). SUPER_ADMIN's
 * permission set is fixed (hardcoded to "all" in EffectivePermissionService)
 * and shown read-only. WAAD_ADMIN cannot toggle critical-security
 * permissions (e.g. settings.manage) for any role — enforced backend-side,
 * mirrored here for UX (disabled toggle + tooltip) rather than relying only
 * on the 403 after the fact.
 */

import { useState, useEffect, useCallback, useMemo } from 'react';

import {
  Box,
  Grid,
  Paper,
  Stack,
  Typography,
  Chip,
  Checkbox,
  Button,
  Alert,
  CircularProgress,
  Tooltip,
  MenuItem,
  TextField
} from '@mui/material';
import LockIcon from '@mui/icons-material/Lock';
import SaveIcon from '@mui/icons-material/Save';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import KeyIcon from '@mui/icons-material/Key';

import CircularLoader from 'components/CircularLoader';
import rolePermissionsService from 'services/rbac/rolePermissions.service';
import { openSnackbar } from 'api/snackbar';
import useAuth from 'hooks/useAuth';
import { RbacUiLabels } from 'constants/rbac';
import { GROUP_LABELS_AR, GROUP_ICONS } from './permissionGroupMeta';

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

const RolePermissionsMatrix = () => {
  const { user: currentUser } = useAuth();
  const isCurrentUserWaadAdmin = currentUser?.roles?.includes('WAAD_ADMIN');

  const [roles, setRoles] = useState([]);
  const [groups, setGroups] = useState([]);
  const [selectedRole, setSelectedRole] = useState(null);
  const [workCodes, setWorkCodes] = useState(new Set());
  const [originalCodes, setOriginalCodes] = useState(new Set());
  const [copyFrom, setCopyFrom] = useState('');

  const [loadingShell, setLoadingShell] = useState(true);
  const [loadingRole, setLoadingRole] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);

  const loadShell = useCallback(async () => {
    try {
      setLoadingShell(true);
      setError(null);
      const [rolesData, groupsData] = await Promise.all([
        rolePermissionsService.getRoles(),
        rolePermissionsService.getGroupedPermissions()
      ]);
      setRoles(rolesData);
      setGroups(groupsData);
      const firstEditable = rolesData.find((r) => r.editable && !r.reserved);
      setSelectedRole((firstEditable || rolesData[0])?.role || null);
    } catch (err) {
      console.error('[RolePermissionsMatrix] Load error:', err);
      setError(err?.response?.data?.message || 'فشل تحميل كتالوج الصلاحيات والأدوار');
    } finally {
      setLoadingShell(false);
    }
  }, []);

  useEffect(() => {
    loadShell();
  }, [loadShell]);

  const loadRolePermissions = useCallback(async (role) => {
    if (!role) return;
    try {
      setLoadingRole(true);
      const codes = await rolePermissionsService.getRolePermissions(role);
      const set = new Set(codes);
      setWorkCodes(set);
      setOriginalCodes(new Set(set));
      setCopyFrom('');
    } catch (err) {
      console.error('[RolePermissionsMatrix] Role load error:', err);
      openSnackbar({ open: true, message: 'فشل تحميل صلاحيات الدور', variant: 'alert', alert: { color: 'error' } });
    } finally {
      setLoadingRole(false);
    }
  }, []);

  useEffect(() => {
    if (selectedRole) loadRolePermissions(selectedRole);
  }, [selectedRole, loadRolePermissions]);

  const currentRoleInfo = useMemo(() => roles.find((r) => r.role === selectedRole), [roles, selectedRole]);
  const isSuperAdminRole = selectedRole === 'SUPER_ADMIN';
  const isReservedRole = Boolean(currentRoleInfo?.reserved);
  const isEditable = Boolean(currentRoleInfo?.editable) && !isSuperAdminRole && !isReservedRole;

  const dirty = useMemo(() => {
    if (workCodes.size !== originalCodes.size) return true;
    for (const c of workCodes) if (!originalCodes.has(c)) return true;
    return false;
  }, [workCodes, originalCodes]);

  const allPermissionsByCode = useMemo(() => {
    const map = new Map();
    groups.forEach((g) => g.permissions.forEach((p) => map.set(p.code, p)));
    return map;
  }, [groups]);

  const toggle = (code) => {
    if (!isEditable) return;
    const permission = allPermissionsByCode.get(code);
    if (isCurrentUserWaadAdmin && permission?.criticalSecurity) return; // UX guard only — backend also enforces
    setWorkCodes((prev) => {
      const next = new Set(prev);
      if (next.has(code)) next.delete(code);
      else next.add(code);
      return next;
    });
  };

  const toggleGroup = (groupPermissions, selectAll) => {
    if (!isEditable) return;
    setWorkCodes((prev) => {
      const next = new Set(prev);
      groupPermissions.forEach((p) => {
        if (isCurrentUserWaadAdmin && p.criticalSecurity) return;
        if (selectAll) next.add(p.code);
        else next.delete(p.code);
      });
      return next;
    });
  };

  const handleCopyFrom = async (role) => {
    setCopyFrom(role);
    if (!role) return;
    try {
      const codes = await rolePermissionsService.getRolePermissions(role);
      setWorkCodes(new Set(codes));
    } catch (err) {
      console.error('[RolePermissionsMatrix] Copy-from error:', err);
    }
  };

  const handleSave = async () => {
    try {
      setSaving(true);
      await rolePermissionsService.updateRolePermissions(selectedRole, Array.from(workCodes));
      setOriginalCodes(new Set(workCodes));
      openSnackbar({ open: true, message: 'تم حفظ صلاحيات الدور بنجاح', variant: 'alert', alert: { color: 'success' } });
    } catch (err) {
      console.error('[RolePermissionsMatrix] Save error:', err);
      const status = err?.response?.status;
      const message =
        status === 403
          ? 'لا يمكنك تعديل هذه الصلاحية — قد تكون صلاحية حساسة محمية أو تتعلق بمدير النظام الأعلى'
          : err?.response?.data?.message || 'فشل حفظ صلاحيات الدور';
      openSnackbar({ open: true, message, variant: 'alert', alert: { color: 'error' } });
    } finally {
      setSaving(false);
    }
  };

  if (loadingShell) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: '4.0rem' }}>
        <CircularLoader />
      </Box>
    );
  }

  if (error) {
    return <Alert severity="error">{error}</Alert>;
  }

  return (
    <Grid container spacing={2}>
      {/* Role cards */}
      <Grid size={{ xs: 12, md: 3 }}>
        <Stack spacing={1}>
          {roles.map((r) => {
            const active = r.role === selectedRole;
            const tone = ROLE_TONE[r.role] || 'default';
            return (
              <Paper
                key={r.role}
                onClick={() => setSelectedRole(r.role)}
                elevation={active ? 3 : 0}
                sx={{
                  p: '0.75rem 1rem',
                  cursor: 'pointer',
                  border: '1px solid',
                  borderColor: active ? `${tone}.main` : 'divider',
                  bgcolor: active ? `${tone}.lighter` : 'background.paper'
                }}
              >
                <Stack direction="row" justifyContent="space-between" alignItems="center">
                  <Typography variant="subtitle2" fontWeight="bold">
                    {r.displayNameAr}
                  </Typography>
                  {r.reserved && <Chip label="لاحقًا" size="small" />}
                  {!r.reserved && !r.editable && <Chip label={RbacUiLabels.protected} size="small" color="error" icon={<LockIcon />} />}
                </Stack>
                <Typography variant="caption" color="text.secondary" display="block">
                  {r.permissionCount} صلاحية · {r.userCount} مستخدم
                </Typography>
              </Paper>
            );
          })}
        </Stack>
      </Grid>

      {/* Matrix */}
      <Grid size={{ xs: 12, md: 9 }}>
        {loadingRole ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: '3.0rem' }}>
            <CircularProgress />
          </Box>
        ) : (
          <Stack spacing={2}>
            {isSuperAdminRole && (
              <Alert severity="info" icon={<LockIcon />}>
                صلاحيات مدير النظام الأعلى ثابتة برمجيًا (كل الصلاحيات) ولا يمكن تعديلها من هنا.
              </Alert>
            )}
            {isReservedRole && <Alert severity="warning">هذا الدور محجوز لمرحلة مستقبلية ولا صلاحيات معرّفة له بعد.</Alert>}
            {!isEditable ? null : (
              <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                <TextField
                  select
                  size="small"
                  label="نسخ الصلاحيات من دور آخر"
                  value={copyFrom}
                  onChange={(e) => handleCopyFrom(e.target.value)}
                  sx={{ minWidth: '14rem' }}
                >
                  <MenuItem value="">— اختر دورًا —</MenuItem>
                  {roles
                    .filter((r) => r.role !== selectedRole)
                    .map((r) => (
                      <MenuItem key={r.role} value={r.role}>
                        {r.displayNameAr}
                      </MenuItem>
                    ))}
                </TextField>
              </Stack>
            )}

            <Grid container spacing={2}>
              {groups.map((g) => {
                const groupPerms = g.permissions;
                const selectedCount = groupPerms.filter((p) => workCodes.has(p.code)).length;
                const GroupIcon = GROUP_ICONS[g.groupName];
                return (
                  <Grid key={g.groupName} size={{ xs: 12, sm: 6, lg: 4 }}>
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
                          {isEditable && (
                            <Button size="small" onClick={() => toggleGroup(groupPerms, selectedCount !== groupPerms.length)}>
                              {selectedCount === groupPerms.length ? 'إزالة الكل' : 'تحديد الكل'}
                            </Button>
                          )}
                        </Stack>
                      </Stack>
                      <Stack divider={<Box sx={{ borderBottom: '1px solid', borderColor: 'divider' }} />}>
                        {groupPerms.map((p) => {
                          const checked = isSuperAdminRole ? true : workCodes.has(p.code);
                          const lockedForActor = isCurrentUserWaadAdmin && p.criticalSecurity;
                          return (
                            <Stack
                              key={p.code}
                              direction="row"
                              alignItems="flex-start"
                              spacing={1}
                              sx={{ p: '0.5rem 0.75rem', opacity: !isEditable ? 0.7 : 1 }}
                            >
                              <Checkbox
                                size="small"
                                checked={checked}
                                disabled={!isEditable || lockedForActor}
                                onChange={() => toggle(p.code)}
                                sx={{ p: 0, mt: '2px' }}
                              />
                              <Box sx={{ flex: 1 }}>
                                <Stack direction="row" spacing={0.5} alignItems="center">
                                  <Typography variant="body2">{p.labelAr}</Typography>
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

            {isEditable && (
              <Stack
                direction="row"
                spacing={1}
                justifyContent="flex-end"
                sx={{ position: 'sticky', bottom: 0, bgcolor: 'background.paper', py: 1 }}
              >
                <Typography variant="caption" color="text.secondary" sx={{ flex: 1, alignSelf: 'center' }}>
                  {dirty ? `تغييرات غير محفوظة — يؤثر على ${currentRoleInfo?.userCount ?? 0} مستخدم` : 'لا توجد تغييرات غير محفوظة'}
                </Typography>
                <Button
                  variant="outlined"
                  startIcon={<RestartAltIcon />}
                  disabled={!dirty || saving}
                  onClick={() => setWorkCodes(new Set(originalCodes))}
                >
                  تراجع
                </Button>
                <Button variant="contained" startIcon={<SaveIcon />} disabled={!dirty || saving} onClick={handleSave}>
                  {saving ? 'جاري الحفظ...' : 'حفظ صلاحيات الدور'}
                </Button>
              </Stack>
            )}
          </Stack>
        )}
      </Grid>
    </Grid>
  );
};

export default RolePermissionsMatrix;
