package com.waad.tba.security.rbac;

/**
 * Static Role Definitions — Authorization Simplification (Phase 5),
 * extended by WAAD-RBAC-PHASE-1-FOUNDATION.
 *
 * This enum is the SINGLE SOURCE OF TRUTH for all roles in the system.
 * Route-level authorization remains role-based via @PreAuthorize("hasRole('...')").
 * WAAD-RBAC-PHASE-1-FOUNDATION additionally introduces a permission catalog
 * (see the {@code permissions}/{@code role_permissions}/{@code user_permission_overrides}
 * tables added in V101) used for effective-permission reporting; it does not
 * replace the existing @PreAuthorize role checks.
 *
 * ROLES:
 *   SUPER_ADMIN      — Full system access, unrestricted, cannot be deleted/disabled/demoted
 *   WAAD_ADMIN       — Manages normal users and operational roles; cannot manage
 *                       SUPER_ADMIN accounts or grant/revoke critical-security permissions
 *   MEDICAL_REVIEWER — Review/approve claims and pre-authorizations
 *   ACCOUNTANT       — Financial operations (settlements, provider accounts)
 *   PROVIDER_STAFF   — Provider portal access (visits, claims submission)
 *   EMPLOYER_ADMIN   — Employer data access (members, claims for their company)
 *   DATA_ENTRY       — Create/edit operational data (members, visits, claims)
 *   FINANCE_VIEWER   — Read-only financial data access
 *   BENEFICIARY      — RESERVED for a future phase (member self-service portal).
 *                       Not yet assignable via UserService/UserController and has
 *                       no seeded permissions; included now only so the enum/DB
 *                       constraint/permission catalog don't need another migration
 *                       when that phase starts.
 *
 * @author TBA WAAD System
 * @version 3.0 — WAAD-RBAC-PHASE-1-FOUNDATION
 */
public enum SystemRole {

    SUPER_ADMIN("مدير النظام", "System Super Administrator"),
    WAAD_ADMIN("مدير وعد", "WAAD Administrator"),
    MEDICAL_REVIEWER("مراجع طبي", "Medical Claims Reviewer"),
    ACCOUNTANT("محاسب", "Financial Accountant"),
    PROVIDER_STAFF("موظف مقدم خدمة", "Healthcare Provider Staff"),
    EMPLOYER_ADMIN("مدير صاحب العمل", "Employer Administrator"),
    DATA_ENTRY("مدخل بيانات", "Data Entry Operator"),
    FINANCE_VIEWER("مشاهد مالي", "Finance Viewer (Read-Only)"),
    BENEFICIARY("مستفيد", "Beneficiary (reserved — future phase)");

    private final String displayNameAr;
    private final String displayNameEn;

    SystemRole(String displayNameAr, String displayNameEn) {
        this.displayNameAr = displayNameAr;
        this.displayNameEn = displayNameEn;
    }

    public String getDisplayNameAr() {
        return displayNameAr;
    }

    public String getDisplayNameEn() {
        return displayNameEn;
    }

    /**
     * Parse role from string (case-insensitive, handles ROLE_ prefix).
     */
    public static SystemRole fromString(String roleName) {
        if (roleName == null || roleName.isBlank()) return null;
        String clean = roleName.trim().toUpperCase();
        if (clean.startsWith("ROLE_")) clean = clean.substring(5);
        try {
            return SystemRole.valueOf(clean);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Get all role names as strings.
     */
    public static String[] getAllRoleNames() {
        SystemRole[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].name();
        }
        return names;
    }
}
