package com.example.wallet.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.ActiveProfiles;

/** Outside the test profile the CNB refresh is scheduled with the configured interval. */
@ActiveProfiles("test")
@SpringBootTest(properties = "wallet.scheduling.enabled=true")
class SchedulingConfigTest {

    @Autowired private ScheduledTaskHolder scheduledTasks;

    @Test
    void cnbRefreshRunsEveryRefreshInterval() {
        assertThat(scheduledTasks.getScheduledTasks())
                .map(scheduled -> scheduled.getTask())
                .filteredOn(task -> task.toString().endsWith("CnbFxRateProvider.refresh"))
                .singleElement()
                .isInstanceOfSatisfying(
                        FixedDelayTask.class,
                        task ->
                                assertThat(task.getIntervalDuration())
                                        .isEqualTo(Duration.ofMinutes(10)));
    }
}
