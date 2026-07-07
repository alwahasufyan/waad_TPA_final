/**
 * ═══════════════════════════════════════════════════════════════════════════
 * 🏥 MEDICAL COMPONENTS - Index Export
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Professional medical-grade components for Claims & Pre-Approvals review
 *
 * Components:
 * - MedicalInboxLayout: Main split-screen layout
 * - ClaimReviewPanel: Left panel (60%) - Claim details
 * - DocumentsViewer: Right panel (40%) - Documents list & preview
 * - DocumentPreview: Inline document viewer (images/PDF)
 *
 * VERSION: 1.0 - Medical Inbox UX Redesign (2026-01-29)
 * ═══════════════════════════════════════════════════════════════════════════
 */

// Main Layout
export { default as MedicalInboxLayout } from './MedicalInboxLayout';

// Panels
export { default as ClaimReviewPanel } from './ClaimReviewPanel';
export { default as DocumentsViewer } from './DocumentsViewer';

// Utilities
export { default as DocumentPreview } from './DocumentPreview';

// Theme
export { MEDICAL_THEME } from '../../theme/medical-theme';
