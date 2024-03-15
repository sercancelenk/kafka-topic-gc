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

import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class Initializer {
    private TaskScheduler virtualTaskScheduler;
    private AsyncTaskExecutor applicationTaskExecutor;
    private AdminService adminService;

    @Value("${application.scheduler.cron}")
    private String applicationSchedulerCron;


    @Value("#{'${clusters}'.split(',')}")
    private String[] clusterBootstraps;

    @Autowired
    public Initializer(TaskScheduler virtualTaskScheduler, @Qualifier("applicationTaskExecutor") AsyncTaskExecutor applicationTaskExecutor,
                       AdminService adminService) {
        this.virtualTaskScheduler = virtualTaskScheduler;
        this.applicationTaskExecutor = applicationTaskExecutor;
        this.adminService = adminService;
    }

    @PostConstruct
    public void initGC() {
        Arrays.stream(clusterBootstraps)
                .forEach(cluster -> {
                    adminService.describeTopics(applicationTaskExecutor, cluster);
//                    virtualTaskScheduler.schedule(() -> adminService.collectDataFromKafka(applicationTaskExecutor, cluster), new CronTrigger(applicationSchedulerCron));
                });
    }
}
