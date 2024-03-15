package com.example.kafka.gc.service;

import com.example.kafka.gc.messaging.kafka.model.*;
import com.example.kafka.gc.messaging.kafka.monitor.MonitoringProducer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.KafkaFuture;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminService {
    private final ObjectMapper objectMapper;
    private final MonitoringProducer producer;
    private final TopicMetadataService topicMetadataService;
    private final TopicMetadataDbService topicMetadataDbService;
    private final KafkaExtension kafkaExtension;

    @SneakyThrows
    public void describeTopics(AsyncTaskExecutor applicationTaskExecutor, String cluster) {
        List<String> ignoredTopicsKeys = List.of("_schemas", "connect", "-");
        kafkaExtension.getBrokerAndDescribedTopics(cluster, ignoredTopicsKeys)
                .ifPresentOrElse(brokerAndDescribedTopics -> {
                    ThreadLocal<TopicMeasurement.TopicMeasurementBuilder> topicMetadataBuilderTL = new InheritableThreadLocal<>();
                    DescribeTopicsResult describeTopicsResult = brokerAndDescribedTopics.describeTopicsResult();
                    AtomicInteger topicIndex = new AtomicInteger(0);
                    log.info("Cluster: {}, Topic Count: {}", cluster, brokerAndDescribedTopics.describeTopicsResult().topicNameValues().size());
                    describeTopicsResult
                            .topicNameValues()
                            .forEach((topic, value) -> applicationTaskExecutor.submit(measureTopic(cluster, topic, value, brokerAndDescribedTopics, topicMetadataBuilderTL, topicIndex.incrementAndGet())));
                }, () -> log.info("Can not describe broker and topics. Cluster: {}", cluster));
    }

    private Runnable measureTopic(String cluster, String topic, KafkaFuture<TopicDescription> topicDescription, BrokerDescribedTopicPair brokerAndDescribedTopics, ThreadLocal<TopicMeasurement.TopicMeasurementBuilder> measurementBuilderTL, int topicIndex) {
        return () -> {
            try {
                topicDescription
                        .thenApply(td -> {
                            measurementBuilderTL.set(TopicMeasurement.builder());

                            PartitionMetadata partitionMetadata = kafkaExtension.measurePartitionMetadata(cluster, topic);
                            LastMessageMetadata lastMessageMetadata = kafkaExtension.measureLastMessageMetadata(cluster, topic, partitionMetadata);

                            measurementBuilderTL.get().broker(brokerAndDescribedTopics.broker());
                            measurementBuilderTL.get().topicMetadata(TopicMetadata.builder()
                                    .name(topic)
                                    .internal(td.isInternal())
                                    .partitionMetadata(partitionMetadata)
                                    .lastMessageMetadata(lastMessageMetadata)
                                    .build());
                            String clusterId = brokerAndDescribedTopics.broker().getClusterId();
                            measurementBuilderTL.get().metadataId(clusterId.concat("|").concat(topic));
                            TopicMeasurement measurement = measurementBuilderTL.get().build();

                            topicMetadataService.set(topic, "Cluster-".concat(clusterId), measurement);
                            try {
                                producer.sendMessage(objectMapper.writeValueAsString(measurement));
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                            log.info("{} Topic {} process done.", topicIndex, topic);
                            return td;
                        }).get(20, TimeUnit.SECONDS);

            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                log.error("Error occurred while requesting topic describe");
                log.error("{} Topic {} process has en error.", topicIndex, topic, e);
            }
        };
    }


}
