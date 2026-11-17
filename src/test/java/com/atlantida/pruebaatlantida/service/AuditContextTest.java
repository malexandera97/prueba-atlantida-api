package com.atlantida.pruebaatlantida.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class AuditContextTest {

    @Test
    @DisplayName("Toma usuario, IP, navegador e Idempotency-Key de la petición")
    void desdeLaPeticion() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Usuario", "  cajero01  ");
        request.addHeader("User-Agent", "Mozilla/5.0");
        request.addHeader("Idempotency-Key", " clave-1 ");

        AuditContext context = AuditContext.from(request);

        assertThat(context.username()).isEqualTo("cajero01");
        assertThat(context.clientIp()).isEqualTo("10.0.0.5");
        assertThat(context.userAgent()).isEqualTo("Mozilla/5.0");
        assertThat(context.idempotencyKey()).isEqualTo("clave-1");
    }

    @Test
    @DisplayName("Sin X-Usuario queda como anonimo; detrás de un proxy usa la primera IP de X-Forwarded-For")
    void valoresPorDefectoYProxy() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.1");
        request.addHeader("X-Forwarded-For", "200.1.2.3, 172.18.0.1");

        AuditContext context = AuditContext.from(request);

        assertThat(context.username()).isEqualTo(AuditContext.ANONYMOUS);
        assertThat(context.clientIp()).isEqualTo("200.1.2.3");
        assertThat(context.idempotencyKey()).isNull();
    }

    @Test
    @DisplayName("Recorta los valores largos al tamaño de las columnas sin perder la clave original para validarla")
    void recortaValoresLargos() {
        AuditContext context = new AuditContext("u".repeat(150), "1".repeat(80), "a".repeat(300), "k".repeat(120));

        assertThat(context.username()).hasSize(100);
        assertThat(context.clientIp()).hasSize(64);
        assertThat(context.userAgent()).hasSize(255);
        assertThat(context.idempotencyKey()).hasSize(120);
        assertThat(context.auditableIdempotencyKey()).hasSize(100);
    }
}
