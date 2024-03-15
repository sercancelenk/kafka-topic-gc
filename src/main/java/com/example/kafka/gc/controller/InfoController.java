package com.example.kafka.gc.controller;

import com.example.kafka.gc.messaging.kafka.model.TopicMeasurement;
import com.example.kafka.gc.service.TopicMeasurementRedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("info")
@RequiredArgsConstructor
public class InfoController {
    private final TopicMeasurementRedisService topicMeasurementRedisService;

    @GetMapping("clusterId/{clusterId}/topic/{topic}")
    public TopicMeasurement getInfo(@PathVariable String clusterId, @PathVariable String topic) {
        return topicMeasurementRedisService.getTopicMeasurement(clusterId, topic).orElse(null);
    }

    @GetMapping("clusterId/{clusterId}/topic-list")
    public List<String> searchTopic(@PathVariable String clusterId, @RequestParam String query) {
        return topicMeasurementRedisService.findTopic(clusterId, query);
    }
}
