package com.atlantida.pruebaatlantida.service;

import jakarta.servlet.http.HttpServletRequest;

public record AuditContext(String username, String clientIp, String userAgent, String idempotencyKey) {

    public static final String USER_HEADER = "X-Usuario";
    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    public static final String ANONYMOUS = "anonimo";

    public static AuditContext from(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String clientIp = forwardedFor != null && !forwardedFor.isBlank()
                ? forwardedFor.split(",")[0].trim()
                : request.getRemoteAddr();
        return new AuditContext(
                request.getHeader(USER_HEADER),
                clientIp,
                request.getHeader("User-Agent"),
                request.getHeader(IDEMPOTENCY_HEADER));
    }

    public AuditContext {
        username = username == null || username.isBlank() ? ANONYMOUS : truncate(username.trim(), 100);
        clientIp = truncate(clientIp, 64);
        userAgent = truncate(userAgent, 255);
        idempotencyKey = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
    }

    public String auditableIdempotencyKey() {
        return truncate(idempotencyKey, 100);
    }

    static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
