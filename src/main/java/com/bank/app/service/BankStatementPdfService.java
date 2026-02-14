package com.bank.app.service;

import com.bank.app.entity.Account;
import com.bank.app.entity.Transaction;
import com.bank.app.entity.User;
import com.bank.app.exception.BadRequestException;
import com.lowagie.text.Chunk;
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
import com.lowagie.text.pdf.draw.LineSeparator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
@Slf4j
public class BankStatementPdfService {

    public byte[] generateStatement(
            User user,
            Account primaryAccount,
            List<Transaction> transactions,
            LocalDate statementStart,
            LocalDate statementEnd
    ) {
        if (transactions == null || transactions.isEmpty()) {
            throw new BadRequestException("No transactions found for the selected period");
        }

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy");
        NumberFormat amountFormatter = NumberFormat.getCurrencyInstance(Locale.US);

        Color brandPrimary = new Color(26, 35, 126);
        Color headerBlue = new Color(13, 71, 161);
        Color dividerColor = new Color(200, 209, 224);
        Color panelColor = new Color(246, 249, 255);
        Color rowAltColor = new Color(250, 252, 255);

        Font bankNameFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, brandPrimary);
        Font taglineFont = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(80, 80, 80));
        Font careFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, brandPrimary);
        Font detailsFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
        Font tableHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        Font tableDataFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
        Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(95, 95, 95));

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, out);
            document.open();

            PdfPTable headerTable = new PdfPTable(new float[]{1.1f, 4.9f});
            headerTable.setWidthPercentage(100f);
            headerTable.setSpacingAfter(6f);

            PdfPCell logoCell = new PdfPCell();
            logoCell.setBorder(PdfPCell.NO_BORDER);
            logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            logoCell.setPadding(0f);
            Image logo = loadLogoFromResources();
            if (logo != null) {
                logo.scaleToFit(50f, 50f);
                logoCell.addElement(logo);
            } else {
                logoCell.addElement(new Paragraph(" "));
            }
            headerTable.addCell(logoCell);

            PdfPCell titleCell = new PdfPCell();
            titleCell.setBorder(PdfPCell.NO_BORDER);
            titleCell.setPaddingLeft(6f);
            titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            titleCell.addElement(new Paragraph("FinCore Bank", bankNameFont));
            titleCell.addElement(new Paragraph("Secure \u2022 Simple \u2022 Smart Banking", taglineFont));
            headerTable.addCell(titleCell);

            document.add(headerTable);
            document.add(new Chunk(new LineSeparator(1f, 100f, dividerColor, Element.ALIGN_CENTER, 0f)));

            Paragraph customerCare = new Paragraph("Customer Care: +1 (800) 555-0199", careFont);
            customerCare.setSpacingBefore(8f);
            customerCare.setSpacingAfter(14f);
            document.add(customerCare);

            Paragraph title = new Paragraph("Bank Statement", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(14f);
            document.add(title);

            PdfPTable detailsTable = new PdfPTable(new float[]{1f, 1f});
            detailsTable.setWidthPercentage(100f);
            detailsTable.setSpacingAfter(14f);

            PdfPCell leftDetails = new PdfPCell();
            leftDetails.setPadding(8f);
            leftDetails.setBackgroundColor(panelColor);
            leftDetails.setBorderColor(dividerColor);
            leftDetails.setBorderWidth(0.8f);
            leftDetails.addElement(new Paragraph("Customer Name: " + buildCustomerName(user), detailsFont));
            leftDetails.addElement(new Paragraph("Account Number: " + maskAccountNumber(primaryAccount.getAccountNumber()), detailsFont));
            detailsTable.addCell(leftDetails);

            PdfPCell rightDetails = new PdfPCell();
            rightDetails.setPadding(8f);
            rightDetails.setBackgroundColor(panelColor);
            rightDetails.setBorderColor(dividerColor);
            rightDetails.setBorderWidth(0.8f);
            Paragraph dateRange = new Paragraph(
                    "Date Range: " + statementStart.format(dateFormatter) + " - " + statementEnd.format(dateFormatter),
                    detailsFont
            );
            dateRange.setAlignment(Element.ALIGN_RIGHT);
            rightDetails.addElement(dateRange);
            Paragraph total = new Paragraph("Total Transactions: " + transactions.size(), detailsFont);
            total.setAlignment(Element.ALIGN_RIGHT);
            rightDetails.addElement(total);
            detailsTable.addCell(rightDetails);

            document.add(detailsTable);

            PdfPTable table = new PdfPTable(new float[]{1.15f, 1.45f, 1.05f, 1.1f, 1.1f, 1.0f, 0.95f});
            table.setWidthPercentage(100f);
            table.setHeaderRows(1);
            table.setSpacingAfter(14f);

            table.addCell(createHeaderCell("Date", tableHeaderFont, headerBlue));
            table.addCell(createHeaderCell("Reference", tableHeaderFont, headerBlue));
            table.addCell(createHeaderCell("Type", tableHeaderFont, headerBlue));
            table.addCell(createHeaderCell("From", tableHeaderFont, headerBlue));
            table.addCell(createHeaderCell("To", tableHeaderFont, headerBlue));
            table.addCell(createHeaderCell("Amount", tableHeaderFont, headerBlue));
            table.addCell(createHeaderCell("Status", tableHeaderFont, headerBlue));

            int rowIndex = 0;
            for (Transaction tx : transactions) {
                Color rowColor = (rowIndex % 2 == 0) ? Color.WHITE : rowAltColor;
                table.addCell(createDataCell(tx.getTransactionDate().format(dateFormatter), tableDataFont, Element.ALIGN_LEFT, rowColor, dividerColor));
                table.addCell(createDataCell(nullSafe(tx.getTransactionReference()), tableDataFont, Element.ALIGN_LEFT, rowColor, dividerColor));
                table.addCell(createDataCell(formatEnum(tx.getType() != null ? tx.getType().name() : null), tableDataFont, Element.ALIGN_LEFT, rowColor, dividerColor));
                table.addCell(createDataCell(maskAccountNumber(resolveAccount(tx.getSourceAccount())), tableDataFont, Element.ALIGN_LEFT, rowColor, dividerColor));
                table.addCell(createDataCell(maskAccountNumber(resolveAccount(tx.getDestinationAccount())), tableDataFont, Element.ALIGN_LEFT, rowColor, dividerColor));
                table.addCell(createDataCell(amountFormatter.format(tx.getAmount()), tableDataFont, Element.ALIGN_RIGHT, rowColor, dividerColor));
                table.addCell(createDataCell(formatEnum(tx.getStatus() != null ? tx.getStatus().name() : null), tableDataFont, Element.ALIGN_CENTER, rowColor, dividerColor));
                rowIndex++;
            }

            document.add(table);

            document.add(new Chunk(new LineSeparator(1f, 100f, dividerColor, Element.ALIGN_CENTER, 0f)));
            Paragraph footer = new Paragraph(
                    "FinCore Bank, 225 Market Street, San Francisco, CA 94105\n" +
                            "IFSC: FINC0001234\n" +
                            "This statement is computer generated and does not require a signature.",
                    footerFont
            );
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.setSpacingBefore(8f);
            document.add(footer);

            document.close();
            out.flush();
            byte[] pdfBytes = out.toByteArray();
            if (pdfBytes.length == 0) {
                throw new BadRequestException("Generated statement is empty");
            }
            return pdfBytes;
        } catch (DocumentException e) {
            log.error("Failed to generate statement PDF", e);
            throw new BadRequestException("Unable to generate statement PDF");
        } catch (Exception e) {
            log.error("Unexpected error while generating statement PDF", e);
            throw new BadRequestException("Unable to generate statement PDF");
        }
    }

    private PdfPCell createHeaderCell(String text, Font font, Color background) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPaddingTop(7f);
        cell.setPaddingBottom(7f);
        cell.setBackgroundColor(background);
        cell.setBorderColor(new Color(178, 193, 214));
        cell.setBorderWidth(0.8f);
        return cell;
    }

    private PdfPCell createDataCell(
            String value,
            Font font,
            int alignment,
            Color background,
            Color borderColor
    ) {
        PdfPCell cell = new PdfPCell(new Phrase(value, font));
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBackgroundColor(background);
        cell.setBorderColor(borderColor);
        cell.setBorderWidth(0.7f);
        cell.setPaddingTop(6f);
        cell.setPaddingBottom(6f);
        cell.setPaddingLeft(5f);
        cell.setPaddingRight(5f);
        return cell;
    }

    private String resolveAccount(Account account) {
        return account != null ? account.getAccountNumber() : "-";
    }

    private String buildCustomerName(User user) {
        String first = user.getFirstName() != null ? user.getFirstName().trim() : "";
        String last = user.getLastName() != null ? user.getLastName().trim() : "";
        String name = (first + " " + last).trim();
        return name.isBlank() ? user.getEmail() : name;
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank() || "-".equals(accountNumber)) {
            return "-";
        }
        String normalized = accountNumber.trim();
        return normalized.length() <= 4 ? "****" : "****" + normalized.substring(normalized.length() - 4);
    }

    private String formatEnum(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.replace('_', ' ').toUpperCase(Locale.ROOT);
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private Image loadLogoFromResources() {
        List<String> candidates = List.of(
                "static/logo.png",
                "static/fin-core-logo.png"
        );
        for (String path : candidates) {
            try {
                ClassPathResource resource = new ClassPathResource(path);
                if (resource.exists()) {
                    return Image.getInstance(resource.getURL());
                }
            } catch (Exception ex) {
                log.debug("Unable to load logo from classpath path: {}", path, ex);
            }
        }
        return null;
    }
}

