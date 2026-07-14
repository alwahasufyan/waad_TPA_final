package com.waad.tba.modules.maintenance.filter;

import com.waad.tba.modules.maintenance.service.MaintenanceModeService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * When maintenance mode is on, block mutating requests from non-admins so the system is quiet
 * during a restore/reset. SUPER_ADMIN always passes (so they can run the operation and later
 * disable maintenance). Reads/health/auth/danger-zone/maintenance endpoints are always allowed.
 * Runs after Spring Security so the authenticated principal is available.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class MaintenanceModeFilter extends OncePerRequestFilter {

    private final MaintenanceModeService maintenanceModeService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (shouldBlock(request)) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(
                    "{\"success\":false,\"code\":\"MAINTENANCE_MODE\","
                            + "\"message\":\"System is in maintenance mode\","
                            + "\"messageAr\":\"النظام في وضع الصيانة حاليًا. يُرجى المحاولة لاحقًا.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean shouldBlock(HttpServletRequest request) {
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) {
            return false;
        }
        String path = request.getRequestURI();
        if (isAllowlisted(path)) {
            return false;
        }
        if (hasSuperAdmin()) {
            return false;
        }
        try {
            return maintenanceModeService.isEnabled();
        } catch (Exception e) {
            // Never block traffic because of a maintenance-flag lookup failure.
            log.warn("Maintenance filter check failed: {}", e.getMessage());
            return false;
        }
    }

    private static boolean isAllowlisted(String path) {
        if (path == null) {
            return true;
        }
        return path.startsWith("/api/v1/auth/")
                || path.startsWith("/actuator/")
                // External monitor heartbeat must keep working during maintenance (it is how we watch the app).
                || path.startsWith("/api/v1/system/monitoring/external-heartbeat")
                || path.startsWith("/api/v1/system/maintenance")
                || path.startsWith("/api/v1/system/danger-zone");
    }

    private static boolean hasSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
    }
}
