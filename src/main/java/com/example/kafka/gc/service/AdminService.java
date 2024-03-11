package com.example.kafka.gc.service;

import com.example.kafka.gc.messaging.kafka.model.Broker;
import com.example.kafka.gc.messaging.kafka.model.OffsetMetadata;
import com.example.kafka.gc.messaging.kafka.model.TopicMetadata;
import com.example.kafka.gc.messaging.kafka.monitor.MonitoringProducer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminService {
    private final ObjectMapper objectMapper;
    private final MonitoringProducer producer;

    @SneakyThrows
    public void collectDataFromKafka(AsyncTaskExecutor applicationTaskExecutor) {
        Properties adminClientProps = new Properties();
        adminClientProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        adminClientProps.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 1000);
        adminClientProps.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 1000);
        List<String> ignoredTopicsKeys = List.of("_schemas", "connect");
        BrokerDescribedTopicPair brokerAndDescribedTopics = getBrokerAndDescribedTopics(adminClientProps, ignoredTopicsKeys);

        if (Objects.nonNull(brokerAndDescribedTopics.describeTopicsResult)) {
            brokerAndDescribedTopics.describeTopicsResult.topicNameValues()
                    .forEach((topic, value) -> {
                        applicationTaskExecutor.submit(() -> {
                            try {
                                TopicDescription topicDescription = value.get(20, TimeUnit.SECONDS);
                                KafkaConsumer<String, String> consumer = createConsumer();
                                TopicMetadata.TopicMetadataBuilder topicMetadataBuilder = TopicMetadata.builder();
                                topicMetadataBuilder.name(topic);
                                topicMetadataBuilder.broker(brokerAndDescribedTopics.broker);

                                topicMetadataBuilder.internal(topicDescription.isInternal());
                                topicMetadataBuilder.partitionCount(topicDescription.partitions().size());
                                topicMetadataBuilder.broker(brokerAndDescribedTopics.broker);

                                long numberOfMessagesCount = collectNumberOfMessagesCount(topic, consumer);
                                List<OffsetMetadata> offsetMetadata = collectOffsets(topic, adminClientProps, consumer.groupMetadata().groupId());
                                topicMetadataBuilder.offsetMetadataList(offsetMetadata);
                                topicMetadataBuilder.numberOfMessages(numberOfMessagesCount);

                                log.info("TopicGC: \n{}", topicMetadataBuilder.build());
                                producer.sendMessage(objectMapper.writeValueAsString(topicMetadataBuilder.build()));
                                consumer.close();
                                log.info("--------------------------------------------------------");
                            } catch (InterruptedException | ExecutionException | TimeoutException |
                                     JsonProcessingException e) {
                                log.error("Error occurred while requesting topic describe");
                            }
                        });
                    });
        }

    }

    public record BrokerDescribedTopicPair(Broker broker, DescribeTopicsResult describeTopicsResult) {
    }

    ;

    private BrokerDescribedTopicPair getBrokerAndDescribedTopics(Properties adminClientProps, List<String> ignoredTopicsKeys) throws InterruptedException, ExecutionException {
        Broker.BrokerBuilder brokerBuilder = Broker.builder();
        DescribeTopicsResult describeTopics = null;

        try (AdminClient client = AdminClient.create(adminClientProps)) {
            // Who are the brokers? Who is the controller?
            DescribeClusterResult cluster = client.describeCluster();

            brokerBuilder.clusterId(cluster.clusterId().get());
            brokerBuilder.nodes(cluster.nodes().get().stream().map(node -> "node id: ".concat(node.id() + "").concat(" Host: ").concat(node.host() + ":" + node.port())).collect(Collectors.joining(" | ")));
            brokerBuilder.controller(cluster.controller().get().host().concat(":").concat(cluster.controller().get().port() + ""));

            ListTopicsOptions options = new ListTopicsOptions();
            options.listInternal(false);
            ListTopicsResult listTopicsResult = client.listTopics(options);

            List<String> topicList = listTopicsResult.names().get().stream().filter(t -> ignoredTopicsKeys.stream().allMatch(i -> !t.contains(i))).toList();

            // check if our demo topic exists, create it if it doesn't
            describeTopics = client.describeTopics(topicList);
        }
        return new BrokerDescribedTopicPair(brokerBuilder.build(), describeTopics);
    }

    private long collectNumberOfMessagesCount(String topic, KafkaConsumer<String, String> consumer) {
        List<TopicPartition> partitions = consumer.partitionsFor(topic).stream().map(p -> new TopicPartition(topic, p.partition()))
                .toList();
        consumer.assign(partitions);
        consumer.assign(partitions);
        consumer.seekToEnd(Collections.emptySet());
        Map<TopicPartition, Long> endPartitions = partitions.stream().collect(Collectors.toMap(Function.identity(), consumer::position));
        return partitions.stream().mapToLong(endPartitions::get).sum();
    }

    private KafkaConsumer<String, String> createConsumer() {
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, "20971520");
        consumerProps.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, "20971520");
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "tgc");
        return new KafkaConsumer<String, String>(consumerProps);
    }

    @SneakyThrows
    private List<OffsetMetadata> collectOffsets(String topic, Properties adminClientProps, String CONSUMER_GROUP) {
        try (AdminClient admin = AdminClient.create(adminClientProps)) {
            // List consumer groups
            List<String> CONSUMER_GRP_LIST = List.of(CONSUMER_GROUP);

            // Describe a group
            ConsumerGroupDescription groupDescription = admin
                    .describeConsumerGroups(CONSUMER_GRP_LIST)
                    .describedGroups().get(CONSUMER_GROUP).get();

            // Get offsets committed by the group
            Map<TopicPartition, OffsetAndMetadata> offsets =
                    admin.listConsumerGroupOffsets(CONSUMER_GROUP)
                            .partitionsToOffsetAndMetadata().get();

            Map<TopicPartition, OffsetSpec> requestLatestOffsets = new HashMap<>();
            Map<TopicPartition, OffsetSpec> requestEarliestOffsets = new HashMap<>();
            // For all topics and partitions that have offsets committed by the group, get their latest offsets, earliest offsets
            // and the offset for 2h ago. Note that I'm populating the request for 2h old offsets, but not using them.
            // You can swap the use of "Earliest" in the `alterConsumerGroupOffset` example with the offsets from 2h ago
            for (TopicPartition tp : offsets.keySet()) {
                requestLatestOffsets.put(tp, OffsetSpec.latest());
                requestEarliestOffsets.put(tp, OffsetSpec.earliest());
            }

            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latestOffsets =
                    admin.listOffsets(requestLatestOffsets).all().get();

            List<OffsetMetadata> offsetMetadataList = new ArrayList<>();
            for (Map.Entry<TopicPartition, OffsetAndMetadata> e : offsets.entrySet()) {
                String topicx = e.getKey().topic();
                if (topicx.toLowerCase().contains(topic.toLowerCase())) {
                    int partition = e.getKey().partition();
                    long committedOffset = e.getValue().offset();
                    long latestOffset = latestOffsets.get(e.getKey()).offset();
                    offsetMetadataList.add(OffsetMetadata.builder()
                            .lastOffset(latestOffset)
                            .partition(partition)
                            .build());
                }
            }

            return offsetMetadataList;

        }
    }
}
