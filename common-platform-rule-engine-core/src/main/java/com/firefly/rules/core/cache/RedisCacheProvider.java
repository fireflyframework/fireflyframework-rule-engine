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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis-based cache provider implementation.
 * Provides distributed caching using Redis with reactive operations.
 */
@RequiredArgsConstructor
@Slf4j
public class RedisCacheProvider implements CacheProvider {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final String cacheName;
    private final Duration defaultTtl;

    // Statistics tracking
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);
    private final AtomicLong evictionCount = new AtomicLong(0);

    @Override
    public <T> Optional<T> get(String key, Class<T> valueType) {
        try {
            String redisKey = buildRedisKey(key);
            String cached = redisTemplate.opsForValue().get(redisKey).block();
            
            if (cached != null) {
                T value = deserializeValue(cached, valueType);
                hitCount.incrementAndGet();
                log.debug("Redis cache hit for key: {} in cache: {}", key, cacheName);
                return Optional.of(value);
            } else {
                missCount.incrementAndGet();
                log.debug("Redis cache miss for key: {} in cache: {}", key, cacheName);
                return Optional.empty();
            }
        } catch (Exception e) {
            missCount.incrementAndGet();
            log.warn("Error retrieving from Redis cache for key: {} in cache: {}", key, cacheName, e);
            return Optional.empty();
        }
    }

    @Override
    public <T> Mono<T> getReactive(String key, Class<T> valueType) {
        String redisKey = buildRedisKey(key);
        return redisTemplate.opsForValue().get(redisKey)
                .map(cached -> {
                    try {
                        T value = deserializeValue(cached, valueType);
                        hitCount.incrementAndGet();
                        log.debug("Redis cache hit for key: {} in cache: {}", key, cacheName);
                        return value;
                    } catch (Exception e) {
                        log.warn("Error deserializing Redis cache value for key: {} in cache: {}", key, cacheName, e);
                        throw new RuntimeException("Cache deserialization error", e);
                    }
                })
                .doOnNext(value -> log.debug("Redis cache hit for key: {} in cache: {}", key, cacheName))
                .doOnError(error -> {
                    missCount.incrementAndGet();
                    log.warn("Error retrieving from Redis cache for key: {} in cache: {}", key, cacheName, error);
                })
                .onErrorResume(error -> {
                    missCount.incrementAndGet();
                    return Mono.empty();
                })
                .switchIfEmpty(Mono.fromRunnable(() -> {
                    missCount.incrementAndGet();
                    log.debug("Redis cache miss for key: {} in cache: {}", key, cacheName);
                }));
    }

    @Override
    public void put(String key, Object value) {
        put(key, value, defaultTtl);
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        try {
            String redisKey = buildRedisKey(key);
            String serializedValue = serializeValue(value);
            
            if (ttl != null && !ttl.isZero()) {
                redisTemplate.opsForValue().set(redisKey, serializedValue, ttl).block();
            } else {
                redisTemplate.opsForValue().set(redisKey, serializedValue).block();
            }
            
            log.debug("Cached value with key: {} in Redis cache: {} with TTL: {}", key, cacheName, ttl);
        } catch (Exception e) {
            log.warn("Error caching value for key: {} in Redis cache: {}", key, cacheName, e);
        }
    }

    @Override
    public Mono<Void> putReactive(String key, Object value) {
        return putReactive(key, value, defaultTtl);
    }

    @Override
    public Mono<Void> putReactive(String key, Object value, Duration ttl) {
        try {
            String redisKey = buildRedisKey(key);
            String serializedValue = serializeValue(value);
            
            Mono<Boolean> operation;
            if (ttl != null && !ttl.isZero()) {
                operation = redisTemplate.opsForValue().set(redisKey, serializedValue, ttl);
            } else {
                operation = redisTemplate.opsForValue().set(redisKey, serializedValue);
            }
            
            return operation
                    .doOnSuccess(result -> log.debug("Cached value with key: {} in Redis cache: {} with TTL: {}", 
                            key, cacheName, ttl))
                    .doOnError(error -> log.warn("Error caching value for key: {} in Redis cache: {}", 
                            key, cacheName, error))
                    .then();
        } catch (Exception e) {
            log.warn("Error serializing value for key: {} in Redis cache: {}", key, cacheName, e);
            return Mono.error(e);
        }
    }

    @Override
    public void evict(String key) {
        try {
            String redisKey = buildRedisKey(key);
            Boolean deleted = redisTemplate.opsForValue().delete(redisKey).block();
            if (Boolean.TRUE.equals(deleted)) {
                evictionCount.incrementAndGet();
            }
            log.debug("Evicted key: {} from Redis cache: {}", key, cacheName);
        } catch (Exception e) {
            log.warn("Error evicting key: {} from Redis cache: {}", key, cacheName, e);
        }
    }

    @Override
    public Mono<Void> evictReactive(String key) {
        String redisKey = buildRedisKey(key);
        return redisTemplate.opsForValue().delete(redisKey)
                .doOnNext(deleted -> {
                    if (Boolean.TRUE.equals(deleted)) {
                        evictionCount.incrementAndGet();
                    }
                })
                .doOnSuccess(deleted -> log.debug("Evicted key: {} from Redis cache: {}", key, cacheName))
                .doOnError(error -> log.warn("Error evicting key: {} from Redis cache: {}", key, cacheName, error))
                .then();
    }

    @Override
    public void evictAll(List<String> keys) {
        try {
            List<String> redisKeys = keys.stream().map(this::buildRedisKey).toList();
            Long deleted = redisTemplate.delete(redisKeys.toArray(new String[0])).block();
            if (deleted != null && deleted > 0) {
                evictionCount.addAndGet(deleted);
            }
            log.debug("Evicted {} keys from Redis cache: {}", keys.size(), cacheName);
        } catch (Exception e) {
            log.warn("Error evicting keys from Redis cache: {}", cacheName, e);
        }
    }

    @Override
    public Mono<Void> evictAllReactive(List<String> keys) {
        List<String> redisKeys = keys.stream().map(this::buildRedisKey).toList();
        return redisTemplate.delete(redisKeys.toArray(new String[0]))
                .doOnNext(deleted -> {
                    if (deleted > 0) {
                        evictionCount.addAndGet(deleted);
                    }
                })
                .doOnSuccess(deleted -> log.debug("Evicted {} keys from Redis cache: {}", keys.size(), cacheName))
                .doOnError(error -> log.warn("Error evicting keys from Redis cache: {}", cacheName, error))
                .then();
    }

    @Override
    public void clear() {
        try {
            String pattern = buildRedisKey("*");
            Flux<String> keys = redisTemplate.scan();
            keys.filter(key -> key.startsWith(buildRedisKey("")))
                    .collectList()
                    .flatMap(keyList -> {
                        if (!keyList.isEmpty()) {
                            return redisTemplate.delete(keyList.toArray(new String[0]));
                        }
                        return Mono.just(0L);
                    })
                    .doOnNext(deleted -> {
                        if (deleted > 0) {
                            evictionCount.addAndGet(deleted);
                        }
                    })
                    .block();
            log.info("Cleared all entries from Redis cache: {}", cacheName);
        } catch (Exception e) {
            log.warn("Error clearing Redis cache: {}", cacheName, e);
        }
    }

    @Override
    public Mono<Void> clearReactive() {
        return redisTemplate.scan()
                .filter(key -> key.startsWith(buildRedisKey("")))
                .collectList()
                .flatMap(keyList -> {
                    if (!keyList.isEmpty()) {
                        return redisTemplate.delete(keyList.toArray(new String[0]));
                    }
                    return Mono.just(0L);
                })
                .doOnNext(deleted -> {
                    if (deleted > 0) {
                        evictionCount.addAndGet(deleted);
                    }
                })
                .doOnSuccess(deleted -> log.info("Cleared all entries from Redis cache: {}", cacheName))
                .doOnError(error -> log.warn("Error clearing Redis cache: {}", cacheName, error))
                .then();
    }

    @Override
    public boolean exists(String key) {
        try {
            String redisKey = buildRedisKey(key);
            Boolean exists = redisTemplate.hasKey(redisKey).block();
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.warn("Error checking key existence in Redis cache for key: {} in cache: {}", key, cacheName, e);
            return false;
        }
    }

    @Override
    public Mono<Boolean> existsReactive(String key) {
        String redisKey = buildRedisKey(key);
        return redisTemplate.hasKey(redisKey)
                .doOnError(error -> log.warn("Error checking key existence in Redis cache for key: {} in cache: {}", 
                        key, cacheName, error))
                .onErrorReturn(false);
    }

    @Override
    public CacheStatistics getStatistics() {
        try {
            // Redis doesn't provide built-in hit/miss statistics like Caffeine
            // We track our own statistics
            long hits = hitCount.get();
            long misses = missCount.get();
            long total = hits + misses;
            double hitRate = total > 0 ? (double) hits / total : 0.0;
            
            // Get approximate size by scanning keys (expensive operation, use sparingly)
            long size = redisTemplate.scan()
                    .filter(key -> key.startsWith(buildRedisKey("")))
                    .count()
                    .block(Duration.ofSeconds(5));
            
            return new CacheStatistics(
                    cacheName,
                    CacheProviderType.REDIS,
                    size,
                    hitRate,
                    hits,
                    misses,
                    evictionCount.get(),
                    0.0, // Redis doesn't track load time
                    true,
                    "Healthy"
            );
        } catch (Exception e) {
            log.error("Error getting Redis cache statistics for cache: {}", cacheName, e);
            return new CacheStatistics(
                    cacheName,
                    CacheProviderType.REDIS,
                    0,
                    0.0,
                    hitCount.get(),
                    missCount.get(),
                    evictionCount.get(),
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
        return CacheProviderType.REDIS;
    }

    @Override
    public String getCacheName() {
        return cacheName;
    }

    private String buildRedisKey(String key) {
        return "firefly:rules:cache:" + cacheName + ":" + key;
    }

    private String serializeValue(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
    }

    private <T> T deserializeValue(String serialized, Class<T> valueType) throws JsonProcessingException {
        // Create a copy of ObjectMapper with FAIL_ON_UNKNOWN_PROPERTIES disabled for better compatibility
        ObjectMapper mapper = objectMapper.copy()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper.readValue(serialized, valueType);
    }
}
