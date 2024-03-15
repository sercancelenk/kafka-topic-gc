package com.example.kafka.gc.service;

import com.example.kafka.gc.messaging.kafka.model.Broker;
import com.example.kafka.gc.messaging.kafka.model.BrokerDescribedTopicPair;
import com.example.kafka.gc.messaging.kafka.model.LastMessageMetadata;
import com.example.kafka.gc.messaging.kafka.model.PartitionMetadata;
import com.example.kafka.gc.model.TopicGcProps;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaExtension {
    private final TopicGcProps topicGcProps;

    private AdminClient createAdminClient(String cluster) {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, cluster);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, topicGcProps.getAdminClient().requestTimeout());
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, topicGcProps.getAdminClient().defaultApiTimeout());
        return AdminClient.create(props);
    }

    protected Optional<BrokerDescribedTopicPair> getBrokerAndDescribedTopics(String bootstrapServers, List<String> ignoredTopicsKeys) {
        Broker.BrokerBuilder brokerBuilder = Broker.builder();
        DescribeTopicsResult describeTopics = null;

        try {
            try (AdminClient client = createAdminClient(bootstrapServers)) {
                // Who are the brokers? Who is the controller?
                DescribeClusterResult cluster = client.describeCluster();

                brokerBuilder.clusterId(cluster.clusterId().get());
                brokerBuilder.nodes(cluster.nodes().get().stream().map(node -> "node id: ".concat(node.id() + "").concat(" Host: ").concat(node.host() + ":" + node.port())).collect(Collectors.joining(" | ")));
                brokerBuilder.controller(cluster.controller().get().host().concat(":").concat(cluster.controller().get().port() + ""));

                ListTopicsOptions options = new ListTopicsOptions();
                options.listInternal(false);
                ListTopicsResult listTopicsResult = client.listTopics(options);

                List<String> topicList = listTopicsResult.names().get().stream()
                        .filter(t -> ignoredTopicsKeys.stream().allMatch(i -> !t.contains(i)))
                        .filter(t -> StringUtils.countMatches(t, ".") > 1)
                        .toList();

                // check if our demo topic exists, create it if it doesn't
                describeTopics = client.describeTopics(topicList);
                return Optional.of(new BrokerDescribedTopicPair(brokerBuilder.build(), describeTopics));
            }
        } catch (Throwable ex) {
            System.out.println("Exception Occurred while describing topics. " + ex.getMessage());
            throw new RuntimeException("Exception Occurred while describing topics. " + ex.getMessage());
        }

    }

    protected KafkaConsumer<String, String> createConsumer(String bootstrapServers) {
        return createConsumer(bootstrapServers, "tgc");
    }

    protected KafkaConsumer<String, String> createConsumer(String bootstrapServers, String groupId) {
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, topicGcProps.getDefaultConsumerProps().maxPartitionFetchBytes());
        consumerProps.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, topicGcProps.getDefaultConsumerProps().fetchMaxBytes());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        return new KafkaConsumer<String, String>(consumerProps);
    }

    protected PartitionMetadata measurePartitionMetadata(String cluster, String topic) {
        try (KafkaConsumer<String, String> consumer = createConsumer(cluster)) {
            List<TopicPartition> partitions = consumer.partitionsFor(topic).stream().map(p -> new TopicPartition(topic, p.partition()))
                    .toList();
            consumer.assign(partitions);
            consumer.seekToEnd(Collections.emptySet());
            Map<TopicPartition, Long> endPartitions = partitions.stream().collect(Collectors.toMap(Function.identity(), consumer::position));
            return PartitionMetadata.builder().partitionCount(partitions.size()).messageCount(partitions.stream().mapToLong(endPartitions::get).sum()).build();
        }
    }

    protected LastMessageMetadata measureLastMessageMetadata(String cluster, String topic, PartitionMetadata partitionMetadata) {
        if(partitionMetadata.getMessageCount()<=0){
            return LastMessageMetadata.builder().build();
        }
        try (KafkaConsumer<String, String> consumer = createConsumer(cluster, UUID.randomUUID().toString())) {
            List<TopicPartition> partitions = consumer.partitionsFor(topic).stream().map(p -> new TopicPartition(topic, p.partition()))
                    .toList();
            consumer.assign(partitions);
            consumer.seekToEnd(Collections.emptySet());

            AtomicLong maxTimestamp = new AtomicLong();
            AtomicReference<ConsumerRecord<String, String>> latestRecord = new AtomicReference<>();

            // get the last offsets for each partition
            consumer.endOffsets(consumer.assignment()).forEach((topicPartition, offset) -> {
                // seek to the last offset of each partition
                consumer.seek(topicPartition, (offset == 0) ? offset : offset - 1);

                // poll to get the last record in each partition
                consumer.poll(Duration.ofSeconds(3)).forEach(record -> {
                    if (record.timestamp() > maxTimestamp.get()) {
                        maxTimestamp.set(record.timestamp());
                        latestRecord.set(record);
                    }
                });
            });
            if (Objects.nonNull(latestRecord.get())) {
                return LastMessageMetadata
                        .builder()
                        .timeOfLastMessage(new Date(latestRecord.get().timestamp()))
                        .offsetOfLastMessage(latestRecord.get().offset())
                        .partitionOfLastMessage(latestRecord.get().partition())
                        .build();
            }
        } catch (Throwable ex) {
            System.out.println("Exception occurred while getting last message " + ex.getMessage());
        }
        return null;
    }

}
