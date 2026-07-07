package com.waad.tba.modules.settlement.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Event published when an APPROVED claim is reversed (transitions to REJECTED).
 *
 * ╔═══════════════════════════════════════════════════════════════════════════════╗
 * ║ CLAIM REVERSAL EVENT ║
 * ║───────────────────────────────────────────────────────────────────────────────║
 * ║ Triggered when a claim transitions from APPROVED → REJECTED, meaning the ║
 * ║ provider credit previously recorded must be reversed (debited back). ║
 * ╚═══════════════════════════════════════════════════════════════════════════════╝
 */
@Getter
public class ClaimReversalEvent extends ApplicationEvent {

    private final Long claimId;
    private final Long providerId;
    private final Long userId;

    public ClaimReversalEvent(Object source, Long claimId, Long providerId, Long userId) {
        super(source);
        this.claimId = claimId;
        this.providerId = providerId;
        this.userId = userId;
    }
}
