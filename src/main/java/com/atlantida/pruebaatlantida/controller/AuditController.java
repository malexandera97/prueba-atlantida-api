package com.atlantida.pruebaatlantida.controller;

import com.atlantida.pruebaatlantida.dto.AuditLogResponse;
import com.atlantida.pruebaatlantida.model.AuditOutcome;
import com.atlantida.pruebaatlantida.service.AuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public List<AuditLogResponse> search(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(required = false) Integer limit) {
        return auditService.search(accountId, outcome, limit);
    }
}
