/**
 * سجل تغييرات الصلاحيات — WAAD-RBAC-USERS-ROLES-PERMISSIONS-COMPLETION-1
 *
 * Tab 4 of /admin/users. Real backend data only (RolePermissionAdminController
 * /audit-log) — replaces the earlier "قريبًا" placeholder now that the read
 * API exists. Covers role-permission-matrix changes, per-user permission
 * overrides, and login/logout, since all three already write to the same
 * UserAuditLog table.
 */

import { useEffect, useState } from 'react';

import {
  Alert,
  Box,
  Chip,
  CircularProgress,
  Grid,
  MenuItem,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Typography
} from '@mui/material';

import MainCard from 'components/MainCard';
import rolePermissionsService from 'services/rbac/rolePermissions.service';

const ACTION_LABELS_AR = {
  ROLE_PERMISSIONS_UPDATED: 'تحديث صلاحيات دور',
  PERMISSION_OVERRIDE_GRANTED: 'منح صلاحية خاصة لمستخدم',
  PERMISSION_OVERRIDE_REVOKED: 'سحب صلاحية خاصة من مستخدم',
  LOGIN_SUCCESS: 'تسجيل دخول ناجح',
  LOGIN_FAILED: 'محاولة دخول فاشلة',
  LOGOUT: 'تسجيل خروج',
  PASSWORD_CHANGE: 'تغيير كلمة المرور',
  PASSWORD_RESET: 'إعادة تعيين كلمة المرور',
  ACCOUNT_LOCKED: 'قفل الحساب',
  ACCOUNT_UNLOCKED: 'فتح الحساب',
  USER_CREATED: 'إنشاء مستخدم',
  USER_UPDATED: 'تعديل مستخدم',
  USER_DELETED: 'حذف مستخدم',
  USER_ACTIVATED: 'تفعيل مستخدم',
  USER_DEACTIVATED: 'تعطيل مستخدم'
};

const ACTION_COLORS = {
  ROLE_PERMISSIONS_UPDATED: 'secondary',
  PERMISSION_OVERRIDE_GRANTED: 'success',
  PERMISSION_OVERRIDE_REVOKED: 'error',
  LOGIN_SUCCESS: 'info',
  LOGIN_FAILED: 'error',
  LOGOUT: 'default',
  ACCOUNT_LOCKED: 'error',
  ACCOUNT_UNLOCKED: 'success'
};

const formatDateTime = (value) => {
  if (!value) return '-';
  try {
    return new Date(value).toLocaleString('en-GB', { hour12: false });
  } catch {
    return value;
  }
};

const AuditLogTab = () => {
  const [entries, setEntries] = useState([]);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(20);
  const [actionFilter, setActionFilter] = useState('');

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        setLoading(true);
        setError(null);
        const data = await rolePermissionsService.getAuditLog({
          action: actionFilter || undefined,
          page,
          size: rowsPerPage
        });
        if (cancelled) return;
        setEntries(Array.isArray(data?.content) ? data.content : []);
        setTotalElements(data?.totalElements || 0);
      } catch (err) {
        if (!cancelled) {
          setError(err?.response?.data?.messageAr || err?.response?.data?.message || 'فشل تحميل سجل التغييرات');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [page, rowsPerPage, actionFilter]);

  return (
    <MainCard title="سجل تغييرات الصلاحيات">
      <Alert severity="info" sx={{ mb: '1.0rem' }}>
        يشمل هذا السجل: تغييرات مصفوفة صلاحيات الأدوار، الصلاحيات الخاصة الممنوحة/المسحوبة لمستخدمين
        محددين، وعمليات تسجيل الدخول والخروج.
      </Alert>

      <Grid container spacing={2} sx={{ mb: '1.0rem' }}>
        <Grid size={{ xs: 12, sm: 6, md: 4 }}>
          <TextField
            select
            fullWidth
            label="نوع الحدث"
            value={actionFilter}
            onChange={(e) => {
              setActionFilter(e.target.value);
              setPage(0);
            }}
          >
            <MenuItem value="">كل الأحداث</MenuItem>
            {Object.entries(ACTION_LABELS_AR).map(([code, label]) => (
              <MenuItem key={code} value={code}>
                {label}
              </MenuItem>
            ))}
          </TextField>
        </Grid>
      </Grid>

      {error && (
        <Alert severity="error" sx={{ mb: '1.0rem' }}>
          {error}
        </Alert>
      )}

      <TableContainer>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>الوقت</TableCell>
              <TableCell>الحدث</TableCell>
              <TableCell>المستهدف</TableCell>
              <TableCell>بواسطة</TableCell>
              <TableCell>التفاصيل</TableCell>
              <TableCell>العنوان (IP)</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={6} align="center" sx={{ py: '3.0rem' }}>
                  <CircularProgress size={28} />
                </TableCell>
              </TableRow>
            ) : entries.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} align="center" sx={{ py: '3.0rem' }}>
                  <Typography variant="body2" color="text.secondary">
                    لا توجد أحداث مطابقة
                  </Typography>
                </TableCell>
              </TableRow>
            ) : (
              entries.map((entry) => (
                <TableRow key={entry.id} hover>
                  <TableCell>
                    <Typography variant="caption">{formatDateTime(entry.createdAt)}</Typography>
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={ACTION_LABELS_AR[entry.action] || entry.action}
                      size="small"
                      color={ACTION_COLORS[entry.action] || 'default'}
                      variant="outlined"
                    />
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2">
                      {entry.targetUsername || (entry.targetUserId ? `مستخدم محذوف (#${entry.targetUserId})` : '-')}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2">
                      {entry.performedByUsername || (entry.performedByUserId ? `مستخدم محذوف (#${entry.performedByUserId})` : '-')}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block', maxWidth: '25rem' }}>
                      {entry.details || '-'}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="caption" color="text.secondary">
                      {entry.ipAddress || '-'}
                    </Typography>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </TableContainer>

      <TablePagination
        component={Box}
        count={totalElements}
        page={page}
        onPageChange={(e, newPage) => setPage(newPage)}
        rowsPerPage={rowsPerPage}
        onRowsPerPageChange={(e) => {
          setRowsPerPage(parseInt(e.target.value, 10));
          setPage(0);
        }}
        rowsPerPageOptions={[10, 20, 50]}
        labelRowsPerPage="صفوف لكل صفحة"
      />
    </MainCard>
  );
};

export default AuditLogTab;
