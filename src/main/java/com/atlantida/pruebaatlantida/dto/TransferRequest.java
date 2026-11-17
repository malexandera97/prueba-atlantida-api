package com.atlantida.pruebaatlantida.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TransferRequest(
        @NotNull(message = "La cuenta origen es obligatoria")
        Long sourceAccountId,

        @NotNull(message = "La cuenta destino es obligatoria")
        Long destinationAccountId,

        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor que cero")
        @Digits(integer = 16, fraction = 2, message = "El monto admite como máximo 2 decimales")
        BigDecimal amount
) {
}
