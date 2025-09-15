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
import com.firefly.rules.core.cache.CacheProvider;
import com.firefly.rules.core.cache.CaffeineCacheProvider;
import com.firefly.rules.core.cache.RedisCacheProvider;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Cache configuration for the Firefly Rule Engine.
 * Provides high-performance caching for parsed AST models and other frequently accessed data.
 * Supports both Caffeine (default) and Redis caching providers.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(CacheConfig.CacheProperties.class)
@Slf4j
public class CacheConfig {

    private final CacheProperties cacheProperties;
    private final ObjectMapper objectMapper;
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public CacheConfig(CacheProperties cacheProperties,
                      ObjectMapper objectMapper,
                      @Autowired(required = false) ReactiveRedisTemplate<String, String> redisTemplate) {
        this.cacheProperties = cacheProperties;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    // AST Cache Providers

    /**
     * AST Cache Provider - uses Redis if enabled, otherwise Caffeine
     */
    @Bean("astCacheProvider")
    @Primary
    public CacheProvider astCacheProvider() {
        if (cacheProperties.getProvider() == CacheProperties.CacheProviderType.REDIS && redisTemplate != null) {
            log.info("Configuring Redis AST cache provider");
            return new RedisCacheProvider(
                    redisTemplate,
                    objectMapper,
                    "ast",
                    cacheProperties.getAst().getExpireAfterWrite()
            );
        } else {
            if (cacheProperties.getProvider() == CacheProperties.CacheProviderType.REDIS) {
                log.warn("Redis cache provider requested but Redis template not available, falling back to Caffeine");
            }
            log.info("Configuring Caffeine AST cache provider");
            Cache<String, Object> caffeineCache = Caffeine.newBuilder()
                    .maximumSize(cacheProperties.getAst().getMaximumSize())
                    .expireAfterWrite(cacheProperties.getAst().getExpireAfterWrite())
                    .expireAfterAccess(cacheProperties.getAst().getExpireAfterAccess())
                    .recordStats()
                    .removalListener((key, value, cause) -> {
                        log.debug("AST cache entry removed: key={}, cause={}", key, cause);
                    })
                    .build();
            return new CaffeineCacheProvider(caffeineCache, "ast");
        }
    }

    /**
     * Legacy AST Cache bean for backward compatibility
     */
    @Bean("astCache")
    @ConditionalOnProperty(name = "firefly.rules.cache.provider", havingValue = "CAFFEINE", matchIfMissing = true)
    public Cache<String, Object> astCache() {
        return Caffeine.newBuilder()
                .maximumSize(cacheProperties.getAst().getMaximumSize())
                .expireAfterWrite(cacheProperties.getAst().getExpireAfterWrite())
                .expireAfterAccess(cacheProperties.getAst().getExpireAfterAccess())
                .recordStats()
                .removalListener((key, value, cause) -> {
                    log.debug("AST cache entry removed: key={}, cause={}", key, cause);
                })
                .build();
    }

    /**
     * Constants Cache Provider
     */
    @Bean("constantsCacheProvider")
    public CacheProvider constantsCacheProvider() {
        if (cacheProperties.getProvider() == CacheProperties.CacheProviderType.REDIS && redisTemplate != null) {
            return new RedisCacheProvider(
                    redisTemplate,
                    objectMapper,
                    "constants",
                    cacheProperties.getConstants().getExpireAfterWrite()
            );
        } else {
            Cache<String, Object> caffeineCache = Caffeine.newBuilder()
                    .maximumSize(cacheProperties.getConstants().getMaximumSize())
                    .expireAfterWrite(cacheProperties.getConstants().getExpireAfterWrite())
                    .expireAfterAccess(cacheProperties.getConstants().getExpireAfterAccess())
                    .recordStats()
                    .removalListener((key, value, cause) -> {
                        log.debug("Constants cache entry removed: key={}, cause={}", key, cause);
                    })
                    .build();
            return new CaffeineCacheProvider(caffeineCache, "constants");
        }
    }

    /**
     * Legacy Constants Cache bean for backward compatibility
     */
    @Bean("constantsCache")
    @ConditionalOnProperty(name = "firefly.rules.cache.provider", havingValue = "CAFFEINE", matchIfMissing = true)
    public Cache<String, Object> constantsCache() {
        return Caffeine.newBuilder()
                .maximumSize(cacheProperties.getConstants().getMaximumSize())
                .expireAfterWrite(cacheProperties.getConstants().getExpireAfterWrite())
                .expireAfterAccess(cacheProperties.getConstants().getExpireAfterAccess())
                .recordStats()
                .removalListener((key, value, cause) -> {
                    log.debug("Constants cache entry removed: key={}, cause={}", key, cause);
                })
                .build();
    }

    /**
     * Rule Definitions Cache for frequently accessed rule definitions.
     * Caches rule definition metadata to reduce database queries.
     */
    @Bean("ruleDefinitionsCache")
    public Cache<String, Object> ruleDefinitionsCache() {
        return Caffeine.newBuilder()
                .maximumSize(200) // Maximum number of cached rule definitions
                .expireAfterWrite(Duration.ofMinutes(10)) // Cache for 10 minutes
                .expireAfterAccess(Duration.ofMinutes(3)) // Evict if not accessed for 3 minutes
                .recordStats() // Enable statistics for monitoring
                .removalListener((key, value, cause) -> {
                    log.debug("Rule definitions cache entry removed: key={}, cause={}", key, cause);
                })
                .build();
    }

    /**
     * Validation Results Cache for DSL validation results.
     * Caches validation results for identical YAML content to speed up repeated validations.
     */
    @Bean("validationCache")
    public Cache<String, Object> validationCache() {
        return Caffeine.newBuilder()
                .maximumSize(100) // Maximum number of cached validation results
                .expireAfterWrite(Duration.ofMinutes(5)) // Cache for 5 minutes
                .recordStats() // Enable statistics for monitoring
                .removalListener((key, value, cause) -> {
                    log.debug("Validation cache entry removed: key={}, cause={}", key, cause);
                })
                .build();
    }

    /**
     * Rule definitions cache provider bean.
     */
    @Bean
    @Qualifier("ruleDefinitionsCacheProvider")
    public CacheProvider ruleDefinitionsCacheProvider() {
        if (cacheProperties.getProvider() == CacheProperties.CacheProviderType.REDIS && redisTemplate != null) {
            return new RedisCacheProvider(
                    redisTemplate,
                    objectMapper,
                    "rule-definitions",
                    cacheProperties.getRuleDefinitions().getExpireAfterWrite()
            );
        } else {
            return new CaffeineCacheProvider(ruleDefinitionsCache(), "rule-definitions");
        }
    }

    /**
     * Validation cache provider bean.
     */
    @Bean
    @Qualifier("validationCacheProvider")
    public CacheProvider validationCacheProvider() {
        if (cacheProperties.getProvider() == CacheProperties.CacheProviderType.REDIS && redisTemplate != null) {
            return new RedisCacheProvider(
                    redisTemplate,
                    objectMapper,
                    "validation",
                    cacheProperties.getValidation().getExpireAfterWrite()
            );
        } else {
            return new CaffeineCacheProvider(validationCache(), "validation");
        }
    }

    /**
     * Scheduled task to log cache statistics for monitoring and optimization.
     * Runs every 5 minutes to provide insights into cache performance.
     * Only logs Caffeine cache statistics when using Caffeine provider.
     */
    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    public void logCacheStatistics() {
        if (cacheProperties.getProvider() == CacheProperties.CacheProviderType.CAFFEINE) {
            try {
                logCacheStats("AST Cache", astCache().stats());
                logCacheStats("Constants Cache", constantsCache().stats());
                logCacheStats("Rule Definitions Cache", ruleDefinitionsCache().stats());
                logCacheStats("Validation Cache", validationCache().stats());
            } catch (Exception e) {
                log.debug("Could not log Caffeine cache statistics: {}", e.getMessage());
            }
        } else {
            log.info("Cache statistics logging is only available for Caffeine cache provider");
        }
    }

    private void logCacheStats(String cacheName, CacheStats stats) {
        log.info("{} Statistics - Hit Rate: {:.2f}%, Evictions: {}, Load Time: {:.2f}ms",
                cacheName,
                stats.hitRate() * 100,
                stats.evictionCount(),
                stats.averageLoadPenalty() / 1_000_000.0); // Convert nanoseconds to milliseconds
    }

    /**
     * Cache configuration properties for external configuration.
     */
    @ConfigurationProperties(prefix = "firefly.rules.cache")
    public static class CacheProperties {
        private CacheProviderType provider = CacheProviderType.CAFFEINE;
        private Ast ast = new Ast();
        private Constants constants = new Constants();
        private RuleDefinitions ruleDefinitions = new RuleDefinitions();
        private Validation validation = new Validation();

        // Getters and setters
        public CacheProviderType getProvider() { return provider; }
        public void setProvider(CacheProviderType provider) { this.provider = provider; }
        public Ast getAst() { return ast; }
        public void setAst(Ast ast) { this.ast = ast; }
        public Constants getConstants() { return constants; }
        public void setConstants(Constants constants) { this.constants = constants; }
        public RuleDefinitions getRuleDefinitions() { return ruleDefinitions; }
        public void setRuleDefinitions(RuleDefinitions ruleDefinitions) { this.ruleDefinitions = ruleDefinitions; }
        public Validation getValidation() { return validation; }
        public void setValidation(Validation validation) { this.validation = validation; }

        public enum CacheProviderType {
            CAFFEINE, REDIS
        }

        public static class Ast {
            private int maximumSize = 1000;
            private Duration expireAfterWrite = Duration.ofHours(2);
            private Duration expireAfterAccess = Duration.ofMinutes(30);

            // Getters and setters
            public int getMaximumSize() { return maximumSize; }
            public void setMaximumSize(int maximumSize) { this.maximumSize = maximumSize; }
            public Duration getExpireAfterWrite() { return expireAfterWrite; }
            public void setExpireAfterWrite(Duration expireAfterWrite) { this.expireAfterWrite = expireAfterWrite; }
            public Duration getExpireAfterAccess() { return expireAfterAccess; }
            public void setExpireAfterAccess(Duration expireAfterAccess) { this.expireAfterAccess = expireAfterAccess; }
        }

        public static class Constants {
            private int maximumSize = 500;
            private Duration expireAfterWrite = Duration.ofMinutes(15);
            private Duration expireAfterAccess = Duration.ofMinutes(5);

            // Getters and setters
            public int getMaximumSize() { return maximumSize; }
            public void setMaximumSize(int maximumSize) { this.maximumSize = maximumSize; }
            public Duration getExpireAfterWrite() { return expireAfterWrite; }
            public void setExpireAfterWrite(Duration expireAfterWrite) { this.expireAfterWrite = expireAfterWrite; }
            public Duration getExpireAfterAccess() { return expireAfterAccess; }
            public void setExpireAfterAccess(Duration expireAfterAccess) { this.expireAfterAccess = expireAfterAccess; }
        }

        public static class RuleDefinitions {
            private int maximumSize = 200;
            private Duration expireAfterWrite = Duration.ofMinutes(10);
            private Duration expireAfterAccess = Duration.ofMinutes(3);

            // Getters and setters
            public int getMaximumSize() { return maximumSize; }
            public void setMaximumSize(int maximumSize) { this.maximumSize = maximumSize; }
            public Duration getExpireAfterWrite() { return expireAfterWrite; }
            public void setExpireAfterWrite(Duration expireAfterWrite) { this.expireAfterWrite = expireAfterWrite; }
            public Duration getExpireAfterAccess() { return expireAfterAccess; }
            public void setExpireAfterAccess(Duration expireAfterAccess) { this.expireAfterAccess = expireAfterAccess; }
        }

        public static class Validation {
            private int maximumSize = 100;
            private Duration expireAfterWrite = Duration.ofMinutes(5);

            // Getters and setters
            public int getMaximumSize() { return maximumSize; }
            public void setMaximumSize(int maximumSize) { this.maximumSize = maximumSize; }
            public Duration getExpireAfterWrite() { return expireAfterWrite; }
            public void setExpireAfterWrite(Duration expireAfterWrite) { this.expireAfterWrite = expireAfterWrite; }
        }
    }
}
