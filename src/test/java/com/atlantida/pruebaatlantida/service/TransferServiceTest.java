package com.atlantida.pruebaatlantida.service;

import com.atlantida.pruebaatlantida.dto.TransferRequest;
import com.atlantida.pruebaatlantida.exception.BusinessRuleException;
import com.atlantida.pruebaatlantida.exception.ConflictException;
import com.atlantida.pruebaatlantida.exception.InvalidRequestException;
import com.atlantida.pruebaatlantida.exception.ResourceNotFoundException;
import com.atlantida.pruebaatlantida.model.Account;
import com.atlantida.pruebaatlantida.model.Transfer;
import com.atlantida.pruebaatlantida.repository.AccountRepository;
import com.atlantida.pruebaatlantida.repository.TransferRepository;
import com.atlantida.pruebaatlantida.service.TransferService.TransferResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {
    private static final ZoneId ZONA = ZoneId.of("America/Tegucigalpa");
    private static final Instant AHORA = Instant.parse("2026-11-17T15:00:00Z");

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransferRepository transferRepository;
    @Mock
    private AuditService auditService;

    private TransferService service;
    private Account juan;
    private Account maria;

    @BeforeEach
    void setUp() {
        service = new TransferService(accountRepository, transferRepository, auditService, Clock.fixed(AHORA, ZONA));
        juan = new Account(1001L, "Juan Pérez", new BigDecimal("5000.00"));
        maria = new Account(1002L, "María López", new BigDecimal("2000.00"));
    }

    private static AuditContext sinClave() {
        return new AuditContext("cajero01", "127.0.0.1", "JUnit", null);
    }

    private static AuditContext conClave(String clave) {
        return new AuditContext("cajero01", "127.0.0.1", "JUnit", clave);
    }

    private void cuentasExistentes() {
        when(accountRepository.findByIdForUpdate(1001L)).thenReturn(Optional.of(juan));
        when(accountRepository.findByIdForUpdate(1002L)).thenReturn(Optional.of(maria));
    }

    @Test
    @DisplayName("Transferencia válida: debita origen, acredita destino y devuelve saldos anterior y nuevo")
    void transferenciaExitosa() {
        cuentasExistentes();
        when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));

        TransferResult result = service.transfer(new TransferRequest(1001L, 1002L, new BigDecimal("500.00")), sinClave());

        assertThat(result.replayed()).isFalse();
        assertThat(juan.getBalance()).isEqualByComparingTo("4500.00");
        assertThat(maria.getBalance()).isEqualByComparingTo("2500.00");
        assertThat(result.transfer().source().previousBalance()).isEqualByComparingTo("5000.00");
        assertThat(result.transfer().source().newBalance()).isEqualByComparingTo("4500.00");
        assertThat(result.transfer().destination().previousBalance()).isEqualByComparingTo("2000.00");
        assertThat(result.transfer().destination().newBalance()).isEqualByComparingTo("2500.00");
        assertThat(result.transfer().createdAt()).isEqualTo(LocalDateTime.ofInstant(AHORA, ZONA));
        verify(auditService).recordSuccess(any(AuditContext.class), any(Transfer.class), eq(false));
    }

    @Test
    @DisplayName("Fondos insuficientes: 422 con el mensaje de la pantalla 4 y los saldos no cambian")
    void fondosInsuficientes() {
        cuentasExistentes();

        assertThatThrownBy(() -> service.transfer(new TransferRequest(1001L, 1002L, new BigDecimal("6000.00")), sinClave()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Fondos insuficientes en la cuenta 1001. Los saldos no cambiaron.");

        assertThat(juan.getBalance()).isEqualByComparingTo("5000.00");
        assertThat(maria.getBalance()).isEqualByComparingTo("2000.00");
        verify(transferRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("Misma cuenta origen y destino: 400 sin tocar la base")
    void mismaCuenta() {
        assertThatThrownBy(() -> service.transfer(new TransferRequest(1001L, 1001L, BigDecimal.TEN), sinClave()))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(accountRepository, transferRepository, auditService);
    }

    @Test
    @DisplayName("Cuenta destino inexistente: 404")
    void cuentaInexistente() {
        when(accountRepository.findByIdForUpdate(1001L)).thenReturn(Optional.of(juan));
        when(accountRepository.findByIdForUpdate(9999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transfer(new TransferRequest(1001L, 9999L, BigDecimal.TEN), sinClave()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No existe la cuenta 9999");
        assertThat(juan.getBalance()).isEqualByComparingTo("5000.00");
    }

    @Test
    @DisplayName("Las cuentas se bloquean siempre en orden de id (evita interbloqueos), aunque el origen sea la mayor")
    void bloqueoEnOrden() {
        cuentasExistentes();
        when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));

        service.transfer(new TransferRequest(1002L, 1001L, new BigDecimal("100.00")), sinClave());

        InOrder orden = inOrder(accountRepository);
        orden.verify(accountRepository).findByIdForUpdate(1001L);
        orden.verify(accountRepository).findByIdForUpdate(1002L);
    }

    @Test
    @DisplayName("Idempotencia: la misma clave devuelve la transferencia original sin mover dinero otra vez")
    void reintentoIdempotente() {
        cuentasExistentes();
        juan.debit(new BigDecimal("500.00"));
        maria.credit(new BigDecimal("500.00"));
        Transfer original = new Transfer(juan, maria, new BigDecimal("500.00"), LocalDateTime.now(), "clave-1");
        when(transferRepository.findByIdempotencyKey("clave-1")).thenReturn(Optional.of(original));

        TransferResult result = service.transfer(new TransferRequest(1001L, 1002L, new BigDecimal("500.00")), conClave("clave-1"));

        assertThat(result.replayed()).isTrue();
        assertThat(juan.getBalance()).isEqualByComparingTo("4500.00");
        assertThat(result.transfer().source().previousBalance()).isEqualByComparingTo("5000.00");
        verify(transferRepository, never()).save(any());
        verify(auditService).recordSuccess(any(AuditContext.class), eq(original), eq(true));
    }

    @Test
    @DisplayName("Idempotencia: la misma clave con otros datos es un conflicto (409)")
    void claveReutilizadaConOtrosDatos() {
        cuentasExistentes();
        Transfer original = new Transfer(juan, maria, new BigDecimal("500.00"), LocalDateTime.now(), "clave-1");
        when(transferRepository.findByIdempotencyKey("clave-1")).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> service.transfer(new TransferRequest(1001L, 1002L, new BigDecimal("10.00")), conClave("clave-1")))
                .isInstanceOf(ConflictException.class);
        verify(transferRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }
}
