package com.example.kafka.gc.extension;

import com.example.kafka.gc.config.TopicGcProps;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.util.List;
import java.util.Properties;

public interface KafkaExtension {
    TopicGcProps getTopicGcProps();

    default AdminClient createAdminClient(String cluster) {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, cluster);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, getTopicGcProps().getAdminClient().requestTimeout());
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, getTopicGcProps().getAdminClient().defaultApiTimeout());
        return AdminClient.create(props);
    }

    default KafkaConsumer<String, String> createConsumer(String bootstrapServers) {
        return createConsumer(bootstrapServers, "tgc");
    }

    default KafkaConsumer<String, String> createConsumer(String bootstrapServers, String groupId) {
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, getTopicGcProps().getDefaultConsumerProps().maxPartitionFetchBytes());
        consumerProps.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, getTopicGcProps().getDefaultConsumerProps().fetchMaxBytes());
        consumerProps.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, false);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        return new KafkaConsumer<String, String>(consumerProps);
    }

    default List<TopicPartition> getTopicPartitions(String topic, KafkaConsumer<String, String> consumer) {
        return consumer.partitionsFor(topic).stream().map(p -> new TopicPartition(topic, p.partition()))
                .toList();
    }
}

