package com.atlantida.pruebaatlantida.service;

import com.atlantida.pruebaatlantida.dto.TransferRequest;
import com.atlantida.pruebaatlantida.dto.TransferResponse;
import com.atlantida.pruebaatlantida.exception.BusinessRuleException;
import com.atlantida.pruebaatlantida.exception.ConflictException;
import com.atlantida.pruebaatlantida.exception.InvalidRequestException;
import com.atlantida.pruebaatlantida.exception.ResourceNotFoundException;
import com.atlantida.pruebaatlantida.model.Account;
import com.atlantida.pruebaatlantida.model.Transfer;
import com.atlantida.pruebaatlantida.repository.AccountRepository;
import com.atlantida.pruebaatlantida.repository.TransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class TransferService {
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final AuditService auditService;
    private final Clock clock;

    public TransferService(AccountRepository accountRepository, TransferRepository transferRepository,
                           AuditService auditService, Clock clock) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    public record TransferResult(TransferResponse transfer, boolean replayed) {
    }

    @Transactional
    public TransferResult transfer(TransferRequest request, AuditContext context) {
        String key = normalizeKey(context.idempotencyKey());
        Long sourceId = request.sourceAccountId();
        Long destinationId = request.destinationAccountId();

        if (sourceId.equals(destinationId)) {
            throw new InvalidRequestException("La cuenta origen y la cuenta destino deben ser diferentes.");
        }

        Account first = lockAccount(Math.min(sourceId, destinationId));
        Account second = lockAccount(Math.max(sourceId, destinationId));
        Account source = first.getId().equals(sourceId) ? first : second;
        Account destination = source == first ? second : first;

        if (key != null) {
            Optional<Transfer> previous = transferRepository.findByIdempotencyKey(key);
            if (previous.isPresent()) {
                Transfer original = previous.get();
                if (!original.matches(sourceId, destinationId, request.amount())) {
                    throw new ConflictException("La clave de idempotencia ya se usó para otra transferencia con datos distintos.");
                }
                auditService.recordSuccess(context, original, true);
                return new TransferResult(TransferResponse.from(original), true);
            }
        }

        if (!source.hasFunds(request.amount())) {
            throw new BusinessRuleException("Fondos insuficientes en la cuenta " + sourceId + ". Los saldos no cambiaron.");
        }

        source.debit(request.amount());
        destination.credit(request.amount());
        Transfer saved = transferRepository.save(
                new Transfer(source, destination, request.amount(), LocalDateTime.now(clock), key));
        auditService.recordSuccess(context, saved, false);

        return new TransferResult(TransferResponse.from(saved), false);
    }

    @Transactional(readOnly = true)
    public TransferResponse findById(Long id) {
        return transferRepository.findWithAccountsById(id)
                .map(TransferResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("No existe la transferencia " + id));
    }

    private Account lockAccount(Long id) {
        return accountRepository.findByIdForUpdate(id).orElseThrow(() -> AccountService.accountNotFound(id));
    }

    private String normalizeKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String key = idempotencyKey.trim();
        if (key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new InvalidRequestException("La cabecera Idempotency-Key admite como máximo " + MAX_IDEMPOTENCY_KEY_LENGTH + " caracteres.");
        }
        return key;
    }
}
