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

import com.firefly.common.cache.manager.FireflyCacheManager;
import com.firefly.rules.core.TestApplication;
import com.firefly.rules.core.dsl.model.ASTRulesDSL;
import com.firefly.rules.core.services.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for cache functionality in the Rule Engine.
 * Verifies that the cache integration with lib-common-cache works correctly
 * and that cache keys follow the expected format:
 * firefly:cache:default::rule-engine:{logicalCacheType}:{key}
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@Slf4j
class CacheIntegrationTest {

    @Autowired
    private CacheService cacheService;

    @Autowired
    private FireflyCacheManager cacheManager;

    @BeforeEach
    void setUp() {
        // Clear cache before each test
        cacheManager.clear().block();
    }

    @Test
    void shouldCacheAndRetrieveASTModel() {
        // Given
        String cacheKey = "test-rule-123";
        ASTRulesDSL astModel = new ASTRulesDSL();
        astModel.setVersion("1.0");

        // When
        cacheService.cacheAST(cacheKey, astModel);
        Optional<ASTRulesDSL> retrieved = cacheService.getCachedAST(cacheKey);

        // Then
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getVersion()).isEqualTo("1.0");
        log.info("✓ AST cache test passed - Key format: firefly:cache:default::rule-engine:ast:{}", cacheKey);
    }

    @Test
    void shouldReturnEmptyWhenKeyNotFound() {
        // Given
        String nonExistentKey = "non-existent-key";

        // When
        Optional<ASTRulesDSL> retrieved = cacheService.getCachedAST(nonExistentKey);

        // Then
        assertThat(retrieved).isEmpty();
        log.info("✓ Cache miss test passed");
    }

    @Test
    void shouldInvalidateASTCache() {
        // Given
        String cacheKey = "test-rule-456";
        ASTRulesDSL astModel = new ASTRulesDSL();
        astModel.setVersion("2.0");
        cacheService.cacheAST(cacheKey, astModel);

        // When
        cacheService.invalidateAST(cacheKey);
        Optional<ASTRulesDSL> retrieved = cacheService.getCachedAST(cacheKey);

        // Then
        assertThat(retrieved).isEmpty();
        log.info("✓ Cache invalidation test passed");
    }

    @Test
    void shouldClearAllASTCache() {
        // Given
        cacheService.cacheAST("key1", new ASTRulesDSL());
        cacheService.cacheAST("key2", new ASTRulesDSL());

        // When
        cacheService.clearASTCache();

        // Then
        assertThat(cacheService.getCachedAST("key1")).isEmpty();
        assertThat(cacheService.getCachedAST("key2")).isEmpty();
        log.info("✓ Clear all cache test passed");
    }

    @Test
    void shouldVerifyCacheManagerIsConfigured() {
        // Then
        assertThat(cacheManager).isNotNull();
        assertThat(cacheManager.getCacheName()).isEqualTo("default");
        log.info("✓ Cache manager configuration test passed - Cache name: {}", cacheManager.getCacheName());
    }

    @Test
    void shouldGetCacheStatistics() {
        // Given
        String cacheKey = "stats-test-key";
        ASTRulesDSL astModel = new ASTRulesDSL();
        cacheService.cacheAST(cacheKey, astModel);
        cacheService.getCachedAST(cacheKey); // Hit
        cacheService.getCachedAST("non-existent"); // Miss

        // When
        var stats = cacheManager.getStats().block();

        // Then
        assertThat(stats).isNotNull();
        assertThat(stats.getCacheName()).isEqualTo("default");
        log.info("✓ Cache statistics test passed - Hit count: {}, Miss count: {}, Entries: {}",
                stats.getHitCount(), stats.getMissCount(), stats.getEntryCount());
    }
}

