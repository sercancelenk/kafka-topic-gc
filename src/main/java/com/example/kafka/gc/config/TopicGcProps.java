package com.example.kafka.gc.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "topic-gc")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopicGcProps {
    public record AdminClientProps(Integer requestTimeout,
                                   Integer defaultApiTimeout){}

    AdminClientProps adminClient;

    public record DefaultConsumerProps(String maxPartitionFetchBytes, String fetchMaxBytes){}

    DefaultConsumerProps defaultConsumerProps;

    public record ClusterInfo(String name, String bootstrapServers){}
    List<ClusterInfo> clusters;

    String schedulerCron;
}
