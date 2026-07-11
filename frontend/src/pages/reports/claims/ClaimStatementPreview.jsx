import { useState, useRef, useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useSnackbar } from 'notistack';
import ClaimStatementPreviewLayout from 'components/reports/claims/ClaimStatementPreviewLayout';

const ClaimStatementPreview = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { enqueueSnackbar } = useSnackbar();

  const queryParams = new URLSearchParams(location.search);
  const claimIds = queryParams.get('ids');
  const onlyRejected = queryParams.get('onlyRejected') === 'true';
  const batchCode = queryParams.get('batchCode') || '';
  const iframeRef = useRef(null);
  const cacheBustRef = useRef(Date.now());
  const [loading, setLoading] = useState(true);

  const previewUrl = `/api/reports/claims/html?claimIds=${claimIds}&onlyRejected=${onlyRejected}&batchCode=${encodeURIComponent(batchCode)}&_t=${cacheBustRef.current}`;
  const pdfUrl = `/api/reports/claims/pdf?claimIds=${claimIds}&onlyRejected=${onlyRejected}&batchCode=${encodeURIComponent(batchCode)}&_t=${cacheBustRef.current}`;
  const claimCount = claimIds ? claimIds.split(',').length : 0;

  useEffect(() => {
    if (!claimIds) {
      enqueueSnackbar('لا توجد مطالبات محددة للعرض', { variant: 'warning' });
      navigate(-1);
    }
  }, [claimIds, navigate, enqueueSnackbar]);

  const handlePrint = () => {
    const printWindow = window.open(pdfUrl, '_blank', 'noopener,noreferrer');

    if (!printWindow) {
      enqueueSnackbar('تعذر فتح نسخة الطباعة. تأكد من السماح بالنوافذ المنبثقة.', { variant: 'warning' });
      return;
    }

    enqueueSnackbar('تم فتح نسخة PDF للطباعة بصيغة ثابتة.', { variant: 'info' });
  };

  return (
    <ClaimStatementPreviewLayout
      claimCount={claimCount}
      loading={loading}
      onBack={() => navigate(-1)}
      onPrint={handlePrint}
    >
      {claimIds && (
        <iframe
          title="Claim Statement Preview"
          ref={iframeRef}
          src={previewUrl}
          style={{ width: '100%', height: '100%', minHeight: '297mm', border: 'none', display: 'block' }}
          onLoad={() => setLoading(false)}
        />
      )}
    </ClaimStatementPreviewLayout>
  );
};

export default ClaimStatementPreview;


