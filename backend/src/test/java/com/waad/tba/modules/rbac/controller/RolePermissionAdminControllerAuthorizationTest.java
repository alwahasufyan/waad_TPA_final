package com.waad.tba.modules.rbac.controller;

import com.waad.tba.modules.rbac.dto.RoleSummaryDto;
import com.waad.tba.modules.rbac.service.RolePermissionAdminService;
import com.waad.tba.security.AuthorizationService;
import com.waad.tba.security.MethodSecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WAAD-RBAC-USERS-ROLES-UI-REDESIGN-1: exercises the real @PreAuthorize
 * enforcement on the new role-permission-matrix endpoints, same minimal-context
 * pattern as KinshipMismatchControllerAuthorizationTest /
 * WaadAdminControllerAccessAuthorizationTest.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { RolePermissionAdminController.class, MethodSecurityConfig.class })
class RolePermissionAdminControllerAuthorizationTest {

    @Autowired
    private RolePermissionAdminController controller;

    @MockitoBean
    private RolePermissionAdminService adminService;

    @MockitoBean
    private AuthorizationService authorizationService;

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AccessDeniedAdvice())
                .build();
    }

    @RestControllerAdvice
    static class AccessDeniedAdvice {
        @ExceptionHandler(AccessDeniedException.class)
        ResponseEntity<Void> handle(AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @Test
    @WithMockUser(roles = "WAAD_ADMIN")
    void waadAdmin_canReadRoles() throws Exception {
        when(adminService.getRoles()).thenReturn(List.<RoleSummaryDto>of());
        mockMvc().perform(get("/api/v1/admin/rbac/roles"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MEDICAL_REVIEWER")
    void medicalReviewer_cannotReadRoles() throws Exception {
        mockMvc().perform(get("/api/v1/admin/rbac/roles"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PROVIDER_STAFF")
    void providerStaff_cannotReadPermissionCatalog() throws Exception {
        mockMvc().perform(get("/api/v1/admin/rbac/permissions/grouped"))
                .andExpect(status().isForbidden());
    }
}
