package com.bank.app.service;

import com.bank.app.dto.AccountOperationRequest;
import com.bank.app.dto.BillPaymentRequest;
import com.bank.app.dto.TransactionRequest;
import com.bank.app.dto.TransactionResponse;
import com.bank.app.entity.Account;
import com.bank.app.entity.Transaction;
import com.bank.app.entity.Transaction.TransactionStatus;
import com.bank.app.entity.Transaction.TransactionType;
import com.bank.app.entity.User;
import com.bank.app.exception.BadRequestException;
import com.bank.app.exception.ResourceNotFoundException;
import com.bank.app.exception.UnauthorizedException;
import com.bank.app.repository.AccountRepository;
import com.bank.app.repository.TransactionRepository;
import com.bank.app.repository.UserRepository;
import com.bank.app.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.io.ByteArrayOutputStream;

@Service
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final BeneficiaryService beneficiaryService;
    private final NotificationService notificationService;
    private final BigDecimal paymentMinAmount;
    private final BigDecimal paymentMaxAmount;

    public TransactionService(TransactionRepository transactionRepository,
                              AccountRepository accountRepository,
                              UserRepository userRepository,
                              BeneficiaryService beneficiaryService,
                              NotificationService notificationService,
                              @Value("${app.payments.min-amount:1.00}") BigDecimal paymentMinAmount,
                              @Value("${app.payments.max-amount:100000.00}") BigDecimal paymentMaxAmount) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.beneficiaryService = beneficiaryService;
        this.notificationService = notificationService;
        this.paymentMinAmount = paymentMinAmount;
        this.paymentMaxAmount = paymentMaxAmount;
    }

    @Transactional
    @CacheEvict(value = "transactions", allEntries = true)
    public TransactionResponse createTransaction(String userEmail, TransactionRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        log.info("action=transaction.create.start userId={} type={} sourceAccountId={} destinationAccountId={} beneficiaryId={} amount={}",
                user.getId(),
                request.getType(),
                request.getSourceAccountId(),
                request.getDestinationAccountId(),
                request.getBeneficiaryId(),
                request.getAmount());

        Account sourceAccount = accountRepository.findById(request.getSourceAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Source account not found"));

        if (!sourceAccount.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("Access denied to source account");
        }

        Account destinationAccount = null;
        String destinationAccountNumber = null;
        if (request.getDestinationAccountId() != null) {
            destinationAccount = accountRepository.findById(request.getDestinationAccountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Destination account not found"));
        } else if (request.getBeneficiaryId() != null) {
            Long destAccountId = beneficiaryService.resolveBeneficiaryToAccountId(request.getBeneficiaryId(), userEmail);
            destinationAccount = accountRepository.findById(destAccountId)
                    .orElseThrow(() -> new ResourceNotFoundException("Beneficiary account not found"));
        } else if (request.getDestinationAccountNumber() != null && !request.getDestinationAccountNumber().isBlank()) {
            destinationAccountNumber = request.getDestinationAccountNumber().trim();
            destinationAccount = accountRepository.findByAccountNumber(destinationAccountNumber).orElse(null);
        }

        validateTransaction(request, userEmail, sourceAccount, destinationAccount, destinationAccountNumber);

        if (request.getType() == TransactionType.TRANSFER) {
            if (destinationAccount == null && destinationAccountNumber != null) {
                Transaction externalTransfer = Transaction.builder()
                        .transactionReference(generateTransactionReference())
                        .type(TransactionType.TRANSFER)
                        .amount(request.getAmount())
                        .description(buildTransferDescription("to", destinationAccountNumber, request.getDescription()))
                        .sourceAccount(sourceAccount)
                        .destinationAccount(null)
                        .status(TransactionStatus.PENDING)
                        .build();

                processTransaction(externalTransfer, sourceAccount, null);
                externalTransfer.setStatus(TransactionStatus.COMPLETED);
                externalTransfer = transactionRepository.save(externalTransfer);

                log.info("action=transaction.transfer.external.success userId={} sourceAccountId={} destinationAccountNumber={} amount={} reference={}",
                        user.getId(),
                        sourceAccount.getId(),
                        maskAccountNumber(destinationAccountNumber),
                        request.getAmount(),
                        externalTransfer.getTransactionReference());

                String senderMsg = "Transfer of â‚¹" + request.getAmount().toPlainString()
                        + " to ****" + maskAccountNumber(destinationAccountNumber) + " completed.";
                notificationService.createNotification(user, com.bank.app.entity.Notification.NotificationType.TRANSFER, senderMsg);

                return mapToResponse(externalTransfer);
            }

            String baseReference = generateTransactionReference();
            Transaction debitEntry = Transaction.builder()
                    .transactionReference(baseReference + "D")
                    .type(TransactionType.TRANSFER)
                    .amount(request.getAmount())
                    .description(buildTransferDescription("to", destinationAccount, request.getDescription()))
                    .sourceAccount(sourceAccount)
                    .destinationAccount(null)
                    .status(TransactionStatus.PENDING)
                    .build();

            Transaction creditEntry = Transaction.builder()
                    .transactionReference(baseReference + "C")
                    .type(TransactionType.TRANSFER)
                    .amount(request.getAmount())
                    .description(buildTransferDescription("from", sourceAccount, request.getDescription()))
                    .sourceAccount(null)
                    .destinationAccount(destinationAccount)
                    .status(TransactionStatus.PENDING)
                    .build();

            processTransfer(sourceAccount, destinationAccount, request.getAmount());
            debitEntry.setStatus(TransactionStatus.COMPLETED);
            creditEntry.setStatus(TransactionStatus.COMPLETED);
            transactionRepository.save(debitEntry);
            transactionRepository.save(creditEntry);

            log.info("action=transaction.transfer.success userId={} sourceAccountId={} destinationAccountId={} amount={} referenceBase={}",
                    user.getId(),
                    sourceAccount.getId(),
                    destinationAccount.getId(),
                    request.getAmount(),
                    baseReference);

            String senderMsg = "Transfer of ₹" + request.getAmount().toPlainString()
                    + " to ****" + maskAccountNumber(destinationAccount.getAccountNumber()) + " completed.";
            notificationService.createNotification(user, com.bank.app.entity.Notification.NotificationType.TRANSFER, senderMsg);

            if (!destinationAccount.getUser().getId().equals(user.getId())) {
                String receiverMsg = "Transfer of ₹" + request.getAmount().toPlainString()
                        + " received from ****" + maskAccountNumber(sourceAccount.getAccountNumber()) + ".";
                notificationService.createNotification(destinationAccount.getUser(), com.bank.app.entity.Notification.NotificationType.TRANSFER, receiverMsg);
            }

            return mapToResponse(debitEntry);
        }

        Transaction transaction = Transaction.builder()
                .transactionReference(generateTransactionReference())
                .type(request.getType())
                .amount(request.getAmount())
                .description(request.getDescription())
                .sourceAccount(sourceAccount)
                .destinationAccount(destinationAccount)
                .status(TransactionStatus.PENDING)
                .build();

        processTransaction(transaction, sourceAccount, destinationAccount);
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction = transactionRepository.save(transaction);

        log.info("action=transaction.create.success userId={} type={} sourceAccountId={} destinationAccountId={} amount={} reference={}",
                user.getId(),
                transaction.getType(),
                sourceAccount.getId(),
                destinationAccount != null ? destinationAccount.getId() : null,
                transaction.getAmount(),
                transaction.getTransactionReference());
        return mapToResponse(transaction);
    }

    @Transactional
    @CacheEvict(value = "transactions", allEntries = true)
    public TransactionResponse deposit(String userEmail, AccountOperationRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        log.info("action=transaction.deposit.start userId={} accountId={} amount={}",
                user.getId(), request.getAccountId(), request.getAmount());

        Account account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        if (!account.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("Access denied to this account");
        }

        Transaction transaction = Transaction.builder()
                .transactionReference(generateTransactionReference())
                .type(TransactionType.DEPOSIT)
                .amount(request.getAmount())
                .description(request.getDescription())
                .sourceAccount(account)
                .status(TransactionStatus.PENDING)
                .build();

        processTransaction(transaction, account, null);
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction = transactionRepository.save(transaction);

        log.info("action=transaction.deposit.success userId={} accountId={} amount={} reference={}",
                user.getId(), account.getId(), transaction.getAmount(), transaction.getTransactionReference());
        return mapToResponse(transaction);
    }

    @Transactional
    @CacheEvict(value = "transactions", allEntries = true)
    public TransactionResponse withdraw(String userEmail, AccountOperationRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        log.info("action=transaction.withdraw.start userId={} accountId={} amount={}",
                user.getId(), request.getAccountId(), request.getAmount());

        Account account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        if (!account.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("Access denied to this account");
        }

        if (account.getBalance().compareTo(request.getAmount()) < 0) {
            throw new BadRequestException("Insufficient balance");
        }

        Transaction transaction = Transaction.builder()
                .transactionReference(generateTransactionReference())
                .type(TransactionType.WITHDRAWAL)
                .amount(request.getAmount())
                .description(request.getDescription())
                .sourceAccount(account)
                .status(TransactionStatus.PENDING)
                .build();

        processTransaction(transaction, account, null);
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction = transactionRepository.save(transaction);

        log.info("action=transaction.withdraw.success userId={} accountId={} amount={} reference={}",
                user.getId(), account.getId(), transaction.getAmount(), transaction.getTransactionReference());
        return mapToResponse(transaction);
    }

    @Transactional
    @CacheEvict(value = "transactions", allEntries = true)
    public TransactionResponse payBill(String userEmail, BillPaymentRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        log.info("action=transaction.payment.start userId={} accountId={} amount={}",
                user.getId(), request.getAccountId(), request.getAmount());

        Account account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        if (!account.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("Access denied to this account");
        }

        validatePaymentLimits(request.getAmount());

        if (account.getBalance().compareTo(request.getAmount()) < 0) {
            throw new BadRequestException("Insufficient balance");
        }

        Transaction transaction = Transaction.builder()
                .transactionReference(generateTransactionReference())
                .type(TransactionType.PAYMENT)
                .amount(request.getAmount())
                .description(buildBillPaymentDescription(request))
                .sourceAccount(account)
                .status(TransactionStatus.PENDING)
                .build();

        processTransaction(transaction, account, null);
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction = transactionRepository.save(transaction);

        log.info("action=transaction.payment.success userId={} accountId={} amount={} reference={}",
                user.getId(), account.getId(), transaction.getAmount(), transaction.getTransactionReference());
        return mapToResponse(transaction);
    }

    @Cacheable(value = "transactions", key = "#userEmail + '_' + #pageable.pageNumber + '_' + #pageable.pageSize + '_' + #pageable.sort + '_' + #startDate + '_' + #endDate + '_' + #direction")
    public Page<TransactionResponse> getUserTransactions(String userEmail, LocalDate startDate, LocalDate endDate, String direction, Pageable pageable) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String normalizedDirection = normalizeDirection(direction);
        LocalDateTime startDateTime = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime endDateTime = endDate != null ? endDate.atTime(23, 59, 59, 999_999_999) : null;

        return transactionRepository.findByUserIdWithFilters(user.getId(), startDateTime, endDateTime, normalizedDirection, pageable)
                .map(this::mapToResponse);
    }

    @Cacheable(value = "transactions", key = "'account_' + #accountId + '_' + #pageable.pageNumber + '_' + #pageable.pageSize + '_' + #pageable.sort")
    public Page<TransactionResponse> getAccountTransactions(Long accountId, String userEmail, Pageable pageable) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        if (!account.getUser().getEmail().equals(userEmail)) {
            throw new UnauthorizedException("Access denied to this account");
        }

        return transactionRepository.findByAccountId(accountId, pageable)
                .map(this::mapToResponse);
    }

    public List<TransactionResponse> getMiniStatement(Long accountId, String userEmail, int limit) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (!account.getUser().getEmail().equals(userEmail)) {
            throw new UnauthorizedException("Access denied to this account");
        }
        if (limit <= 0) {
            throw new BadRequestException("Limit must be between 1 and 20");
        }
        Pageable pageable = PageRequest.of(0, Math.min(limit, 20));
        return transactionRepository.findByAccountId(accountId, pageable).getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public TransactionResponse getTransactionByReference(String reference, String userEmail) {
        Transaction transaction = transactionRepository.findByTransactionReference(reference)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        boolean isAuthorized = 
                (transaction.getSourceAccount() != null && transaction.getSourceAccount().getUser().getId().equals(user.getId())) ||
                (transaction.getDestinationAccount() != null && transaction.getDestinationAccount().getUser().getId().equals(user.getId()));

        if (!isAuthorized) {
            throw new UnauthorizedException("Access denied to this transaction");
        }

        return mapToResponse(transaction);
    }

    public byte[] generateStatementPdf(String userEmail, LocalDate startDate, LocalDate endDate) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        LocalDateTime startDateTime = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime endDateTime = endDate != null ? endDate.atTime(23, 59, 59, 999_999_999) : null;

        List<Transaction> transactions = transactionRepository.findByUserIdAndDateRange(
                user.getId(),
                startDateTime,
                endDateTime
        );

        return buildStatementPdf(user, transactions, startDate, endDate);
    }

    private String normalizeDirection(String direction) {
        if (direction == null || direction.isBlank() || "ALL".equalsIgnoreCase(direction)) {
            return null;
        }
        String normalized = direction.trim().toUpperCase();
        if (!normalized.equals("CREDIT") && !normalized.equals("DEBIT")) {
            throw new BadRequestException("Invalid direction filter");
        }
        return normalized;
    }

    private byte[] buildStatementPdf(User user, List<Transaction> transactions, LocalDate startDate, LocalDate endDate) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font textFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

            Paragraph title = new Paragraph("Bank Statement", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            document.add(new Paragraph(" "));

            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy");
            String rangeText = "All Dates";
            if (startDate != null || endDate != null) {
                String start = startDate != null ? startDate.format(dateFormatter) : "Beginning";
                String end = endDate != null ? endDate.format(dateFormatter) : "Today";
                rangeText = start + " to " + end;
            }

            document.add(new Paragraph("Customer: " + user.getFirstName() + " " + user.getLastName(), textFont));
            document.add(new Paragraph("Date Range: " + rangeText, textFont));
            document.add(new Paragraph("Total Transactions: " + transactions.size(), textFont));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{2.2f, 1.5f, 1.4f, 2.0f, 2.0f, 1.5f, 2.0f});

            addHeaderCell(table, "Date", labelFont);
            addHeaderCell(table, "Type", labelFont);
            addHeaderCell(table, "Amount", labelFont);
            addHeaderCell(table, "From", labelFont);
            addHeaderCell(table, "To", labelFont);
            addHeaderCell(table, "Status", labelFont);
            addHeaderCell(table, "Reference", labelFont);

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
            for (Transaction tx : transactions) {
                table.addCell(new PdfPCell(new Phrase(tx.getTransactionDate().format(dtf), textFont)));
                table.addCell(new PdfPCell(new Phrase(tx.getType().name(), textFont)));
                table.addCell(new PdfPCell(new Phrase(tx.getAmount().toPlainString(), textFont)));
                table.addCell(new PdfPCell(new Phrase(maskAccount(tx.getSourceAccount()), textFont)));
                table.addCell(new PdfPCell(new Phrase(maskAccount(tx.getDestinationAccount()), textFont)));
                table.addCell(new PdfPCell(new Phrase(tx.getStatus().name(), textFont)));
                table.addCell(new PdfPCell(new Phrase(tx.getTransactionReference(), textFont)));
            }

            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new BadRequestException("Failed to generate statement");
        } catch (Exception e) {
            throw new BadRequestException("Failed to generate statement");
        }
    }

    private void addHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        cell.setPadding(6);
        table.addCell(cell);
    }

    private String maskAccount(Account account) {
        if (account == null || account.getAccountNumber() == null || account.getAccountNumber().length() < 4) {
            return "-";
        }
        String acc = account.getAccountNumber();
        return "****" + acc.substring(acc.length() - 4);
    }

    private void validatePaymentLimits(BigDecimal amount) {
        if (amount.compareTo(paymentMinAmount) < 0) {
            throw new BadRequestException("Payment amount is below the minimum limit");
        }
        if (amount.compareTo(paymentMaxAmount) > 0) {
            throw new BadRequestException("Payment amount exceeds the maximum limit");
        }
    }

    private String buildBillPaymentDescription(BillPaymentRequest request) {
        String base = "Bill Payment to " + request.getBillerName().trim();
        if (request.getBillReference() != null && !request.getBillReference().isBlank()) {
            base += " (" + request.getBillReference().trim() + ")";
        }
        if (request.getDescription() != null && !request.getDescription().isBlank()) {
            base += " - " + request.getDescription().trim();
        }
        return base;
    }

    private void validateTransaction(TransactionRequest request, String userEmail, Account sourceAccount, Account destinationAccount, String destinationAccountNumber) {
        if (request.getType() == TransactionType.WITHDRAWAL
                || request.getType() == TransactionType.TRANSFER
                || request.getType() == TransactionType.PAYMENT) {
            if (sourceAccount.getBalance().compareTo(request.getAmount()) < 0) {
                throw new BadRequestException("Insufficient balance");
            }
        }

        if (request.getType() == TransactionType.TRANSFER) {
            int targetCount = 0;
            if (request.getDestinationAccountId() != null) targetCount++;
            if (request.getBeneficiaryId() != null) targetCount++;
            if (destinationAccountNumber != null) targetCount++;
            if (targetCount == 0) {
                throw new BadRequestException("Destination account, beneficiary, or account number is required for transfer");
            }
            if (targetCount > 1) {
                throw new BadRequestException("Provide only one transfer target");
            }

            if (destinationAccountNumber != null && destinationAccountNumber.equals(sourceAccount.getAccountNumber())) {
                throw new BadRequestException("Source and destination accounts must be different");
            }

            if (destinationAccount != null) {
                if (!destinationAccount.getUser().getId().equals(sourceAccount.getUser().getId())
                        && !beneficiaryService.isBeneficiaryAccount(userEmail, destinationAccount.getId())) {
                    throw new UnauthorizedException("Destination account is not an approved beneficiary");
                }
            }
        }

        if (request.getType() == TransactionType.TRANSFER && destinationAccount != null
                && destinationAccount.getId().equals(sourceAccount.getId())) {
            throw new BadRequestException("Source and destination accounts must be different");
        }
    }

    private String buildTransferDescription(String direction, String accountNumber, String userDescription) {
        String base = "Transfer " + direction + " ****" + maskAccountNumber(accountNumber);
        if (userDescription == null || userDescription.isBlank()) {
            return base;
        }
        return base + " - " + userDescription.trim();
    }

    private void processTransaction(Transaction transaction, Account sourceAccount, Account destinationAccount) {
        BigDecimal amount = transaction.getAmount();

        switch (transaction.getType()) {
            case DEPOSIT:
                sourceAccount.setBalance(sourceAccount.getBalance().add(amount));
                break;
            case WITHDRAWAL:
            case PAYMENT:
                sourceAccount.setBalance(sourceAccount.getBalance().subtract(amount));
                break;
            case TRANSFER:
                sourceAccount.setBalance(sourceAccount.getBalance().subtract(amount));
                if (destinationAccount != null) {
                    destinationAccount.setBalance(destinationAccount.getBalance().add(amount));
                    accountRepository.save(destinationAccount);
                }
                break;
        }
        accountRepository.save(sourceAccount);
    }

    private void processTransfer(Account sourceAccount, Account destinationAccount, BigDecimal amount) {
        sourceAccount.setBalance(sourceAccount.getBalance().subtract(amount));
        destinationAccount.setBalance(destinationAccount.getBalance().add(amount));
        accountRepository.save(sourceAccount);
        accountRepository.save(destinationAccount);
    }

    private String generateTransactionReference() {
        return "TXN" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String buildTransferDescription(String direction, Account counterparty, String userDescription) {
        String base = "Transfer " + direction + " ****" + maskAccountNumber(counterparty.getAccountNumber());
        if (userDescription == null || userDescription.isBlank()) {
            return base;
        }
        return base + " - " + userDescription.trim();
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "XXXX";
        }
        return accountNumber.substring(accountNumber.length() - 4);
    }

    private TransactionResponse mapToResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .transactionReference(transaction.getTransactionReference())
                .type(transaction.getType())
                .amount(transaction.getAmount())
                .description(transaction.getDescription())
                .sourceAccountNumber(transaction.getSourceAccount() != null ? 
                        transaction.getSourceAccount().getAccountNumber() : null)
                .destinationAccountNumber(transaction.getDestinationAccount() != null ? 
                        transaction.getDestinationAccount().getAccountNumber() : null)
                .status(transaction.getStatus())
                .transactionDate(transaction.getTransactionDate())
                .build();
    }
}
