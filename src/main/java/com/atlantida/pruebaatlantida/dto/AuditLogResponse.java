package com.atlantida.pruebaatlantida.dto;

import com.atlantida.pruebaatlantida.model.AuditLog;
import com.atlantida.pruebaatlantida.model.AuditOutcome;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        LocalDateTime createdAt,
        String action,
        AuditOutcome outcome,
        int httpStatus,
        String message,
        String username,
        String clientIp,
        String userAgent,
        Long sourceAccountId,
        Long destinationAccountId,
        BigDecimal amount,
        String idempotencyKey,
        Long transferId
) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getCreatedAt(), log.getAction(), log.getOutcome(), log.getHttpStatus(),
                log.getMessage(), log.getUsername(), log.getClientIp(), log.getUserAgent(), log.getSourceAccountId(),
                log.getDestinationAccountId(), log.getAmount(), log.getIdempotencyKey(), log.getTransferId());
    }
}
