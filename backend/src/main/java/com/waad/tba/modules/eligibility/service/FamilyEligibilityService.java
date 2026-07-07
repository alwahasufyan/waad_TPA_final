package com.waad.tba.modules.eligibility.service;

import com.waad.tba.modules.eligibility.domain.EligibilityResult;
import com.waad.tba.modules.eligibility.dto.EligibilityCheckRequest;
import com.waad.tba.modules.eligibility.dto.FamilyEligibilityResponse;
import com.waad.tba.modules.eligibility.dto.FamilyEligibilityResponse.FamilyMemberEligibility;
import com.waad.tba.modules.eligibility.dto.FamilyEligibilityResponse.FamilySummary;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * Family Eligibility Service
 * Checks eligibility for a member and all family members
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FamilyEligibilityService {

    private final EligibilityEngineService eligibilityService;
    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public FamilyEligibilityResponse checkFamilyEligibility(Long memberId, LocalDate serviceDate) {
        String requestId = UUID.randomUUID().toString();
        log.info("[FamilyEligibility] Checking family eligibility for member {} on {}", memberId, serviceDate);

        // Find the member
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));

        // Determine principal and get all family members
        Member principal;
        List<Member> allFamilyMembers = new ArrayList<>();
        
        if (member.getParent() == null) {
            // This is the principal
            principal = member;
            allFamilyMembers.add(principal);
            if (principal.getDependents() != null) {
                allFamilyMembers.addAll(principal.getDependents());
            }
        } else {
            // This is a dependent - get the principal and all dependents
            principal = member.getParent();
            allFamilyMembers.add(principal);
            if (principal.getDependents() != null) {
                allFamilyMembers.addAll(principal.getDependents());
            }
        }

        // Check eligibility for each family member
        List<FamilyMemberEligibility> familyEligibilities = new ArrayList<>();
        FamilyMemberEligibility primaryMemberEligibility = null;
        int eligibleCount = 0;
        int ineligibleCount = 0;

        for (Member familyMember : allFamilyMembers) {
            FamilyMemberEligibility eligibility = checkMemberEligibility(familyMember, serviceDate);
            familyEligibilities.add(eligibility);
            
            if (familyMember.getId().equals(memberId)) {
                primaryMemberEligibility = eligibility;
            }
            
            if (eligibility.isEligible()) {
                eligibleCount++;
            } else {
                ineligibleCount++;
            }
        }

        // Build summary
        String familyStatus;
        if (eligibleCount == allFamilyMembers.size()) {
            familyStatus = "ALL_ELIGIBLE";
        } else if (eligibleCount > 0) {
            familyStatus = "PARTIAL";
        } else {
            familyStatus = "NONE_ELIGIBLE";
        }

        FamilySummary summary = FamilySummary.builder()
                .totalMembers(allFamilyMembers.size())
                .eligibleCount(eligibleCount)
                .ineligibleCount(ineligibleCount)
                .familyStatus(familyStatus)
                .build();

        // Remove the primary member from familyMembers list (to avoid duplication)
        familyEligibilities.removeIf(e -> e.getMemberId().equals(memberId));

        return FamilyEligibilityResponse.builder()
                .requestId(requestId)
                .primaryMember(primaryMemberEligibility)
                .familyMembers(familyEligibilities)
                .summary(summary)
                .build();
    }

    private FamilyMemberEligibility checkMemberEligibility(Member member, LocalDate serviceDate) {
        // Build eligibility request
        EligibilityCheckRequest request = EligibilityCheckRequest.builder()
                .memberId(member.getId())
                .serviceDate(serviceDate != null ? serviceDate : LocalDate.now())
                .build();

        // Check eligibility
        EligibilityResult result;
        String reason = null;
        String reasonAr = null;
        
        try {
            result = eligibilityService.checkEligibility(request);
            
            if (!result.isEligible() && result.getReasons() != null && !result.getReasons().isEmpty()) {
                EligibilityResult.ReasonDetail firstReason = result.getReasons().get(0);
                reason = firstReason.getDetails();
                reasonAr = firstReason.getMessageAr();
            }
        } catch (Exception e) {
            log.error("[FamilyEligibility] Error checking eligibility for member {}: {}", member.getId(), e.getMessage());
            // Create a simple not-eligible result for errors
            result = EligibilityResult.notEligible(
                    UUID.randomUUID().toString(),
                    null,  // no snapshot
                    List.of(EligibilityResult.ReasonDetail.builder()
                            .code("SYSTEM_ERROR")
                            .messageAr("حدث خطأ أثناء التحقق من الأهلية")
                            .messageEn("Error during eligibility check")
                            .details(e.getMessage())
                            .hardFailure(true)
                            .build()),
                    0L,
                    0
            );
            reason = e.getMessage();
            reasonAr = "حدث خطأ أثناء التحقق من الأهلية";
        }

        // Determine relationship type
        String relationshipType = "PRINCIPAL";
        if (member.getParent() != null) {
            relationshipType = member.getRelationship() != null ? 
                    member.getRelationship().name() : "DEPENDENT";
        }

        // Build response
        return FamilyMemberEligibility.builder()
                .memberId(member.getId())
                .memberName(member.getFullName())
                .memberNameAr(member.getFullName()) // Using fullName for both (Arabic stored in fullName)
                .civilId(member.getNationalNumber())
                .cardNumber(member.getCardNumber())
                .relationshipType(relationshipType)
                .memberStatus(member.getStatus() != null ? member.getStatus().name() : null)
                .dateOfBirth(member.getBirthDate())
                .gender(member.getGender() != null ? member.getGender().name() : null)
                .eligible(result.isEligible())
                .eligibilityStatus(result.getStatus() != null ? result.getStatus().name() : null)
                .reason(reason)
                .reasonAr(reasonAr)
                .policyNumber(result.getSnapshot() != null ? result.getSnapshot().getPolicyNumber() : null)
                .coverageStart(result.getSnapshot() != null ? result.getSnapshot().getCoverageStart() : null)
                .coverageEnd(result.getSnapshot() != null ? result.getSnapshot().getCoverageEnd() : null)
                .employerName(result.getSnapshot() != null ? result.getSnapshot().getEmployerName() : 
                        (member.getEmployer() != null ? member.getEmployer().getName() : null))
                .build();
    }
}
