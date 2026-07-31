/**
 * User Details Page
 * Read-only view of user info and assigned role.
 */

import { useState, useEffect, useCallback, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';

// MUI Components
import {
  Box,
  Typography,
  Avatar,
  Stack,
  Chip,
  Grid,
  Alert,
  Paper,
  Button
} from '@mui/material';

// MUI Icons
import PersonIcon from '@mui/icons-material/Person';
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import LockIcon from '@mui/icons-material/Lock';
import ShieldIcon from '@mui/icons-material/Shield';

// Project Components
import MainCard from 'components/MainCard';
import ModernPageHeader from 'components/tba/ModernPageHeader';
import CircularLoader from 'components/CircularLoader';

// Services
import usersService from 'services/rbac/users.service';
import { SystemRole, RoleDisplayNames, getRoleDisplayName, RbacUiLabels, PermissionLabels, CriticalSecurityPermissions } from 'constants/rbac';

// Hooks
import useAuth from 'hooks/useAuth';

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

/**
 * Get initials from name
 */
const getInitials = (name) => {
  if (!name) return '?';
  return name
    .split(' ')
    .map((n) => n[0])
    .join('')
    .toUpperCase()
    .slice(0, 2);
};

/**
 * Get role color based on role name
 */
const getRoleColor = (roleName) => {
  const roleColors = {
    SUPER_ADMIN: 'error',
    ACCOUNTANT: 'warning',
    MEDICAL_REVIEWER: 'secondary',
    PROVIDER_STAFF: 'info',
    EMPLOYER_ADMIN: 'primary',
    DATA_ENTRY: 'default',
    FINANCE_VIEWER: 'default'
  };
  return roleColors[roleName] || 'default';
};



// ============================================================================
// TAB PANEL COMPONENT
// ============================================================================

// ============================================================================
// ROLES DISPLAY (read-only)
// ============================================================================

const RolesDisplay = ({ user, allRoles, userRoleIds }) => {
  return (
    <Box>
      <Alert severity="info" sx={{ mb: '1.5rem' }}>
        نوع المستخدم والدور المعيّن. لتغيير الدور، استخدم صفحة تعديل المستخدم.
      </Alert>

      <Grid container spacing={2}>
        {allRoles.map((role) => {
          const isAssigned = userRoleIds.includes(role?.id);
          const roleName = role?.name || '';

          return (
            <Grid key={role?.id} size={{ xs: 12, sm: 6, md: 4 }}>
              <Paper
                elevation={isAssigned ? 3 : 0}
                sx={{
                  p: '1.0rem',
                  border: '1px solid',
                  borderColor: isAssigned ? 'primary.main' : 'divider',
                  borderRadius: '0.25rem',
                  bgcolor: isAssigned ? 'primary.lighter' : 'background.paper',
                  opacity: isAssigned ? 1 : 0.5
                }}
              >
                <Stack direction="row" spacing={1.5} alignItems="center">
                  <Avatar
                    sx={{
                      width: '2.5rem',
                      height: '2.5rem',
                      bgcolor: isAssigned ? `${getRoleColor(roleName)}.main` : 'grey.300'
                    }}
                  >
                    <AdminPanelSettingsIcon />
                  </Avatar>
                  <Box sx={{ flex: 1 }}>
                    <Typography variant="subtitle1" fontWeight="medium">
                      {role?.displayName || role?.name || '-'}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {roleName}
                    </Typography>
                  </Box>
                  {isAssigned && <Chip label="مُعيّن" size="small" color="primary" />}
                </Stack>
              </Paper>
            </Grid>
          );
        })}
      </Grid>

      {allRoles.length === 0 && <Alert severity="warning">لا توجد أدوار متاحة في النظام</Alert>}
    </Box>
  );
};

// ============================================================================
// EFFECTIVE PERMISSIONS DISPLAY (read-only — WAAD-RBAC-PHASE-1-FOUNDATION)
// ============================================================================

/**
 * Shows the user's effective permissions: role-based permissions plus any
 * active audited overrides (backend already merges both — see
 * EffectivePermissionService). There is no override CRUD UI yet, so this is
 * read-only; per WAAD-RBAC-PHASE-2-FRONTEND-INTEGRATION requirement 5, that
 * is tracked as a follow-up: WAAD-RBAC-PHASE-3-USER-OVERRIDES-UI.
 */
const EffectivePermissionsDisplay = ({ userId }) => {
  const [permissions, setPermissions] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        setLoading(true);
        setError(null);
        const data = await usersService.getEffectivePermissions(userId);
        if (!cancelled) setPermissions(Array.isArray(data) ? data : []);
      } catch (err) {
        if (!cancelled) {
          setError(err?.response?.data?.message || 'فشل تحميل الصلاحيات الفعلية');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [userId]);

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: '2.0rem' }}>
        <CircularLoader />
      </Box>
    );
  }

  if (error) {
    return <Alert severity="error">{error}</Alert>;
  }

  const list = permissions || [];

  return (
    <Box>
      <Alert severity="info" icon={<ShieldIcon />} sx={{ mb: '1.0rem' }}>
        {RbacUiLabels.inheritedPermissions} — لا توجد بعد واجهة لإدارة الاستثناءات الفردية ({RbacUiLabels.overridePermissions});
        هذه القائمة للعرض فقط حالياً.
      </Alert>

      {list.length === 0 ? (
        <Alert severity="warning">لا توجد صلاحيات فعلية لهذا المستخدم</Alert>
      ) : (
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          {list.map((code) => {
            const isCritical = CriticalSecurityPermissions.includes(code);
            return (
              <Chip
                key={code}
                label={PermissionLabels[code] || code}
                size="small"
                color={isCritical ? 'error' : 'default'}
                variant={isCritical ? 'filled' : 'outlined'}
                icon={isCritical ? <LockIcon sx={{ fontSize: '14px !important' }} /> : undefined}
                title={isCritical ? RbacUiLabels.sensitivePermission : undefined}
              />
            );
          })}
        </Stack>
      )}
    </Box>
  );
};

// ============================================================================
// MAIN COMPONENT
// ============================================================================

const UserDetails = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user: currentUser } = useAuth();

  // ========================================
  // VALIDATE ID - Must be numeric
  // ========================================

  const numericId = Number(id);
  const isValidId = id && !isNaN(numericId) && numericId > 0;

  // State
  const [user, setUser] = useState(null);
  const [allRoles, setAllRoles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Derived state
  const userRoleIds = useMemo(() => {
    if (!user) return [];
    const roles = user.roles || (user.role ? [{ name: user.role }] : []);
    return roles
      .map((r) => {
        const found = allRoles.find((ar) => ar.name === r.name);
        return found ? found.id : null;
      })
      .filter(Boolean);
  }, [user, allRoles]);

  // Check if current user is SUPER_ADMIN
  const isSuperAdmin = currentUser?.roles?.includes('SUPER_ADMIN');
  const isCurrentUserWaadAdmin = currentUser?.roles?.includes('WAAD_ADMIN');
  const targetIsSuperAdmin = (user?.roles || (user?.role ? [{ name: user.role }] : [])).some(
    (r) => (typeof r === 'string' ? r : r?.name) === 'SUPER_ADMIN'
  );

  // ========================================
  // DATA LOADING
  // ========================================

  const loadData = useCallback(async () => {
    // Guard: Don't load if ID is invalid
    if (!isValidId) return;

    try {
      setLoading(true);
      setError(null);

      const [userRes] = await Promise.all([
        usersService.getUserById(numericId)
      ]);

      setUser(userRes?.data?.data || userRes?.data || null);
      // Use static roles from SystemRole
      setAllRoles(Object.values(SystemRole).map((name, idx) => ({ id: idx + 1, name, displayName: RoleDisplayNames[name]?.ar || name })));
    } catch (err) {
      console.error('[UserDetails] Load error:', err);
      setError(err?.response?.data?.message || 'فشل تحميل بيانات المستخدم');
    } finally {
      setLoading(false);
    }
  }, [numericId, isValidId]);

  useEffect(() => {
    // Redirect to users list if ID is invalid
    if (!isValidId) {
      console.warn('[UserDetails] Invalid user ID:', id);
      navigate('/admin/users', { replace: true });
      return;
    }
    loadData();
  }, [loadData, isValidId, id, navigate]);

  // ========================================
  // LOADING STATE
  // ========================================

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '25.0rem' }}>
        <CircularLoader />
      </Box>
    );
  }

  // ========================================
  // ERROR STATE
  // ========================================

  if (error) {
    return (
      <Box>
        <ModernPageHeader
          title="تفاصيل المستخدم"
          icon={PersonIcon}
          breadcrumbs={[
            { label: 'الرئيسية', path: '/' },
            { label: 'المستخدمين', path: '/admin/users' },
            { label: 'تفاصيل' }
          ]}
        />
        <Alert severity="error" sx={{ mt: '1.0rem' }}>
          {error}
        </Alert>
      </Box>
    );
  }

  // ========================================
  // MAIN RENDER
  // ========================================

  return (
    <Box>
      {/* ====== PAGE HEADER ====== */}
      <ModernPageHeader
        title={user?.fullName || user?.username || 'تفاصيل المستخدم'}
        subtitle="إدارة أدوار وصلاحيات المستخدم"
        icon={PersonIcon}
        breadcrumbs={[
          { label: 'الرئيسية', path: '/' },
          { label: 'المستخدمين', path: '/admin/users' },
          { label: user?.username || 'تفاصيل' }
        ]}
        actions={
          <Button variant="outlined" startIcon={<ArrowBackIcon />} onClick={() => navigate('/admin/users')}>
            العودة للقائمة
          </Button>
        }
      />

      {/* ====== USER INFO CARD ====== */}
      <MainCard sx={{ mb: '1.5rem' }}>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={3} alignItems={{ sm: 'center' }}>
          <Avatar sx={{ width: '5.0rem', height: '5.0rem', bgcolor: 'primary.main', fontSize: '2rem' }}>
            {getInitials(user?.fullName || user?.username)}
          </Avatar>

          <Box sx={{ flex: 1 }}>
            <Typography variant="h5" gutterBottom>
              {user?.fullName || user?.username || '-'}
            </Typography>
            <Typography variant="body2" color="text.secondary" gutterBottom>
              {user?.email || '-'}
            </Typography>
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              <Chip label={user?.enabled !== false ? 'نشط' : 'معطل'} color={user?.enabled !== false ? 'success' : 'default'} size="small" />
              {(user?.roles || (user?.role ? [{ name: user.role }] : [])).map((role) => (
                <Chip
                  key={role?.id || role?.name}
                  label={getRoleDisplayName(role?.name, 'ar') || role?.name || '-'}
                  color={getRoleColor(role?.name)}
                  size="small"
                  variant="light"
                />
              ))}
            </Stack>
          </Box>

          <Box sx={{ textAlign: { xs: 'left', sm: 'right' } }}>
            <Typography variant="caption" color="text.secondary" display="block">
              آخر دخول
            </Typography>
            <Typography variant="body2">
              {user?.lastLogin ? new Date(user.lastLogin).toLocaleDateString('en-GB') : 'لم يسجل الدخول'}
            </Typography>
          </Box>
        </Stack>
      </MainCard>

      {/* ====== WAAD_ADMIN viewing a SUPER_ADMIN account ====== */}
      {isCurrentUserWaadAdmin && targetIsSuperAdmin && (
        <Alert severity="warning" icon={<LockIcon />} sx={{ mb: '1.5rem' }}>
          {RbacUiLabels.protected}: {RbacUiLabels.superAdminProtected}
        </Alert>
      )}

      {/* ====== ROLES ====== */}
      <MainCard sx={{ mb: '1.5rem' }}>
        <RolesDisplay
          user={user}
          allRoles={allRoles}
          userRoleIds={userRoleIds}
        />
      </MainCard>

      {/* ====== EFFECTIVE PERMISSIONS ====== */}
      <MainCard title={RbacUiLabels.effectivePermissions}>
        <EffectivePermissionsDisplay userId={numericId} />
      </MainCard>
    </Box>
  );
};

export default UserDetails;

