package com.example.kafka.gc.messaging.kafka.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@RedisHash
public class TopicMeasurement implements Serializable {
    @Id
    private String metadataId;
    Broker broker;
    TopicMetadata topicMetadata;
}
