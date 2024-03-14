package com.example.kafka.gc.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.serializer.*;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Log4j2
@Configuration
@EnableRedisRepositories
@RequiredArgsConstructor
public class RedisConfiguration {
    private final Environment environment;
    private List<String> sentinelList;
    private String sentinelP;
    private String redisHost;
    private Integer redisPort;
    private String redisPassword;
    private Boolean redisStandaloneEnable;
    private Boolean redisSentinelEnable;

    @PostConstruct
    public void init() {
        redisSentinelEnable = environment.getProperty("redis.sentinel.enable", Boolean.class, false);
        redisStandaloneEnable = environment.getProperty("redis.standalone.enable", Boolean.class, false);

        if (BooleanUtils.isTrue(redisSentinelEnable) && BooleanUtils.isTrue(redisStandaloneEnable))
            throw new RuntimeException("Both sentinel and standalone configuration already setting up. You should be set one of them.");

        if (BooleanUtils.isTrue(redisSentinelEnable)) {
            String sentinelListStr = environment.getRequiredProperty("redis.sentinel.list");
            sentinelList = Arrays.asList(sentinelListStr.split(","));
            sentinelP = environment.getProperty("redis.sentinel.p", "");
        }
        if (BooleanUtils.isTrue(redisStandaloneEnable)) {
            redisHost = environment.getRequiredProperty("redis.standalone.host", String.class);
            redisPort = environment.getRequiredProperty("redis.standalone.port", Integer.class);
            redisPassword = environment.getProperty("redis.standalone.p", "");
        }

    }


    private RedisSentinelConfiguration redisSentinelConfiguration() {
        RedisSentinelConfiguration config = null;

        if (BooleanUtils.isTrue(redisSentinelEnable)) {
            config = new RedisSentinelConfiguration();
            if (Optional.ofNullable(sentinelList).isPresent() && !sentinelList.isEmpty()) {
                config = config.master(environment.getProperty("redis.sentinel.master", "mymaster"));
                if (!StringUtils.isEmpty(sentinelP)) {
                    config.setPassword(RedisPassword.of(sentinelP));
                }

                for (String sentinel : sentinelList) {
                    config.addSentinel(new RedisNode(sentinel.split(":")[0], Integer.parseInt(sentinel.split(":")[1])));
                }
            }
        }
        return config;
    }

    private RedisStandaloneConfiguration redisStandaloneConfiguration() {
        RedisStandaloneConfiguration standaloneConfiguration = new RedisStandaloneConfiguration();
        if (BooleanUtils.isTrue(redisStandaloneEnable)) {
            if (!StringUtils.isEmpty(redisHost) && Optional.ofNullable(redisPort).isPresent()) {
                standaloneConfiguration.setHostName(redisHost);
                standaloneConfiguration.setPort(redisPort);
                if (!StringUtils.isEmpty(redisPassword))
                    standaloneConfiguration.setPassword(RedisPassword.of(redisPassword));
            }
        }
        return standaloneConfiguration;
    }

    // Reactive API
    @Primary
    @Bean(name = "redisConnectionFactory")
    public LettuceConnectionFactory redisConnectionFactory() {
        ClientOptions clientOptions = ClientOptions.builder()
                .timeoutOptions(TimeoutOptions.enabled())
                .autoReconnect(true)
                .socketOptions(SocketOptions.create())
                .build();
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .clientOptions(clientOptions)
                .commandTimeout(Duration.ofSeconds(environment.getProperty("redis.command.timeoutSeconds", Integer.class, 10)))
                .build();

        Boolean redisStandaloneEnable = environment.getProperty("redis.standalone.enable", Boolean.class);
        Boolean redisSentinelConfigurationEnable = environment.getProperty("redis.sentinel.enable", Boolean.class);

        if (BooleanUtils.isFalse(redisStandaloneEnable) && BooleanUtils.isFalse(redisSentinelConfigurationEnable))
            throw new RuntimeException("Please set Redis properties. Sentinel or Standalone");

        if (BooleanUtils.isTrue(redisStandaloneEnable)) {
            log.info("Using redis standalone version.");
            log.info("Properties : redis.standalone.host : {}, redis.standalone.port: {}", redisHost, redisPort);
            return new LettuceConnectionFactory(redisStandaloneConfiguration(), clientConfig);
        } else {
            log.info("Using redis sentinel version.");
            log.info("Properties : redis.sentinel.list : {}", sentinelList);
            return new LettuceConnectionFactory(redisSentinelConfiguration(), clientConfig);
        }
    }

    @Bean
    public RedisTemplate<?, ?> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();

        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(Object.class);

        RedisSerializationContext.RedisSerializationContextBuilder<String, Object> builder =
                RedisSerializationContext.newSerializationContext(new StringRedisSerializer());

        RedisSerializer<String> stringSerializer = new StringRedisSerializer();

        template.setConnectionFactory(redisConnectionFactory());

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.setEnableTransactionSupport(true);

        template.afterPropertiesSet();

        return template;
    }
}