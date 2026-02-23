package com.bank.app.service;

import com.bank.app.dto.IdempotentTransferResponse;
import com.bank.app.dto.TransactionRequest;
import com.bank.app.dto.TransactionResponse;
import com.bank.app.entity.Transaction;
import com.bank.app.entity.User;
import com.bank.app.exception.BadRequestException;
import com.bank.app.kafka.TransactionEventPublisher;
import com.bank.app.kafka.model.TransactionEvent;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Tracer;
import com.bank.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final TransactionService transactionService;
    private final UserRepository userRepository;
    private final IdempotencyService idempotencyService;
    private final TransactionEventPublisher transactionEventPublisher;
    private final MetricsService metricsService;
    private final Tracer tracer;

    public IdempotentTransferResponse transferWithIdempotency(
            String userEmail,
            String idempotencyKey,
            String ipAddress,
            TransactionRequest request
    ) {
        Timer.Sample sample = metricsService.startTransferTimer();
        if (request == null || request.getType() == null || request.getType() != Transaction.TransactionType.TRANSFER) {
            metricsService.incrementTransactionCounter("DEBIT", "FAILED");
            throw new BadRequestException("Transfer endpoint only supports type TRANSFER");
        }

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        try {
            IdempotentTransferResponse result = idempotencyService.checkAndStore(user.getId(), idempotencyKey, () -> {
                TransactionResponse response = transactionService.processTransfer(userEmail, request);
                if (response.getId() != null) {
                    MDC.put("transactionId", String.valueOf(response.getId()));
                }
                publishTransactionEvent(user, response, request, ipAddress);
                return IdempotentTransferResponse.builder()
                        .status(response.getStatus() != null ? response.getStatus().name() : null)
                        .transactionId(response.getId())
                        .amount(response.getAmount())
                        .timestamp(response.getTransactionDate())
                        .build();
            });
            metricsService.incrementTransactionCounter("DEBIT", "SUCCESS");
            metricsService.incrementTransactionCounter("CREDIT", "SUCCESS");
            log.info("action=transfer.completed transactionId={} amount={}", result.getTransactionId(), result.getAmount());
            return result;
        } catch (RuntimeException ex) {
            metricsService.incrementTransactionCounter("DEBIT", "FAILED");
            throw ex;
        } finally {
            metricsService.stopTransferTimer(sample);
            MDC.remove("transactionId");
        }
    }

    private void publishTransactionEvent(
            User user,
            TransactionResponse response,
            TransactionRequest request,
            String ipAddress
    ) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sourceAccountId", request.getSourceAccountId());
        metadata.put("destinationAccountId", request.getDestinationAccountId());
        metadata.put("transactionReference", response.getTransactionReference());

        TransactionEvent event = TransactionEvent.builder()
                .eventType("TRANSFER_DEBIT")
                .transactionId(response.getId())
                .transactionReference(response.getTransactionReference())
                .accountId(request.getSourceAccountId())
                .accountNumber(response.getSourceAccountNumber())
                .amount(response.getAmount())
                .performedBy(user.getEmail())
                .accountHolderEmail(user.getEmail())
                .ipAddress(ipAddress)
                .timestamp(response.getTransactionDate() != null ? response.getTransactionDate() : LocalDateTime.now())
                .traceId(resolveTraceId())
                .metadata(metadata)
                .build();

        transactionEventPublisher.publish(event);
    }

    private String resolveTraceId() {
        if (tracer.currentSpan() != null && tracer.currentSpan().context() != null) {
            return tracer.currentSpan().context().traceId();
        }
        return MDC.get("traceId");
    }
}
