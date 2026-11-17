package com.atlantida.pruebaatlantida.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditApiIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void datosIniciales() {
        jdbc.update("DELETE FROM audit_log");
        jdbc.update("DELETE FROM transfers");
        jdbc.update("UPDATE accounts SET balance = 5000.00 WHERE id = 1001");
        jdbc.update("UPDATE accounts SET balance = 2000.00 WHERE id = 1002");
        jdbc.update("UPDATE accounts SET balance = 10000.00 WHERE id = 1003");
    }

    private static String cuerpo(long origen, long destino, String monto) {
        return "{\"sourceAccountId\":" + origen + ",\"destinationAccountId\":" + destino + ",\"amount\":" + monto + "}";
    }

    private int auditorias() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Integer.class);
    }

    private Map<String, Object> ultimaAuditoria() {
        return jdbc.queryForMap("SELECT * FROM audit_log ORDER BY id DESC LIMIT 1");
    }

    @Test
    @DisplayName("Transferencia exitosa: se audita como EXITOSA con usuario, IP, cuentas, monto y número de transferencia")
    void auditaExitosa() throws Exception {
        String respuesta = mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Usuario", "cajero01").header("Idempotency-Key", "aud-1").header("User-Agent", "JUnit")
                        .content(cuerpo(1001, 1002, "500.00")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer transferId = JsonPath.read(respuesta, "$.id");

        assertThat(auditorias()).isEqualTo(1);
        Map<String, Object> fila = ultimaAuditoria();
        assertThat(fila.get("action")).isEqualTo("TRANSFERENCIA");
        assertThat(fila.get("outcome")).isEqualTo("EXITOSA");
        assertThat(fila.get("http_status")).isEqualTo(201);
        assertThat(fila.get("username")).isEqualTo("cajero01");
        assertThat(fila.get("client_ip")).isNotNull();
        assertThat(fila.get("user_agent")).isEqualTo("JUnit");
        assertThat(fila.get("source_account_id")).isEqualTo(1001L);
        assertThat(fila.get("destination_account_id")).isEqualTo(1002L);
        assertThat((BigDecimal) fila.get("amount")).isEqualByComparingTo("500.00");
        assertThat(fila.get("idempotency_key")).isEqualTo("aud-1");
        assertThat(fila.get("transfer_id")).isEqualTo(transferId.longValue());
    }

    @Test
    @DisplayName("Fondos insuficientes: la transferencia hace rollback pero el intento queda auditado como RECHAZADA (422)")
    void auditaRechazoAunqueHagaRollback() throws Exception {
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).header("X-Usuario", "cajero02")
                        .content(cuerpo(1001, 1003, "6000.00")))
                .andExpect(status().is(422));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transfers", Integer.class)).isZero();
        Map<String, Object> fila = ultimaAuditoria();
        assertThat(fila.get("outcome")).isEqualTo("RECHAZADA");
        assertThat(fila.get("http_status")).isEqualTo(422);
        assertThat(fila.get("message")).isEqualTo("Fondos insuficientes en la cuenta 1001. Los saldos no cambiaron.");
        assertThat(fila.get("username")).isEqualTo("cajero02");
        assertThat(fila.get("source_account_id")).isEqualTo(1001L);
        assertThat(fila.get("destination_account_id")).isEqualTo(1003L);
        assertThat((BigDecimal) fila.get("amount")).isEqualByComparingTo("6000.00");
        assertThat(fila.get("transfer_id")).isNull();
    }

    @Test
    @DisplayName("Todos los rechazos de POST /api/transfers se auditan con su código: 400, 404, 409 y 415")
    void auditaTodosLosRechazos() throws Exception {
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(cuerpo(1001, 1002, "0")))
                .andExpect(status().isBadRequest());
        Map<String, Object> validacion = ultimaAuditoria();
        assertThat(validacion.get("http_status")).isEqualTo(400);
        assertThat((String) validacion.get("message")).contains("amount");
        assertThat((BigDecimal) validacion.get("amount")).isEqualByComparingTo("0");
        assertThat(validacion.get("username")).isEqualTo("anonimo");

        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(cuerpo(1001, 1001, "10")))
                .andExpect(status().isBadRequest());
        assertThat(ultimaAuditoria().get("message")).isEqualTo("La cuenta origen y la cuenta destino deben ser diferentes.");

        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(cuerpo(1001, 9999, "10")))
                .andExpect(status().isNotFound());
        assertThat(ultimaAuditoria().get("destination_account_id")).isEqualTo(9999L);
        assertThat(ultimaAuditoria().get("http_status")).isEqualTo(404);

        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content("{monto: 10"))
                .andExpect(status().isBadRequest());
        assertThat(ultimaAuditoria().get("source_account_id")).isNull();

        mvc.perform(post("/api/transfers").contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType());
        assertThat(ultimaAuditoria().get("http_status")).isEqualTo(415);

        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", "x-1")
                .content(cuerpo(1001, 1002, "10"))).andExpect(status().isCreated());
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", "x-1")
                .content(cuerpo(1001, 1002, "99"))).andExpect(status().isConflict());
        assertThat(ultimaAuditoria().get("http_status")).isEqualTo(409);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE outcome = 'RECHAZADA'", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE outcome = 'EXITOSA'", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Reintento idempotente: se audita como REINTENTO apuntando a la misma transferencia")
    void auditaReintento() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", "rep-1")
                    .content(cuerpo(1001, 1002, "100.00")));
        }

        assertThat(jdbc.queryForList("SELECT outcome FROM audit_log ORDER BY id", String.class))
                .containsExactly("EXITOSA", "REINTENTO");
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT transfer_id) FROM audit_log", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT balance FROM accounts WHERE id = 1001", BigDecimal.class)).isEqualByComparingTo("4900.00");
    }

    @Test
    @DisplayName("Las consultas no generan auditoría")
    void consultasNoSeAuditan() throws Exception {
        mvc.perform(get("/api/accounts")).andExpect(status().isOk());
        mvc.perform(get("/api/accounts/1001")).andExpect(status().isOk());
        mvc.perform(get("/api/accounts/9999")).andExpect(status().isNotFound());
        assertThat(auditorias()).isZero();
    }

    @Test
    @DisplayName("GET /api/audit: más reciente primero, filtros por cuenta y resultado, límite y validación del filtro")
    void consultarAuditoria() throws Exception {
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(cuerpo(1001, 1002, "100")));
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(cuerpo(1003, 1002, "200")));
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(cuerpo(1002, 1003, "99999")));

        mvc.perform(get("/api/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].outcome").value("RECHAZADA"))
                .andExpect(jsonPath("$[0].httpStatus").value(422))
                .andExpect(jsonPath("$[2].outcome").value("EXITOSA"));

        mvc.perform(get("/api/audit").param("accountId", "1001"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].sourceAccountId").value(1001));
        mvc.perform(get("/api/audit").param("outcome", "EXITOSA"))
                .andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/audit").param("accountId", "1003").param("outcome", "RECHAZADA"))
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/audit").param("limit", "1"))
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/audit").param("outcome", "DESCONOCIDO"))
                .andExpect(status().isBadRequest());
    }
}
