package com.example.kafka.gc.messaging.kafka.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.kafka.common.Node;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicMetadata {
    Broker broker;
    String name;
    Boolean internal;
    int partitionCount;
    long numberOfMessages;
    @Builder.Default
    List<OffsetMetadata> offsetMetadataList = new ArrayList<>();
}
