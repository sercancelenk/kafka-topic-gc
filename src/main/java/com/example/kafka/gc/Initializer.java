package com.example.kafka.gc;

import com.example.kafka.gc.service.AdminService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class Initializer {
    private TaskScheduler virtualTaskScheduler;
    private AsyncTaskExecutor applicationTaskExecutor;
    private AdminService adminService;

    @Value("${application.scheduler.cron}")
    private String applicationSchedulerCron;

    private static List<String> clusterBootstraps = List.of("localhost:9092", "staging-confluent-broker-01-earth.trendyol.com:9092,staging-confluent-broker-02-earth.trendyol.com:9092,staging-confluent-broker-03-earth.trendyol.com:9092,staging-confluent-broker-04-earth.trendyol.com:9092,staging-confluent-broker-05-earth.trendyol.com:9092,staging-confluent-broker-06-earth.trendyol.com:9092,staging-confluent-broker-07-earth.trendyol.com:9092,staging-confluent-broker-08-earth.trendyol.com:9092");

    @Autowired
    public Initializer(TaskScheduler virtualTaskScheduler, @Qualifier("applicationTaskExecutor") AsyncTaskExecutor applicationTaskExecutor,
                       AdminService adminService) {
        this.virtualTaskScheduler = virtualTaskScheduler;
        this.applicationTaskExecutor = applicationTaskExecutor;
        this.adminService = adminService;
    }

    @PostConstruct
    public void initGC() {
        clusterBootstraps
                .forEach(cluster -> {
                    adminService.collectDataFromKafka(applicationTaskExecutor, cluster);
                    virtualTaskScheduler.schedule(() -> adminService.collectDataFromKafka(applicationTaskExecutor, cluster), new CronTrigger(applicationSchedulerCron));
                });
    }
}
