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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Caffeine-based cache provider implementation.
 * Provides local in-memory caching using Caffeine cache.
 */
@RequiredArgsConstructor
@Slf4j
public class CaffeineCacheProvider implements CacheProvider {

    private final Cache<String, Object> cache;
    private final String cacheName;

    @Override
    public <T> Optional<T> get(String key, Class<T> valueType) {
        try {
            Object cached = cache.getIfPresent(key);
            if (cached != null && valueType.isInstance(cached)) {
                log.debug("Caffeine cache hit for key: {} in cache: {}", key, cacheName);
                return Optional.of(valueType.cast(cached));
            }
            log.debug("Caffeine cache miss for key: {} in cache: {}", key, cacheName);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Error retrieving from Caffeine cache for key: {} in cache: {}", key, cacheName, e);
            return Optional.empty();
        }
    }

    @Override
    public <T> Mono<T> getReactive(String key, Class<T> valueType) {
        return Mono.fromCallable(() -> get(key, valueType))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    @Override
    public void put(String key, Object value) {
        try {
            cache.put(key, value);
            log.debug("Cached value with key: {} in Caffeine cache: {}", key, cacheName);
        } catch (Exception e) {
            log.warn("Error caching value for key: {} in Caffeine cache: {}", key, cacheName, e);
        }
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        // Caffeine doesn't support per-entry TTL, so we use the cache's configured TTL
        // For per-entry TTL, consider using Redis
        put(key, value);
        log.debug("Cached value with key: {} in Caffeine cache: {} (TTL ignored, using cache configuration)", 
                key, cacheName);
    }

    @Override
    public Mono<Void> putReactive(String key, Object value) {
        return Mono.fromRunnable(() -> put(key, value));
    }

    @Override
    public Mono<Void> putReactive(String key, Object value, Duration ttl) {
        return Mono.fromRunnable(() -> put(key, value, ttl));
    }

    @Override
    public void evict(String key) {
        cache.invalidate(key);
        log.debug("Evicted key: {} from Caffeine cache: {}", key, cacheName);
    }

    @Override
    public Mono<Void> evictReactive(String key) {
        return Mono.fromRunnable(() -> evict(key));
    }

    @Override
    public void evictAll(List<String> keys) {
        cache.invalidateAll(keys);
        log.debug("Evicted {} keys from Caffeine cache: {}", keys.size(), cacheName);
    }

    @Override
    public Mono<Void> evictAllReactive(List<String> keys) {
        return Mono.fromRunnable(() -> evictAll(keys));
    }

    @Override
    public void clear() {
        cache.invalidateAll();
        log.info("Cleared all entries from Caffeine cache: {}", cacheName);
    }

    @Override
    public Mono<Void> clearReactive() {
        return Mono.fromRunnable(this::clear);
    }

    @Override
    public boolean exists(String key) {
        return cache.getIfPresent(key) != null;
    }

    @Override
    public Mono<Boolean> existsReactive(String key) {
        return Mono.fromCallable(() -> exists(key));
    }

    @Override
    public CacheStatistics getStatistics() {
        try {
            CacheStats stats = cache.stats();
            return new CacheStatistics(
                    cacheName,
                    CacheProviderType.CAFFEINE,
                    cache.estimatedSize(),
                    stats.hitRate(),
                    stats.hitCount(),
                    stats.missCount(),
                    stats.evictionCount(),
                    stats.averageLoadPenalty() / 1_000_000.0, // Convert nanoseconds to milliseconds
                    true,
                    "Healthy"
            );
        } catch (Exception e) {
            log.error("Error getting Caffeine cache statistics for cache: {}", cacheName, e);
            return new CacheStatistics(
                    cacheName,
                    CacheProviderType.CAFFEINE,
                    0,
                    0.0,
                    0,
                    0,
                    0,
                    0.0,
                    false,
                    "Error: " + e.getMessage()
            );
        }
    }

    @Override
    public Mono<CacheStatistics> getStatisticsReactive() {
        return Mono.fromCallable(this::getStatistics);
    }

    @Override
    public CacheProviderType getProviderType() {
        return CacheProviderType.CAFFEINE;
    }

    @Override
    public String getCacheName() {
        return cacheName;
    }
}
