package com.example.kafka.gc.messaging.kafka.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OffsetMetadata {
    private int partition;
    private long lastOffset;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OffsetMetadata that = (OffsetMetadata) o;
        return partition == that.partition;
    }

    @Override
    public int hashCode() {
        return Objects.hash(partition);
    }
}