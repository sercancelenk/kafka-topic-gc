package com.example.kafka.gc.controller;

import com.example.kafka.gc.messaging.kafka.monitor.MonitoringConsumerConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RequestMapping(path = "/live")
@RestController
@RequiredArgsConstructor
@Slf4j
public class LiveController {
    private final MonitoringConsumerConfig consumer;

    @GetMapping(value = "tickers", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> getTickersStream() {
        return consumer.getTopicMetadataStream();
    }
}
