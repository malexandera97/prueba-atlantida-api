package com.atlantida.pruebaatlantida.controller;

import com.atlantida.pruebaatlantida.dto.TransferRequest;
import com.atlantida.pruebaatlantida.exception.BusinessRuleException;
import com.atlantida.pruebaatlantida.service.AuditContext;
import com.atlantida.pruebaatlantida.service.TransferService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransferApiIntegrationTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransferService transferService;

    @BeforeEach
    void datosIniciales() {
        jdbc.update("DELETE FROM audit_log");
        jdbc.update("DELETE FROM transfers");
        jdbc.update("UPDATE accounts SET balance = 5000.00 WHERE id = 1001");
        jdbc.update("UPDATE accounts SET balance = 2000.00 WHERE id = 1002");
        jdbc.update("UPDATE accounts SET balance = 10000.00 WHERE id = 1003");
    }

    private BigDecimal saldo(long id) {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, id);
    }

    private static String cuerpo(long origen, long destino, String monto) {
        return "{\"sourceAccountId\":" + origen + ",\"destinationAccountId\":" + destino + ",\"amount\":" + monto + "}";
    }

    @Test
    @DisplayName("GET /api/accounts devuelve las 3 cuentas del Anexo A y GET /api/accounts/{id} su detalle")
    void consultarCuentas() throws Exception {
        mvc.perform(get("/api/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value(1001))
                .andExpect(jsonPath("$[0].holderName").value("Juan Pérez"))
                .andExpect(jsonPath("$[0].balance").value(5000.00));
        mvc.perform(get("/api/accounts/1003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holderName").value("Carlos Gómez"));
        mvc.perform(get("/api/accounts/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No existe la cuenta 9999"));
    }

    @Test
    @DisplayName("POST /api/transfers exitosa: 201, Location, saldos anterior/nuevo y los saldos quedan guardados")
    void transferenciaExitosa() throws Exception {
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(cuerpo(1001, 1002, "500.00")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.amount").value(500.00))
                .andExpect(jsonPath("$.source.previousBalance").value(5000.00))
                .andExpect(jsonPath("$.source.newBalance").value(4500.00))
                .andExpect(jsonPath("$.destination.previousBalance").value(2000.00))
                .andExpect(jsonPath("$.destination.newBalance").value(2500.00));

        assertThat(saldo(1001)).isEqualByComparingTo("4500.00");
        assertThat(saldo(1002)).isEqualByComparingTo("2500.00");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transfers", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Fondos insuficientes: 422 con el mensaje de la API, sin cambios en saldos ni transferencias")
    void fondosInsuficientes() throws Exception {
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(cuerpo(1001, 1003, "6000.00")))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.message").value("Fondos insuficientes en la cuenta 1001. Los saldos no cambiaron."));

        assertThat(saldo(1001)).isEqualByComparingTo("5000.00");
        assertThat(saldo(1003)).isEqualByComparingTo("10000.00");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transfers", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Validaciones: misma cuenta, monto cero, campo faltante, cuenta inexistente y JSON inválido")
    void validaciones() throws Exception {
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(cuerpo(1001, 1001, "10")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La cuenta origen y la cuenta destino deben ser diferentes."));
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(cuerpo(1001, 1002, "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("amount"));
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content("{\"sourceAccountId\":1001,\"amount\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("destinationAccountId"));
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(cuerpo(1001, 9999, "10")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content("{monto: 10"))
                .andExpect(status().isBadRequest());
        assertThat(saldo(1001)).isEqualByComparingTo("5000.00");
    }

    @Test
    @DisplayName("Idempotencia: el reintento con la misma Idempotency-Key devuelve 200 con la misma transferencia y no cobra dos veces")
    void idempotencia() throws Exception {
        String primera = mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "abc-123").content(cuerpo(1001, 1002, "500.00")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(primera, "$.id");

        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "abc-123").content(cuerpo(1001, 1002, "500.00")))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.source.newBalance").value(4500.00));

        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "abc-123").content(cuerpo(1001, 1002, "99.00")))
                .andExpect(status().isConflict());

        assertThat(saldo(1001)).isEqualByComparingTo("4500.00");
        assertThat(saldo(1002)).isEqualByComparingTo("2500.00");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transfers", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Historial por cuenta: enviadas y recibidas, la más reciente primero, con el saldo resultante")
    void historial() throws Exception {
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(cuerpo(1001, 1002, "500.00")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(cuerpo(1003, 1001, "1000.00")))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/accounts/1001/transfers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].direction").value("RECIBIDA"))
                .andExpect(jsonPath("$[0].counterpartAccountId").value(1003))
                .andExpect(jsonPath("$[0].balanceAfter").value(5500.00))
                .andExpect(jsonPath("$[1].direction").value("ENVIADA"))
                .andExpect(jsonPath("$[1].balanceAfter").value(4500.00));
        mvc.perform(get("/api/accounts/9999/transfers")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Concurrencia: 10 transferencias simultáneas de 300 desde una cuenta con 2,000 → solo 6 pasan, sin sobregiro")
    void concurrenciaSinSobregiro() throws Exception {
        int hilos = 10;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Boolean>> resultados = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            Callable<Boolean> tarea = () -> {
                salida.await();
                try {
                    transferService.transfer(new TransferRequest(1002L, 1003L, new BigDecimal("300.00")),
                            new AuditContext("prueba-concurrencia", "127.0.0.1", "JUnit", null));
                    return true;
                } catch (BusinessRuleException fondosInsuficientes) {
                    return false;
                }
            };
            resultados.add(pool.submit(tarea));
        }
        salida.countDown();
        int exitosas = 0;
        for (Future<Boolean> r : resultados) {
            if (r.get()) {
                exitosas++;
            }
        }
        pool.shutdown();

        assertThat(exitosas).isEqualTo(6);
        assertThat(saldo(1002)).isEqualByComparingTo("200.00");
        assertThat(saldo(1003)).isEqualByComparingTo("11800.00");
        assertThat(saldo(1001).add(saldo(1002)).add(saldo(1003))).isEqualByComparingTo("17000.00");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transfers", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE outcome = 'EXITOSA' AND transfer_id IS NOT NULL",
                Integer.class)).isEqualTo(6);
    }
}
