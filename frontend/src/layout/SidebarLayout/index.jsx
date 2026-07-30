/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * TBA WAAD SYSTEM - ENTERPRISE NAVBAR LAYOUT ARCHITECTURE
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Professional enterprise-grade layout using top navigation instead of sidebar.
 *
 * ARCHITECTURE (NON-NEGOTIABLE):
 * - TopBar: 64px height - Logo, Horizontal Navigation Menu, User, Profile
 * - Content: 100% width fill, NO max-width containers
 * - Layout: 100vw × 100vh viewport occupation
 *
 * @author TBA WAAD Development Team
 * @version 5.0.0 - Navbar Architecture
 * ═══════════════════════════════════════════════════════════════════════════════
 */

import { useState, useCallback, createContext, useContext } from 'react';
import { Outlet, useLocation, Navigate, useNavigate } from 'react-router-dom';
import {
  Box,
  Drawer,
  IconButton,
  Typography,
  Divider,
  Stack,
  Tooltip,
  useMediaQuery,
  useTheme,
  alpha,
  styled,
  Button,
  ListItemIcon,
  ListItemText,
  Avatar,
  List,
  ListItem,
  ListItemButton,
  Collapse
} from '@mui/material';
import { Menu as MenuIcon, ExpandMore as ExpandMoreIcon, Logout as LogoutIcon, Apps as AppsIcon } from '@mui/icons-material';

// Project imports
import useAuth from 'hooks/useAuth';
import { useRBAC } from 'api/rbac';
import useRBACSidebar from 'hooks/useRBACSidebar';
import Loader from 'components/Loader';
import PageErrorBoundary from 'components/SafeStates/PageErrorBoundary';
import { useCompanySettings } from 'contexts/CompanySettingsContext';
import SimpleBar from 'components/third-party/SimpleBar';
import Profile from 'layout/Dashboard/Header/HeaderContent/Profile';
import SystemCategoriesDialog from 'components/dashboard/SystemCategoriesDialog';

// ═══════════════════════════════════════════════════════════════════════════════
// CONSTANTS & STYLES
// ═══════════════════════════════════════════════════════════════════════════════

const TOPBAR_HEIGHT = '4rem'; // Relative to root font size

const MainContent = styled(Box)(({ theme }) => ({
  display: 'flex',
  flexDirection: 'column',
  width: '100%',
  minWidth: 0,
  height: '100vh',
  overflow: 'hidden'
}));

// Enterprise TopBar - 64px
const TopBar = styled(Box)(({ theme }) => ({
  height: TOPBAR_HEIGHT,
  minHeight: TOPBAR_HEIGHT,
  maxHeight: TOPBAR_HEIGHT,
  backgroundColor: theme.palette.background.paper,
  borderBottom: `1px solid ${theme.palette.divider}`,
  flexShrink: 0,
  zIndex: theme.zIndex.appBar
}));

// ═══════════════════════════════════════════════════════════════════════════════
// MOBILE NAVIGATION COMPONENTS (DRAWER)
// ═══════════════════════════════════════════════════════════════════════════════

const MobileNavItem = ({ item, level = 0, onClose }) => {
  const navigate = useNavigate();
  const location = useLocation();
  const theme = useTheme();

  if (!item || item.type === 'divider') {
    return <Divider sx={{ my: 1, mx: '1.0rem' }} />;
  }

  const isActive = item.url === location.pathname || (item.url && location.pathname.startsWith(item.url + '/'));
  const Icon = item.icon;
  const paddingLeft = 16 + level * 24;

  const handleClick = () => {
    if (item.url) {
      navigate(item.url);
      if (onClose) onClose();
    }
  };

  return (
    <ListItem disablePadding sx={{ display: 'block' }}>
      <ListItemButton
        onClick={handleClick}
        sx={{
          minHeight: '2.5rem',
          px: '0.75rem',
          pl: `${paddingLeft / 16}rem`,
          borderRadius: 1,
          mx: 1,
          my: 0.125,
          backgroundColor: isActive ? alpha(theme.palette.primary.main, 0.12) : 'transparent',
          color: isActive ? 'primary.main' : 'text.primary',
          '&:hover': {
            backgroundColor: isActive ? alpha(theme.palette.primary.main, 0.16) : 'transparent'
          }
        }}
      >
        {Icon && (
          <ListItemIcon sx={{ minWidth: '2.5rem', color: isActive ? 'primary.main' : 'text.secondary' }}>
            <Icon sx={{ fontSize: '1.4rem' }} />
          </ListItemIcon>
        )}
        <ListItemText
          primary={item.title}
          primaryTypographyProps={{
            fontSize: level === 0 ? '0.875rem' : '0.82rem',
            fontWeight: isActive ? 600 : 400
          }}
        />
      </ListItemButton>
    </ListItem>
  );
};

const MobileNavCollapse = ({ item, level = 0, onClose }) => {
  const [open, setOpen] = useState(false);
  const theme = useTheme();
  const Icon = item.icon;
  const paddingLeft = 16 + level * 24;

  const handleToggle = () => setOpen(!open);

  return (
    <>
      <ListItem disablePadding sx={{ display: 'block' }}>
        <ListItemButton
          onClick={handleToggle}
          sx={{
            minHeight: '2.5rem',
            px: '0.75rem',
            pl: `${paddingLeft / 16}rem`,
            borderRadius: 1,
            mx: 1,
            my: 0.125,
            color: 'text.primary'
          }}
        >
          {Icon && (
            <ListItemIcon sx={{ minWidth: '2.5rem', color: 'text.secondary' }}>
              <Icon sx={{ fontSize: '1.4rem' }} />
            </ListItemIcon>
          )}
          <ListItemText primary={item.title} primaryTypographyProps={{ fontSize: '0.875rem', fontWeight: 500 }} />
          <ExpandMoreIcon sx={{ fontSize: '1.15rem', transform: open ? 'rotate(180deg)' : 'rotate(0deg)', transition: '0.2s' }} />
        </ListItemButton>
      </ListItem>
      <Collapse in={open} timeout="auto" unmountOnExit>
        <List component="div" disablePadding>
          {item.children?.map((child) => (
            <MobileNavItemRenderer key={child.id} item={child} level={level + 1} onClose={onClose} />
          ))}
        </List>
      </Collapse>
    </>
  );
};

const MobileNavGroup = ({ item, onClose }) => {
  if (!item.children || item.children.length === 0) return null;

  return (
    <Box component="nav" sx={{ mb: 1 }}>
      {item.title && (
        <Typography
          sx={{ fontSize: '0.7rem', fontWeight: 700, textTransform: 'uppercase', color: 'text.secondary', px: '1.0rem', py: 1, mt: 1 }}
        >
          {item.title}
        </Typography>
      )}
      <List disablePadding>
        {item.children.map((child) => (
          <MobileNavItemRenderer key={child.id} item={child} level={0} onClose={onClose} />
        ))}
      </List>
    </Box>
  );
};

const MobileNavItemRenderer = ({ item, level, onClose }) => {
  if (!item) return null;
  switch (item.type) {
    case 'group':
      return <MobileNavGroup item={item} onClose={onClose} />;
    case 'collapse':
      return <MobileNavCollapse item={item} level={level} onClose={onClose} />;
    case 'item':
      return <MobileNavItem item={item} level={level} onClose={onClose} />;
    case 'divider':
      return <Divider sx={{ my: 1, mx: '1.0rem' }} />;
    default:
      return null;
  }
};

// ═══════════════════════════════════════════════════════════════════════════════
// MAIN LAYOUT COMPONENT
// ═══════════════════════════════════════════════════════════════════════════════

// Context kept for backwards compatibility if any deeply nested component uses it
const SidebarContext = createContext({
  expanded: true,
  toggleExpanded: () => {},
  setExpanded: () => {},
  openGroups: {},
  toggleGroup: () => {}
});
export const useSidebar = () => useContext(SidebarContext);

export default function SidebarLayout() {
  const theme = useTheme();
  const { user, logout, authStatus } = useAuth();
  const { companyName, companyNameEn, getLogoSrc, settings, businessType, primaryColor } = useCompanySettings();
  const isMobile = useMediaQuery(theme.breakpoints.down('lg'));

  const [mobileOpen, setMobileOpen] = useState(false);
  const toggleMobile = useCallback(() => setMobileOpen((prev) => !prev), []);
  const [categoriesOpen, setCategoriesOpen] = useState(false);

  const { sidebarGroups, loading } = useRBACSidebar();
  const { isProviderRole: isProvider } = useRBAC();

  // Wait for session check to complete before making redirect decisions
  if (authStatus === 'INITIALIZING') {
    return <Loader />;
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  const displayName = companyName || companyNameEn || 'TBA';
  const primaryRole = user.roles?.[0]?.replace('_', ' ') || 'مستخدم';

  return (
    <SidebarContext.Provider
      value={{ expanded: false, toggleExpanded: () => {}, setExpanded: () => {}, openGroups: {}, toggleGroup: () => {} }}
    >
      <Box sx={{ display: 'flex', width: '100vw', height: '100vh', overflow: 'hidden' }}>
        {/* Mobile Drawer (Only shown on small screens) */}
        {isMobile && (
          <Drawer
            variant="temporary"
            anchor="right"
            open={mobileOpen}
            onClose={toggleMobile}
            ModalProps={{ keepMounted: true }}
            sx={{ '& .MuiDrawer-paper': { width: '17.5rem', boxSizing: 'border-box' } }}
          >
            <Box sx={{ p: '1.0rem', display: 'flex', alignItems: 'center', borderBottom: 1, borderColor: 'divider' }}>
              <Typography variant="h6" fontWeight={700} color="primary">
                القائمة الرئيسية
              </Typography>
            </Box>
            <SimpleBar style={{ height: 'calc(100vh - 140px)' }}>
              <Box sx={{ py: 1 }}>
                {!loading &&
                  sidebarGroups?.map((group) => <MobileNavItemRenderer key={group.id} item={group} level={0} onClose={toggleMobile} />)}
              </Box>
            </SimpleBar>
            <Box
              sx={{
                position: 'absolute',
                bottom: 0,
                width: '100%',
                p: '1.0rem',
                borderTop: 1,
                borderColor: 'divider',
                bgcolor: 'background.paper'
              }}
            >
              <Stack direction="row" alignItems="center" spacing={1.5}>
                <Avatar sx={{ width: '2.25rem', height: '2.25rem', bgcolor: isProvider ? 'success.main' : 'primary.main' }}>
                  {user.fullName?.[0] || user.username?.[0] || 'U'}
                </Avatar>
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <Typography variant="subtitle2" noWrap fontWeight={600}>
                    {user.fullName || user.username}
                  </Typography>
                  <Typography variant="caption" color="text.secondary" noWrap>
                    {primaryRole}
                  </Typography>
                </Box>
                <IconButton size="small" onClick={logout} color="error">
                  <LogoutIcon sx={{ fontSize: '1.2rem' }} />
                </IconButton>
              </Stack>
            </Box>
          </Drawer>
        )}

        {/* Main Content Area */}
        <MainContent>
          <TopBar>
            <Box
              sx={{
                width: '100%',
                maxWidth: '100rem',
                mx: 'auto',
                px: { xs: 2, sm: 3 },
                height: '100%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between'
              }}
            >
              {/* Left Section: Logo & Mobile Menu */}
              <Stack direction="row" alignItems="center" spacing={2} sx={{ minWidth: '12.5em' }}>
                {isMobile && (
                  <IconButton onClick={toggleMobile} edge="start">
                    <MenuIcon sx={{ fontSize: '1.5rem' }} />
                  </IconButton>
                )}
                <Box
                  component="img"
                  src={getLogoSrc()}
                  alt={displayName}
                  sx={{ height: '2.4rem', width: 'auto', maxWidth: '8rem', objectFit: 'contain' }}
                  onError={(e) => {
                    e.target.style.display = 'none';
                  }}
                />
                {!isMobile && (
                  <Box>
                    <Typography variant="subtitle1" fontWeight={700} color="primary.main" lineHeight={1.1}>
                      {displayName}
                    </Typography>
                    <Typography
                      variant="caption"
                      sx={{
                        display: 'block',
                        fontSize: '0.75rem',
                        color: 'text.secondary',
                        mt: -0.2,
                        opacity: 0.85
                      }}
                    >
                      {businessType}
                    </Typography>
                  </Box>
                )}
              </Stack>

              {/* Center Section: Categories launcher + Desktop Navigation */}
              {!isMobile && !loading && (
                <Stack direction="row" alignItems="center" spacing={1} sx={{ flex: 1, justifyContent: 'center', minWidth: 0 }}>
                  <Button
                    onClick={() => setCategoriesOpen(true)}
                    variant="outlined"
                    color="primary"
                    startIcon={<AppsIcon />}
                    sx={{ flexShrink: 0, borderRadius: 2, px: 1.5, fontWeight: 700, fontSize: '0.85rem', whiteSpace: 'nowrap' }}
                  >
                    فئات النظام
                  </Button>
                  {/* Provider users navigate solely through the System Categories launcher above —
                      showing this group nav too would duplicate the exact same RBAC-filtered links. */}
                </Stack>
              )}

              {/* Right Section: Categories (mobile) + Profile (name only) */}
              <Stack
                direction="row"
                alignItems="center"
                spacing={1}
                sx={{ minWidth: { xs: 'auto', lg: '10rem' }, justifyContent: 'flex-end' }}
              >
                {isMobile && (
                  <Tooltip title="فئات النظام" disableInteractive>
                    <IconButton onClick={() => setCategoriesOpen(true)} color="primary" aria-label="فئات النظام">
                      <AppsIcon />
                    </IconButton>
                  </Tooltip>
                )}
                {!isMobile && <Profile />}
              </Stack>
            </Box>
          </TopBar>

          {/* Page Content */}
          <Box
            component="main"
            sx={{
              flex: 1,
              width: '100%',
              minWidth: 0,
              height: `calc(100vh - ${TOPBAR_HEIGHT})`,
              display: 'flex',
              flexDirection: 'column',
              backgroundColor: (theme) => (theme.palette.mode === 'dark' ? 'background.default' : alpha(theme.palette.grey[100], 0.5))
            }}
          >
            {/* Scrollable Content Container */}
            <Box sx={{ flex: 1, overflow: 'auto', display: 'flex', flexDirection: 'column' }}>
              <Box
                sx={{
                  width: '100%',
                  maxWidth: '100rem',
                  mx: 'auto',
                  px: { xs: 2, sm: 3 },
                  py: { xs: 1, sm: '0.75rem' },
                  flex: 1,
                  display: 'flex',
                  flexDirection: 'column'
                }}
              >
                <PageErrorBoundary pageName="Dashboard Content">
                  <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0 }}>
                    <Outlet />
                  </Box>
                </PageErrorBoundary>
              </Box>
            </Box>

            {/* Uniform System Footer (Always Visible) */}
            <Box
              sx={{
                textAlign: 'center',
                py: 0.5,
                color: 'text.secondary',
                fontSize: '0.75rem',
                flexShrink: 0,
                bgcolor: 'background.paper',
                borderTop: 1,
                borderColor: 'divider',
                width: '100%'
              }}
            >
              Designed & Developed by TBA WAAD Team - 2026
            </Box>
          </Box>
        </MainContent>

        {/* System Categories launcher dialog */}
        <SystemCategoriesDialog open={categoriesOpen} onClose={() => setCategoriesOpen(false)} primaryColor={primaryColor} />
      </Box>
    </SidebarContext.Provider>
  );
}
