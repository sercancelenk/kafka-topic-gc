package com.example.kafka.gc.service;

import com.example.kafka.gc.config.TopicGcProps;
import com.example.kafka.gc.extension.KafkaExtension;
import com.example.kafka.gc.messaging.kafka.model.Broker;
import com.example.kafka.gc.messaging.kafka.model.BrokerDescribedTopicPair;
import com.example.kafka.gc.messaging.kafka.model.LastMessageMetadata;
import com.example.kafka.gc.messaging.kafka.model.PartitionMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.ConsumerGroupState;
import org.apache.kafka.common.TopicPartition;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminClientService implements KafkaExtension {
    private final TopicGcProps topicGcProps;

    protected Optional<BrokerDescribedTopicPair> getBrokerAndDescribedTopics(TopicGcProps.ClusterInfo clusterInfo, List<String> ignoredTopicsKeys) {
        Broker.BrokerBuilder brokerBuilder = Broker.builder();
        DescribeTopicsResult describeTopics = null;

        try {
            try (AdminClient client = createAdminClient(clusterInfo.bootstrapServers())) {
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

    protected PartitionMetadata measurePartitionMetadata(String cluster, String topic) {
        try (KafkaConsumer<String, String> consumer = createConsumer(cluster)) {
            List<TopicPartition> partitions = getTopicPartitions(topic, consumer);
            consumer.assign(partitions);
            consumer.seekToEnd(Collections.emptySet());
            Map<TopicPartition, Long> endPartitions = partitions.stream().collect(Collectors.toMap(Function.identity(), consumer::position));
            return PartitionMetadata.builder().partitionCount(partitions.size()).messageCount(partitions.stream().mapToLong(endPartitions::get).sum()).build();
        }
    }

    protected LastMessageMetadata measureLastMessageMetadata(String cluster, String topic, PartitionMetadata partitionMetadata) {
        if (partitionMetadata.getMessageCount() <= 0) {
            return LastMessageMetadata.builder().build();
        }
        try (KafkaConsumer<String, String> consumer = createConsumer(cluster, UUID.randomUUID().toString())) {
            List<TopicPartition> partitions = getTopicPartitions(topic, consumer);
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

    protected void measureConsumerGroupsMetadata(String cluster) {
        try (AdminClient adminClient = createAdminClient(cluster)) {


            List<Triple<String, Optional<ConsumerGroupState>, Boolean>> groupInfos = adminClient.listConsumerGroups()
                    .all()
                    .get(60, TimeUnit.SECONDS)
                    .stream()
                    .map(a -> Triple.of(a.groupId(), a.state(), a.isSimpleConsumerGroup()))
                    .toList();

            List<String> groupIds = groupInfos.stream().map(Triple::getLeft).toList();
            adminClient.describeConsumerGroups(groupIds)
                    .all()
                    .get(60, TimeUnit.SECONDS)
                    .entrySet().forEach(cgi -> {
                        boolean hasNoMembers = cgi.getValue().members().isEmpty();
                        log.info("{} {} {}", cgi.getKey(), cgi.getValue().groupId(), cgi.getValue().state());
                    });

        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public TopicGcProps getTopicGcProps() {
        return topicGcProps;
    }
}
