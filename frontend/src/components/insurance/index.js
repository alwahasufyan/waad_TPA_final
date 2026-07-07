/**
 * Insurance Domain Components
 * TBA-WAAD Insurance System
 *
 * Specialized UI components for insurance workflows
 */

// Timeline & Progress Components
export { default as StatusTimeline } from './StatusTimeline';
export { default as CoverageGauge } from './CoverageGauge';
export { default as BenefitPolicyLifecycleBar } from './BenefitPolicyLifecycleBar';
export { default as ValidityCountdown } from './ValidityCountdown';

// Financial Components
export { default as AmountComparisonBar } from './AmountComparisonBar';

// Status & Badge Components
export { default as NetworkBadge, NETWORK_TIERS } from './NetworkBadge';
export { default as CardStatusBadge, CARD_STATUS_CONFIG } from './CardStatusBadge';
export { default as PriorityBadge, PRIORITY_CONFIG } from './PriorityBadge';
export { default as MemberTypeIndicator, RELATIONSHIP_CONFIG } from './MemberTypeIndicator';

// Terminology Constants
export { INSURANCE_TERMS, getTerminology } from './terminology';

// Workflow Constants
export { CLAIM_WORKFLOW_STEPS, PREAPPROVAL_WORKFLOW_STEPS, getWorkflowSteps } from './workflowSteps';
