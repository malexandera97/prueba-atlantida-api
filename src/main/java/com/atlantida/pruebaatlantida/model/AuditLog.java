package com.atlantida.pruebaatlantida.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    public static final String ACTION_TRANSFER = "TRANSFERENCIA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false, length = 30, updatable = false)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private AuditOutcome outcome;

    @Column(name = "http_status", nullable = false, updatable = false)
    private int httpStatus;

    @Column(length = 500, updatable = false)
    private String message;

    @Column(nullable = false, length = 100, updatable = false)
    private String username;

    @Column(name = "client_ip", length = 64, updatable = false)
    private String clientIp;

    @Column(name = "user_agent", length = 255, updatable = false)
    private String userAgent;

    @Column(name = "source_account_id", updatable = false)
    private Long sourceAccountId;

    @Column(name = "destination_account_id", updatable = false)
    private Long destinationAccountId;

    @Column(precision = 18, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "idempotency_key", length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "transfer_id", updatable = false)
    private Long transferId;

    protected AuditLog() {
    }

    public AuditLog(LocalDateTime createdAt, AuditOutcome outcome, int httpStatus, String message, String username,
                    String clientIp, String userAgent, Long sourceAccountId, Long destinationAccountId,
                    BigDecimal amount, String idempotencyKey, Long transferId) {
        this.createdAt = createdAt;
        this.action = ACTION_TRANSFER;
        this.outcome = outcome;
        this.httpStatus = httpStatus;
        this.message = message;
        this.username = username;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.transferId = transferId;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getAction() {
        return action;
    }

    public AuditOutcome getOutcome() {
        return outcome;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getMessage() {
        return message;
    }

    public String getUsername() {
        return username;
    }

    public String getClientIp() {
        return clientIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Long getSourceAccountId() {
        return sourceAccountId;
    }

    public Long getDestinationAccountId() {
        return destinationAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Long getTransferId() {
        return transferId;
    }
}
