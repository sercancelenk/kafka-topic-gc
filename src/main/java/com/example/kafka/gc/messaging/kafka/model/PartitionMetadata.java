package com.example.kafka.gc.messaging.kafka.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartitionMetadata {
    int partitionCount;
    long messageCount;
}
