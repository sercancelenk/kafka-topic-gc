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
    private Date timeOfLastMessage;
    private long offsetOfLastMessage;
    private long partitionOfLastMessage;
}
