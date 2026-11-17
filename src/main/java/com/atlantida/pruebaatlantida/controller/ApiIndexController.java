package com.atlantida.pruebaatlantida.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class ApiIndexController {
    @GetMapping({"/", "/api"})
    public Map<String, Object> index() {
        return Map.of(
                "application", "prueba-atlantida-api",
                "status", "UP",
                "endpoints", List.of(
                        "GET /api/accounts",
                        "GET /api/accounts/{id}",
                        "GET /api/accounts/{id}/transfers",
                        "POST /api/transfers (cabeceras opcionales Idempotency-Key y X-Usuario)",
                        "GET /api/transfers/{id}",
                        "GET /api/audit?accountId=&outcome=&limit="));
    }
}
