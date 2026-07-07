#!/bin/bash

# ✅ Permission Migration Validation Script
# Purpose: Verify complete migration from role-based to permission-based authorization
# Usage: ./validate-permission-migration.sh

set -e

echo "=========================================="
echo "🔍 PERMISSION MIGRATION VALIDATION"
echo "=========================================="
echo ""

ERRORS=0

# Test 1: Verify RouteGuard is NOT imported in MainRoutes.jsx
echo "✅ Test 1: Checking RouteGuard import removed from MainRoutes.jsx..."
if grep -q "import.*RouteGuard.*from.*'./RouteGuard'" frontend/src/routes/MainRoutes.jsx 2>/dev/null; then
    echo "❌ FAILED: RouteGuard still imported in MainRoutes.jsx"
    ERRORS=$((ERRORS + 1))
else
    echo "✅ PASSED: RouteGuard import not found"
fi
echo ""

# Test 2: Verify RouteGuard.jsx is deprecated (renamed)
echo "✅ Test 2: Checking RouteGuard.jsx is deprecated..."
if [ -f "frontend/src/routes/RouteGuard.jsx" ]; then
    echo "❌ FAILED: RouteGuard.jsx still exists (should be .deprecated)"
    ERRORS=$((ERRORS + 1))
elif [ -f "frontend/src/routes/RouteGuard.jsx.deprecated" ]; then
    echo "✅ PASSED: RouteGuard.jsx successfully deprecated"
else
    echo "⚠️  WARNING: RouteGuard.jsx not found (may have been deleted)"
fi
echo ""

# Test 3: Verify no allowedRoles usage in MainRoutes.jsx
echo "✅ Test 3: Checking for allowedRoles props in MainRoutes.jsx..."
ALLOWED_ROLES_COUNT=$(grep -c "allowedRoles=" frontend/src/routes/MainRoutes.jsx 2>/dev/null || echo "0")
if [ "$ALLOWED_ROLES_COUNT" -gt 0 ]; then
    echo "❌ FAILED: Found $ALLOWED_ROLES_COUNT allowedRoles usages"
    grep -n "allowedRoles=" frontend/src/routes/MainRoutes.jsx
    ERRORS=$((ERRORS + 1))
else
    echo "✅ PASSED: No allowedRoles props found"
fi
echo ""

# Test 4: Verify PermissionGuard is imported
echo "✅ Test 4: Checking PermissionGuard import in MainRoutes.jsx..."
if grep -q "import.*PermissionGuard" frontend/src/routes/MainRoutes.jsx 2>/dev/null; then
    echo "✅ PASSED: PermissionGuard import found"
else
    echo "❌ FAILED: PermissionGuard not imported"
    ERRORS=$((ERRORS + 1))
fi
echo ""

# Test 5: Verify PERMISSIONS constants imported
echo "✅ Test 5: Checking PERMISSIONS constants import..."
if grep -q "import.*PERMISSIONS.*from.*'constants/permissions.constants'" frontend/src/routes/MainRoutes.jsx 2>/dev/null; then
    echo "✅ PASSED: PERMISSIONS constants imported"
else
    echo "❌ FAILED: PERMISSIONS constants not imported"
    ERRORS=$((ERRORS + 1))
fi
echo ""

# Test 6: Verify route-permissions.config.js exists
echo "✅ Test 6: Checking route-permissions.config.js exists..."
if [ -f "frontend/src/config/route-permissions.config.js" ]; then
    echo "✅ PASSED: route-permissions.config.js found"
else
    echo "❌ FAILED: route-permissions.config.js missing"
    ERRORS=$((ERRORS + 1))
fi
echo ""

# Test 7: Count PermissionGuard usages
echo "✅ Test 7: Counting PermissionGuard usages in MainRoutes.jsx..."
PERMISSION_GUARD_COUNT=$(grep -c "<PermissionGuard" frontend/src/routes/MainRoutes.jsx 2>/dev/null || echo "0")
echo "✅ Found $PERMISSION_GUARD_COUNT PermissionGuard usages"
if [ "$PERMISSION_GUARD_COUNT" -lt 80 ]; then
    echo "⚠️  WARNING: Expected ~87 PermissionGuard usages, found $PERMISSION_GUARD_COUNT"
    echo "   (This may be normal if some routes are public)"
fi
echo ""

# Test 8: Verify no RouteGuard component usage in active code
echo "✅ Test 8: Checking for RouteGuard component usage in src/..."
ROUTE_GUARD_USAGE=$(grep -r "<RouteGuard" frontend/src/ 2>/dev/null | grep -v ".deprecated" | grep -v "ProviderRouteGuard" | wc -l || echo "0")
if [ "$ROUTE_GUARD_USAGE" -gt 0 ]; then
    echo "❌ FAILED: Found $ROUTE_GUARD_USAGE RouteGuard component usages"
    grep -r "<RouteGuard" frontend/src/ | grep -v ".deprecated" | grep -v "ProviderRouteGuard"
    ERRORS=$((ERRORS + 1))
else
    echo "✅ PASSED: No RouteGuard component usage found"
fi
echo ""

# Test 9: Verify PermissionGuard has isRouteGuard prop in most cases
echo "✅ Test 9: Checking PermissionGuard uses isRouteGuard prop..."
ROUTE_GUARD_PROP_COUNT=$(grep -c "isRouteGuard" frontend/src/routes/MainRoutes.jsx 2>/dev/null || echo "0")
echo "✅ Found $ROUTE_GUARD_PROP_COUNT isRouteGuard props"
if [ "$ROUTE_GUARD_PROP_COUNT" -lt 70 ]; then
    echo "⚠️  WARNING: Expected ~87 isRouteGuard props, found $ROUTE_GUARD_PROP_COUNT"
fi
echo ""

# Test 10: Verify SUPER_ADMIN check uses correct syntax
echo "✅ Test 10: Checking SUPER_ADMIN bypass in PermissionGuard.jsx..."
if grep -q "userRole === 'SUPER_ADMIN'" frontend/src/components/PermissionGuard.jsx 2>/dev/null; then
    echo "✅ PASSED: SUPER_ADMIN check uses correct syntax (userRole === 'SUPER_ADMIN')"
else
    echo "❌ FAILED: SUPER_ADMIN check not found or uses wrong syntax"
    echo "   Expected: userRole === 'SUPER_ADMIN'"
    ERRORS=$((ERRORS + 1))
fi
echo ""

# Final Summary
echo "=========================================="
echo "📊 VALIDATION SUMMARY"
echo "=========================================="
if [ $ERRORS -eq 0 ]; then
    echo "✅ All tests PASSED"
    echo "✅ Migration is COMPLETE and VERIFIED"
    echo ""
    echo "Next Steps:"
    echo "1. Run the application locally"
    echo "2. Test SUPER_ADMIN access (all routes)"
    echo "3. Test ACCOUNTANT access (settlements + reports)"
    echo "4. Test PROVIDER access (provider portal only)"
    echo "5. Test REVIEWER access (claims/pre-auth inbox)"
    echo "6. Deploy to staging environment"
    exit 0
else
    echo "❌ $ERRORS test(s) FAILED"
    echo "❌ Migration verification FAILED"
    echo ""
    echo "Please fix the errors above before deployment"
    exit 1
fi
