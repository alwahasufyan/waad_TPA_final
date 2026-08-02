package com.waad.tba.modules.rbac.controller;

import com.waad.tba.modules.rbac.dto.UserPermissionOverrideDto;
import com.waad.tba.modules.rbac.service.EffectivePermissionService;
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
 * WAAD-RBAC-PHASE-3B-USER-OVERRIDES-API-UI: same minimal-context pattern as
 * RolePermissionAdminControllerAuthorizationTest — only SUPER_ADMIN/WAAD_ADMIN
 * may reach any endpoint here.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { UserPermissionOverrideController.class, MethodSecurityConfig.class })
class UserPermissionOverrideControllerAuthorizationTest {

    @Autowired
    private UserPermissionOverrideController controller;

    @MockitoBean
    private EffectivePermissionService effectivePermissionService;

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
    void waadAdmin_canListOverrides() throws Exception {
        when(effectivePermissionService.getOverrideDtosForUser(2L)).thenReturn(List.<UserPermissionOverrideDto>of());
        mockMvc().perform(get("/api/v1/admin/rbac/users/2/overrides"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MEDICAL_REVIEWER")
    void medicalReviewer_cannotListOverrides() throws Exception {
        mockMvc().perform(get("/api/v1/admin/rbac/users/2/overrides"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountant_cannotListOverrides() throws Exception {
        mockMvc().perform(get("/api/v1/admin/rbac/users/2/overrides"))
                .andExpect(status().isForbidden());
    }
}
