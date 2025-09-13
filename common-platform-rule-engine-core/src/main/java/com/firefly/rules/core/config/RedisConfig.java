/*
 * Copyright 2025 Firefly Software Solutions Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.firefly.rules.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for the Firefly Rule Engine.
 * Only activated when Redis cache provider is enabled.
 */
@Configuration
@ConditionalOnProperty(name = "firefly.rules.cache.provider", havingValue = "REDIS")
@Slf4j
public class RedisConfig {

    /**
     * Configure ReactiveRedisTemplate for cache operations.
     * Uses String serialization for both keys and values.
     */
    @Bean
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        
        log.info("Configuring Redis reactive template for cache operations");
        
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        
        RedisSerializationContext<String, String> serializationContext = 
                RedisSerializationContext.<String, String>newSerializationContext()
                        .key(stringSerializer)
                        .value(stringSerializer)
                        .hashKey(stringSerializer)
                        .hashValue(stringSerializer)
                        .build();
        
        ReactiveRedisTemplate<String, String> template = 
                new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
        
        log.info("Redis reactive template configured successfully");
        return template;
    }

    /**
     * Provide ObjectMapper for Redis serialization.
     * Uses the same ObjectMapper as the rest of the application.
     */
    @Bean
    @ConditionalOnProperty(name = "firefly.rules.cache.provider", havingValue = "REDIS")
    public ObjectMapper redisObjectMapper() {
        return new ObjectMapper();
    }
}
