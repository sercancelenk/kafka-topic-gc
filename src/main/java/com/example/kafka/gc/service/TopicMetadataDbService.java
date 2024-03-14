package com.example.kafka.gc.service;

import com.example.kafka.gc.persistence.domain.TopicMetadata;
import com.example.kafka.gc.persistence.repository.TopicMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TopicMetadataDbService {
    private final TopicMetadataRepository topicMetadataRepository;

    @Transactional
    public void save(){
        TopicMetadata i = new TopicMetadata();
        i.setSource("Source");
        topicMetadataRepository.save(i);
    }
}
