package com.bank.app.service;

import com.bank.app.dto.AccountOperationRequest;
import com.bank.app.dto.BillPaymentRequest;
import com.bank.app.dto.TransactionRequest;
import com.bank.app.dto.TransactionResponse;
import com.bank.app.entity.Account;
import com.bank.app.entity.Notification;
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
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final BeneficiaryService beneficiaryService;
    private final NotificationService notificationService;
    private final BankStatementPdfService bankStatementPdfService;
    private final BigDecimal minTransactionAmount;
    private final BigDecimal maxTransactionAmount;

    public TransactionService(
            TransactionRepository transactionRepository,
            AccountRepository accountRepository,
            UserRepository userRepository,
            BeneficiaryService beneficiaryService,
            NotificationService notificationService,
            BankStatementPdfService bankStatementPdfService,
            @Value("${app.transaction.min-amount:1.00}") BigDecimal minTransactionAmount,
            @Value("${app.transaction.max-amount:100000.00}") BigDecimal maxTransactionAmount
    ) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.beneficiaryService = beneficiaryService;
        this.notificationService = notificationService;
        this.bankStatementPdfService = bankStatementPdfService;
        this.minTransactionAmount = minTransactionAmount;
        this.maxTransactionAmount = maxTransactionAmount;
    }

    @Transactional
    public TransactionResponse createTransaction(String userEmail, TransactionRequest request) {
        if (request == null || request.getAmount() == null || request.getType() == null) {
            throw new BadRequestException("Transaction type and amount are required");
        }

        TransactionType type = request.getType();

        User user = getUserByEmail(userEmail);
        Account account = resolvePrimaryAccount(user);

        if (type == TransactionType.DEPOSIT) {
            return depositInternal(user, account, request.getAmount(), null);
        }
        if (type.isWithdrawal()) {
            return withdrawInternal(user, account, request.getAmount(), null);
        }
        if (type == TransactionType.TRANSFER) {
            return transferInternal(user, request);
        }
        if (type.isPayment()) {
            throw new BadRequestException("Use /bill-payment endpoint for payment transactions");
        }

        throw new BadRequestException("Unsupported transaction type for this endpoint");
    }

    @Transactional
    public TransactionResponse processTransfer(String userEmail, TransactionRequest request) {
        if (request == null || request.getType() == null || request.getType() != TransactionType.TRANSFER) {
            throw new BadRequestException("Transfer endpoint only supports type TRANSFER");
        }
        User user = getUserByEmail(userEmail);
        return transferInternal(user, request);
    }

    @Transactional
    public TransactionResponse deposit(String userEmail, AccountOperationRequest request) {
        User user = getUserByEmail(userEmail);
        Account account = getUserAccount(user, request.getAccountId());
        return depositInternal(user, account, request.getAmount(), request.getDescription());
    }

    @Transactional
    public TransactionResponse withdraw(String userEmail, AccountOperationRequest request) {
        User user = getUserByEmail(userEmail);
        Account account = getUserAccount(user, request.getAccountId());
        return withdrawInternal(user, account, request.getAmount(), request.getDescription());
    }

    @Transactional
    public TransactionResponse payBill(String userEmail, BillPaymentRequest request) {
        User user = getUserByEmail(userEmail);
        Account account = getUserAccount(user, request.getAccountId());
        validateAmount(request.getAmount());

        if (account.getBalance().compareTo(request.getAmount()) < 0) {
            throw new BadRequestException("Insufficient balance");
        }

        account.setBalance(account.getBalance().subtract(request.getAmount()));
        accountRepository.save(account);

        String description = request.getDescription();
        if (description == null || description.isBlank()) {
            description = "Bill payment - " + request.getBillerName();
        }

        Transaction tx = Transaction.builder()
                .transactionReference(generateReference())
                .type(TransactionType.PAYMENT)
                .amount(request.getAmount())
                .description(description)
                .sourceAccount(account)
                .status(TransactionStatus.COMPLETED)
                .user(user)
                .build();

        tx = transactionRepository.save(tx);
        notificationService.createNotification(
                user,
                Notification.NotificationType.PAYMENT,
                "Bill payment of " + request.getAmount() + " processed"
        );

        return mapToResponse(tx);
    }

    public Page<TransactionResponse> getUserTransactions(
            String userEmail,
            LocalDate startDate,
            LocalDate endDate,
            String direction,
            Pageable pageable
    ) {
        User user = getUserByEmail(userEmail);
        if (direction == null || direction.isBlank() || "ALL".equalsIgnoreCase(direction)) {
            Page<Transaction> page = fetchUserTransactions(user.getId(), startDate, endDate, pageable);
            List<TransactionResponse> responses = page.getContent().stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
            return new PageImpl<>(responses, pageable, page.getTotalElements());
        }

        List<Transaction> all = fetchUserTransactions(user.getId(), startDate, endDate, Pageable.unpaged()).getContent();
        List<Transaction> filtered = applyDirectionFilter(all, direction, user.getId());

        int pageNumber = pageable.getPageNumber();
        int pageSize = pageable.getPageSize();
        int start = Math.min(pageNumber * pageSize, filtered.size());
        int end = Math.min(start + pageSize, filtered.size());
        List<Transaction> pageSlice = start < end ? filtered.subList(start, end) : Collections.emptyList();

        List<TransactionResponse> responses = pageSlice.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return new PageImpl<>(responses, pageable, filtered.size());
    }

    public Page<TransactionResponse> getAccountTransactions(Long accountId, String userEmail, Pageable pageable) {
        User user = getUserByEmail(userEmail);
        Account account = getUserAccount(user, accountId);

        Page<Transaction> page = transactionRepository.findByAccountId(account.getId(), pageable);

        List<TransactionResponse> responses = page.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return new PageImpl<>(responses, pageable, page.getTotalElements());
    }

    public List<TransactionResponse> getMiniStatement(Long accountId, String userEmail, int limit) {
        Pageable pageable = PageRequest.of(0, Math.max(1, limit), Sort.by(Sort.Direction.DESC, "transactionDate"));
        return getAccountTransactions(accountId, userEmail, pageable).getContent();
    }

    public TransactionResponse getTransactionByReference(String reference, String userEmail) {
        Transaction tx = transactionRepository.findByTransactionReference(reference)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        if (!isUserAuthorizedForTransaction(tx, userEmail)) {
            throw new UnauthorizedException("Access denied to this transaction");
        }

        return mapToResponse(tx);
    }

    public byte[] generateStatementPdf(String userEmail, LocalDate startDate, LocalDate endDate) {
        User user = getUserByEmail(userEmail);
        Page<Transaction> page = fetchUserTransactions(user.getId(), startDate, endDate, Pageable.unpaged());
        if (page == null || page.getContent() == null) {
            throw new BadRequestException("Unable to load transactions for statement");
        }
        List<Transaction> transactions = page.getContent().stream()
                .sorted(Comparator.comparing(Transaction::getTransactionDate).reversed())
                .collect(Collectors.toList());
        if (transactions.isEmpty()) {
            throw new BadRequestException("No transactions found for the selected period");
        }
        Account primaryAccount = resolvePrimaryAccount(user);
        NumberFormat amountFormat = NumberFormat.getCurrencyInstance(Locale.US);
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy");
        String customerName = buildCustomerName(user);
        String maskedAccountNumber = maskAccountNumber(primaryAccount.getAccountNumber());
        LocalDate statementStart = startDate != null
                ? startDate
                : transactions.stream()
                .map(tx -> tx.getTransactionDate().toLocalDate())
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now());
        LocalDate statementEnd = endDate != null
                ? endDate
                : transactions.stream()
                .map(tx -> tx.getTransactionDate().toLocalDate())
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now());
        return bankStatementPdfService.generateStatement(
                user,
                primaryAccount,
                transactions,
                statementStart,
                statementEnd
        );
    }

    private TransactionResponse depositInternal(User user, Account account, BigDecimal amount, String description) {
        validateAmount(amount);
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        Transaction tx = Transaction.builder()
                .transactionReference(generateReference())
                .type(TransactionType.DEPOSIT)
                .amount(amount)
                .description(description)
                .destinationAccount(account)
                .status(TransactionStatus.COMPLETED)
                .user(user)
                .build();

        tx = transactionRepository.save(tx);
        notificationService.createNotification(
                user,
                Notification.NotificationType.PAYMENT,
                "Deposit of " + amount + " completed"
        );

        return mapToResponse(tx);
    }

    private TransactionResponse withdrawInternal(User user, Account account, BigDecimal amount, String description) {
        validateAmount(amount);

        if (account.getBalance().compareTo(amount) < 0) {
            throw new BadRequestException("Insufficient balance");
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        Transaction tx = Transaction.builder()
                .transactionReference(generateReference())
                .type(TransactionType.WITHDRAWAL)
                .amount(amount)
                .description(description)
                .sourceAccount(account)
                .status(TransactionStatus.COMPLETED)
                .user(user)
                .build();

        tx = transactionRepository.save(tx);
        notificationService.createNotification(
                user,
                Notification.NotificationType.PAYMENT,
                "Withdrawal of " + amount + " completed"
        );

        return mapToResponse(tx);
    }

    private TransactionResponse transferInternal(User user, TransactionRequest request) {
        if (request.getSourceAccountId() == null || request.getDestinationAccountId() == null) {
            throw new BadRequestException("Source and destination accounts are required for transfer");
        }
        if (request.getSourceAccountId().equals(request.getDestinationAccountId())) {
            throw new BadRequestException("Source and destination accounts must be different");
        }

        BigDecimal amount = request.getAmount();
        validateAmount(amount);

        Account source = getUserAccount(user, request.getSourceAccountId());
        Account destination = getUserAccount(user, request.getDestinationAccountId());

        if (source.getBalance().compareTo(amount) < 0) {
            throw new BadRequestException("Insufficient balance");
        }

        source.setBalance(source.getBalance().subtract(amount));
        destination.setBalance(destination.getBalance().add(amount));
        accountRepository.save(source);
        accountRepository.save(destination);

        String reference = generateReference();

        Transaction debitTx = Transaction.builder()
                .transactionReference(reference)
                .type(TransactionType.TRANSFER)
                .amount(amount)
                .description("Transfer to " + destination.getAccountNumber())
                .sourceAccount(source)
                .destinationAccount(destination)
                .status(TransactionStatus.COMPLETED)
                .user(user)
                .build();

        Transaction creditTx = Transaction.builder()
                .transactionReference(reference)
                .type(TransactionType.TRANSFER)
                .amount(amount)
                .description("Transfer from " + source.getAccountNumber())
                .sourceAccount(source)
                .destinationAccount(destination)
                .status(TransactionStatus.COMPLETED)
                .user(user)
                .build();

        transactionRepository.save(debitTx);
        transactionRepository.save(creditTx);

        return mapToResponse(debitTx);
    }

    private Page<Transaction> fetchUserTransactions(Long userId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        if (startDate != null || endDate != null) {
            LocalDateTime start = startDate != null ? startDate.atStartOfDay() : LocalDate.of(1970, 1, 1).atStartOfDay();
            LocalDateTime end = endDate != null ? endDate.atTime(23, 59, 59) : LocalDateTime.now();
            return transactionRepository.findByUserAccountsAndTransactionDateBetween(userId, start, end, pageable);
        }
        return transactionRepository.findByUserAccounts(userId, pageable);
    }

    private List<Transaction> applyDirectionFilter(List<Transaction> txs, String direction, Long userId) {
        if (direction == null || direction.isBlank()) {
            return txs;
        }
        String dir = direction.trim().toLowerCase();
        return txs.stream()
                .filter(tx -> {
                    boolean credit = isCreditTransaction(tx, userId);
                    return ("credit".equals(dir) && credit) || ("debit".equals(dir) && !credit);
                })
                .collect(Collectors.toList());
    }

    private boolean isCreditTransaction(Transaction tx, Long userId) {
        if (tx.getType() == TransactionType.DEPOSIT) {
            return tx.getDestinationAccount() != null && tx.getDestinationAccount().getUser().getId().equals(userId);
        }
        if (tx.getType() == TransactionType.TRANSFER) {
            return tx.getDestinationAccount() != null && tx.getDestinationAccount().getUser().getId().equals(userId);
        }
        return false;
    }

    private boolean isUserAuthorizedForTransaction(Transaction tx, String userEmail) {
        if (tx.getUser() != null && userEmail.equals(tx.getUser().getEmail())) {
            return true;
        }
        if (tx.getSourceAccount() != null && userEmail.equals(tx.getSourceAccount().getUser().getEmail())) {
            return true;
        }
        return tx.getDestinationAccount() != null && userEmail.equals(tx.getDestinationAccount().getUser().getEmail());
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    private Account getUserAccount(User user, Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (!account.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("Access denied to this account");
        }
        if (!Boolean.TRUE.equals(account.getActive())) {
            throw new BadRequestException("Account is inactive");
        }
        return account;
    }

    private Account resolvePrimaryAccount(User user) {
        List<Account> accounts = accountRepository.findByUserId(user.getId());
        return accounts.stream()
                .filter(a -> Boolean.TRUE.equals(a.getActive()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("No active account available"));
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new BadRequestException("Amount is required");
        }
        if (amount.compareTo(minTransactionAmount) < 0) {
            throw new BadRequestException("Amount must be at least " + minTransactionAmount);
        }
        if (amount.compareTo(maxTransactionAmount) > 0) {
            throw new BadRequestException("Amount exceeds maximum limit");
        }
    }

    private TransactionResponse mapToResponse(Transaction tx) {
        return TransactionResponse.builder()
                .id(tx.getId())
                .transactionReference(tx.getTransactionReference())
                .type(tx.getType())
                .amount(tx.getAmount())
                .description(tx.getDescription())
                .sourceAccountNumber(tx.getSourceAccount() != null ? tx.getSourceAccount().getAccountNumber() : null)
                .destinationAccountNumber(tx.getDestinationAccount() != null ? tx.getDestinationAccount().getAccountNumber() : null)
                .status(tx.getStatus())
                .transactionDate(tx.getTransactionDate())
                .build();
    }

    private String generateReference() {
        return "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private PdfPCell createHeaderCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPaddingTop(6f);
        cell.setPaddingBottom(6f);
        cell.setBackgroundColor(new Color(22, 61, 122));
        cell.setBorderColor(new Color(188, 198, 212));
        cell.setBorderWidth(0.8f);
        return cell;
    }

    private PdfPCell createDataCell(String value, Font font, int horizontalAlignment, Color backgroundColor) {
        PdfPCell cell = new PdfPCell(new Phrase(value != null ? value : "-", font));
        cell.setHorizontalAlignment(horizontalAlignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPaddingTop(5f);
        cell.setPaddingBottom(5f);
        cell.setPaddingLeft(4f);
        cell.setPaddingRight(4f);
        cell.setBackgroundColor(backgroundColor);
        cell.setBorderColor(new Color(205, 213, 224));
        cell.setBorderWidth(0.7f);
        return cell;
    }

    private String resolveAccountNumber(Account account) {
        return account != null ? account.getAccountNumber() : "-";
    }

    private String buildCustomerName(User user) {
        String firstName = user.getFirstName() != null ? user.getFirstName().trim() : "";
        String lastName = user.getLastName() != null ? user.getLastName().trim() : "";
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isBlank() ? user.getEmail() : fullName;
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank() || "-".equals(accountNumber)) {
            return "-";
        }
        String normalized = accountNumber.trim();
        int length = normalized.length();
        if (length <= 4) {
            return "****";
        }
        return "****" + normalized.substring(length - 4);
    }

    private Image loadBankLogo() {
        List<String> candidates = List.of("static/fin-core-logo.png", "fin-core-logo.png", "assets/fin-core-logo.png");
        for (String candidate : candidates) {
            try {
                ClassPathResource resource = new ClassPathResource(candidate);
                if (resource.exists()) {
                    return Image.getInstance(resource.getURL());
                }
            } catch (Exception ex) {
                log.debug("Unable to load logo from classpath path: {}", candidate, ex);
            }
        }
        try {
            return buildFallbackLogo();
        } catch (Exception ex) {
            log.warn("Unable to generate fallback logo for PDF statement", ex);
            return null;
        }
    }

    private Image buildFallbackLogo() throws IOException, DocumentException {
        int size = 160;
        BufferedImage bufferedImage = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = bufferedImage.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        graphics.setColor(new Color(22, 61, 122));
        graphics.fillOval(8, 8, 144, 144);

        graphics.setColor(new Color(238, 189, 64));
        graphics.setStroke(new BasicStroke(5f));
        graphics.drawOval(14, 14, 132, 132);

        graphics.setColor(Color.WHITE);
        graphics.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 52));
        graphics.drawString("FC", 37, 97);
        graphics.dispose();

        try (ByteArrayOutputStream logoBytes = new ByteArrayOutputStream()) {
            ImageIO.write(bufferedImage, "png", logoBytes);
            return Image.getInstance(logoBytes.toByteArray());
        }
    }
}
