// third-party
import { merge } from 'lodash-es';

// project imports
import Accordion from './Accordion';
import AccordionDetails from './AccordionDetails';
import AccordionSummary from './AccordionSummary';
import Alert from './Alert';
import AlertTitle from './AlertTitle';
import Autocomplete from './Autocomplete';
import Badge from './Badge';
import Button from './Button';
import ButtonBase from './ButtonBase';
import ButtonGroup from './ButtonGroup';
import CardContent from './CardContent';
import ChartsAxiasHighlight from './ChartsAxisHighlight';
import ChartsAxis from './ChartsAxis';
import ChartTooltip from './ChartTooltip';
import Checkbox from './Checkbox';
import Chip from './Chip';
import DatePicker from './DatePicker';
import Dialog from './Dialog';
import DialogContentText from './DialogContentText';
import DialogTitle from './DialogTitle';
import Drawer from './Drawer';
import Fab from './Fab';
import FormHelperText from './FormHelperText';
import IconButton from './IconButton';
import InputBase from './InputBase';
import InputLabel from './InputLabel';
import LinearProgress from './LinearProgress';
import Link from './Link';
import ListItemButton from './ListItemButton';
import ListItemIcon from './ListItemIcon';
import Menu from './Menu';
import OutlinedInput from './OutlinedInput';
import Pagination from './Pagination';
import PaginationItem from './PaginationItem';
import PickersTextField from './PickersTextField';
import Popover from './Popover';
import Radio from './Radio';
import Select from './Select';
import Slider from './Slider';
import Switch from './Switch';
import Tab from './Tab';
import TableBody from './TableBody';
import TableCell from './TableCell';
import TableContainer from './TableContainer';
import TableFooter from './TableFooter';
import TableHead from './TableHead';
import TablePagination from './TablePagination';
import TableRow from './TableRow';
import Tabs from './Tabs';
import ToggleButton from './ToggleButton';
import Tooltip from './Tooltip';
import TreeItem from './TreeItem';
import Typography from './Typography';

// ==============================|| OVERRIDES - MAIN ||============================== //

export default function ComponentsOverrides(theme) {
  return merge(
    Accordion(theme),
    AccordionDetails(theme),
    AccordionSummary(theme),
    Alert(theme),
    AlertTitle(),
    Autocomplete(),
    Badge(theme),
    Button(theme),
    ButtonBase(),
    ButtonGroup(),
    CardContent(),
    ChartsAxiasHighlight(theme),
    ChartsAxis(theme),
    ChartTooltip(theme),
    Checkbox(theme),
    Chip(theme),
    DatePicker(),
    Dialog(theme),
    DialogContentText(theme),
    DialogTitle(),
    Drawer(),
    Fab(theme),
    FormHelperText(),
    IconButton(theme),
    InputBase(),
    InputLabel(theme),
    LinearProgress(),
    Link(),
    ListItemButton(theme),
    ListItemIcon(theme),
    Menu(theme),
    OutlinedInput(theme),
    Pagination(),
    PaginationItem(theme),
    PickersTextField(theme),
    Popover(theme),
    Radio(theme),
    Select(),
    Slider(theme),
    Switch(theme),
    Tab(theme),
    TableBody(theme),
    TableCell(theme),
    TableContainer(),
    TableFooter(theme),
    TableHead(theme),
    TablePagination(),
    TableRow(),
    Tabs(),
    ToggleButton(theme),
    Tooltip(theme),
    TreeItem(),
    Typography()
  );
}
