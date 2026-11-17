package com.atlantida.pruebaatlantida.dto;

import com.atlantida.pruebaatlantida.model.Account;
import com.atlantida.pruebaatlantida.model.Transfer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransferResponse(
        Long id,
        BigDecimal amount,
        LocalDateTime createdAt,
        AccountMovement source,
        AccountMovement destination
) {
    public record AccountMovement(Long accountId, String holderName, BigDecimal previousBalance, BigDecimal newBalance) {
    }

    public static TransferResponse from(Transfer transfer) {
        Account source = transfer.getSource();
        Account destination = transfer.getDestination();
        return new TransferResponse(
                transfer.getId(),
                transfer.getAmount(),
                transfer.getCreatedAt(),
                new AccountMovement(source.getId(), source.getHolderName(),
                        transfer.sourceBalanceBefore(), transfer.getSourceBalanceAfter()),
                new AccountMovement(destination.getId(), destination.getHolderName(),
                        transfer.destinationBalanceBefore(), transfer.getDestinationBalanceAfter()));
    }
}
