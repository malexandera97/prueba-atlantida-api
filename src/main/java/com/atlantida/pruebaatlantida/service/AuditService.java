package com.atlantida.pruebaatlantida.service;

import com.atlantida.pruebaatlantida.dto.AuditLogResponse;
import com.atlantida.pruebaatlantida.dto.TransferRequest;
import com.atlantida.pruebaatlantida.model.AuditLog;
import com.atlantida.pruebaatlantida.model.AuditOutcome;
import com.atlantida.pruebaatlantida.model.Transfer;
import com.atlantida.pruebaatlantida.repository.AuditLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditService {

    static final int DEFAULT_LIMIT = 100;
    static final int MAX_LIMIT = 500;

    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    public AuditService(AuditLogRepository auditLogRepository, Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSuccess(AuditContext context, Transfer transfer, boolean replayed) {
        AuditOutcome outcome = replayed ? AuditOutcome.REINTENTO : AuditOutcome.EXITOSA;
        HttpStatus status = replayed ? HttpStatus.OK : HttpStatus.CREATED;
        String message = replayed
                ? "Reintento idempotente de la transferencia " + transfer.getId()
                : "Transferencia " + transfer.getId() + " realizada";
        auditLogRepository.save(new AuditLog(LocalDateTime.now(clock), outcome, status.value(), message,
                context.username(), context.clientIp(), context.userAgent(),
                transfer.getSource().getId(), transfer.getDestination().getId(), transfer.getAmount(),
                context.auditableIdempotencyKey(), transfer.getId()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRejection(AuditContext context, TransferRequest request, int httpStatus, String message) {
        auditLogRepository.save(new AuditLog(LocalDateTime.now(clock), AuditOutcome.RECHAZADA, httpStatus,
                AuditContext.truncate(message, 500), context.username(), context.clientIp(), context.userAgent(),
                request == null ? null : request.sourceAccountId(),
                request == null ? null : request.destinationAccountId(),
                request == null ? null : request.amount(),
                context.auditableIdempotencyKey(), null));
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> search(Long accountId, AuditOutcome outcome, Integer limit) {
        Specification<AuditLog> specification = Specification.unrestricted();
        if (accountId != null) {
            specification = specification.and((root, query, cb) -> cb.or(
                    cb.equal(root.get("sourceAccountId"), accountId),
                    cb.equal(root.get("destinationAccountId"), accountId)));
        }
        if (outcome != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("outcome"), outcome));
        }
        int size = limit == null ? DEFAULT_LIMIT : Math.clamp(limit, 1, MAX_LIMIT);
        PageRequest page = PageRequest.of(0, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return auditLogRepository.findAll(specification, page).map(AuditLogResponse::from).getContent();
    }
}
