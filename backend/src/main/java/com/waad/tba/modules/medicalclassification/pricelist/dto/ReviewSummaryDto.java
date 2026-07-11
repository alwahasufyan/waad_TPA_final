package com.waad.tba.modules.medicalclassification.pricelist.dto;

import lombok.Builder;
import lombok.Value;

/**
 * Workspace header data: progress, critical-queue breakdown, Approve-Remaining
 * gate state, and the knowledge (learning-loop) counter.
 */
@Value
@Builder
public class ReviewSummaryDto {

    Long importId;
    String status;
    Integer totalLines;

    long needsReview;
    long pendingBulk;
    long approved;
    long rejected;

    long unknownQueue;
    long lowConfidenceQueue;
    long duplicateQueue;
    long guardQueue;

    /** True only when the critical queue is empty and a hidden majority exists (A5). */
    boolean approveRemainingEnabled;

    /** Dictionary decisions recorded from this import (learning loop). */
    long knowledgeDecisions;
}
