package com.bank.app.batch.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class BatchScheduler {

    private final JobLauncher jobLauncher;

    @Qualifier("monthlyStatementJob")
    private final Job monthlyStatementJob;

    @Qualifier("dormantAccountJob")
    private final Job dormantAccountJob;

    @Qualifier("failedTransactionCleanupJob")
    private final Job failedTransactionCleanupJob;

    @Scheduled(cron = "${app.batch.cron.monthly-statement:0 0 8 1 * *}")
    public void runMonthlyStatementJob() {
        launch(monthlyStatementJob, "monthlyStatement");
    }

    @Scheduled(cron = "${app.batch.cron.dormant-account:0 0 0 * * SUN}")
    public void runDormantAccountJob() {
        launch(dormantAccountJob, "dormantAccount");
    }

    @Scheduled(cron = "${app.batch.cron.failed-tx-cleanup:0 0 * * * *}")
    public void runFailedTransactionCleanupJob() {
        launch(failedTransactionCleanupJob, "failedTransactionCleanup");
    }

    private void launch(Job job, String jobName) {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("runAt", System.currentTimeMillis())
                    .addString("jobName", jobName)
                    .toJobParameters();
            jobLauncher.run(job, params);
        } catch (Exception ex) {
            log.error("action=batch.job_failed job={} message={}", jobName, ex.getMessage(), ex);
        }
    }
}
