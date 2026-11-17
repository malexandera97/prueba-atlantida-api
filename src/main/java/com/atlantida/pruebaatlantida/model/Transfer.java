package com.atlantida.pruebaatlantida.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transfers")
public class Transfer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_account_id", nullable = false)
    private Account source;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_account_id", nullable = false)
    private Account destination;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "idempotency_key", length = 100, unique = true)
    private String idempotencyKey;

    @Column(name = "source_balance_after", precision = 18, scale = 2)
    private BigDecimal sourceBalanceAfter;

    @Column(name = "destination_balance_after", precision = 18, scale = 2)
    private BigDecimal destinationBalanceAfter;

    protected Transfer() {
    }

    public Transfer(Account source, Account destination, BigDecimal amount, LocalDateTime createdAt, String idempotencyKey) {
        this.source = source;
        this.destination = destination;
        this.amount = amount;
        this.createdAt = createdAt;
        this.idempotencyKey = idempotencyKey;
        this.sourceBalanceAfter = source.getBalance();
        this.destinationBalanceAfter = destination.getBalance();
    }

    public boolean matches(Long sourceAccountId, Long destinationAccountId, BigDecimal requestedAmount) {
        return source.getId().equals(sourceAccountId)
                && destination.getId().equals(destinationAccountId)
                && amount.compareTo(requestedAmount) == 0;
    }

    public BigDecimal sourceBalanceBefore() {
        return sourceBalanceAfter.add(amount);
    }

    public BigDecimal destinationBalanceBefore() {
        return destinationBalanceAfter.subtract(amount);
    }

    public Long getId() {
        return id;
    }

    public Account getSource() {
        return source;
    }

    public Account getDestination() {
        return destination;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public BigDecimal getSourceBalanceAfter() {
        return sourceBalanceAfter;
    }

    public BigDecimal getDestinationBalanceAfter() {
        return destinationBalanceAfter;
    }
}
