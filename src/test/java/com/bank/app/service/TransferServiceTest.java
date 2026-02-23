package com.bank.app.service;

import com.bank.app.dto.IdempotentTransferResponse;
import com.bank.app.dto.TransactionRequest;
import com.bank.app.dto.TransactionResponse;
import com.bank.app.entity.Account;
import com.bank.app.entity.Transaction;
import com.bank.app.entity.User;
import com.bank.app.exception.BadRequestException;
import com.bank.app.exception.DuplicateTransferRequestException;
import com.bank.app.kafka.TransactionEventPublisher;
import com.bank.app.repository.UserRepository;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private TransactionService transactionService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private IdempotencyService idempotencyService;
    @Mock
    private TransactionEventPublisher transactionEventPublisher;
    @Mock
    private MetricsService metricsService;
    @Mock
    private Tracer tracer;
    @Mock
    private Timer.Sample timerSample;

    private TransferService transferService;

    @BeforeEach
    void setUp() {
        transferService = new TransferService(
                transactionService,
                userRepository,
                idempotencyService,
                transactionEventPublisher,
                metricsService,
                tracer
        );
        when(metricsService.startTransferTimer()).thenReturn(timerSample);
        lenient().when(tracer.currentSpan()).thenReturn(null);
    }

    @Test
    void transfer_successfulTransfer_publishesEventAndReturnsResponse() {
        User user = buildUser();
        TransactionRequest request = buildTransferRequest(1L, 2L, "500.00");
        TransactionResponse txResponse = buildTransactionResponse(99L, "500.00");

        when(userRepository.findByEmail("user@fincore.com")).thenReturn(Optional.of(user));
        when(transactionService.processTransfer("user@fincore.com", request)).thenReturn(txResponse);
        doAnswer(invocation -> {
            Supplier<IdempotentTransferResponse> supplier = invocation.getArgument(2);
            return supplier.get();
        }).when(idempotencyService).checkAndStore(anyLong(), anyString(), any());

        IdempotentTransferResponse response = transferService.transferWithIdempotency(
                "user@fincore.com",
                "d290f1ee-6c54-4b01-90e6-d701748f0851",
                "127.0.0.1",
                request
        );

        assertThat(response.getTransactionId()).isEqualTo(99L);
        assertThat(response.getAmount()).isEqualByComparingTo("500.00");
        verify(transactionEventPublisher).publish(any());
        verify(metricsService).incrementTransactionCounter("DEBIT", "SUCCESS");
        verify(metricsService).incrementTransactionCounter("CREDIT", "SUCCESS");
        verify(metricsService).stopTransferTimer(timerSample);
    }

    @Test
    void transfer_insufficientBalance_throwsBadRequest() {
        User user = buildUser();
        TransactionRequest request = buildTransferRequest(1L, 2L, "900000.00");

        when(userRepository.findByEmail("user@fincore.com")).thenReturn(Optional.of(user));
        doAnswer(invocation -> {
            Supplier<IdempotentTransferResponse> supplier = invocation.getArgument(2);
            return supplier.get();
        }).when(idempotencyService).checkAndStore(anyLong(), anyString(), any());
        when(transactionService.processTransfer("user@fincore.com", request))
                .thenThrow(new BadRequestException("Insufficient balance"));

        assertThrows(BadRequestException.class, () ->
                transferService.transferWithIdempotency("user@fincore.com", "d290f1ee-6c54-4b01-90e6-d701748f0851", "127.0.0.1", request)
        );

        verify(metricsService).incrementTransactionCounter("DEBIT", "FAILED");
        verify(transactionEventPublisher, never()).publish(any());
    }

    @Test
    void transfer_sameAccountTransfer_throwsBadRequest() {
        User user = buildUser();
        TransactionRequest request = buildTransferRequest(1L, 1L, "50.00");

        when(userRepository.findByEmail("user@fincore.com")).thenReturn(Optional.of(user));
        doAnswer(invocation -> {
            Supplier<IdempotentTransferResponse> supplier = invocation.getArgument(2);
            return supplier.get();
        }).when(idempotencyService).checkAndStore(anyLong(), anyString(), any());
        when(transactionService.processTransfer("user@fincore.com", request))
                .thenThrow(new BadRequestException("Source and destination accounts must be different"));

        assertThrows(BadRequestException.class, () ->
                transferService.transferWithIdempotency("user@fincore.com", "d290f1ee-6c54-4b01-90e6-d701748f0851", "127.0.0.1", request)
        );

        verify(metricsService).incrementTransactionCounter("DEBIT", "FAILED");
    }

    @Test
    void transfer_lockedAccountTransfer_throwsBadRequest() {
        User user = buildUser();
        TransactionRequest request = buildTransferRequest(1L, 2L, "100.00");

        when(userRepository.findByEmail("user@fincore.com")).thenReturn(Optional.of(user));
        doAnswer(invocation -> {
            Supplier<IdempotentTransferResponse> supplier = invocation.getArgument(2);
            return supplier.get();
        }).when(idempotencyService).checkAndStore(anyLong(), anyString(), any());
        when(transactionService.processTransfer("user@fincore.com", request))
                .thenThrow(new BadRequestException("Account is inactive"));

        assertThrows(BadRequestException.class, () ->
                transferService.transferWithIdempotency("user@fincore.com", "d290f1ee-6c54-4b01-90e6-d701748f0851", "127.0.0.1", request)
        );

        verify(metricsService).incrementTransactionCounter("DEBIT", "FAILED");
    }

    @Test
    void transfer_idempotencyKeyDuplicate_throwsDuplicateRequestException() {
        User user = buildUser();
        TransactionRequest request = buildTransferRequest(1L, 2L, "500.00");

        when(userRepository.findByEmail("user@fincore.com")).thenReturn(Optional.of(user));
        doThrow(new DuplicateTransferRequestException(
                IdempotentTransferResponse.builder()
                        .transactionId(101L)
                        .amount(new BigDecimal("500.00"))
                        .timestamp(LocalDateTime.now())
                        .status("COMPLETED")
                        .build()
        )).when(idempotencyService).checkAndStore(anyLong(), anyString(), any());

        assertThrows(DuplicateTransferRequestException.class, () ->
                transferService.transferWithIdempotency("user@fincore.com", "d290f1ee-6c54-4b01-90e6-d701748f0851", "127.0.0.1", request)
        );

        verify(transactionService, never()).processTransfer(anyString(), any());
        verify(metricsService).incrementTransactionCounter("DEBIT", "FAILED");
    }

    private User buildUser() {
        return User.builder()
                .id(7L)
                .email("user@fincore.com")
                .firstName("Fin")
                .lastName("Core")
                .role(com.bank.app.entity.Role.USER)
                .enabled(true)
                .build();
    }

    private TransactionRequest buildTransferRequest(Long sourceId, Long destinationId, String amount) {
        return TransactionRequest.builder()
                .type(Transaction.TransactionType.TRANSFER)
                .sourceAccountId(sourceId)
                .destinationAccountId(destinationId)
                .amount(new BigDecimal(amount))
                .build();
    }

    private TransactionResponse buildTransactionResponse(Long id, String amount) {
        return TransactionResponse.builder()
                .id(id)
                .transactionReference("TXN-ABC123")
                .amount(new BigDecimal(amount))
                .status(Transaction.TransactionStatus.COMPLETED)
                .sourceAccountNumber("111111")
                .destinationAccountNumber("222222")
                .transactionDate(LocalDateTime.now())
                .build();
    }
}
