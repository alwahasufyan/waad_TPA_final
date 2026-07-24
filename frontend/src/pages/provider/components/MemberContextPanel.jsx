import { Stack, Divider, Typography } from '@mui/material';
import {
  Person as PersonIcon,
  LocalHospital as VisitIcon,
  Business as BusinessIcon,
  CalendarToday as CalendarIcon,
  Badge as BadgeIcon,
  CreditCard as CardIcon
} from '@mui/icons-material';
import { InfoCard, ReadOnlyField } from './ClaimSectionPrimitives';
import { VISIT_TYPE_LABELS } from '../constants';

/**
 * Side-panel: member/visit context, persistent across every step (Phase 3B).
 * Same data `ClaimContextHeader` showed as a horizontal chip row in the old
 * vertical layout; presented as a compact vertical list here since it now
 * lives in a narrow side rail, stacked above ClaimSummaryPanel. No new data,
 * no new API call. Uses `dense` fields (not the full SectionHeader) to keep
 * the side column short — a tall side panel was forcing an outer page
 * scrollbar, pushing the action footer below the fold (owner feedback).
 */
export function MemberContextPanel({
  linkedMemberName,
  linkedMemberCivilId,
  linkedMemberCardNumber,
  linkedVisitId,
  linkedVisitDate,
  visitDetails,
  linkedVisitType,
  linkedProviderName,
  userProviderName
}) {
  return (
    <InfoCard bgcolor="common.white">
      <Stack direction="row" spacing={0.75} alignItems="center" sx={{ mb: '0.5rem' }}>
        <PersonIcon fontSize="small" color="primary" />
        <Typography variant="caption" fontWeight={700}>
          المؤمَّن عليه والزيارة
        </Typography>
      </Stack>
      <Divider sx={{ mb: '0.5rem' }} />
      <Stack spacing={0}>
        <ReadOnlyField dense icon={PersonIcon} label="المؤمَّن عليه" value={linkedMemberName} highlight />
        <ReadOnlyField dense icon={BadgeIcon} label="الرقم المدني" value={linkedMemberCivilId} />
        <ReadOnlyField dense icon={CardIcon} label="رقم البطاقة" value={linkedMemberCardNumber} />
        <ReadOnlyField dense icon={VisitIcon} label="رقم الزيارة" value={linkedVisitId ? `#${linkedVisitId}` : null} />
        <ReadOnlyField dense icon={CalendarIcon} label="تاريخ الزيارة" value={linkedVisitDate || visitDetails?.visitDate} />
        <ReadOnlyField dense icon={VisitIcon} label="نوع الزيارة" value={VISIT_TYPE_LABELS[linkedVisitType] || linkedVisitType} />
        <ReadOnlyField
          dense
          icon={BusinessIcon}
          label="مقدم الخدمة"
          value={linkedProviderName || userProviderName || visitDetails?.providerName}
        />
      </Stack>
    </InfoCard>
  );
}
