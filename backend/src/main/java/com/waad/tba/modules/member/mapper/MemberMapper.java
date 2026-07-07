package com.waad.tba.modules.member.mapper;

import org.springframework.stereotype.Component;

import com.waad.tba.modules.member.dto.MemberResponseDto;
import com.waad.tba.modules.member.entity.Member;

/**
 * Member Mapper
 * 
 * Maps Member entity to response DTOs.
 * Uses nationalNumber (NOT civilId) per API contract.
 * Uses birthDate (NOT dateOfBirth) for field consistency.
 * 
 * @version 2026.1 - Breaking change: civilId/dateOfBirth removed
 */
@Component
public class MemberMapper {

    public MemberResponseDto toResponseDto(Member entity) {
        if (entity == null) return null;

        return MemberResponseDto.builder()
                .id(entity.getId())
                .employerId(entity.getEmployer() != null ? entity.getEmployer().getId() : null)
                .employerName(entity.getEmployer() != null ? entity.getEmployer().getName() : null)
                .fullName(entity.getFullName())
                .nationalNumber(entity.getNationalNumber())
                .policyNumber(entity.getPolicyNumber())
                .benefitPolicyId(entity.getBenefitPolicy() != null ? entity.getBenefitPolicy().getId() : null)
                .benefitPolicyCode(entity.getBenefitPolicy() != null ? entity.getBenefitPolicy().getPolicyCode() : null)
                .benefitPolicyName(entity.getBenefitPolicy() != null ? entity.getBenefitPolicy().getName() : null)
                .birthDate(entity.getBirthDate())
                .gender(entity.getGender() != null ? entity.getGender().name() : null)
                .phone(entity.getPhone())
                .email(entity.getEmail())
                .active(entity.getActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
