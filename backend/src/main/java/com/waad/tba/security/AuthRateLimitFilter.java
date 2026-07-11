package com.waad.tba.security;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Stage 1 (D9) — lightweight, dependency-free brute-force protection for the
 * sensitive authentication endpoints (login, registration, password reset).
 *
 * <p><b>Design goals (production-safe by construction):</b>
 * <ul>
 *   <li><b>Fail-open:</b> any internal error in this filter is swallowed and the
 *       request proceeds normally — rate limiting must never take auth down.</li>
 *   <li><b>Additive:</b> only guards a fixed set of auth POST paths; every other
 *       request passes through untouched. No workflow or contract change.</li>
 *   <li><b>Generous + configurable:</b> defaults ({@code 20} requests /
 *       {@code 60}s per client-IP per path) never affect legitimate use; tune
 *       via {@code security.rate-limit.auth.*} without a code change.</li>
 *   <li><b>Defense-in-depth:</b> complements the existing account-lockout
 *       (5 failed logins → 30 min) with an IP-level throttle that also covers
 *       account enumeration via forgot-password/register.</li>
 * </ul>
 *
 * <p>Uses a fixed-window counter in a bounded in-memory map (no new
 * dependencies, no Redis). Session-cookie auth remains the preferred path; this
 * filter runs before authentication so it protects unauthenticated endpoints.
 */
@Slf4j
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    /** Sensitive unauthenticated auth endpoints worth throttling. */
    private static final Set<String> GUARDED_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/session/login",
            "/api/v1/auth/register",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/token/forgot-password",
            "/api/v1/auth/token/reset-password");

    /** Safety valve to keep the counter map bounded under an attack. */
    private static final int MAX_TRACKED_KEYS = 50_000;

    private final ConcurrentHashMap<String, Window> counters = new ConcurrentHashMap<>();

    @Value("${security.rate-limit.auth.enabled:true}")
    private boolean enabled;

    @Value("${security.rate-limit.auth.max-requests:20}")
    private int maxRequests;

    @Value("${security.rate-limit.auth.window-seconds:60}")
    private long windowSeconds;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            if (enabled
                    && "POST".equalsIgnoreCase(request.getMethod())
                    && GUARDED_PATHS.contains(request.getRequestURI())
                    && isRateLimited(clientIp(request), request.getRequestURI())) {
                writeTooManyRequests(response, request.getRequestURI());
                return; // block — do not continue the chain
            }
        } catch (Exception ex) {
            // FAIL-OPEN: never let the throttle break authentication.
            log.warn("AuthRateLimitFilter error (failing open): {}", ex.getMessage());
        }
        chain.doFilter(request, response);
    }

    private boolean isRateLimited(String ip, String path) {
        long windowMs = windowSeconds * 1000L;
        long now = System.currentTimeMillis();

        // Opportunistic bounded-map protection: if we somehow blow past the cap
        // (e.g. distributed attack), clear stale windows rather than grow forever.
        if (counters.size() > MAX_TRACKED_KEYS) {
            counters.entrySet().removeIf(e -> now - e.getValue().windowStart > windowMs);
        }

        String key = ip + "|" + path;
        Window window = counters.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStart >= windowMs) {
                return new Window(now);
            }
            existing.count.incrementAndGet();
            return existing;
        });

        boolean limited = window.count.get() > maxRequests;
        if (limited) {
            log.warn("Auth rate limit hit: ip={} path={} count={} (max={}/{}s)",
                    ip, path, window.count.get(), maxRequests, windowSeconds);
        }
        return limited;
    }

    private void writeTooManyRequests(HttpServletResponse response, String path) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", String.valueOf(windowSeconds));
        String body = "{\"success\":false,"
                + "\"code\":\"RATE_LIMITED\","
                + "\"message\":\"Too many attempts. Please wait a moment and try again.\","
                + "\"messageAr\":\"عدد محاولات كبير. يُرجى الانتظار قليلاً ثم إعادة المحاولة.\","
                + "\"path\":\"" + path + "\"}";
        response.getWriter().write(body);
    }

    /** Honour X-Forwarded-For (nginx reverse proxy) then fall back to remote addr. */
    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return request.getRemoteAddr();
    }

    /** Fixed-window counter. */
    private static final class Window {
        final long windowStart;
        final AtomicInteger count;

        Window(long windowStart) {
            this.windowStart = windowStart;
            this.count = new AtomicInteger(1);
        }
    }
}
