package com.example.kafka.gc.service;

import com.example.kafka.gc.messaging.kafka.model.Broker;
import com.example.kafka.gc.messaging.kafka.model.TopicMetadata;
import com.example.kafka.gc.messaging.kafka.monitor.MonitoringProducer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminService {
    private final ObjectMapper objectMapper;
    private final MonitoringProducer producer;
    private final TopicMetadataService topicMetadataService;

    @SneakyThrows
    public void collectDataFromKafka(AsyncTaskExecutor applicationTaskExecutor, String cluster) {
        Properties adminClientProps = new Properties();
        adminClientProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, cluster);
        adminClientProps.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 20000);
        adminClientProps.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 20000);
        List<String> ignoredTopicsKeys = List.of("_schemas", "connect");
        BrokerDescribedTopicPair brokerAndDescribedTopics = getBrokerAndDescribedTopics(adminClientProps, ignoredTopicsKeys);
        if (Objects.nonNull(brokerAndDescribedTopics.describeTopicsResult)) {
            brokerAndDescribedTopics.describeTopicsResult.topicNameValues()
                    .forEach((topic, value) -> {
                        applicationTaskExecutor.submit(collectData(cluster, topic, value, brokerAndDescribedTopics));
                    });
        }

    }
    private Runnable collectData(String cluster, String topic, KafkaFuture<TopicDescription> value, BrokerDescribedTopicPair brokerAndDescribedTopics) {
        return () -> {
            try {
                TopicDescription topicDescription = value.get(20, TimeUnit.SECONDS);
                ThreadLocal<TopicMetadata.TopicMetadataBuilder> topicMetadataBuilderTL = new InheritableThreadLocal<>();
                topicMetadataBuilderTL.set(TopicMetadata.builder());
                topicMetadataBuilderTL.get().name(topic);
                topicMetadataBuilderTL.get().broker(brokerAndDescribedTopics.broker);
                topicMetadataBuilderTL.get().internal(topicDescription.isInternal());
                topicMetadataBuilderTL.get().partitionCount(topicDescription.partitions().size());
                topicMetadataBuilderTL.get().broker(brokerAndDescribedTopics.broker);

                collectNumberOfMessagesCount(cluster, topic, topicMetadataBuilderTL.get());
                if(topicMetadataBuilderTL.get().build().getNumberOfMessages() > 0)
                    getLastMessage(cluster, topic, topicMetadataBuilderTL.get());

                topicMetadataBuilderTL.get().metadataId(brokerAndDescribedTopics.broker.getClusterId().concat("|").concat(topic));
                TopicMetadata topicMeta = topicMetadataBuilderTL.get().build();

                topicMetadataService.set(topicMeta.getMetadataId(), topicMeta);
                producer.sendMessage(objectMapper.writeValueAsString(topicMeta));
            } catch (InterruptedException | ExecutionException | TimeoutException |
                     JsonProcessingException e) {
                log.error("Error occurred while requesting topic describe");
            }
        };
    }

    public record BrokerDescribedTopicPair(Broker broker, DescribeTopicsResult describeTopicsResult) {
    }

    ;

    private BrokerDescribedTopicPair getBrokerAndDescribedTopics(Properties adminClientProps, List<String> ignoredTopicsKeys) throws InterruptedException, ExecutionException {
        Broker.BrokerBuilder brokerBuilder = Broker.builder();
        DescribeTopicsResult describeTopics = null;

        try{
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
                return new BrokerDescribedTopicPair(brokerBuilder.build(), describeTopics);
            }
        }catch (Throwable ex){
            System.out.println("Exception Occurred while describing topics. " + ex.getMessage());
            throw new RuntimeException("Exception Occurred while describing topics. " + ex.getMessage());
        }

    }

    private void collectNumberOfMessagesCount(String cluster, String topic, TopicMetadata.TopicMetadataBuilder topicMetadataBuilder) {
        try (KafkaConsumer<String, String> consumer = createConsumer(cluster)) {
            List<TopicPartition> partitions = consumer.partitionsFor(topic).stream().map(p -> new TopicPartition(topic, p.partition()))
                    .toList();
            consumer.assign(partitions);
            consumer.assign(partitions);
            consumer.seekToEnd(Collections.emptySet());
            Map<TopicPartition, Long> endPartitions = partitions.stream().collect(Collectors.toMap(Function.identity(), consumer::position));
            topicMetadataBuilder.partitionCount(partitions.size());
            topicMetadataBuilder.numberOfMessages(partitions.stream().mapToLong(endPartitions::get).sum());
        }
    }


    private KafkaConsumer<String, String> createConsumer(String cluster) {
        return createConsumer(cluster, "tgc");
    }

    private KafkaConsumer<String, String> createConsumer(String cluster, String groupId) {
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, cluster);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, "20971520");
        consumerProps.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, "20971520");
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        return new KafkaConsumer<String, String>(consumerProps);
    }

    public void getLastMessage(String cluster, String topic, TopicMetadata.TopicMetadataBuilder topicMetadataBuilder) {
        try (KafkaConsumer<String, String> consumer = createConsumer(cluster, UUID.randomUUID().toString())) {
            consumer.subscribe(Collections.singletonList(topic));

            consumer.poll(Duration.ofSeconds(10));

            consumer.assignment().forEach(System.out::println);

            AtomicLong maxTimestamp = new AtomicLong();
            AtomicReference<ConsumerRecord<String, String>> latestRecord = new AtomicReference<>();

            // get the last offsets for each partition
            consumer.endOffsets(consumer.assignment()).forEach((topicPartition, offset) -> {
                // seek to the last offset of each partition
                consumer.seek(topicPartition, (offset == 0) ? offset : offset - 1);

                // poll to get the last record in each partition
                consumer.poll(Duration.ofSeconds(10)).forEach(record -> {

                    // the latest record in the 'topic' is the one with the highest timestamp
                    if (record.timestamp() > maxTimestamp.get()) {
                        maxTimestamp.set(record.timestamp());
                        latestRecord.set(record);
                    }
                });
            });
            if(Objects.nonNull(latestRecord.get()))
                topicMetadataBuilder.lastMessageTime(new Date(latestRecord.get().timestamp()));
        } catch (Throwable ex) {
            System.out.println("Exception occurred while getting last message " + ex.getMessage());
        }
    }
}
