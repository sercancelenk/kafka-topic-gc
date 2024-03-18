package com.example.kafka.gc;

import com.example.kafka.gc.config.TopicGcProps;
import com.example.kafka.gc.service.TopicGcService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class Initializer {
    private TaskScheduler virtualTaskScheduler;
    private AsyncTaskExecutor applicationTaskExecutor;
    private TopicGcService topicGcService;

    private final TopicGcProps topicGcProps;


    @Autowired
    public Initializer(TaskScheduler virtualTaskScheduler, @Qualifier("applicationTaskExecutor") AsyncTaskExecutor applicationTaskExecutor,
                       TopicGcService topicGcService, TopicGcProps topicGcProps) {
        this.virtualTaskScheduler = virtualTaskScheduler;
        this.applicationTaskExecutor = applicationTaskExecutor;
        this.topicGcService = topicGcService;
        this.topicGcProps = topicGcProps;
    }

    @PostConstruct
    public void initGC() {
        log.info("Kafka Topic GC initializing with props: {}", topicGcProps);
        topicGcProps.getClusters()
                .forEach(cluster -> {
                    topicGcService.describeTopics(applicationTaskExecutor, cluster);
//                    virtualTaskScheduler.schedule(() -> topicGcService.describeTopics(applicationTaskExecutor, cluster), new CronTrigger(topicGcProps.getSchedulerCron()));
                });
    }
}
