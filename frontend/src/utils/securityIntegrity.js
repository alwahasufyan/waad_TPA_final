/**
 * Security & Role Integrity Audit
 * ================================
 *
 * PRODUCTION READINESS (2026-01-13):
 * - Verifies RBAC rules are intact
 * - Checks SUPER_ADMIN protection
 * - Validates permission hierarchy
 *
 * This file provides runtime verification functions
 * to ensure security rules are not bypassed.
 */

import { SystemRole, RolePrivilegeLevel, getPrivilegeLevel } from 'constants/rbac';

// SUPER_ADMIN-only domains (hardcoded — no longer from constants)
const SUPER_ADMIN_ONLY_DOMAINS = Object.freeze(['RBAC', 'SYSTEM']);

// ==============================|| SECURITY RULES ||============================== //

/**
 * Security rules that must ALWAYS be enforced
 * These are verified at runtime to prevent regression
 */
export const SECURITY_RULES = {
  // SUPER_ADMIN Protection
  SUPER_ADMIN_CANNOT_BE_DELETED_BY_OTHERS: {
    id: 'SA-001',
    description: 'SUPER_ADMIN cannot be deleted by non-SUPER_ADMIN',
    verify: (currentRole, targetRole, action) => {
      if (action === 'DELETE' && targetRole === SystemRole.SUPER_ADMIN) {
        return currentRole === SystemRole.SUPER_ADMIN;
      }
      return true;
    }
  },

  SUPER_ADMIN_CANNOT_BE_MODIFIED_BY_OTHERS: {
    id: 'SA-002',
    description: 'SUPER_ADMIN can only be modified by SUPER_ADMIN',
    verify: (currentRole, targetRole, action) => {
      if (['UPDATE', 'MODIFY', 'CHANGE_ROLE'].includes(action) && targetRole === SystemRole.SUPER_ADMIN) {
        return currentRole === SystemRole.SUPER_ADMIN;
      }
      return true;
    }
  },

  SUPER_ADMIN_ROLE_CANNOT_BE_ASSIGNED_BY_OTHERS: {
    id: 'SA-003',
    description: 'Only SUPER_ADMIN can assign SUPER_ADMIN role',
    verify: (currentRole, roleToAssign) => {
      if (roleToAssign === SystemRole.SUPER_ADMIN) {
        return currentRole === SystemRole.SUPER_ADMIN;
      }
      return true;
    }
  },

  // Hierarchy Enforcement
  NO_PRIVILEGE_ESCALATION: {
    id: 'HE-001',
    description: 'Users cannot assign roles higher than their own',
    verify: (currentRole, roleToAssign) => {
      const currentLevel = getPrivilegeLevel(currentRole);
      const assignLevel = getPrivilegeLevel(roleToAssign);
      return currentLevel >= assignLevel;
    }
  },

  MODIFY_LOWER_ONLY: {
    id: 'HE-002',
    description: 'Users can only modify users with lower privilege',
    verify: (currentRole, targetRole) => {
      if (currentRole === SystemRole.SUPER_ADMIN) return true;
      const currentLevel = getPrivilegeLevel(currentRole);
      const targetLevel = getPrivilegeLevel(targetRole);
      return currentLevel > targetLevel;
    }
  },

  // Domain Access
  RBAC_DOMAIN_SUPER_ADMIN_ONLY: {
    id: 'DA-001',
    description: 'RBAC domain is SUPER_ADMIN only',
    verify: (currentRole, domain) => {
      if (domain === 'RBAC' || domain === 'SYSTEM') {
        return currentRole === SystemRole.SUPER_ADMIN;
      }
      return true;
    }
  },

  // Non-SUPER_ADMIN Boundaries
  NON_ADMIN_NO_RBAC_ACCESS: {
    id: 'IA-001',
    description: 'Only SUPER_ADMIN can access RBAC/SYSTEM domains',
    verify: (currentRole, domain) => {
      if (currentRole !== SystemRole.SUPER_ADMIN && SUPER_ADMIN_ONLY_DOMAINS.includes(domain)) {
        return false;
      }
      return true;
    }
  }
};

// ==============================|| VERIFICATION FUNCTIONS ||============================== //

/**
 * Verify a specific security rule
 * @param {string} ruleId - Rule ID to verify
 * @param {...any} args - Arguments for the rule verify function
 * @returns {{ passed: boolean, rule: object }}
 */
export const verifyRule = (ruleId, ...args) => {
  const rule = Object.values(SECURITY_RULES).find((r) => r.id === ruleId);
  if (!rule) {
    console.warn(`Unknown security rule: ${ruleId}`);
    return { passed: true, rule: null };
  }

  const passed = rule.verify(...args);

  if (!passed && import.meta.env.DEV) {
    console.warn(`🔒 Security Rule Violation: ${rule.id} - ${rule.description}`);
  }

  return { passed, rule };
};

/**
 * Verify all security rules for a user operation
 * @param {string} currentRole - Current user's role
 * @param {string} targetRole - Target user's role (if applicable)
 * @param {string} action - Action being performed
 * @param {string} domain - Domain being accessed (if applicable)
 * @returns {{ allPassed: boolean, results: object[] }}
 */
export const verifyAllRules = (currentRole, targetRole, action, domain = null) => {
  const results = [];

  for (const [key, rule] of Object.entries(SECURITY_RULES)) {
    let passed = true;

    // Determine which args to pass based on rule type
    if (rule.id.startsWith('SA-') || rule.id.startsWith('HE-')) {
      // User operation rules
      if (rule.id === 'SA-003' || rule.id === 'HE-001') {
        // Role assignment rules
        passed = rule.verify(currentRole, targetRole);
      } else {
        // User modification rules
        passed = rule.verify(currentRole, targetRole, action);
      }
    } else if (rule.id.startsWith('DA-') || rule.id.startsWith('IA-')) {
      // Domain access rules
      if (domain) {
        passed = rule.verify(currentRole, domain);
      }
    }

    results.push({
      id: rule.id,
      description: rule.description,
      passed
    });
  }

  const allPassed = results.every((r) => r.passed);

  if (!allPassed && import.meta.env.DEV) {
    console.error(
      '🚨 Security Rules Verification Failed:',
      results.filter((r) => !r.passed)
    );
  }

  return { allPassed, results };
};

// ==============================|| RUNTIME INTEGRITY CHECKS ||============================== //

/**
 * Run integrity checks on startup
 * Verifies that security constants are properly configured
 */
export const runIntegrityChecks = () => {
  const checks = [];

  // Check: SUPER_ADMIN has highest privilege
  const superAdminLevel = RolePrivilegeLevel[SystemRole.SUPER_ADMIN];
  const allLevels = Object.values(RolePrivilegeLevel);
  const isHighest = allLevels.every((level) => level <= superAdminLevel);
  checks.push({
    name: 'SUPER_ADMIN highest privilege',
    passed: isHighest,
    value: superAdminLevel
  });

  // Check: RBAC is in SUPER_ADMIN_ONLY_DOMAINS
  const rbacProtected = SUPER_ADMIN_ONLY_DOMAINS.includes('RBAC');
  checks.push({
    name: 'RBAC domain protected',
    passed: rbacProtected,
    value: SUPER_ADMIN_ONLY_DOMAINS
  });

  // Check: SYSTEM is in SUPER_ADMIN_ONLY_DOMAINS
  const systemProtected = SUPER_ADMIN_ONLY_DOMAINS.includes('SYSTEM');
  checks.push({
    name: 'SYSTEM domain protected',
    passed: systemProtected,
    value: SUPER_ADMIN_ONLY_DOMAINS
  });

  // Check: All SystemRole values have privilege levels
  const allRolesHaveLevels = Object.values(SystemRole).every((role) => RolePrivilegeLevel[role] !== undefined);
  checks.push({
    name: 'All roles have privilege levels',
    passed: allRolesHaveLevels,
    value: Object.keys(RolePrivilegeLevel)
  });

  const allPassed = checks.every((c) => c.passed);

  if (!allPassed) {
    console.error(
      '🚨 SECURITY INTEGRITY CHECK FAILED:',
      checks.filter((c) => !c.passed)
    );
  } else if (import.meta.env.DEV) {
    console.info('✅ Security integrity checks passed:', checks.length);
  }

  return { allPassed, checks };
};

// ==============================|| EXPORTS ||============================== //

export default {
  SECURITY_RULES,
  verifyRule,
  verifyAllRules,
  runIntegrityChecks
};
