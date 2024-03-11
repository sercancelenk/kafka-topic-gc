
package com.example.kafka.gc.messaging.kafka.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "reactor.kafka")
@Slf4j
public class ReactorKafkaProperties {

    private Map<String, String> configurations = new HashMap();

    public Map<String, String> getConfigurations() {
        return this.configurations;
    }

    public Map<String, Object> getConvertedConfigurations() {
        Map<String, Object> converted = new HashMap<>(configurations);
        return converted;
    }
}
