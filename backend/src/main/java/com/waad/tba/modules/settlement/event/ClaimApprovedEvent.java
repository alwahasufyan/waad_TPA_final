package com.waad.tba.modules.settlement.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Event published when a claim is approved.
 * 
 * ╔═══════════════════════════════════════════════════════════════════════════════╗
 * ║                         CLAIM APPROVED EVENT                                  ║
 * ║───────────────────────────────────────────────────────────────────────────────║
 * ║ Triggered when claim status transitions to APPROVED.                          ║
 * ║ Listeners can perform async operations like crediting provider accounts.      ║
 * ╚═══════════════════════════════════════════════════════════════════════════════╝
 * 
 * @since Phase 3A - Backend Integration
 */
@Getter
public class ClaimApprovedEvent extends ApplicationEvent {
    
    private final Long claimId;
    private final Long providerId;
    private final Long userId;
    
    public ClaimApprovedEvent(Object source, Long claimId, Long providerId, Long userId) {
        super(source);
        this.claimId = claimId;
        this.providerId = providerId;
        this.userId = userId;
    }
}
