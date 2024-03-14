package com.example.kafka.gc.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.task.SimpleAsyncTaskSchedulerBuilder;
import org.springframework.boot.web.embedded.tomcat.TomcatProtocolHandlerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;

import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadFactory;

@Configuration
@Slf4j
@EnableAsync
public class ThreadExecutorConfig {
    @Bean
    public AsyncTaskExecutor applicationTaskExecutor() {
        final ThreadFactory factory = Thread.ofVirtual().name("byzas-async-", 0)
                .uncaughtExceptionHandler((t, e) -> System.out.println("Exception in viertual thread " + e.getMessage())).factory();
        return new TaskExecutorAdapter(Executors.newThreadPerTaskExecutor(factory));
    }

    @Bean
    public TaskScheduler virtualTaskScheduler(@Qualifier("applicationTaskExecutor") AsyncTaskExecutor applicationTaskExecutor) {
        SimpleAsyncTaskSchedulerBuilder simpleAsyncTaskSchedulerBuilder = new SimpleAsyncTaskSchedulerBuilder();
        simpleAsyncTaskSchedulerBuilder.virtualThreads(true);
        simpleAsyncTaskSchedulerBuilder.threadNamePrefix("MyTask-");

        SimpleAsyncTaskScheduler build = simpleAsyncTaskSchedulerBuilder.build();

        final ThreadFactory factory = Thread.ofVirtual().name("byzas-routine-", 0)
                .uncaughtExceptionHandler((t, e) -> System.out.println("Exception in viertual thread " + e.getMessage())).factory();

        build.setTargetTaskExecutor(Executors.newThreadPerTaskExecutor(factory));
//        build.setTargetTaskExecutor(Executors.newFixedThreadPool(5, factory));
        return build;
    }

    @Bean
    public TomcatProtocolHandlerCustomizer<?> protocolHandlerVirtualThreadExecutorCustomizer() {
        return protocolHandler -> {
            log.info("Configuring " + protocolHandler + " to use VirtualThreadPerTaskExecutor");
            protocolHandler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        };
    }

}