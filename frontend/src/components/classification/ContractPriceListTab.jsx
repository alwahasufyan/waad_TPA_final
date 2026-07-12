import { useCallback, useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { useNavigate, Link as RouterLink } from 'react-router-dom';
import { classificationService } from 'services/api/classification.service';
import { getContractPricingItems } from 'services/api/provider-contracts.service';
import { formatCurrency } from 'utils/formatters';

// MUI
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Grid,
  IconButton,
  InputAdornment,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  Paper,
  Snackbar,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Tooltip,
  Typography
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import ClearIcon from '@mui/icons-material/Clear';
import UploadFileIcon from '@mui/icons-material/UploadFile';
import AddIcon from '@mui/icons-material/Add';
import HistoryIcon from '@mui/icons-material/History';
import VerifiedIcon from '@mui/icons-material/Verified';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import PriceChangeIcon from '@mui/icons-material/PriceChange';
import BlockIcon from '@mui/icons-material/Block';
import CategoryIcon from '@mui/icons-material/Category';

// Components
import HelpDialog from 'components/common/HelpDialog';
import {
  PriceCorrectionDialog,
  AddServiceDialog,
  DeactivateServiceDialog,
  ClassificationCorrectionDialog,
  PriceAuditDialog
} from './ContractPriceEditDialogs';

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * CONTRACT PRICE LIST TAB — MC-4C simplified (2026-07-12)
 * ═══════════════════════════════════════════════════════════════════════════
 * The contract is a CONSUMER of the active price list. Individual edits update
 * the active list IN PLACE (audited) — they never create a new version.
 *  • Top actions: رفع قائمة أسعار جديدة · إضافة خدمة جديدة · سجل التعديلات
 *  • Row actions: تعديل السعر · إيقاف الخدمة · تعديل التصنيف / الكود
 * A new version is created only by a full import or by restoring an archive.
 */

const money = (v) => (v == null ? '—' : formatCurrency(Number(v)));

const VERSION_STATUS = {
  ACTIVE: { label: 'سارية', color: 'success' },
  SUPERSEDED: { label: 'سابقة', color: 'default' },
  ARCHIVED: { label: 'مؤرشفة', color: 'default' },
  DRAFT: { label: 'بانتظار النشر', color: 'primary' }
};

const ContractPriceListTab = ({ contractId, providerId }) => {
  const navigate = useNavigate();

  const [summary, setSummary] = useState(null);
  const [summaryError, setSummaryError] = useState(null);

  const [items, setItems] = useState([]);
  const [totalItems, setTotalItems] = useState(0);
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [loading, setLoading] = useState(false);
  const [toast, setToast] = useState(null);

  // row-action menu
  const [menuAnchor, setMenuAnchor] = useState(null);
  const [menuItem, setMenuItem] = useState(null);

  // dialogs
  const [dialog, setDialog] = useState(null); // 'price' | 'deactivate' | 'classification' | 'add' | 'audit'
  const [dialogItem, setDialogItem] = useState(null);

  const loadSummary = useCallback(() => {
    classificationService
      .getContractPriceListSummary(contractId)
      .then(setSummary)
      .catch((err) => {
        console.error('pricelist summary failed:', err);
        setSummaryError('تعذر تحميل ملخص قائمة الأسعار');
      });
  }, [contractId]);

  const fetchItems = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getContractPricingItems(contractId, {
        page,
        size: rowsPerPage,
        q: search || undefined
      });
      const content = data?.content ?? data?.data?.content ?? [];
      setItems(Array.isArray(content) ? content : []);
      setTotalItems(data?.totalElements ?? data?.data?.totalElements ?? content.length);
    } catch (err) {
      console.error('pricing items failed:', err);
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, [contractId, page, rowsPerPage, search]);

  useEffect(() => {
    loadSummary();
  }, [loadSummary]);
  useEffect(() => {
    fetchItems();
  }, [fetchItems]);

  const active = summary?.activeVersion;
  const draft = summary?.draft;
  const history = summary?.history ?? [];

  const openMenu = (e, item) => {
    setMenuAnchor(e.currentTarget);
    setMenuItem(item);
  };
  const closeMenu = () => {
    setMenuAnchor(null);
    setMenuItem(null);
  };
  const openDialog = (name, item) => {
    setDialogItem(item || menuItem);
    setDialog(name);
    closeMenu();
  };
  const closeDialog = () => {
    setDialog(null);
    setDialogItem(null);
  };
  const onSaved = (msg) => {
    closeDialog();
    setToast(msg);
    fetchItems(); // stay on same page — no navigation, no new version
    loadSummary();
  };

  return (
    <Box>
      {/* Header: help + the three top actions (MC-4C) */}
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} alignItems={{ md: 'center' }} sx={{ mb: '1.0rem' }}>
        <HelpDialog
          title="قائمة أسعار العقد — كيف؟"
          points={[
            'الأسعار السارية تأتي من آخر قائمة منشورة لهذا العقد.',
            'لتعديل سعر خدمة أو إيقافها أو تعديل تصنيفها: استخدم إجراءات الصف (⋮) — تُحفظ فورًا دون إنشاء نسخة جديدة.',
            'لإضافة خدمة ناقصة: زر «إضافة خدمة جديدة».',
            'لتحديث القائمة بالكامل: «رفع قائمة أسعار جديدة» (ينشئ نسخة جديدة بعد المراجعة).',
            'كل تعديل يُسجَّل في «سجل التعديلات» بالقيمة القديمة والجديدة والسبب.',
            'المطالبات القديمة محمية: تعديل السعر الحالي لا يغيّرها.'
          ]}
        />
        <Box sx={{ flexGrow: 1 }} />
        <Button variant="outlined" startIcon={<HistoryIcon />} onClick={() => setDialog('audit')}>
          سجل التعديلات
        </Button>
        <Button variant="outlined" startIcon={<AddIcon />} onClick={() => setDialog('add')} disabled={!active}>
          إضافة خدمة جديدة
        </Button>
        <Button
          variant="contained"
          startIcon={<UploadFileIcon />}
          onClick={() => navigate(`/classification/imports?providerId=${providerId ?? ''}&open=1`)}
        >
          رفع قائمة أسعار جديدة
        </Button>
      </Stack>

      {summaryError && (
        <Alert severity="warning" sx={{ mb: '1.0rem' }}>
          {summaryError}
        </Alert>
      )}

      <Grid container spacing={1.5} sx={{ mb: '1.0rem' }}>
        {/* Active list card — leads with the list, not the version number (F7) */}
        <Grid size={{ xs: 12, md: 7 }}>
          <Paper variant="outlined" sx={{ p: '1.0rem', height: '100%' }}>
            <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
              <VerifiedIcon color={active ? 'success' : 'disabled'} fontSize="small" />
              <Typography variant="subtitle2">قائمة الأسعار السارية</Typography>
              {draft && (
                <Chip
                  size="small"
                  color="primary"
                  variant="outlined"
                  component={RouterLink}
                  to={`/classification/versions/${draft.id}`}
                  clickable
                  label="قائمة جديدة بانتظار النشر — افتح التقرير"
                />
              )}
            </Stack>
            {active ? (
              <Stack direction="row" spacing={3} flexWrap="wrap" useFlexGap>
                <Box>
                  <Typography variant="h5" sx={{ fontWeight: 800 }}>
                    {active.serviceCount}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    خدمة سارية
                  </Typography>
                </Box>
                <Box>
                  <Typography variant="h5" sx={{ fontWeight: 800 }}>
                    {money(active.totalValue)}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    القيمة الإجمالية
                  </Typography>
                </Box>
                <Box>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>
                    {active.publishedAt ? new Date(active.publishedAt).toLocaleDateString('en-GB') : '—'}
                  </Typography>
                  <Typography variant="caption" color="text.secondary" display="block">
                    سارية منذ · بواسطة {active.publishedBy || '—'}
                  </Typography>
                  <Typography variant="caption" color="text.disabled">
                    المصدر: {active.sourceType === 'IMPORT' ? 'استيراد' : active.sourceType === 'ROLLBACK' ? 'استرجاع' : 'قائمة'} · v
                    {active.versionNo}
                  </Typography>
                </Box>
              </Stack>
            ) : (
              <Alert severity="info">لا توجد قائمة أسعار سارية لهذا العقد بعد — ابدأ بـ «رفع قائمة أسعار جديدة».</Alert>
            )}
          </Paper>
        </Grid>

        {/* Brief version history */}
        <Grid size={{ xs: 12, md: 5 }}>
          <Paper variant="outlined" sx={{ p: '1.0rem', height: '100%' }}>
            <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
              <HistoryIcon color="action" fontSize="small" />
              <Typography variant="subtitle2">القوائم السابقة</Typography>
            </Stack>
            {history.length === 0 ? (
              <Typography variant="caption" color="text.secondary">
                لا يوجد سجل بعد
              </Typography>
            ) : (
              <Stack spacing={0.5}>
                {history.slice(0, 5).map((v) => (
                  <Stack
                    key={v.id}
                    direction="row"
                    spacing={1}
                    alignItems="center"
                    component={RouterLink}
                    to={`/classification/versions/${v.id}`}
                    sx={{ textDecoration: 'none', color: 'inherit', '&:hover': { bgcolor: 'action.hover' }, borderRadius: 1, px: 0.5 }}
                  >
                    <Typography variant="body2" sx={{ fontWeight: 700, minWidth: 32 }}>
                      v{v.versionNo}
                    </Typography>
                    <Typography variant="caption" color="text.secondary" sx={{ flex: 1 }}>
                      {v.date ? new Date(v.date).toLocaleDateString('en-GB') : '—'}
                    </Typography>
                    <Chip
                      size="small"
                      variant="outlined"
                      color={VERSION_STATUS[v.status]?.color || 'default'}
                      label={VERSION_STATUS[v.status]?.label || v.status}
                    />
                  </Stack>
                ))}
              </Stack>
            )}
          </Paper>
        </Grid>
      </Grid>

      {/* Search over active rows */}
      <TextField
        placeholder="ابحث عن خدمة (اسم أو كود) لمعرفة سعرها الساري..."
        value={search}
        onChange={(e) => {
          setSearch(e.target.value);
          setPage(0);
        }}
        size="small"
        fullWidth
        sx={{ mb: 1, maxWidth: 480 }}
        InputProps={{
          startAdornment: (
            <InputAdornment position="start">
              <SearchIcon color="action" />
            </InputAdornment>
          ),
          endAdornment: search ? (
            <InputAdornment position="end">
              <IconButton
                size="small"
                onClick={() => {
                  setSearch('');
                  setPage(0);
                }}
              >
                <ClearIcon fontSize="small" />
              </IconButton>
            </InputAdornment>
          ) : null
        }}
      />

      <TableContainer component={Paper} variant="outlined" sx={{ maxHeight: '30rem' }}>
        <Table size="small" stickyHeader>
          <TableHead>
            <TableRow>
              <TableCell sx={{ width: '8rem' }}>الكود</TableCell>
              <TableCell sx={{ minWidth: '16rem' }}>الخدمة</TableCell>
              <TableCell sx={{ minWidth: '12rem' }}>التصنيف</TableCell>
              <TableCell align="right" sx={{ width: '8rem' }}>
                السعر الساري
              </TableCell>
              <TableCell align="center" sx={{ width: '5rem' }}>
                إجراءات
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={5} align="center" sx={{ py: '2rem' }}>
                  <CircularProgress size={28} />
                </TableCell>
              </TableRow>
            ) : items.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} align="center" sx={{ py: '2rem' }}>
                  <Typography color="text.secondary">{search ? 'لا توجد خدمة مطابقة' : 'لا توجد أسعار سارية'}</Typography>
                </TableCell>
              </TableRow>
            ) : (
              items.map((item) => (
                <TableRow key={item.id} hover>
                  <TableCell>
                    <Typography variant="caption" sx={{ fontFamily: 'monospace', fontWeight: 700 }}>
                      {item.serviceCode || '—'}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {item.serviceName}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="caption" color="text.secondary">
                      {item.medicalCategoryName || item.categoryName || '—'}
                    </Typography>
                  </TableCell>
                  <TableCell align="right">
                    <Typography sx={{ fontWeight: 700 }} color="primary.main">
                      {money(item.contractPrice)}
                    </Typography>
                  </TableCell>
                  <TableCell align="center">
                    <Tooltip title="إجراءات الصف">
                      <IconButton size="small" onClick={(e) => openMenu(e, item)}>
                        <MoreVertIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {totalItems > 0 && (
        <TablePagination
          component="div"
          count={totalItems}
          page={page}
          onPageChange={(e, p) => setPage(p)}
          rowsPerPage={rowsPerPage}
          onRowsPerPageChange={(e) => {
            setRowsPerPage(parseInt(e.target.value, 10));
            setPage(0);
          }}
          rowsPerPageOptions={[10, 25, 50]}
          labelRowsPerPage="عدد الصفوف:"
          labelDisplayedRows={({ from, to, count }) => `${from}-${to} من ${count !== -1 ? count : `أكثر من ${to}`}`}
        />
      )}

      {/* Row-action menu */}
      <Menu anchorEl={menuAnchor} open={Boolean(menuAnchor)} onClose={closeMenu}>
        <MenuItem onClick={() => openDialog('price')}>
          <ListItemIcon>
            <PriceChangeIcon fontSize="small" />
          </ListItemIcon>
          <ListItemText>تعديل السعر</ListItemText>
        </MenuItem>
        <MenuItem onClick={() => openDialog('classification')}>
          <ListItemIcon>
            <CategoryIcon fontSize="small" />
          </ListItemIcon>
          <ListItemText>تعديل التصنيف / الكود</ListItemText>
        </MenuItem>
        <MenuItem onClick={() => openDialog('deactivate')} sx={{ color: 'error.main' }}>
          <ListItemIcon>
            <BlockIcon fontSize="small" color="error" />
          </ListItemIcon>
          <ListItemText>إيقاف الخدمة</ListItemText>
        </MenuItem>
      </Menu>

      {/* Dialogs */}
      <PriceCorrectionDialog open={dialog === 'price'} contractId={contractId} item={dialogItem} onClose={closeDialog} onSaved={onSaved} />
      <ClassificationCorrectionDialog
        open={dialog === 'classification'}
        contractId={contractId}
        item={dialogItem}
        onClose={closeDialog}
        onSaved={onSaved}
      />
      <DeactivateServiceDialog
        open={dialog === 'deactivate'}
        contractId={contractId}
        item={dialogItem}
        onClose={closeDialog}
        onSaved={onSaved}
      />
      <AddServiceDialog open={dialog === 'add'} contractId={contractId} onClose={closeDialog} onSaved={onSaved} />
      <PriceAuditDialog open={dialog === 'audit'} contractId={contractId} onClose={closeDialog} />

      <Snackbar
        open={Boolean(toast)}
        autoHideDuration={3500}
        onClose={() => setToast(null)}
        message={toast}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      />
    </Box>
  );
};

ContractPriceListTab.propTypes = {
  contractId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]).isRequired,
  providerId: PropTypes.oneOfType([PropTypes.string, PropTypes.number])
};

export default ContractPriceListTab;
