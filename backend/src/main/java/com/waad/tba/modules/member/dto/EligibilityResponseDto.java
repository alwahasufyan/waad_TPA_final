package com.waad.tba.modules.member.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "DTO for member eligibility check response")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EligibilityResponseDto {

    @Schema(description = "Member ID", example = "123")
    private Long memberId;

    @Schema(description = "Card number", example = "WAAD|MEMBER|1735234859123")
    private String cardNumber;

    @Schema(description = "Full name", example = "أحمد محمد علي")
    private String fullName;

    @Schema(description = "Is member eligible for services", example = "true")
    private Boolean eligible;

    @Schema(description = "Eligibility status text", example = "ELIGIBLE")
    private String eligibilityStatus;

    @Schema(description = "Timestamp of eligibility check", example = "2024-12-29T10:30:00")
    private LocalDateTime eligibilityCheckedAt;

    @Schema(description = "Service date for eligibility check", example = "2024-12-29")
    private LocalDate serviceDate;

    @Schema(description = "Member status", example = "ACTIVE")
    private String memberStatus;

    @Schema(description = "Card status", example = "ACTIVE")
    private String cardStatus;

    @Schema(description = "Benefit policy information")
    private BenefitPolicyInfo benefitPolicy;

    @Schema(description = "Employer information")
    private EmployerInfo employer;

    @Schema(description = "List of ineligibility reasons")
    @Builder.Default
    private List<IneligibilityReason> ineligibilityReasons = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BenefitPolicyInfo {
        @Schema(description = "Policy ID", example = "5")
        private Long id;

        @Schema(description = "Policy name", example = "Gold Coverage")
        private String name;

        @Schema(description = "Policy code", example = "POL-2024-001")
        private String code;

        @Schema(description = "Policy status", example = "ACTIVE")
        private String status;

        @Schema(description = "Policy start date", example = "2024-01-01")
        private LocalDate startDate;

        @Schema(description = "Policy end date", example = "2024-12-31")
        private LocalDate endDate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmployerInfo {
        @Schema(description = "Employer ID", example = "1")
        private Long id;

        @Schema(description = "Employer name", example = "شركة الواحة")
        private String name;

        @Schema(description = "Employer code", example = "EMP-01")
        private String code;

        @Schema(description = "Employer active status", example = "true")
        private Boolean active;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IneligibilityReason {
        @Schema(description = "Reason code", example = "MEMBER_SUSPENDED")
        private String code;

        @Schema(description = "Reason message in Arabic", example = "العضو موقوف")
        private String messageAr;

        @Schema(description = "Reason message in English", example = "Member is suspended")
        private String messageEn;
    }
}
