package com.example.kafka.gc;

import com.example.kafka.gc.service.TopicGcService;
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
    private TopicGcService topicGcService;

    @Value("${application.scheduler.cron}")
    private String applicationSchedulerCron;


    @Value("#{'${clusters}'.split(',')}")
    private String[] clusterBootstraps;

    @Autowired
    public Initializer(TaskScheduler virtualTaskScheduler, @Qualifier("applicationTaskExecutor") AsyncTaskExecutor applicationTaskExecutor,
                       TopicGcService topicGcService) {
        this.virtualTaskScheduler = virtualTaskScheduler;
        this.applicationTaskExecutor = applicationTaskExecutor;
        this.topicGcService = topicGcService;
    }

    @PostConstruct
    public void initGC() {
        Arrays.stream(clusterBootstraps)
                .forEach(cluster -> {
                    topicGcService.describeTopics(applicationTaskExecutor, cluster);
                    virtualTaskScheduler.schedule(() -> topicGcService.describeTopics(applicationTaskExecutor, cluster), new CronTrigger(applicationSchedulerCron));
                });
    }
}
