package com.atlantida.pruebaatlantida.controller;

import com.atlantida.pruebaatlantida.dto.TransferRequest;
import com.atlantida.pruebaatlantida.dto.TransferResponse;
import com.atlantida.pruebaatlantida.service.AuditContext;
import com.atlantida.pruebaatlantida.service.TransferService;
import com.atlantida.pruebaatlantida.service.TransferService.TransferResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {
    public static final String TRANSFER_REQUEST_ATTRIBUTE = TransferController.class.getName() + ".request";
    static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> transfer(@Valid @RequestBody TransferRequest request, HttpServletRequest httpRequest) {
        httpRequest.setAttribute(TRANSFER_REQUEST_ATTRIBUTE, request);
        TransferResult result = transferService.transfer(request, AuditContext.from(httpRequest));
        if (result.replayed()) {
            return ResponseEntity.ok().header(REPLAYED_HEADER, "true").body(result.transfer());
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(result.transfer().id()).toUri();
        return ResponseEntity.created(location).body(result.transfer());
    }

    @GetMapping("/{id}")
    public TransferResponse findById(@PathVariable Long id) {
        return transferService.findById(id);
    }
}
