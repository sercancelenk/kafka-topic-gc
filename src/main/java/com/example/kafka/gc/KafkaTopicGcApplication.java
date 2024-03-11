package com.example.kafka.gc;

import com.example.kafka.gc.messaging.kafka.model.TopicMetadata;
import com.example.kafka.gc.service.AdminService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

@SpringBootApplication
@Slf4j
public class KafkaTopicGcApplication {

    public static void main(String[] args) {
        SpringApplication.run(KafkaTopicGcApplication.class, args);
    }

    @Bean
    public CommandLineRunner runner(TaskScheduler virtualTaskScheduler, @Qualifier("applicationTaskExecutor") AsyncTaskExecutor applicationTaskExecutor,
                                    AdminService adminService) {
        return args -> {
            //                            log.info("Job {} is running at {}, virtual: {}", i, new Date(), Thread.currentThread().isVirtual());
            virtualTaskScheduler.schedule(() -> adminService.collectDataFromKafka(applicationTaskExecutor), new CronTrigger("0/20 * * * * *"));
        };
    }




}
