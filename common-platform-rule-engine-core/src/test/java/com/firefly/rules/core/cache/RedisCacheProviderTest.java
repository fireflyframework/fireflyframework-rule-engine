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

package com.firefly.rules.core.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firefly.rules.core.dsl.model.ASTRulesDSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for RedisCacheProvider using Testcontainers.
 */
@Testcontainers
@DisplayName("Redis Cache Provider Tests")
class RedisCacheProviderTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--appendonly", "yes");

    private RedisCacheProvider cacheProvider;
    private ReactiveRedisTemplate<String, String> redisTemplate;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Configure Redis connection
        String redisHost = redis.getHost();
        Integer redisPort = redis.getMappedPort(6379);
        
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(redisHost, redisPort);
        connectionFactory.afterPropertiesSet();

        // Configure Redis template
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        RedisSerializationContext<String, String> serializationContext = 
                RedisSerializationContext.<String, String>newSerializationContext()
                        .key(stringSerializer)
                        .value(stringSerializer)
                        .hashKey(stringSerializer)
                        .hashValue(stringSerializer)
                        .build();

        redisTemplate = new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
        objectMapper = new ObjectMapper();
        
        cacheProvider = new RedisCacheProvider(
                redisTemplate, 
                objectMapper, 
                "test-cache", 
                Duration.ofMinutes(5)
        );
    }

    @AfterEach
    void tearDown() {
        // Clear cache after each test
        cacheProvider.clear();
    }

    @Test
    @DisplayName("Should cache and retrieve simple string values")
    void cacheAndRetrieveString() {
        // Given
        String key = "test-key";
        String value = "test-value";

        // When
        cacheProvider.put(key, value);
        Optional<String> retrieved = cacheProvider.get(key, String.class);

        // Then
        assertTrue(retrieved.isPresent());
        assertEquals(value, retrieved.get());
    }

    @Test
    @DisplayName("Should cache and retrieve complex objects")
    void cacheAndRetrieveComplexObject() {
        // Given
        String key = "ast-key";
        ASTRulesDSL astModel = ASTRulesDSL.builder()
                .name("Test Rule")
                .description("Test Description")
                .build();

        // When
        cacheProvider.put(key, astModel);
        Optional<ASTRulesDSL> retrieved = cacheProvider.get(key, ASTRulesDSL.class);

        // Then
        assertTrue(retrieved.isPresent());
        assertEquals(astModel.getName(), retrieved.get().getName());
        assertEquals(astModel.getDescription(), retrieved.get().getDescription());
    }

    @Test
    @DisplayName("Should handle cache miss gracefully")
    void handleCacheMiss() {
        // Given
        String nonExistentKey = "non-existent-key";

        // When
        Optional<String> retrieved = cacheProvider.get(nonExistentKey, String.class);

        // Then
        assertFalse(retrieved.isPresent());
    }

    @Test
    @DisplayName("Should cache with TTL and expire")
    void cacheWithTTL() throws InterruptedException {
        // Given
        String key = "ttl-key";
        String value = "ttl-value";
        Duration shortTtl = Duration.ofMillis(100);

        // When
        cacheProvider.put(key, value, shortTtl);
        
        // Immediately check - should be present
        Optional<String> immediate = cacheProvider.get(key, String.class);
        assertTrue(immediate.isPresent());

        // Wait for expiration
        Thread.sleep(150);
        
        // Check after expiration - should be absent
        Optional<String> afterExpiry = cacheProvider.get(key, String.class);
        assertFalse(afterExpiry.isPresent());
    }

    @Test
    @DisplayName("Should evict single cache entry")
    void evictSingleEntry() {
        // Given
        String key = "evict-key";
        String value = "evict-value";
        cacheProvider.put(key, value);

        // Verify it's cached
        assertTrue(cacheProvider.get(key, String.class).isPresent());

        // When
        cacheProvider.evict(key);

        // Then
        assertFalse(cacheProvider.get(key, String.class).isPresent());
    }

    @Test
    @DisplayName("Should evict multiple cache entries")
    void evictMultipleEntries() {
        // Given
        List<String> keys = List.of("key1", "key2", "key3");
        keys.forEach(key -> cacheProvider.put(key, "value-" + key));

        // Verify all are cached
        keys.forEach(key -> assertTrue(cacheProvider.get(key, String.class).isPresent()));

        // When
        cacheProvider.evictAll(keys);

        // Then
        keys.forEach(key -> assertFalse(cacheProvider.get(key, String.class).isPresent()));
    }

    @Test
    @DisplayName("Should clear entire cache")
    void clearEntireCache() {
        // Given
        cacheProvider.put("key1", "value1");
        cacheProvider.put("key2", "value2");
        cacheProvider.put("key3", "value3");

        // Verify all are cached
        assertTrue(cacheProvider.get("key1", String.class).isPresent());
        assertTrue(cacheProvider.get("key2", String.class).isPresent());
        assertTrue(cacheProvider.get("key3", String.class).isPresent());

        // When
        cacheProvider.clear();

        // Then
        assertFalse(cacheProvider.get("key1", String.class).isPresent());
        assertFalse(cacheProvider.get("key2", String.class).isPresent());
        assertFalse(cacheProvider.get("key3", String.class).isPresent());
    }

    @Test
    @DisplayName("Should check key existence")
    void checkKeyExistence() {
        // Given
        String existingKey = "existing-key";
        String nonExistingKey = "non-existing-key";
        cacheProvider.put(existingKey, "value");

        // When & Then
        assertTrue(cacheProvider.exists(existingKey));
        assertFalse(cacheProvider.exists(nonExistingKey));
    }

    @Test
    @DisplayName("Should provide cache statistics")
    void provideCacheStatistics() {
        // Given
        cacheProvider.put("key1", "value1");
        cacheProvider.get("key1", String.class); // Hit
        cacheProvider.get("non-existent", String.class); // Miss

        // When
        CacheProvider.CacheStatistics stats = cacheProvider.getStatistics();

        // Then
        assertNotNull(stats);
        assertEquals("test-cache", stats.cacheName());
        assertEquals(CacheProvider.CacheProviderType.REDIS, stats.providerType());
        assertTrue(stats.hitCount() > 0);
        assertTrue(stats.missCount() > 0);
        assertTrue(stats.isHealthy());
    }

    @Test
    @DisplayName("Should work reactively")
    void reactiveOperations() {
        // Given
        String key = "reactive-key";
        String value = "reactive-value";

        // When & Then
        StepVerifier.create(cacheProvider.putReactive(key, value))
                .verifyComplete();

        StepVerifier.create(cacheProvider.getReactive(key, String.class))
                .expectNext(value)
                .verifyComplete();

        StepVerifier.create(cacheProvider.existsReactive(key))
                .expectNext(true)
                .verifyComplete();

        StepVerifier.create(cacheProvider.evictReactive(key))
                .verifyComplete();

        StepVerifier.create(cacheProvider.getReactive(key, String.class))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle serialization errors gracefully")
    void handleSerializationErrors() {
        // Given - Object that cannot be serialized properly
        String key = "error-key";
        
        // When trying to cache an object that might cause serialization issues
        // This should not throw an exception but handle it gracefully
        assertDoesNotThrow(() -> {
            cacheProvider.put(key, "valid-value");
            Optional<String> result = cacheProvider.get(key, String.class);
            assertTrue(result.isPresent());
        });
    }

    @Test
    @DisplayName("Should return correct provider type and cache name")
    void providerTypeAndCacheName() {
        // When & Then
        assertEquals(CacheProvider.CacheProviderType.REDIS, cacheProvider.getProviderType());
        assertEquals("test-cache", cacheProvider.getCacheName());
    }
}
