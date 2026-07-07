import { create } from 'zustand';
import menuItem from 'menu-items/components';

// ==============================|| MENU API - STATE MANAGEMENT ||============================== //

/**
 * Zustand store for menu state management
 * Replaces old Redux implementation
 */
export const useMenuStore = create((set) => ({
  openDrawer: true,
  openComponentDrawer: true,
  menuMaster: menuItem,

  handlerDrawerOpen: (isOpen) => set({ openDrawer: isOpen }),
  handlerComponentDrawer: (isOpen) => set({ openComponentDrawer: isOpen })
}));

// Export hooks for backward compatibility
export const handlerDrawerOpen = (isOpen) => useMenuStore.setState({ openDrawer: isOpen });
export const handlerComponentDrawer = (isOpen) => useMenuStore.setState({ openComponentDrawer: isOpen });

/**
 * Hook to get menu master data.
 * Role-based filtering is handled by Navigation components via filterMenuItemsByRole.
 */
export const useGetMenuMaster = () => {
  const openDrawer = useMenuStore((state) => state.openDrawer);

  return {
    menuMaster: {
      isDashboardDrawerOpened: openDrawer
    }
  };
};

export default useMenuStore;
