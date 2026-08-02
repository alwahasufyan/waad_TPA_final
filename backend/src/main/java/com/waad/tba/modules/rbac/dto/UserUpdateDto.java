package com.waad.tba.modules.rbac.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateDto {

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    private String phone;
    private Boolean active;

    // Role / user type
    @Pattern(regexp = "^(SUPER_ADMIN|WAAD_ADMIN|MEDICAL_REVIEWER|ACCOUNTANT|PROVIDER_STAFF|EMPLOYER_ADMIN|DATA_ENTRY|FINANCE_VIEWER)$", message = "نوع المستخدم غير صالح")
    private String userType;

    // Employer/Provider associations
    private Long employerId;
    private Long providerId;

    // Custom permissions for EMPLOYER users
    private Boolean canViewClaims;
    private Boolean canViewVisits;
    private Boolean canViewReports;
    private Boolean canViewMembers;
    private Boolean canViewBenefitPolicies;

    /**
     * WAAD-RBAC-PER-USER-LANDING-PAGE-1: admin-set post-login landing route
     * (e.g. "/dashboard"), or null to clear it and fall back to the
     * role-based bootstrap default. {@code defaultLandingPagePermission} is
     * NOT persisted — it is the permission code the frontend's menu
     * definition says this route requires, sent only so the backend can
     * verify (against the user's real effective permissions) that the
     * chosen route is actually accessible to them before saving it.
     * Required whenever {@code defaultLandingPage} is non-null.
     */
    private String defaultLandingPage;
    private String defaultLandingPagePermission;
}
