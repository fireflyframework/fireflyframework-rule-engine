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

package com.firefly.rules.core.services.impl;

import com.firefly.rules.core.cache.CacheProvider;
import com.firefly.rules.core.dsl.ast.model.ASTRulesDSL;
import com.firefly.rules.core.services.CacheService;
import com.firefly.rules.core.services.ConstantService;

import com.firefly.rules.interfaces.dtos.crud.ConstantDTO;
import com.firefly.rules.interfaces.dtos.crud.RuleDefinitionDTO;
import com.firefly.rules.interfaces.dtos.validation.ValidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of the CacheService interface.
 * Provides high-performance caching operations using Caffeine cache.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CacheServiceImpl implements CacheService {

    @Qualifier("astCacheProvider")
    private final CacheProvider astCacheProvider;

    @Qualifier("constantsCacheProvider")
    private final CacheProvider constantsCacheProvider;

    @Qualifier("ruleDefinitionsCacheProvider")
    private final CacheProvider ruleDefinitionsCacheProvider;

    @Qualifier("validationCacheProvider")
    private final CacheProvider validationCacheProvider;
    
    private final ConstantService constantService;

    // AST Cache Operations

    @Override
    public Optional<ASTRulesDSL> getCachedAST(String cacheKey) {
        try {
            return astCacheProvider.get(cacheKey, ASTRulesDSL.class);
        } catch (Exception e) {
            log.warn("Error retrieving AST from cache for key: {}", cacheKey, e);
            return Optional.empty();
        }
    }

    @Override
    public void cacheAST(String cacheKey, ASTRulesDSL astModel) {
        try {
            astCacheProvider.put(cacheKey, astModel);
            log.debug("Cached AST model with key: {}", cacheKey);
        } catch (Exception e) {
            log.warn("Error caching AST model for key: {}", cacheKey, e);
        }
    }

    @Override
    public String generateCacheKey(String yamlContent) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(yamlContent.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            // Fallback to simple hash code
            return String.valueOf(yamlContent.hashCode());
        }
    }

    @Override
    public void invalidateAST(String cacheKey) {
        astCacheProvider.evict(cacheKey);
        log.debug("Invalidated AST cache entry for key: {}", cacheKey);
    }

    @Override
    public void clearASTCache() {
        astCacheProvider.clear();
        log.info("Cleared all AST cache entries");
    }

    // Constants Cache Operations

    @Override
    public Optional<ConstantDTO> getCachedConstant(String code) {
        try {
            return constantsCacheProvider.get(code, ConstantDTO.class);
        } catch (Exception e) {
            log.warn("Error retrieving constant from cache for code: {}", code, e);
            return Optional.empty();
        }
    }

    @Override
    public void cacheConstant(String code, ConstantDTO constant) {
        try {
            constantsCacheProvider.put(code, constant);
            log.debug("Cached constant with code: {}", code);
        } catch (Exception e) {
            log.warn("Error caching constant for code: {}", code, e);
        }
    }

    @Override
    public List<ConstantDTO> getCachedConstants(List<String> codes) {
        List<ConstantDTO> cachedConstants = new ArrayList<>();
        for (String code : codes) {
            getCachedConstant(code).ifPresent(cachedConstants::add);
        }
        return cachedConstants;
    }

    @Override
    public void cacheConstants(List<ConstantDTO> constants) {
        for (ConstantDTO constant : constants) {
            cacheConstant(constant.getCode(), constant);
        }
    }

    @Override
    public void invalidateConstant(String code) {
        constantsCacheProvider.evict(code);
        log.debug("Invalidated constants cache entry for code: {}", code);
    }

    @Override
    public void clearConstantsCache() {
        constantsCacheProvider.clear();
        log.info("Cleared all constants cache entries");
    }

    // Rule Definitions Cache Operations

    @Override
    public Optional<RuleDefinitionDTO> getCachedRuleDefinition(String code) {
        try {
            return ruleDefinitionsCacheProvider.get(code, RuleDefinitionDTO.class);
        } catch (Exception e) {
            log.warn("Error retrieving rule definition from cache for code: {}", code, e);
            return Optional.empty();
        }
    }

    @Override
    public void cacheRuleDefinition(String code, RuleDefinitionDTO ruleDefinition) {
        try {
            ruleDefinitionsCacheProvider.put(code, ruleDefinition);
            log.debug("Cached rule definition with code: {}", code);
        } catch (Exception e) {
            log.warn("Error caching rule definition for code: {}", code, e);
        }
    }

    @Override
    public void invalidateRuleDefinition(String code) {
        ruleDefinitionsCacheProvider.evict(code);
        log.debug("Invalidated rule definitions cache entry for code: {}", code);
    }

    @Override
    public void clearRuleDefinitionsCache() {
        ruleDefinitionsCacheProvider.clear();
        log.info("Cleared all rule definitions cache entries");
    }

    // Validation Cache Operations

    @Override
    public Optional<ValidationResult> getCachedValidationResult(String cacheKey) {
        try {
            return validationCacheProvider.get(cacheKey, ValidationResult.class);
        } catch (Exception e) {
            log.warn("Error retrieving validation result from cache for key: {}", cacheKey, e);
            return Optional.empty();
        }
    }

    @Override
    public void cacheValidationResult(String cacheKey, ValidationResult validationResult) {
        try {
            validationCacheProvider.put(cacheKey, validationResult);
            log.debug("Cached validation result with key: {}", cacheKey);
        } catch (Exception e) {
            log.warn("Error caching validation result for key: {}", cacheKey, e);
        }
    }

    @Override
    public void invalidateValidationResult(String cacheKey) {
        validationCacheProvider.evict(cacheKey);
        log.debug("Invalidated validation cache entry for key: {}", cacheKey);
    }

    @Override
    public void clearValidationCache() {
        validationCacheProvider.clear();
        log.info("Cleared all validation cache entries");
    }

    // Cache Management Operations

    @Override
    public Mono<CacheStatistics> getCacheStatistics() {
        return Mono.fromCallable(() -> {
            CacheProvider.CacheStatistics astStats = astCacheProvider.getStatistics();
            CacheProvider.CacheStatistics constantsStats = constantsCacheProvider.getStatistics();
            CacheProvider.CacheStatistics ruleDefinitionsStats = ruleDefinitionsCacheProvider.getStatistics();
            CacheProvider.CacheStatistics validationStats = validationCacheProvider.getStatistics();

            return new CacheStatistics(
                    createCacheStat(astStats),
                    createCacheStat(constantsStats),
                    createCacheStat(ruleDefinitionsStats),
                    createCacheStat(validationStats)
            );
        });
    }

    private CacheStat createCacheStat(CacheProvider.CacheStatistics stats) {
        return new CacheStat(
                stats.cacheName(),
                stats.size(),
                stats.hitRate(),
                stats.hitCount(),
                stats.missCount(),
                stats.evictionCount(),
                stats.averageLoadTime()
        );
    }

    @Override
    public CacheProviderInfo getCacheProviderInfo() {
        CacheProvider.CacheProviderType providerType = astCacheProvider.getProviderType();
        return new CacheProviderInfo(
                providerType,
                providerType.getDescription(),
                providerType == CacheProvider.CacheProviderType.REDIS,
                List.of(
                        astCacheProvider.getCacheName(),
                        constantsCacheProvider.getCacheName(),
                        ruleDefinitionsCacheProvider.getCacheName(),
                        validationCacheProvider.getCacheName()
                )
        );
    }

    @Override
    public void clearAllCaches() {
        clearASTCache();
        clearConstantsCache();
        clearRuleDefinitionsCache();
        clearValidationCache();
        log.info("Cleared all caches");
    }

    @Override
    public Mono<Void> warmUpCaches() {
        log.info("Starting cache warm-up process");
        
        // Warm up constants cache with frequently used constants
        return constantService.filterConstants(null)
                .map(response -> response.getContent())
                .doOnNext(this::cacheConstants)
                .then()
                .doOnSuccess(v -> log.info("Cache warm-up completed successfully"))
                .doOnError(e -> log.error("Cache warm-up failed", e));
    }
}
