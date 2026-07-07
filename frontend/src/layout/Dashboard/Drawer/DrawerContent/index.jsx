import useMediaQuery from '@mui/material/useMediaQuery';

// project imports
// NavUser removed - Phase D0: Profile access only in header menu
import NavCard from './NavCard';
import Navigation from './Navigation';
import SimpleBar from 'components/third-party/SimpleBar';
import { useGetMenuMaster } from 'api/menu';

// ==============================|| DRAWER CONTENT ||============================== //

export default function DrawerContent() {
  const { menuMaster } = useGetMenuMaster();
  const drawerOpen = menuMaster.isDashboardDrawerOpened;
  const downLG = useMediaQuery((theme) => theme.breakpoints.down('lg'));

  return (
    <>
      <SimpleBar sx={{ '& .simplebar-content': { display: 'flex', flexDirection: 'column' } }}>
        <Navigation />
        {drawerOpen && !downLG && <NavCard />}
      </SimpleBar>
      {/* NavUser removed - Profile access only in header menu (Phase D0) */}
    </>
  );
}
