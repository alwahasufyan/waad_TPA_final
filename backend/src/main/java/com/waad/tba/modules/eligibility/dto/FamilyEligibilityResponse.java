package com.waad.tba.modules.eligibility.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Family Eligibility Response DTO
 * Returns eligibility status for a member and all family members
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FamilyEligibilityResponse {

    /**
     * Request tracking ID
     */
    private String requestId;

    /**
     * The primary member being checked
     */
    private FamilyMemberEligibility primaryMember;

    /**
     * All family members (dependents or principal + siblings)
     */
    private List<FamilyMemberEligibility> familyMembers;

    /**
     * Summary statistics
     */
    private FamilySummary summary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FamilyMemberEligibility {
        private Long memberId;
        private String memberName;
        private String memberNameAr;
        private String civilId;
        private String cardNumber;
        private String relationshipType; // PRINCIPAL, SPOUSE, CHILD, PARENT, etc.
        private String memberStatus;
        private LocalDate dateOfBirth;
        private String gender;
        
        // Eligibility status
        private boolean eligible;
        private String eligibilityStatus;
        private String reason;
        private String reasonAr;
        
        // Coverage info
        private String policyNumber;
        private LocalDate coverageStart;
        private LocalDate coverageEnd;
        private String employerName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FamilySummary {
        private int totalMembers;
        private int eligibleCount;
        private int ineligibleCount;
        private String familyStatus; // ALL_ELIGIBLE, PARTIAL, NONE_ELIGIBLE
    }
}
