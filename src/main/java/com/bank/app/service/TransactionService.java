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
import com.lowagie.text.pdf.PdfContentByte;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.io.ByteArrayOutputStream;
import java.awt.Color;

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
        LocalDateTime startDateTime = startDate != null
                ? startDate.atStartOfDay()
                : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime endDateTime = endDate != null
                ? endDate.atTime(23, 59, 59, 999_999_999)
                : LocalDateTime.of(2999, 12, 31, 23, 59, 59);

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

        LocalDateTime startDateTime = startDate != null
                ? startDate.atStartOfDay()
                : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime endDateTime = endDate != null
                ? endDate.atTime(23, 59, 59, 999_999_999)
                : LocalDateTime.of(2999, 12, 31, 23, 59, 59);

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
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();

            Color brandBlue = new Color(26, 35, 126);
            Color brandOrange = new Color(255, 111, 0);
            Color headerGray = new Color(245, 247, 250);
            Color textGray = new Color(80, 87, 96);

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, brandBlue);
            Font bankFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, brandBlue);
            Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 9, textGray);
            Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, brandBlue);
            Font textFont = FontFactory.getFont(FontFactory.HELVETICA, 9, textGray);
            Font tableHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.BLACK);

            addBrandHeader(document, writer, bankFont, subtitleFont, brandBlue, brandOrange);

            Paragraph title = new Paragraph("Bank Statement", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(10);
            document.add(title);

            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy");
            String rangeText = "All Dates";
            if (startDate != null || endDate != null) {
                String start = startDate != null ? startDate.format(dateFormatter) : "Beginning";
                String end = endDate != null ? endDate.format(dateFormatter) : "Today";
                rangeText = start + " to " + end;
            }

            String statementAccount = resolveStatementAccountNumber(transactions);

            PdfPTable summary = new PdfPTable(2);
            summary.setWidthPercentage(100);
            summary.setWidths(new float[]{1.5f, 1.0f});
            summary.setSpacingAfter(10);
            summary.addCell(makeCell("Customer: " + user.getFirstName() + " " + user.getLastName(), textFont, PdfPCell.NO_BORDER, Element.ALIGN_LEFT));
            summary.addCell(makeCell("Date Range: " + rangeText, textFont, PdfPCell.NO_BORDER, Element.ALIGN_RIGHT));
            summary.addCell(makeCell("Account Number: " + statementAccount, textFont, PdfPCell.NO_BORDER, Element.ALIGN_LEFT));
            summary.addCell(makeCell("Total Transactions: " + transactions.size(), textFont, PdfPCell.NO_BORDER, Element.ALIGN_RIGHT));
            summary.addCell(makeCell("Statement Generated: " + LocalDate.now().format(dateFormatter), textFont, PdfPCell.NO_BORDER, Element.ALIGN_RIGHT));
            document.add(summary);

            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{2.2f, 1.5f, 1.4f, 2.0f, 2.0f, 1.5f, 2.0f});

            addHeaderCell(table, "Date", tableHeaderFont, headerGray);
            addHeaderCell(table, "Type", tableHeaderFont, headerGray);
            addHeaderCell(table, "Amount", tableHeaderFont, headerGray);
            addHeaderCell(table, "From", tableHeaderFont, headerGray);
            addHeaderCell(table, "To", tableHeaderFont, headerGray);
            addHeaderCell(table, "Status", tableHeaderFont, headerGray);
            addHeaderCell(table, "Reference", tableHeaderFont, headerGray);

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
            for (Transaction tx : transactions) {
                table.addCell(makeCell(tx.getTransactionDate().format(dtf), textFont, PdfPCell.BOX, Element.ALIGN_LEFT));
                table.addCell(makeCell(tx.getType().name(), textFont, PdfPCell.BOX, Element.ALIGN_LEFT));
                table.addCell(makeCell(tx.getAmount().toPlainString(), textFont, PdfPCell.BOX, Element.ALIGN_RIGHT));
                table.addCell(makeCell(maskAccount(tx.getSourceAccount()), textFont, PdfPCell.BOX, Element.ALIGN_LEFT));
                table.addCell(makeCell(maskAccount(tx.getDestinationAccount()), textFont, PdfPCell.BOX, Element.ALIGN_LEFT));
                table.addCell(makeCell(tx.getStatus().name(), textFont, PdfPCell.BOX, Element.ALIGN_LEFT));
                table.addCell(makeCell(tx.getTransactionReference(), textFont, PdfPCell.BOX, Element.ALIGN_LEFT));
            }

            document.add(table);
            document.add(new Paragraph(" "));
            addFooter(document, textFont, brandBlue, brandOrange);
            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new BadRequestException("Failed to generate statement");
        } catch (Exception e) {
            throw new BadRequestException("Failed to generate statement");
        }
    }

    private void addHeaderCell(PdfPTable table, String text, Font font, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        cell.setPadding(6);
        cell.setBackgroundColor(bg);
        table.addCell(cell);
    }

    private PdfPCell makeCell(String text, Font font, int border, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(border);
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(6);
        return cell;
    }

    private void addBrandHeader(Document document, PdfWriter writer, Font bankFont, Font subtitleFont, Color brandBlue, Color brandOrange) throws DocumentException {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{1.0f, 4.0f});
        header.setSpacingAfter(4);

        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(PdfPCell.NO_BORDER);
        logoCell.setFixedHeight(52);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        header.addCell(logoCell);

        PdfPCell textCell = new PdfPCell();
        textCell.setBorder(PdfPCell.NO_BORDER);
        textCell.addElement(new Phrase("FinCore Bank", bankFont));
        textCell.addElement(new Phrase("Secure • Simple • Smart Banking", subtitleFont));
        header.addCell(textCell);

        document.add(header);

        PdfContentByte cb = writer.getDirectContent();
        float contentWidth = document.getPageSize().getWidth() - document.leftMargin() - document.rightMargin();
        float logoColWidth = contentWidth * (1.0f / (1.0f + 4.0f));
        float logoSize = 34;
        float logoX = document.leftMargin() + (logoColWidth - logoSize) / 2.0f;
        float logoY = document.getPageSize().getHeight() - document.topMargin() - 38;
        drawLogo(cb, logoX, logoY, brandBlue, brandOrange);

        PdfContentByte lineCb = writer.getDirectContent();
        float lineY = logoY - 18;
        float lineStartX = document.leftMargin() + logoColWidth;
        float lineEndX = document.getPageSize().getWidth() - document.rightMargin();
        lineCb.setColorStroke(brandBlue);
        lineCb.setLineWidth(1.5f);
        lineCb.moveTo(lineStartX, lineY);
        lineCb.lineTo(lineEndX, lineY);
        lineCb.stroke();

        Paragraph care = new Paragraph("Customer Care: +91 1800-123-456 • support@fincorebank.com", subtitleFont);
        care.setAlignment(Element.ALIGN_LEFT);
        care.setIndentationLeft(logoColWidth);
        care.setSpacingBefore(2);
        care.setSpacingAfter(8);
        document.add(care);
    }

    private void drawLogo(PdfContentByte cb, float x, float y, Color brandBlue, Color brandOrange) {
        float size = 34;
        cb.setColorFill(brandBlue);
        cb.roundRectangle(x, y - size, size, size, 8);
        cb.fill();

        cb.setColorFill(brandOrange);
        float roofY = y - 12;
        cb.moveTo(x + 6, roofY);
        cb.lineTo(x + size / 2, y - 3);
        cb.lineTo(x + size - 6, roofY);
        cb.closePathFillStroke();

        cb.rectangle(x + 7, roofY - 4, size - 14, 3);
        cb.fill();

        float colWidth = 3;
        float gap = 3;
        float baseY = y - size + 9;
        float baseX = x + 9;
        for (int i = 0; i < 3; i++) {
            cb.rectangle(baseX + i * (colWidth + gap), baseY, colWidth, 14);
            cb.fill();
        }
    }

    private void addFooter(Document document, Font textFont, Color brandBlue, Color brandOrange) throws DocumentException {
        Paragraph footer = new Paragraph(
                "FinCore Bank • 42, MG Road, Bengaluru, KA 560001 • IFSC: FINC0000123\n" +
                "This statement is computer generated and does not require a signature.",
                textFont
        );
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(6);
        document.add(footer);
    }

    private String resolveStatementAccountNumber(List<Transaction> transactions) {
        for (Transaction tx : transactions) {
            if (tx.getSourceAccount() != null && tx.getSourceAccount().getAccountNumber() != null) {
                return maskAccount(tx.getSourceAccount());
            }
            if (tx.getDestinationAccount() != null && tx.getDestinationAccount().getAccountNumber() != null) {
                return maskAccount(tx.getDestinationAccount());
            }
        }
        return "-";
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
