package com.example.kafka.gc.persistence.repository;

import com.example.kafka.gc.persistence.domain.TopicMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicMetadataRepository extends JpaRepository<TopicMetadata, Long> {
}
