/**
 * Settlement API Contracts - TypeScript Interfaces
 *
 * ╔═══════════════════════════════════════════════════════════════════════════╗
 * ║              STRICT API v1 CONTRACTS - SETTLEMENT MODULE                  ║
 * ║───────────────────────────────────────────────────────────────────────────║
 * ║  These interfaces EXACTLY mirror backend API v1 contracts.               ║
 * ║  DO NOT modify without updating backend contracts first.                  ║
 * ║                                                                           ║
 * ║  RULES:                                                                   ║
 * ║  ✓ Frontend NEVER sends monetary values to backend                       ║
 * ║  ✓ All financial calculations are read-only from responses               ║
 * ║  ✓ Request interfaces define EXACTLY what backend accepts                ║
 * ║  ✓ Response interfaces define EXACTLY what backend returns               ║
 * ║  ✓ No `any` types allowed                                                ║
 * ║                                                                           ║
 * ║  @see backend: com.waad.tba.modules.settlement.api.*                     ║
 * ║  @see SETTLEMENT_API_CONTRACT.md                                         ║
 * ╚═══════════════════════════════════════════════════════════════════════════╝
 */

// ═══════════════════════════════════════════════════════════════════════════
// ENUMS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Batch lifecycle status
 * Backend: com.waad.tba.modules.settlement.entity.SettlementBatch.BatchStatus
 */
export enum BatchStatus {
  DRAFT = 'DRAFT',
  CONFIRMED = 'CONFIRMED',
  PAID = 'PAID',
  CANCELLED = 'CANCELLED'
}

/**
 * Payment method for settling batches
 * Backend: com.waad.tba.modules.settlement.entity.SettlementBatch.PaymentMethod
 */
export enum PaymentMethod {
  BANK_TRANSFER = 'BANK_TRANSFER',
  CHEQUE = 'CHEQUE',
  CASH = 'CASH',
  OTHER = 'OTHER'
}

// ═══════════════════════════════════════════════════════════════════════════
// REQUEST CONTRACTS (What Frontend Sends to Backend)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Create Settlement Batch Request
 * Backend: CreateSettlementBatchRequest.java
 *
 * ⚠️ FINANCIAL SAFETY: NO amount fields allowed
 */
export interface CreateSettlementBatchRequest {
  /** Provider ID (required) */
  providerId: number;

  /** Optional description */
  description?: string;

  /** Optional initial claim IDs to add */
  claimIds?: number[];
}

/**
 * Add Claims to Batch Request
 * Backend: AddClaimsToBatchRequest.java
 *
 * ⚠️ FINANCIAL SAFETY: NO amount fields - backend calculates from claims
 */
export interface AddClaimsToBatchRequest {
  /** Claim IDs to add (required, min 1) */
  claimIds: number[];
}

/**
 * Remove Claims from Batch Request
 * Backend: RemoveClaimsFromBatchRequest.java
 */
export interface RemoveClaimsFromBatchRequest {
  /** Claim IDs to remove (required, min 1) */
  claimIds: number[];
}

/**
 * Confirm Settlement Batch Request
 * Backend: ConfirmSettlementBatchRequest.java
 *
 * ⚠️ FINANCIAL SAFETY: NO amount confirmation required
 *    Backend recalculates and locks the total automatically
 */
export interface ConfirmSettlementBatchRequest {
  /** Optional confirmation note */
  note?: string;
}

/**
 * Pay Settlement Batch Request
 * Backend: PaySettlementBatchRequest.java
 *
 * ⚠️⚠️⚠️ CRITICAL FINANCIAL SAFETY ⚠️⚠️⚠️
 * This contract EXPLICITLY FORBIDS amount fields.
 * Payment amount = batch.totalNetAmount (immutable after CONFIRMED).
 * Frontend CANNOT influence payment amount.
 */
export interface PaySettlementBatchRequest {
  /** Payment reference (required: bank transfer number, cheque number, etc.) */
  paymentReference: string;

  /** Payment method (required) */
  paymentMethod: PaymentMethod;

  /** Optional payment note */
  paymentNote?: string;

  // ❌ NO paymentAmount field - backend uses batch.totalNetAmount
  // ❌ NO overrideAmount field - no amount manipulation allowed
  // ❌ NO adjustments field - strictly forbidden
}

/**
 * Cancel Settlement Batch Request
 * Backend: CancelSettlementBatchRequest.java
 */
export interface CancelSettlementBatchRequest {
  /** Cancellation reason (optional but recommended) */
  reason?: string;
}

// ═══════════════════════════════════════════════════════════════════════════
// RESPONSE CONTRACTS (What Backend Returns to Frontend)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Settlement Batch Summary Response
 * Backend: SettlementBatchResponse.java
 *
 * READ-ONLY: All financial fields calculated by backend
 */
export interface SettlementBatchResponse {
  readonly id: number;
  readonly batchNumber: string;
  readonly providerId: number;
  readonly providerName: string;
  readonly status: BatchStatus;
  readonly description?: string;

  // FINANCIAL DATA (READ-ONLY - Backend Authoritative)
  readonly totalAmount: number;
  readonly totalDeductions: number;
  readonly totalNetAmount: number;
  readonly claimsCount: number;

  // PAYMENT INFO (nullable until paid)
  readonly paymentReference?: string;
  readonly paymentMethod?: PaymentMethod;
  readonly paymentNote?: string;
  readonly paidAt?: string; // ISO 8601 datetime
  readonly paidBy?: number;
  readonly paidByName?: string;

  // CANCELLATION INFO (nullable unless cancelled)
  readonly cancelledAt?: string;
  readonly cancelledBy?: number;
  readonly cancelledByName?: string;
  readonly cancellationReason?: string;

  // AUDIT FIELDS
  readonly createdAt: string; // ISO 8601 datetime
  readonly createdBy?: number;
  readonly createdByName?: string;
  readonly confirmedAt?: string;
  readonly confirmedBy?: number;
  readonly confirmedByName?: string;
}

/**
 * Batch Claim Item Response
 * Backend: BatchClaimItemResponse.java
 *
 * Represents a single claim within a batch
 */
export interface BatchClaimItemResponse {
  readonly claimId: number;
  readonly claimNumber: string;
  readonly memberName: string;
  readonly serviceDate: string;
  readonly serviceName?: string;
  readonly approvedAmount: number;
  readonly deductions: number;
  readonly netAmount: number;
  readonly claimStatus: string;
}

/**
 * Available Claim for Batching Response
 * Backend: AvailableClaimResponse.java
 *
 * Claims that are APPROVED and not yet batched
 */
export interface AvailableClaimResponse {
  readonly id: number;
  readonly claimNumber: string;
  readonly memberName: string;
  readonly serviceDate: string;
  readonly serviceName?: string;
  readonly approvedAmount: number;
  readonly deductions: number;
  readonly netAmount: number;
}

/**
 * Batch Operation Result Response
 * Backend: BatchOperationResultResponse.java
 *
 * Result of add/remove claims operations
 */
export interface BatchOperationResultResponse {
  readonly success: boolean;
  readonly message: string;
  readonly affectedClaimIds: number[];
  readonly updatedBatch: SettlementBatchResponse;
}

/**
 * Settlement Batch List Response (Paginated)
 * Backend: SettlementBatchListResponse.java
 */
export interface SettlementBatchListResponse {
  readonly batches: SettlementBatchResponse[];
  readonly totalElements: number;
  readonly totalPages: number;
  readonly currentPage: number;
  readonly pageSize: number;
}

// ═══════════════════════════════════════════════════════════════════════════
// PROVIDER ACCOUNT CONTRACTS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Provider Account Summary Response
 * Backend: ProviderAccountResponse (if exists)
 */
export interface ProviderAccountResponse {
  readonly id: number;
  readonly providerId: number;
  readonly providerName: string;
  readonly runningBalance: number;
  readonly lastTransactionDate?: string;
  readonly lastBatchId?: number;
  readonly lastBatchNumber?: string;
}

/**
 * Provider Account Transaction Response
 */
export interface ProviderAccountTransactionResponse {
  readonly id: number;
  readonly transactionDate: string;
  readonly type: 'DEBIT' | 'CREDIT';
  readonly amount: number;
  readonly runningBalance: number;
  readonly referenceType: 'CLAIM' | 'BATCH_PAYMENT' | 'ADJUSTMENT';
  readonly referenceId?: number;
  readonly referenceNumber?: string;
  readonly description?: string;
}

/**
 * Provider Account Transaction List Response (Paginated)
 */
export interface ProviderAccountTransactionListResponse {
  readonly transactions: ProviderAccountTransactionResponse[];
  readonly totalElements: number;
  readonly totalPages: number;
  readonly currentPage: number;
  readonly pageSize: number;
}
