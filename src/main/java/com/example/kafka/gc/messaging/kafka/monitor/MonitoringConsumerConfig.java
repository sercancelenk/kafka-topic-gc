package com.example.kafka.gc.messaging.kafka.monitor;

import com.example.kafka.gc.messaging.kafka.config.ReactorKafkaProperties;
import com.example.kafka.gc.messaging.kafka.model.TopicMetadata;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.receiver.ReceiverPartition;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
@Getter
@RequiredArgsConstructor
public class MonitoringConsumerConfig {
    private final static String TOPIC = "topicgc.monitoring.topic";
    private final ReactorKafkaProperties reactorKafkaProperties;
    private ReceiverOptions<String, String> receiverOptions;
    private Disposable disposable;
    private Flux<String> topicMetadataStream;


    @PostConstruct
    public void init() {
        Map<String, Object> props = reactorKafkaProperties.getConvertedConfigurations();
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "MonitoringConsumerGroup_" + UUID.randomUUID().toString());

        receiverOptions = ReceiverOptions.<String, String>create(props)
                .addAssignListener(partitions -> partitions.forEach(ReceiverPartition::seekToEnd))
                .commitBatchSize(10);

        disposable = consumeMessages(TOPIC);
        log.info("Heartbeat consumer loaded");
    }

    public Disposable consumeMessages(String topic) {

        ReceiverOptions<String, String> options = receiverOptions.subscription(Collections.singleton(topic))
                .addAssignListener(partitions -> log.debug("onPartitionsAssigned {}", partitions))
                .addRevokeListener(partitions -> log.debug("onPartitionsRevoked {}", partitions))
                .commitInterval(Duration.ZERO);

        topicMetadataStream = KafkaReceiver.create(options)
                .receiveAutoAck()
                .concatMap(r -> r)
                .map(ConsumerRecord::value)
                .publish()
                .autoConnect();

        return topicMetadataStream.subscribe(record -> log.info("Consumer Subscribed Successfully"));
    }

    @PreDestroy
    public void preDestroy() {
        log.info("Destroying the Consumer");
        disposable.dispose();
        log.info("Consumer Subscribe Flux disposed");
    }


}
