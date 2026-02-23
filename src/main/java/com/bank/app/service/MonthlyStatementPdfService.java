package com.bank.app.service;

import com.bank.app.entity.Account;
import com.bank.app.entity.Transaction;
import com.bank.app.repository.TransactionRepository;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class MonthlyStatementPdfService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final TransactionRepository transactionRepository;

    public void generateMonthlyStatementPdf(Account account, YearMonth month, String baseDirectory) {
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.atEndOfMonth().atTime(23, 59, 59);
        LocalDateTime now = LocalDateTime.now();

        BigDecimal netInPeriod = defaultZero(transactionRepository.calculateNetChangeForAccountBetween(account.getId(), start, end));
        BigDecimal netAfterPeriod = end.isBefore(now)
                ? defaultZero(transactionRepository.calculateNetChangeForAccountBetween(account.getId(), end.plusSeconds(1), now))
                : BigDecimal.ZERO;

        BigDecimal closingBalance = defaultZero(account.getBalance()).subtract(netAfterPeriod);
        BigDecimal openingBalance = closingBalance.subtract(netInPeriod);

        String year = String.valueOf(month.getYear());
        String monthPart = String.format("%02d", month.getMonthValue());
        Path directory = Paths.get(baseDirectory, year, monthPart);
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create statement directory " + directory, ex);
        }

        Path outputPath = directory.resolve("ACC" + account.getAccountNumber() + ".pdf");
        try (
                PdfWriter writer = new PdfWriter(outputPath.toFile());
                PdfDocument pdf = new PdfDocument(writer);
                Document document = new Document(pdf);
                Stream<Transaction> txStream = transactionRepository.streamByAccountAndTransactionDateBetween(
                        account.getId(), start, end
                )
        ) {
            document.add(new Paragraph("Monthly Statement")
                    .setBold()
                    .setFontSize(16)
                    .setTextAlignment(TextAlignment.CENTER));
            document.add(new Paragraph("Account Holder: " + account.getUser().getFirstName() + " " + account.getUser().getLastName()));
            document.add(new Paragraph("Account Number: " + account.getAccountNumber()));
            document.add(new Paragraph("Statement Month: " + month));
            document.add(new Paragraph("Opening Balance: " + fmt(openingBalance)));
            document.add(new Paragraph("Closing Balance: " + fmt(closingBalance)));
            document.add(new Paragraph(" "));

            Table table = new Table(new float[]{2.0f, 4.0f, 2.0f, 2.0f, 2.0f}).useAllAvailableWidth();
            addHeader(table, "Date");
            addHeader(table, "Description");
            addHeader(table, "Debit");
            addHeader(table, "Credit");
            addHeader(table, "Balance");

            BigDecimal[] runningBalance = {openingBalance};
            BigDecimal[] totalDebit = {BigDecimal.ZERO};
            BigDecimal[] totalCredit = {BigDecimal.ZERO};

            txStream.forEach(tx -> {
                BigDecimal debit = BigDecimal.ZERO;
                BigDecimal credit = BigDecimal.ZERO;

                if (tx.getSourceAccount() != null && account.getId().equals(tx.getSourceAccount().getId())) {
                    debit = defaultZero(tx.getAmount());
                    totalDebit[0] = totalDebit[0].add(debit);
                    runningBalance[0] = runningBalance[0].subtract(debit);
                } else if (tx.getDestinationAccount() != null && account.getId().equals(tx.getDestinationAccount().getId())) {
                    credit = defaultZero(tx.getAmount());
                    totalCredit[0] = totalCredit[0].add(credit);
                    runningBalance[0] = runningBalance[0].add(credit);
                }

                table.addCell(cell(tx.getTransactionDate() != null ? tx.getTransactionDate().format(DATE_FORMAT) : "-"));
                table.addCell(cell(tx.getDescription() != null ? tx.getDescription() : tx.getType().name()));
                table.addCell(cell(debit.compareTo(BigDecimal.ZERO) > 0 ? fmt(debit) : "-"));
                table.addCell(cell(credit.compareTo(BigDecimal.ZERO) > 0 ? fmt(credit) : "-"));
                table.addCell(cell(fmt(runningBalance[0])));
            });

            document.add(table);
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Total Debits: " + fmt(totalDebit[0])).setBold());
            document.add(new Paragraph("Total Credits: " + fmt(totalCredit[0])).setBold());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to generate statement PDF for account " + account.getAccountNumber(), ex);
        }
    }

    private void addHeader(Table table, String value) {
        Cell cell = new Cell().add(new Paragraph(value).setBold());
        cell.setBackgroundColor(ColorConstants.LIGHT_GRAY);
        table.addHeaderCell(cell);
    }

    private Cell cell(String value) {
        return new Cell().add(new Paragraph(value));
    }

    private String fmt(BigDecimal amount) {
        return "₹" + defaultZero(amount).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
