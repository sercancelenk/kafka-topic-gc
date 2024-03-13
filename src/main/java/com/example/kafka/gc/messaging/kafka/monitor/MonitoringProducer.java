package com.example.kafka.gc.messaging.kafka.monitor;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;
import reactor.kafka.sender.SenderRecord;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class MonitoringProducer {
    private static final String BOOTSTRAP_SERVERS = "localhost:9072";
    private final static String TOPIC = "topicgc.monitoring.topic";

    private KafkaSender<String, String> sender;

    @PostConstruct
    public void init() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "sample-producer");
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        SenderOptions<String, String> senderOptions = SenderOptions.create(props);
        sender = KafkaSender.create(senderOptions);
    }

    public void sendMessage(String socketMessageDto) {

        String correlationId = "Transaction_" + UUID.randomUUID();
        String key = "Key" + UUID.randomUUID();

        Flux<String> srcFlux = Flux.just(socketMessageDto);

        try {
            //ew ProducerRecord<>("", p1.key, p1.value);
            ProducerRecord<String, String> producerRecord = new ProducerRecord<>(TOPIC, socketMessageDto);
            sender.send(srcFlux.map(p1 -> SenderRecord.create(producerRecord, correlationId)))
                    .log()
                    //.doOnNext(result -> handleOnNext(result, eventRecord, errorHandlerCallback))
                    .doOnError(e -> log.error("->", e))
                    .parallel(3)
                    .subscribe();
        } catch (Exception ex) {
            log.error("Error Sending/Constructing Producer/Data: {}, {}", ex.getMessage(), ex);
        }

    }

    public void close() {
        sender.close();
    }

    @PreDestroy
    public void destory() {
        close();
    }

}
