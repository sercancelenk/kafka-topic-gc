package com.example.kafka.gc.messaging.kafka.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LastMessageMetadata {
    @Builder.Default
    private Date timeOfLastMessage = null;
    @Builder.Default
    private long offsetOfLastMessage = -1;
    @Builder.Default
    private long partitionOfLastMessage = -1;
}
