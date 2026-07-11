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
  Paper,
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
import EditNoteIcon from '@mui/icons-material/EditNote';
import VerifiedIcon from '@mui/icons-material/Verified';
import HistoryIcon from '@mui/icons-material/History';

// Components
import HelpDialog from 'components/common/HelpDialog';
import ExceptionEditDialog from './ExceptionEditDialog';
/**
 * ═══════════════════════════════════════════════════════════════════════════
 * CONTRACT PRICE LIST TAB — the contract as a CONSUMER (MC-4B, design §4)
 * ═══════════════════════════════════════════════════════════════════════════
 * D3: exactly TWO primary actions — «استيراد قائمة أسعار» and «تعديل استثنائي».
 * D4: brief version history (v, date, status) — details live in the report.
 * Everything else is read-only: prices are edited BEFORE publishing, never here.
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
  const [exceptionItem, setExceptionItem] = useState(null);
  const [exceptionOpen, setExceptionOpen] = useState(false);

  useEffect(() => {
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
        search: search || undefined
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
    fetchItems();
  }, [fetchItems]);

  const active = summary?.activeVersion;
  const draft = summary?.draft;
  const history = summary?.history ?? [];

  return (
    <Box>
      {/* Header: help + the ONLY two primary actions (D3) */}
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} alignItems={{ md: 'center' }} sx={{ mb: '1.0rem' }}>
        <HelpDialog
          title="قائمة أسعار العقد — كيف؟"
          points={[
            'هذه الشاشة للاطلاع فقط: الأسعار السارية تأتي من آخر قائمة منشورة.',
            'لتحديث الأسعار: «استيراد قائمة أسعار» — يرفع ملف المرفق ويمر بالمراجعة والنشر.',
            'لتصحيح سعر واحد أو إضافة خدمة ناقصة: «تعديل استثنائي».',
            'لا يمكن تعديل الأسعار المنشورة مباشرة — هذا يحمي المطالبات القديمة.',
            'سجل النسخ يُظهر تاريخ كل قائمة؛ التفاصيل الكاملة في تقرير النسخة.',
            'ابحث باسم الخدمة أو كودها لمعرفة سعرها الساري.'
          ]}
        />
        <Box sx={{ flexGrow: 1 }} />
        <Button variant="contained" startIcon={<UploadFileIcon />} onClick={() => navigate(`/classification/imports?providerId=${providerId ?? ''}&open=1`)}>
          استيراد قائمة أسعار
        </Button>
      </Stack>

      {summaryError && (
        <Alert severity="warning" sx={{ mb: '1.0rem' }}>
          {summaryError}
        </Alert>
      )}

      <Grid container spacing={1.5} sx={{ mb: '1.0rem' }}>
        {/* Active version card */}
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
                  <Typography variant="h5" sx={{ fontWeight: 800 }}>v{active.versionNo}</Typography>
                  <Typography variant="caption" color="text.secondary">رقم القائمة</Typography>
                </Box>
                <Box>
                  <Typography variant="h5" sx={{ fontWeight: 800 }}>{active.serviceCount}</Typography>
                  <Typography variant="caption" color="text.secondary">خدمة</Typography>
                </Box>
                <Box>
                  <Typography variant="h5" sx={{ fontWeight: 800 }}>{money(active.totalValue)}</Typography>
                  <Typography variant="caption" color="text.secondary">القيمة الإجمالية</Typography>
                </Box>
                <Box>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>
                    {active.publishedAt ? new Date(active.publishedAt).toLocaleDateString('en-GB') : '—'}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    نُشرت بواسطة {active.publishedBy || '—'}
                  </Typography>
                  <Box>
                    <Button size="small" component={RouterLink} to={`/classification/versions/${active.id}`}>
                      عرض التقرير
                    </Button>
                  </Box>
                </Box>
              </Stack>
            ) : (
              <Alert severity="info">
                لا توجد قائمة أسعار منشورة لهذا العقد بعد — ابدأ بـ «استيراد قائمة أسعار».
              </Alert>
            )}
          </Paper>
        </Grid>

        {/* Brief history (D4) */}
        <Grid size={{ xs: 12, md: 5 }}>
          <Paper variant="outlined" sx={{ p: '1.0rem', height: '100%' }}>
            <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
              <HistoryIcon color="action" fontSize="small" />
              <Typography variant="subtitle2">سجل النسخ</Typography>
            </Stack>
            {history.length === 0 ? (
              <Typography variant="caption" color="text.secondary">لا يوجد سجل بعد</Typography>
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
                    <Typography variant="body2" sx={{ fontWeight: 700, minWidth: 32 }}>v{v.versionNo}</Typography>
                    <Typography variant="caption" color="text.secondary" sx={{ flex: 1 }}>
                      {v.date ? new Date(v.date).toLocaleDateString('en-GB') : '—'}
                    </Typography>
                    <Chip size="small" variant="outlined" color={VERSION_STATUS[v.status]?.color || 'default'} label={VERSION_STATUS[v.status]?.label || v.status} />
                  </Stack>
                ))}
              </Stack>
            )}
          </Paper>
        </Grid>
      </Grid>

      {/* Read-only price search — the officer's daily need */}
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
              <IconButton size="small" onClick={() => { setSearch(''); setPage(0); }}>
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
              <TableCell align="right" sx={{ width: '8rem' }}>السعر الساري</TableCell>
              <TableCell align="center" sx={{ width: '6rem' }}>إجراءات</TableCell>
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
                  <Typography color="text.secondary">
                    {search ? 'لا توجد خدمة مطابقة' : 'لا توجد أسعار منشورة'}
                  </Typography>
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
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>{item.serviceName}</Typography>
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
                    <Tooltip title="تعديل استثنائي">
                      <IconButton
                        size="small"
                        color="primary"
                        onClick={() => {
                          setExceptionItem(item);
                          setExceptionOpen(true);
                        }}
                      >
                        <EditNoteIcon fontSize="small" />
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
      {exceptionOpen && exceptionItem && active && (
        <ExceptionEditDialog
          open={exceptionOpen}
          selectedItem={exceptionItem}
          contractId={contractId}
          onClose={() => {
            setExceptionOpen(false);
            setExceptionItem(null);
          }}
          onSuccess={() => {
            setExceptionOpen(false);
            setExceptionItem(null);
            fetchItems(); // Refresh the list
          }}
        />
      )}
    </Box>
  );
};

ContractPriceListTab.propTypes = {
  contractId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]).isRequired,
  providerId: PropTypes.oneOfType([PropTypes.string, PropTypes.number])
};

export default ContractPriceListTab;
