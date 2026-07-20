import { useState } from 'react';

import useMediaQuery from '@mui/material/useMediaQuery';
import Stack from '@mui/material/Stack';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Tooltip from '@mui/material/Tooltip';
import Avatar from '@mui/material/Avatar';
import LocalHospitalIcon from '@mui/icons-material/LocalHospital';
import AppsIcon from '@mui/icons-material/Apps';

// project imports
import Profile from './Profile';
import ProviderThemeToggle from 'components/provider/ProviderThemeToggle';
// Arabic-only system – i18n disabled by design (Localization component removed)
// Notification bell + language toggle: to be implemented later (per request)
import FullScreen from './FullScreen';
import MobileSection from './MobileSection';
import HorizontalNavigation from './HorizontalNavigation';
import SystemCategoriesDialog from 'components/dashboard/SystemCategoriesDialog';

import useConfig from 'hooks/useConfig';
import useAuth from 'hooks/useAuth';
import { useRBAC } from 'api/rbac';
import { useCompanySettings } from 'contexts/CompanySettingsContext';
import { MenuOrientation } from 'config';
import DrawerHeader from 'layout/Dashboard/Drawer/DrawerHeader';

// ==============================|| HEADER - CONTENT ||============================== //

export default function HeaderContent() {
  const { state } = useConfig();
  const { user } = useAuth();
  const { isProviderRole: isProvider } = useRBAC();
  const { companyName, companyNameEn, primaryColor, getLogoSrc, settings } = useCompanySettings();

  const downLG = useMediaQuery((theme) => theme.breakpoints.down('lg'));

  const [categoriesOpen, setCategoriesOpen] = useState(false);

  const providerName = user?.providerName || null;

  // Display name: Arabic for RTL, English for LTR
  const displayName = companyName || companyNameEn || 'TBA';

  return (
    <>
      {state.menuOrientation === MenuOrientation.HORIZONTAL && !downLG && <DrawerHeader open={true} />}

      {/* ✅ System Logo/Title - Different for Provider */}
      {!downLG && (
        <Box sx={{ display: 'flex', alignItems: 'center', mr: '1.0rem' }}>
          {isProvider ? (
            // Provider Portal branding
            <Stack direction="row" spacing={1} alignItems="center">
              <Avatar sx={{ bgcolor: 'primary.main', width: '2.0rem', height: '2.0rem' }}>
                <LocalHospitalIcon sx={{ fontSize: '1.125rem' }} />
              </Avatar>
              <Box>
                <Typography variant="subtitle2" sx={{ fontWeight: 700, color: 'primary.main', lineHeight: 1.2 }}>
                  بوابة مقدم الخدمة
                </Typography>
                {providerName && (
                  <Typography variant="caption" sx={{ color: 'text.secondary', fontSize: '0.7rem' }}>
                    {providerName}
                  </Typography>
                )}
              </Box>
            </Stack>
          ) : (
            // Company branding from settings (SINGLE SOURCE OF TRUTH)
            <Stack direction="row" spacing={1} alignItems="center">
              <Box
                component="img"
                src={getLogoSrc()}
                alt={displayName}
                sx={{ height: '2.0rem', width: 'auto', maxWidth: '6.25rem', objectFit: 'contain' }}
                onError={(e) => {
                  e.target.style.display = 'none';
                }}
              />
              <Box sx={{ display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
                <Typography
                  variant="subtitle2"
                  sx={{ fontWeight: 700, lineHeight: 1.1, color: 'primary.main', fontSize: '0.85rem', whiteSpace: 'nowrap' }}
                >
                  {displayName}
                </Typography>
                <Typography variant="caption" sx={{ color: 'text.secondary', fontSize: '0.75rem', lineHeight: 1, whiteSpace: 'nowrap' }}>
                  {settings?.businessType || 'إدارة التأمين الصحي'}
                </Typography>
              </Box>
            </Stack>
          )}
        </Box>
      )}

      {/* ✅ Navigation Horizontal - القائمة الأفقية (not for provider users — the System
          Categories launcher below already surfaces the same RBAC-filtered menu, and
          showing both was a duplicated/confusing navigation path for provider staff). */}
      {!downLG && !isProvider && <HorizontalNavigation />}

      <Box sx={{ flexGrow: 1 }} />

      {/* ✅ System Categories launcher (centered) — opens the full app grid */}
      <Tooltip title="فئات النظام" disableInteractive>
        <Button
          onClick={() => setCategoriesOpen(true)}
          variant="outlined"
          color="primary"
          startIcon={<AppsIcon />}
          aria-label="فئات النظام"
          sx={{
            borderRadius: 2,
            px: { xs: 1, sm: 1.75 },
            py: 0.5,
            fontWeight: 700,
            fontSize: '0.8rem',
            textTransform: 'none',
            whiteSpace: 'nowrap',
            '& .MuiButton-startIcon': { mr: { xs: 0, sm: 0.5 }, ml: { xs: 0, sm: -0.25 } }
          }}
        >
          <Box component="span" sx={{ display: { xs: 'none', sm: 'inline' } }}>
            فئات النظام
          </Box>
        </Button>
      </Tooltip>

      <Box sx={{ flexGrow: 1 }} />

      <Stack direction="row" sx={{ alignItems: 'center', gap: 1 }}>
        {/* ✅ Theme Toggle (User Preference) - Available for ALL Users */}
        <ProviderThemeToggle />
        {/* Notification bell + language toggle: to be added later (per request) */}
        {!downLG && <FullScreen />}
        {!downLG && <Profile />}
        {downLG && <MobileSection />}
      </Stack>

      <SystemCategoriesDialog open={categoriesOpen} onClose={() => setCategoriesOpen(false)} primaryColor={primaryColor} />
    </>
  );
}
