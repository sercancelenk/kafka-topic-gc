package com.example.kafka.gc.messaging.kafka.model;

import org.apache.kafka.clients.admin.DescribeTopicsResult;

public record BrokerDescribedTopicPair(Broker broker, DescribeTopicsResult describeTopicsResult) {
    }