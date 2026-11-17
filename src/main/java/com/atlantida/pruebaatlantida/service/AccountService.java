package com.atlantida.pruebaatlantida.service;

import com.atlantida.pruebaatlantida.dto.AccountResponse;
import com.atlantida.pruebaatlantida.dto.TransferHistoryItem;
import com.atlantida.pruebaatlantida.exception.ResourceNotFoundException;
import com.atlantida.pruebaatlantida.repository.AccountRepository;
import com.atlantida.pruebaatlantida.repository.TransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AccountService {
    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;

    public AccountService(AccountRepository accountRepository, TransferRepository transferRepository) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> findAll() {
        return accountRepository.findAllByOrderByIdAsc().stream().map(AccountResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse findById(Long id) {
        return accountRepository.findById(id)
                .map(AccountResponse::from)
                .orElseThrow(() -> accountNotFound(id));
    }

    @Transactional(readOnly = true)
    public List<TransferHistoryItem> history(Long accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw accountNotFound(accountId);
        }
        return transferRepository.findHistoryByAccountId(accountId).stream()
                .map(t -> TransferHistoryItem.of(t, accountId))
                .toList();
    }

    static ResourceNotFoundException accountNotFound(Long id) {
        return new ResourceNotFoundException("No existe la cuenta " + id);
    }
}
