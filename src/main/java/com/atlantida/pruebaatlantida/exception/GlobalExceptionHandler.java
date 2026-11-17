package com.atlantida.pruebaatlantida.exception;

import com.atlantida.pruebaatlantida.controller.TransferController;
import com.atlantida.pruebaatlantida.dto.ApiError;
import com.atlantida.pruebaatlantida.dto.TransferRequest;
import com.atlantida.pruebaatlantida.service.AuditContext;
import com.atlantida.pruebaatlantida.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TRANSFERS_PATH = "/api/transfers";

    private final AuditService auditService;

    public GlobalExceptionHandler(AuditService auditService) {
        this.auditService = auditService;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> notFound(ResourceNotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req, List.of());
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> businessRule(BusinessRuleException ex, HttpServletRequest req) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), req, List.of());
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiError> invalidRequest(InvalidRequestException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req, List.of());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> conflict(ConflictException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), req, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ApiError.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new ApiError.FieldError(f.getField(), f.getDefaultMessage()))
                .toList();
        String detail = errors.stream().map(e -> e.field() + ": " + e.message()).collect(Collectors.joining("; "));
        Object target = ex.getBindingResult().getTarget();
        auditTransferRejection(req, HttpStatus.BAD_REQUEST, "La solicitud tiene datos inválidos (" + detail + ")",
                target instanceof TransferRequest transferRequest ? transferRequest : null);
        return response(HttpStatus.BAD_REQUEST, "La solicitud tiene datos inválidos", req, errors);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingRequestHeaderException.class})
    public ResponseEntity<ApiError> malformed(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "La solicitud tiene un formato o un valor no válido", req, List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> integrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Restricción de base de datos en {}: {}", req.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "La operación entra en conflicto con datos ya registrados. Los saldos no cambiaron.", req, List.of());
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ApiError> lockFailure(PessimisticLockingFailureException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "La cuenta está siendo usada por otra operación. Intente de nuevo.", req, List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> noRoute(NoResourceFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "No existe el recurso " + req.getRequestURI(), req, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> methodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "El método " + ex.getMethod() + " no está permitido en " + req.getRequestURI(), req, List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> mediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "El cuerpo debe enviarse como JSON (Content-Type: application/json)", req, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest req) {
        log.error("Error no controlado en {}", req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error interno. Intente más tarde.", req, List.of());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest req, List<ApiError.FieldError> errors) {
        auditTransferRejection(req, status, message, null);
        return response(status, message, req, errors);
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String message, HttpServletRequest req, List<ApiError.FieldError> errors) {
        ApiError body = new ApiError(LocalDateTime.now(), status.value(), status.getReasonPhrase(), message, req.getRequestURI(), errors);
        return ResponseEntity.status(status).body(body);
    }

    private void auditTransferRejection(HttpServletRequest req, HttpStatus status, String message, TransferRequest boundRequest) {
        if (!"POST".equalsIgnoreCase(req.getMethod()) || !TRANSFERS_PATH.equals(req.getRequestURI())) {
            return;
        }
        TransferRequest request = req.getAttribute(TransferController.TRANSFER_REQUEST_ATTRIBUTE) instanceof TransferRequest attribute
                ? attribute
                : boundRequest;
        try {
            auditService.recordRejection(AuditContext.from(req), request, status.value(), message);
        } catch (RuntimeException auditFailure) {
            log.error("No se pudo registrar la auditoría del rechazo en {}", req.getRequestURI(), auditFailure);
        }
    }
}
