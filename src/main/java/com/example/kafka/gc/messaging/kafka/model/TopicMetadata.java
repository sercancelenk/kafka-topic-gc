package com.example.kafka.gc.messaging.kafka.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.kafka.common.Node;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@RedisHash
public class TopicMetadata implements Serializable {
    @Id private String metadataId;
    Broker broker;
    String name;
    Boolean internal;
    int partitionCount;
    long numberOfMessages;
    Date lastMessageTime;
    long lastOffset;
    long lastOffsetPartition;
}
