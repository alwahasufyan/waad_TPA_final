package com.waad.tba.modules.rbac.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDto {
    private Long id;
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private Boolean active;
    private String role;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // WAAD-RBAC-USERS-LIST-PROVIDER-LINKAGE-1: surfaced in the users list/table
    // so an admin can see which provider a PROVIDER_STAFF user is bound to, or
    // how many providers a MEDICAL_REVIEWER is assigned, without opening each
    // user individually.
    private Long providerId;
    private String providerName;
    private Integer assignedProviderCount;

    // WAAD-RBAC-PER-USER-LANDING-PAGE-1: bonus fix while touching this DTO —
    // employerId was never actually returned here, so UserEdit.jsx's employer
    // selector always loaded blank on edit despite reading `user.employerId`.
    private Long employerId;

    // WAAD-RBAC-PER-USER-LANDING-PAGE-1: admin-set post-login landing route,
    // or null if none set (role-based bootstrap default applies).
    private String defaultLandingPage;
}
