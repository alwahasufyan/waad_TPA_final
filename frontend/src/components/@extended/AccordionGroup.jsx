// material-ui
import { styled } from '@mui/material/styles';
import Stack from '@mui/material/Stack';

// A reusable wrapper that applies consistent styles to nested MUI Accordions
const AccordionGroup = styled(Stack, {
  shouldForwardProp: (prop) => prop !== 'variant'
})(({ theme, variant = 'outlined' }) => {
  if (variant === 'simple') {
    return {
      '& .MuiAccordion-root': {
        borderColor: theme.vars.palette.divider,
        '& .MuiAccordionSummary-root': {
          backgroundColor: 'transparent',
          flexDirection: 'row',
          '&:focus-visible': { backgroundColor: theme.vars.palette.primary.lighter }
        },
        '& .MuiAccordionDetails-root': { borderColor: theme.vars.palette.divider },
        '& .Mui-expanded': { color: theme.vars.palette.primary.main }
      }
    };
  }

  if (variant === 'settings') {
    return {
      '& .MuiAccordion-root': {
        borderColor: theme.vars.palette.divider,
        '& .MuiAccordionSummary-root': {
          backgroundColor: 'transparent',
          flexDirection: 'row',
          paddingLeft: theme.spacing(1)
        },
        '& .MuiAccordionDetails-root': { border: 'none' },
        '& .Mui-expanded': { color: theme.vars.palette.primary.main }
      }
    };
  }

  // default: outlined
  return {
    gap: theme.spacing(2.5),
    '& .MuiAccordion-root': {
      border: '1px solid',
      borderColor: theme.vars.palette.divider,
      borderRadius: theme.vars.shape.borderRadius,
      boxShadow: 'none',
      '&:before': { display: 'none' },
      '& .MuiAccordionSummary-root': {
        backgroundColor: 'transparent',
        flexDirection: 'row',
        paddingTop: theme.spacing(2),
        paddingBottom: theme.spacing(2),
        paddingLeft: theme.spacing(2.5),
        paddingRight: theme.spacing(2.5),
        [theme.breakpoints.up('sm')]: {
          paddingTop: theme.spacing(2.5),
          paddingBottom: theme.spacing(2.5),
          paddingLeft: theme.spacing(3),
          paddingRight: theme.spacing(3)
        },
        [theme.breakpoints.up('lg')]: {
          paddingTop: theme.spacing(3),
          paddingBottom: theme.spacing(3),
          paddingLeft: theme.spacing(3.75),
          paddingRight: theme.spacing(3.75)
        }
      },
      '& .MuiAccordionSummary-content': { margin: 0 },
      '& .MuiAccordionDetails-root': {
        borderTop: 'none',
        paddingTop: 0,
        paddingLeft: theme.spacing(2.5),
        paddingRight: theme.spacing(2.5),
        paddingBottom: theme.spacing(2),
        [theme.breakpoints.up('sm')]: {
          paddingLeft: theme.spacing(3),
          paddingRight: theme.spacing(3),
          paddingBottom: theme.spacing(2.5)
        },
        [theme.breakpoints.up('lg')]: {
          paddingLeft: theme.spacing(3.75),
          paddingRight: theme.spacing(3.75),
          paddingBottom: theme.spacing(3)
        }
      }
    }
  };
});

export default AccordionGroup;
