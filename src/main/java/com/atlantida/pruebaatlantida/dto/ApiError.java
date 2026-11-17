package com.atlantida.pruebaatlantida.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ApiError(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> errors
) {
    public record FieldError(String field, String message) {
    }
}
