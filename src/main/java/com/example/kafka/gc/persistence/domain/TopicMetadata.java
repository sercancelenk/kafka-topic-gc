package com.example.kafka.gc.persistence.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@SuperBuilder
@Data
@Entity
@Table(name = "topic_metadata")
public class TopicMetadata extends AuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(name = "source", nullable = false)
    private String source;

}
