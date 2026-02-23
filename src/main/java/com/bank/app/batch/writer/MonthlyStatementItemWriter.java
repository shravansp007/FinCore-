package com.bank.app.batch.writer;

import com.bank.app.entity.Account;
import com.bank.app.service.MonthlyStatementPdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

@Component
@RequiredArgsConstructor
public class MonthlyStatementItemWriter implements ItemWriter<Account> {

    private final MonthlyStatementPdfService monthlyStatementPdfService;

    @Value("${app.batch.statement.output-dir:/statements}")
    private String statementOutputDirectory;

    @Override
    public void write(Chunk<? extends Account> chunk) {
        YearMonth monthToGenerate = YearMonth.now().minusMonths(1);
        for (Account account : chunk.getItems()) {
            monthlyStatementPdfService.generateMonthlyStatementPdf(account, monthToGenerate, statementOutputDirectory);
        }
    }
}
