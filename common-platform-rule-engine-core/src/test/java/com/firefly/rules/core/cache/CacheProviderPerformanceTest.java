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
import com.firefly.rules.core.dsl.ast.model.ASTRulesDSL;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance comparison tests between Caffeine and Redis cache providers.
 * These tests help understand the performance characteristics of each provider.
 */
@Testcontainers
@DisplayName("Cache Provider Performance Tests")
class CacheProviderPerformanceTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--appendonly", "yes");

    private CacheProvider caffeineCacheProvider;
    private CacheProvider redisCacheProvider;
    private List<ASTRulesDSL> testData;

    @BeforeEach
    void setUp() {
        // Setup Caffeine cache provider
        Cache<String, Object> caffeineCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofMinutes(10))
                .recordStats()
                .build();
        caffeineCacheProvider = new CaffeineCacheProvider(caffeineCache, "performance-test");

        // Setup Redis cache provider
        String redisHost = redis.getHost();
        Integer redisPort = redis.getMappedPort(6379);
        
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(redisHost, redisPort);
        connectionFactory.afterPropertiesSet();

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        RedisSerializationContext<String, String> serializationContext = 
                RedisSerializationContext.<String, String>newSerializationContext()
                        .key(stringSerializer)
                        .value(stringSerializer)
                        .hashKey(stringSerializer)
                        .hashValue(stringSerializer)
                        .build();

        ReactiveRedisTemplate<String, String> redisTemplate = 
                new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
        
        redisCacheProvider = new RedisCacheProvider(
                redisTemplate, 
                new ObjectMapper(), 
                "performance-test", 
                Duration.ofMinutes(10)
        );

        // Prepare test data
        testData = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            testData.add(ASTRulesDSL.builder()
                    .name("Rule " + i)
                    .description("Performance test rule " + i)
                    .version("1.0." + i)
                    .build());
        }
    }

    @Test
    @DisplayName("Compare write performance between Caffeine and Redis")
    void compareWritePerformance() {
        // Test Caffeine write performance
        long caffeineStartTime = System.nanoTime();
        for (int i = 0; i < testData.size(); i++) {
            caffeineCacheProvider.put("key-" + i, testData.get(i));
        }
        long caffeineWriteTime = System.nanoTime() - caffeineStartTime;

        // Test Redis write performance
        long redisStartTime = System.nanoTime();
        for (int i = 0; i < testData.size(); i++) {
            redisCacheProvider.put("key-" + i, testData.get(i));
        }
        long redisWriteTime = System.nanoTime() - redisStartTime;

        // Log results
        System.out.printf("Caffeine write time: %.2f ms%n", caffeineWriteTime / 1_000_000.0);
        System.out.printf("Redis write time: %.2f ms%n", redisWriteTime / 1_000_000.0);
        System.out.printf("Caffeine is %.2fx faster for writes%n", (double) redisWriteTime / caffeineWriteTime);

        // Caffeine should be significantly faster for writes
        assertTrue(caffeineWriteTime < redisWriteTime, 
                "Caffeine should be faster for writes than Redis");
    }

    @Test
    @DisplayName("Compare read performance between Caffeine and Redis")
    void compareReadPerformance() {
        // Pre-populate both caches
        for (int i = 0; i < testData.size(); i++) {
            caffeineCacheProvider.put("key-" + i, testData.get(i));
            redisCacheProvider.put("key-" + i, testData.get(i));
        }

        // Test Caffeine read performance
        long caffeineStartTime = System.nanoTime();
        for (int i = 0; i < testData.size(); i++) {
            Optional<ASTRulesDSL> result = caffeineCacheProvider.get("key-" + i, ASTRulesDSL.class);
            assertTrue(result.isPresent());
        }
        long caffeineReadTime = System.nanoTime() - caffeineStartTime;

        // Test Redis read performance
        long redisStartTime = System.nanoTime();
        for (int i = 0; i < testData.size(); i++) {
            Optional<ASTRulesDSL> result = redisCacheProvider.get("key-" + i, ASTRulesDSL.class);
            assertTrue(result.isPresent());
        }
        long redisReadTime = System.nanoTime() - redisStartTime;

        // Log results
        System.out.printf("Caffeine read time: %.2f ms%n", caffeineReadTime / 1_000_000.0);
        System.out.printf("Redis read time: %.2f ms%n", redisReadTime / 1_000_000.0);
        System.out.printf("Caffeine is %.2fx faster for reads%n", (double) redisReadTime / caffeineReadTime);

        // Caffeine should be significantly faster for reads
        assertTrue(caffeineReadTime < redisReadTime, 
                "Caffeine should be faster for reads than Redis");
    }

    @Test
    @DisplayName("Compare bulk operations performance")
    void compareBulkOperations() {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            keys.add("bulk-key-" + i);
        }

        // Pre-populate caches
        for (int i = 0; i < keys.size(); i++) {
            caffeineCacheProvider.put(keys.get(i), testData.get(i));
            redisCacheProvider.put(keys.get(i), testData.get(i));
        }

        // Test Caffeine bulk eviction
        long caffeineStartTime = System.nanoTime();
        caffeineCacheProvider.evictAll(keys);
        long caffeineBulkTime = System.nanoTime() - caffeineStartTime;

        // Re-populate for Redis test
        for (int i = 0; i < keys.size(); i++) {
            redisCacheProvider.put(keys.get(i), testData.get(i));
        }

        // Test Redis bulk eviction
        long redisStartTime = System.nanoTime();
        redisCacheProvider.evictAll(keys);
        long redisBulkTime = System.nanoTime() - redisStartTime;

        // Log results
        System.out.printf("Caffeine bulk eviction time: %.2f ms%n", caffeineBulkTime / 1_000_000.0);
        System.out.printf("Redis bulk eviction time: %.2f ms%n", redisBulkTime / 1_000_000.0);

        // Both should complete successfully
        assertTrue(caffeineBulkTime > 0);
        assertTrue(redisBulkTime > 0);
    }

    @Test
    @DisplayName("Compare cache statistics accuracy")
    void compareCacheStatistics() {
        // Perform some operations to generate statistics
        for (int i = 0; i < 10; i++) {
            caffeineCacheProvider.put("stats-key-" + i, testData.get(i));
            redisCacheProvider.put("stats-key-" + i, testData.get(i));
        }

        // Generate hits and misses
        for (int i = 0; i < 10; i++) {
            caffeineCacheProvider.get("stats-key-" + i, ASTRulesDSL.class); // Hit
            redisCacheProvider.get("stats-key-" + i, ASTRulesDSL.class); // Hit
        }
        
        for (int i = 10; i < 15; i++) {
            caffeineCacheProvider.get("stats-key-" + i, ASTRulesDSL.class); // Miss
            redisCacheProvider.get("stats-key-" + i, ASTRulesDSL.class); // Miss
        }

        // Get statistics
        CacheProvider.CacheStatistics caffeineStats = caffeineCacheProvider.getStatistics();
        CacheProvider.CacheStatistics redisStats = redisCacheProvider.getStatistics();

        // Verify statistics
        assertNotNull(caffeineStats);
        assertNotNull(redisStats);
        
        System.out.printf("Caffeine - Hits: %d, Misses: %d, Hit Rate: %.2f%%%n", 
                caffeineStats.hitCount(), caffeineStats.missCount(), caffeineStats.hitRate() * 100);
        System.out.printf("Redis - Hits: %d, Misses: %d, Hit Rate: %.2f%%%n", 
                redisStats.hitCount(), redisStats.missCount(), redisStats.hitRate() * 100);

        // Both should have recorded hits and misses
        assertTrue(caffeineStats.hitCount() > 0);
        assertTrue(caffeineStats.missCount() > 0);
        assertTrue(redisStats.hitCount() > 0);
        assertTrue(redisStats.missCount() > 0);
        
        // Hit rates should be reasonable (around 66% for this test)
        assertTrue(caffeineStats.hitRate() > 0.5 && caffeineStats.hitRate() < 1.0);
        assertTrue(redisStats.hitRate() > 0.5 && redisStats.hitRate() < 1.0);
    }

    @Test
    @DisplayName("Test cache provider types and names")
    void testProviderTypesAndNames() {
        // Verify provider types
        assertEquals(CacheProvider.CacheProviderType.CAFFEINE, caffeineCacheProvider.getProviderType());
        assertEquals(CacheProvider.CacheProviderType.REDIS, redisCacheProvider.getProviderType());

        // Verify cache names
        assertEquals("performance-test", caffeineCacheProvider.getCacheName());
        assertEquals("performance-test", redisCacheProvider.getCacheName());

        // Verify provider descriptions
        assertEquals("Local in-memory cache using Caffeine", 
                CacheProvider.CacheProviderType.CAFFEINE.getDescription());
        assertEquals("Distributed cache using Redis", 
                CacheProvider.CacheProviderType.REDIS.getDescription());
    }

    @Test
    @DisplayName("Test concurrent access patterns")
    void testConcurrentAccess() throws InterruptedException {
        // This test simulates concurrent access patterns that might occur in production
        int numThreads = 5;
        int operationsPerThread = 20;
        
        // Test Caffeine concurrent access
        Thread[] caffeineThreads = new Thread[numThreads];
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            caffeineThreads[t] = new Thread(() -> {
                for (int i = 0; i < operationsPerThread; i++) {
                    String key = "concurrent-caffeine-" + threadId + "-" + i;
                    caffeineCacheProvider.put(key, testData.get(i % testData.size()));
                    caffeineCacheProvider.get(key, ASTRulesDSL.class);
                }
            });
        }

        long caffeineStartTime = System.nanoTime();
        for (Thread thread : caffeineThreads) {
            thread.start();
        }
        for (Thread thread : caffeineThreads) {
            thread.join();
        }
        long caffeineConcurrentTime = System.nanoTime() - caffeineStartTime;

        // Test Redis concurrent access
        Thread[] redisThreads = new Thread[numThreads];
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            redisThreads[t] = new Thread(() -> {
                for (int i = 0; i < operationsPerThread; i++) {
                    String key = "concurrent-redis-" + threadId + "-" + i;
                    redisCacheProvider.put(key, testData.get(i % testData.size()));
                    redisCacheProvider.get(key, ASTRulesDSL.class);
                }
            });
        }

        long redisStartTime = System.nanoTime();
        for (Thread thread : redisThreads) {
            thread.start();
        }
        for (Thread thread : redisThreads) {
            thread.join();
        }
        long redisConcurrentTime = System.nanoTime() - redisStartTime;

        // Log results
        System.out.printf("Caffeine concurrent time: %.2f ms%n", caffeineConcurrentTime / 1_000_000.0);
        System.out.printf("Redis concurrent time: %.2f ms%n", redisConcurrentTime / 1_000_000.0);

        // Both should complete successfully
        assertTrue(caffeineConcurrentTime > 0);
        assertTrue(redisConcurrentTime > 0);
    }
}
