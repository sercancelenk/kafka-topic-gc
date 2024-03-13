package com.example.kafka.gc.messaging.kafka.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OffsetMetadata implements Serializable {
    private int partition;
    private long lastOffset;
    private Date timeStampDate;

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