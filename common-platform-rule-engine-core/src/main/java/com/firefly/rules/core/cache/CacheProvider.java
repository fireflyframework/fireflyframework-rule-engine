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

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Abstract cache provider interface for the Firefly Rule Engine.
 * Supports both local (Caffeine) and distributed (Redis) caching implementations.
 */
public interface CacheProvider {

    /**
     * Get a value from the cache.
     *
     * @param key The cache key
     * @param valueType The expected value type
     * @return Optional containing the cached value if found
     */
    <T> Optional<T> get(String key, Class<T> valueType);

    /**
     * Get a value from the cache reactively.
     *
     * @param key The cache key
     * @param valueType The expected value type
     * @return Mono containing the cached value if found
     */
    <T> Mono<T> getReactive(String key, Class<T> valueType);

    /**
     * Put a value into the cache.
     *
     * @param key The cache key
     * @param value The value to cache
     */
    void put(String key, Object value);

    /**
     * Put a value into the cache with expiration.
     *
     * @param key The cache key
     * @param value The value to cache
     * @param ttl Time to live
     */
    void put(String key, Object value, Duration ttl);

    /**
     * Put a value into the cache reactively.
     *
     * @param key The cache key
     * @param value The value to cache
     * @return Mono that completes when the value is cached
     */
    Mono<Void> putReactive(String key, Object value);

    /**
     * Put a value into the cache reactively with expiration.
     *
     * @param key The cache key
     * @param value The value to cache
     * @param ttl Time to live
     * @return Mono that completes when the value is cached
     */
    Mono<Void> putReactive(String key, Object value, Duration ttl);

    /**
     * Remove a value from the cache.
     *
     * @param key The cache key to remove
     */
    void evict(String key);

    /**
     * Remove a value from the cache reactively.
     *
     * @param key The cache key to remove
     * @return Mono that completes when the value is evicted
     */
    Mono<Void> evictReactive(String key);

    /**
     * Remove multiple values from the cache.
     *
     * @param keys The cache keys to remove
     */
    void evictAll(List<String> keys);

    /**
     * Remove multiple values from the cache reactively.
     *
     * @param keys The cache keys to remove
     * @return Mono that completes when all values are evicted
     */
    Mono<Void> evictAllReactive(List<String> keys);

    /**
     * Clear all entries from the cache.
     */
    void clear();

    /**
     * Clear all entries from the cache reactively.
     *
     * @return Mono that completes when the cache is cleared
     */
    Mono<Void> clearReactive();

    /**
     * Check if a key exists in the cache.
     *
     * @param key The cache key to check
     * @return true if the key exists, false otherwise
     */
    boolean exists(String key);

    /**
     * Check if a key exists in the cache reactively.
     *
     * @param key The cache key to check
     * @return Mono containing true if the key exists, false otherwise
     */
    Mono<Boolean> existsReactive(String key);

    /**
     * Get cache statistics.
     *
     * @return Cache statistics
     */
    CacheStatistics getStatistics();

    /**
     * Get cache statistics reactively.
     *
     * @return Mono containing cache statistics
     */
    Mono<CacheStatistics> getStatisticsReactive();

    /**
     * Get the cache provider type.
     *
     * @return The cache provider type
     */
    CacheProviderType getProviderType();

    /**
     * Get the cache name.
     *
     * @return The cache name
     */
    String getCacheName();

    /**
     * Cache statistics data transfer object.
     */
    record CacheStatistics(
            String cacheName,
            CacheProviderType providerType,
            long size,
            double hitRate,
            long hitCount,
            long missCount,
            long evictionCount,
            double averageLoadTime,
            boolean isHealthy,
            String healthStatus
    ) {}

    /**
     * Cache provider types.
     */
    enum CacheProviderType {
        CAFFEINE("Local in-memory cache using Caffeine"),
        REDIS("Distributed cache using Redis");

        private final String description;

        CacheProviderType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
