package com.bank.app.batch.config;

import com.bank.app.batch.model.FailedTransactionCleanupItem;
import com.bank.app.batch.processor.DormantAccountItemProcessor;
import com.bank.app.batch.processor.FailedTransactionCleanupItemProcessor;
import com.bank.app.batch.processor.MonthlyStatementItemProcessor;
import com.bank.app.batch.writer.DormantAccountItemWriter;
import com.bank.app.batch.writer.FailedTransactionCleanupItemWriter;
import com.bank.app.batch.writer.MonthlyStatementItemWriter;
import com.bank.app.entity.Account;
import com.bank.app.entity.Transaction;
import com.bank.app.repository.AccountRepository;
import com.bank.app.repository.TransactionRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.batch.item.support.builder.CompositeItemProcessorBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Map;

@Configuration
public class BatchConfig {

    @Bean
    public RepositoryItemReader<Account> monthlyStatementReader(AccountRepository accountRepository) {
        return new RepositoryItemReaderBuilder<Account>()
                .name("monthlyStatementReader")
                .repository(accountRepository)
                .methodName("findByActiveTrue")
                .pageSize(100)
                .sorts(Map.of("id", org.springframework.data.domain.Sort.Direction.ASC))
                .build();
    }

    @Bean
    public RepositoryItemReader<Account> dormantAccountReader(AccountRepository accountRepository) {
        return new RepositoryItemReaderBuilder<Account>()
                .name("dormantAccountReader")
                .repository(accountRepository)
                .methodName("findByActiveTrueAndAccountStatus")
                .arguments(java.util.List.of(Account.AccountStatus.ACTIVE))
                .pageSize(100)
                .sorts(Map.of("id", org.springframework.data.domain.Sort.Direction.ASC))
                .build();
    }

    @Bean
    public RepositoryItemReader<Transaction> failedTransactionReader(TransactionRepository transactionRepository) {
        return new RepositoryItemReaderBuilder<Transaction>()
                .name("failedTransactionReader")
                .repository(transactionRepository)
                .methodName("findByStatus")
                .arguments(java.util.List.of(Transaction.TransactionStatus.PENDING))
                .pageSize(100)
                .sorts(Map.of("id", org.springframework.data.domain.Sort.Direction.ASC))
                .build();
    }

    @Bean
    public Step monthlyStatementStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("monthlyStatementReader") RepositoryItemReader<Account> reader,
            MonthlyStatementItemProcessor processor,
            MonthlyStatementItemWriter writer
    ) {
        return new StepBuilder("monthlyStatementStep", jobRepository)
                .<Account, Account>chunk(100, transactionManager)
                .reader(reader)
                .processor(new CompositeItemProcessorBuilder<Account, Account>().delegates(processor).build())
                .writer(writer)
                .build();
    }

    @Bean
    public Step dormantAccountStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("dormantAccountReader") RepositoryItemReader<Account> reader,
            DormantAccountItemProcessor processor,
            DormantAccountItemWriter writer
    ) {
        return new StepBuilder("dormantAccountStep", jobRepository)
                .<Account, Account>chunk(100, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    @Bean
    public Step failedTransactionCleanupStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("failedTransactionReader") RepositoryItemReader<Transaction> reader,
            FailedTransactionCleanupItemProcessor processor,
            FailedTransactionCleanupItemWriter writer
    ) {
        return new StepBuilder("failedTransactionCleanupStep", jobRepository)
                .<Transaction, FailedTransactionCleanupItem>chunk(100, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    @Bean
    public Job monthlyStatementJob(JobRepository jobRepository, Step monthlyStatementStep) {
        return new JobBuilder("monthlyStatementJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(monthlyStatementStep)
                .build();
    }

    @Bean
    public Job dormantAccountJob(JobRepository jobRepository, Step dormantAccountStep) {
        return new JobBuilder("dormantAccountJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(dormantAccountStep)
                .build();
    }

    @Bean
    public Job failedTransactionCleanupJob(JobRepository jobRepository, Step failedTransactionCleanupStep) {
        return new JobBuilder("failedTransactionCleanupJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(failedTransactionCleanupStep)
                .build();
    }
}
