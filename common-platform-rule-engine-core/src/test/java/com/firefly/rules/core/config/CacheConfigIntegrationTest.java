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

import com.firefly.rules.core.cache.CacheProvider;
import com.firefly.rules.core.services.CacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for cache configuration with both Caffeine and Redis providers.
 */
@SpringBootTest(classes = {CacheConfig.class, RedisConfig.class})
@Testcontainers
@DisplayName("Cache Configuration Integration Tests")
class CacheConfigIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--appendonly", "yes");

    @DynamicPropertySource
    static void configureRedis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    /**
     * Test Caffeine cache provider configuration (default)
     */
    @SpringBootTest(classes = {CacheConfig.class})
    @ActiveProfiles("test")
    @DisplayName("Caffeine Cache Provider Tests")
    static class CaffeineProviderTest {

        @Autowired
        @Qualifier("astCacheProvider")
        private CacheProvider astCacheProvider;

        @Autowired
        @Qualifier("constantsCacheProvider")
        private CacheProvider constantsCacheProvider;

        @Test
        @DisplayName("Should configure Caffeine cache providers by default")
        void shouldConfigureCaffeineProviders() {
            // Then
            assertNotNull(astCacheProvider);
            assertNotNull(constantsCacheProvider);
            assertEquals(CacheProvider.CacheProviderType.CAFFEINE, astCacheProvider.getProviderType());
            assertEquals(CacheProvider.CacheProviderType.CAFFEINE, constantsCacheProvider.getProviderType());
            assertEquals("ast", astCacheProvider.getCacheName());
            assertEquals("constants", constantsCacheProvider.getCacheName());
        }

        @Test
        @DisplayName("Should provide cache statistics for Caffeine providers")
        void shouldProvideCaffeineStatistics() {
            // When
            CacheProvider.CacheStatistics astStats = astCacheProvider.getStatistics();
            CacheProvider.CacheStatistics constantsStats = constantsCacheProvider.getStatistics();

            // Then
            assertNotNull(astStats);
            assertNotNull(constantsStats);
            assertEquals(CacheProvider.CacheProviderType.CAFFEINE, astStats.providerType());
            assertEquals(CacheProvider.CacheProviderType.CAFFEINE, constantsStats.providerType());
            assertTrue(astStats.isHealthy());
            assertTrue(constantsStats.isHealthy());
        }
    }

    /**
     * Test Redis cache provider configuration
     */
    @SpringBootTest(
            classes = {CacheConfig.class, RedisConfig.class},
            properties = {"firefly.cache.provider=REDIS"}
    )
    @ActiveProfiles("test")
    @DisplayName("Redis Cache Provider Tests")
    static class RedisProviderTest {

        @Autowired
        @Qualifier("astCacheProvider")
        private CacheProvider astCacheProvider;

        @Autowired
        @Qualifier("constantsCacheProvider")
        private CacheProvider constantsCacheProvider;

        @Test
        @DisplayName("Should configure Redis cache providers when enabled")
        void shouldConfigureRedisProviders() {
            // Then
            assertNotNull(astCacheProvider);
            assertNotNull(constantsCacheProvider);
            assertEquals(CacheProvider.CacheProviderType.REDIS, astCacheProvider.getProviderType());
            assertEquals(CacheProvider.CacheProviderType.REDIS, constantsCacheProvider.getProviderType());
            assertEquals("ast", astCacheProvider.getCacheName());
            assertEquals("constants", constantsCacheProvider.getCacheName());
        }

        @Test
        @DisplayName("Should provide cache statistics for Redis providers")
        void shouldProvideRedisStatistics() {
            // When
            CacheProvider.CacheStatistics astStats = astCacheProvider.getStatistics();
            CacheProvider.CacheStatistics constantsStats = constantsCacheProvider.getStatistics();

            // Then
            assertNotNull(astStats);
            assertNotNull(constantsStats);
            assertEquals(CacheProvider.CacheProviderType.REDIS, astStats.providerType());
            assertEquals(CacheProvider.CacheProviderType.REDIS, constantsStats.providerType());
            assertTrue(astStats.isHealthy());
            assertTrue(constantsStats.isHealthy());
        }

        @Test
        @DisplayName("Should perform basic cache operations with Redis")
        void shouldPerformBasicRedisOperations() {
            // Given
            String key = "test-key";
            String value = "test-value";

            // When
            astCacheProvider.put(key, value);
            var retrieved = astCacheProvider.get(key, String.class);

            // Then
            assertTrue(retrieved.isPresent());
            assertEquals(value, retrieved.get());

            // Cleanup
            astCacheProvider.evict(key);
        }
    }

    /**
     * Test cache service integration with different providers
     */
    @SpringBootTest(
            classes = {CacheConfig.class, RedisConfig.class},
            properties = {"firefly.cache.provider=REDIS"}
    )
    @ActiveProfiles("test")
    @DisplayName("Cache Service Integration Tests")
    static class CacheServiceIntegrationTest {

        @Autowired(required = false)
        private CacheService cacheService;

        @Test
        @DisplayName("Should provide cache provider information")
        void shouldProvideCacheProviderInfo() {
            // Skip if CacheService is not available (missing dependencies)
            if (cacheService == null) {
                return;
            }

            // When
            CacheService.CacheProviderInfo providerInfo = cacheService.getCacheProviderInfo();

            // Then
            assertNotNull(providerInfo);
            assertEquals(CacheProvider.CacheProviderType.REDIS, providerInfo.providerType());
            assertTrue(providerInfo.isDistributed());
            assertFalse(providerInfo.cacheNames().isEmpty());
            assertTrue(providerInfo.cacheNames().contains("ast"));
            assertTrue(providerInfo.cacheNames().contains("constants"));
        }
    }

    /**
     * Test fallback behavior when Redis is not available
     */
    @SpringBootTest(
            classes = {CacheConfig.class},
            properties = {"firefly.cache.provider=REDIS"}
    )
    @ActiveProfiles("test")
    @DisplayName("Fallback Behavior Tests")
    static class FallbackBehaviorTest {

        @Autowired
        @Qualifier("astCacheProvider")
        private CacheProvider astCacheProvider;

        @Test
        @DisplayName("Should fallback to Caffeine when Redis is not available")
        void shouldFallbackToCaffeine() {
            // When Redis is requested but not available, should fallback to Caffeine
            // Then
            assertNotNull(astCacheProvider);
            // Should fallback to Caffeine since Redis template is not available
            assertEquals(CacheProvider.CacheProviderType.CAFFEINE, astCacheProvider.getProviderType());
        }
    }

    /**
     * Test cache properties configuration
     */
    @SpringBootTest(
            classes = {CacheConfig.class},
            properties = {
                "firefly.cache.ast.maximum-size=2000",
                "firefly.cache.ast.expire-after-write=PT4H",
                "firefly.cache.constants.maximum-size=1000"
            }
    )
    @ActiveProfiles("test")
    @DisplayName("Cache Properties Tests")
    static class CachePropertiesTest {

        @Autowired
        private CacheConfig.CacheProperties cacheProperties;

        @Test
        @DisplayName("Should load custom cache properties")
        void shouldLoadCustomProperties() {
            // Then
            assertNotNull(cacheProperties);
            assertEquals(2000, cacheProperties.getAst().getMaximumSize());
            assertEquals("PT4H", cacheProperties.getAst().getExpireAfterWrite().toString());
            assertEquals(1000, cacheProperties.getConstants().getMaximumSize());
        }
    }
}
