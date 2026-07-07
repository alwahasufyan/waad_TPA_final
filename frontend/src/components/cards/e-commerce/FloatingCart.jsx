import { sum } from 'lodash-es';
import { Link } from 'react-router-dom';

// material-ui
import Fab from '@mui/material/Fab';
import Badge from '@mui/material/Badge';

// project imports
import { useGetCart } from 'api/cart';

// assets
import ShoppingCartOutlined from '@ant-design/icons/ShoppingCartOutlined';

// ==============================|| CART ITEMS - FLOATING BUTTON ||============================== //

export default function FloatingCart() {
  const { cart } = useGetCart();

  let totalQuantity = 0;
  if (cart && cart.products && cart.products.length > 0) {
    totalQuantity = sum(cart.products.map((item) => item.quantity));
  }

  return (
    <Fab
      component={Link}
      to="/apps/e-commerce/checkout"
      size="large"
      sx={(theme) => ({
        top: '75%',
        position: 'fixed',
        right: 0,
        zIndex: theme.zIndex.speedDial,
        boxShadow: theme.vars.customShadows.primary,
        bgcolor: 'primary.lighter',
        color: 'primary.main',
        borderRadius: '25%',
        borderTopRightRadius: 0,
        borderBottomRightRadius: 0,
        '&:hover': {
          bgcolor: 'primary.100',
          boxShadow: theme.vars.customShadows.primary
        },
        '&:focus-visible': {
          outline: `2px solid ${theme.vars.palette.primary.dark}`,
          outlineOffset: 2
        }
      })}
    >
      <Badge showZero badgeContent={totalQuantity} color="error">
        <ShoppingCartOutlined style={{ fontSize: '1.5rem' }} />
      </Badge>
    </Fab>
  );
}
