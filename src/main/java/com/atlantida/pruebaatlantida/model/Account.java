package com.atlantida.pruebaatlantida.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "accounts")
public class Account {
    @Id
    private Long id;

    @Column(name = "holder_name", nullable = false, length = 100)
    private String holderName;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal balance;

    protected Account() {
    }

    public Account(Long id, String holderName, BigDecimal balance) {
        this.id = id;
        this.holderName = holderName;
        this.balance = balance;
    }

    public boolean hasFunds(BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }

    public void debit(BigDecimal amount) {
        if (!hasFunds(amount)) {
            throw new IllegalStateException("Saldo insuficiente en la cuenta " + id);
        }
        balance = balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        balance = balance.add(amount);
    }

    public Long getId() {
        return id;
    }

    public String getHolderName() {
        return holderName;
    }

    public BigDecimal getBalance() {
        return balance;
    }
}
