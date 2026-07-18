import { Link as RouterLink } from 'react-router-dom';

// material-ui
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Divider from '@mui/material/Divider';
import Link from '@mui/material/Link';
import { alpha, useTheme } from '@mui/material/styles';

// project imports
import useAuth from 'hooks/useAuth';
import AuthLogin from 'sections/auth/jwt/AuthLogin';
import { useCompanySettings } from 'contexts/CompanySettingsContext';

// icons
import VerifiedUserOutlinedIcon from '@mui/icons-material/VerifiedUserOutlined';

const GOLD = '#D8B25A';

// ================================|| JWT - LOGIN (split-screen) ||================================ //

export default function Login() {
  const { isLoggedIn } = useAuth();
  const theme = useTheme();
  const { companyName, getLogoSrc } = useCompanySettings();

  const brandName = `${companyName || 'وعد'} الطبي`;

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        p: { xs: 0, md: 3 },
        background: `linear-gradient(135deg, ${alpha(theme.palette.primary.lighter, 0.5)} 0%, ${theme.palette.background.default} 100%)`
      }}
    >
      <Box
        sx={{
          width: '100%',
          maxWidth: '68rem',
          minHeight: { xs: '100vh', md: '40rem' },
          display: 'flex',
          overflow: 'hidden',
          borderRadius: { xs: 0, md: 4 },
          boxShadow: { xs: 'none', md: '0 30px 80px rgba(9,20,22,0.28)' },
          bgcolor: 'background.paper'
        }}
      >
        {/* ── Brand panel (appears on the right in RTL) ─────────────────────── */}
        <Box
          sx={{
            flex: 1.05,
            display: { xs: 'none', md: 'flex' },
            flexDirection: 'column',
            p: { md: 5 },
            color: '#EAF5F3',
            position: 'relative',
            overflow: 'hidden',
            background: 'linear-gradient(155deg, #123f3a 0%, #0d302c 55%, #0a2724 100%)'
          }}
        >
          {/* soft glow + grid texture */}
          <Box
            sx={{
              position: 'absolute',
              inset: 0,
              backgroundImage:
                'radial-gradient(600px circle at 80% 10%, rgba(216,178,90,0.10), transparent 45%), linear-gradient(rgba(255,255,255,0.03) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.03) 1px, transparent 1px)',
              backgroundSize: 'auto, 30px 30px, 30px 30px',
              pointerEvents: 'none'
            }}
          />

          {/* Top: brand identity (system logo) */}
          <Stack direction="row" spacing={1.25} alignItems="center" sx={{ position: 'relative' }}>
            <Box
              sx={{
                minWidth: 56,
                height: 56,
                px: 1,
                borderRadius: 2,
                bgcolor: alpha('#ffffff', 0.95),
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                boxShadow: '0 6px 18px rgba(0,0,0,0.18)'
              }}
            >
              <Box
                component="img"
                src={getLogoSrc()}
                alt={brandName}
                sx={{ height: 40, width: 'auto', maxWidth: 120, objectFit: 'contain' }}
                onError={(e) => {
                  e.target.style.display = 'none';
                }}
              />
            </Box>
            <Box>
              <Typography sx={{ fontWeight: 800, fontSize: '1.05rem', lineHeight: 1.2 }}>{brandName}</Typography>
              <Typography sx={{ fontSize: '0.72rem', color: alpha('#EAF5F3', 0.7), letterSpacing: 0.5 }}>
                WaadCare · TPA Platform
              </Typography>
            </Box>
          </Stack>

          {/* Middle: badge + headline + description (vertically centered) */}
          <Box sx={{ position: 'relative', flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center', py: 4 }}>
            <Box
              sx={{
                display: 'inline-flex',
                alignSelf: 'flex-start',
                alignItems: 'center',
                gap: 1,
                px: 1.5,
                py: 0.5,
                mb: 2.5,
                borderRadius: 999,
                bgcolor: alpha('#ffffff', 0.08),
                border: '1px solid',
                borderColor: alpha('#ffffff', 0.14)
              }}
            >
              <Box sx={{ width: 7, height: 7, borderRadius: '50%', bgcolor: GOLD }} />
              <Typography sx={{ fontSize: '0.72rem', fontWeight: 600, color: alpha('#EAF5F3', 0.9) }}>
                نظام متكامل لإدارة النفقات الطبية
              </Typography>
            </Box>

            <Typography sx={{ fontSize: { md: '2.3rem' }, fontWeight: 800, lineHeight: 1.25, mb: 2.5 }}>
              مرحباً بك في{' '}
              <Box
                component="span"
                sx={{
                  background: `linear-gradient(135deg, ${GOLD}, #F0D08A)`,
                  backgroundClip: 'text',
                  WebkitBackgroundClip: 'text',
                  WebkitTextFillColor: 'transparent'
                }}
              >
                منصة {brandName}
              </Box>
            </Typography>

            <Typography sx={{ fontSize: '0.98rem', color: alpha('#EAF5F3', 0.82), lineHeight: 2, maxWidth: '32rem' }}>
              نظام مؤسسي حديث لإدارة المستفيدين وجهات العمل ومقدّمي الخدمات، ومعالجة المطالبات والموافقات بكفاءة وشفافية كاملة.
            </Typography>
          </Box>
        </Box>

        {/* ── Form panel (appears on the left in RTL) ───────────────────────── */}
        <Box
          sx={{
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'center',
            p: { xs: 3, sm: 5 },
            maxWidth: { md: '30rem' },
            mx: 'auto',
            width: '100%'
          }}
        >
          <Typography sx={{ fontSize: '0.78rem', fontWeight: 700, letterSpacing: 0.5, color: 'primary.main', mb: 0.5 }}>
            بوابة الدخول الآمنة
          </Typography>
          <Typography variant="h3" sx={{ fontWeight: 800, mb: 0.75 }}>
            تسجيل الدخول
          </Typography>
          <Typography variant="body2" sx={{ color: 'text.secondary', mb: 3 }}>
            أدخل بياناتك للوصول إلى لوحة تحكم نظام {brandName}.
          </Typography>

          <AuthLogin isDemo={isLoggedIn} />

          <Divider sx={{ my: 2.5 }}>
            <Typography variant="caption" color="text.secondary">
              دخول آمن ومشفّر
            </Typography>
          </Divider>

          {/* Gold security note */}
          <Stack
            direction="row"
            spacing={1.25}
            sx={{
              p: 1.5,
              borderRadius: 2,
              alignItems: 'flex-start',
              bgcolor: alpha(GOLD, 0.1),
              border: '1px solid',
              borderColor: alpha(GOLD, 0.4)
            }}
          >
            <VerifiedUserOutlinedIcon sx={{ fontSize: '1.2rem', color: '#A9812F', mt: '0.1rem' }} />
            <Typography variant="caption" sx={{ color: 'text.secondary', lineHeight: 1.7 }}>
              الدخول متاح فقط للمستخدمين المعتمدين من إدارة النظام. للحصول على حساب، تواصل مع مسؤول الحسابات في مؤسستك.
            </Typography>
          </Stack>

          {/* Footer */}
          <Stack direction="row" sx={{ mt: 3, justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 1 }}>
            <Typography variant="caption" color="text.secondary">
              © WaadCare 2026 · جميع الحقوق محفوظة
            </Typography>
            <Link component={RouterLink} to="/" variant="caption" color="primary" sx={{ fontWeight: 600, textDecoration: 'none' }}>
              العودة للرئيسية ←
            </Link>
          </Stack>
        </Box>
      </Box>
    </Box>
  );
}
