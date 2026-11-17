package com.atlantida.pruebaatlantida.dto;

import com.atlantida.pruebaatlantida.model.Account;
import com.atlantida.pruebaatlantida.model.Transfer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransferHistoryItem(
        Long transferId,
        Direction direction,
        Long counterpartAccountId,
        String counterpartHolderName,
        BigDecimal amount,
        BigDecimal balanceAfter,
        LocalDateTime createdAt
) {
    public enum Direction { ENVIADA, RECIBIDA }

    public static TransferHistoryItem of(Transfer transfer, Long accountId) {
        boolean sent = transfer.getSource().getId().equals(accountId);
        Account counterpart = sent ? transfer.getDestination() : transfer.getSource();
        return new TransferHistoryItem(
                transfer.getId(),
                sent ? Direction.ENVIADA : Direction.RECIBIDA,
                counterpart.getId(),
                counterpart.getHolderName(),
                transfer.getAmount(),
                sent ? transfer.getSourceBalanceAfter() : transfer.getDestinationBalanceAfter(),
                transfer.getCreatedAt());
    }
}
