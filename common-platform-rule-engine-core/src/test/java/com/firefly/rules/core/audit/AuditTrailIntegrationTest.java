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

package com.firefly.rules.core.audit;

import com.firefly.rules.core.services.AuditTrailService;
import com.firefly.rules.core.services.impl.AuditTrailServiceImpl;
import com.firefly.rules.interfaces.dtos.audit.AuditEventType;
import com.firefly.rules.interfaces.dtos.audit.AuditTrailDTO;
import com.firefly.rules.interfaces.dtos.audit.AuditTrailFilterDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;
import org.junit.jupiter.api.Disabled;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the audit trail system.
 * Tests the complete audit trail functionality including service and repository layers.
 */
@Disabled("Requires full Spring Boot context - enable for integration testing")
@SpringBootTest(classes = {
    AuditTrailService.class,
    AuditTrailServiceImpl.class,
    AuditHelper.class
})
@ActiveProfiles("test")
class AuditTrailIntegrationTest {

    @Autowired
    private AuditTrailService auditTrailService;

    @Test
    void auditTrailLifecycle_ShouldWorkEndToEnd() {
        // Given
        UUID entityId = UUID.randomUUID();
        String ruleCode = "integration_test_rule_v1";
        String userId = "integration.test@company.com";

        // When - Record audit event
        StepVerifier.create(auditTrailService.recordAuditEvent(
                AuditEventType.RULE_DEFINITION_CREATE,
                entityId,
                ruleCode,
                userId,
                "POST",
                "/api/v1/rules/definitions",
                "{\"code\":\"integration_test_rule\"}",
                "{\"id\":\"" + entityId + "\"}",
                201,
                true,
                250L
        ))
                .assertNext(auditTrailDTO -> {
                    assertThat(auditTrailDTO).isNotNull();
                    assertThat(auditTrailDTO.getOperationType()).isEqualTo("RULE_DEFINITION_CREATE");
                    assertThat(auditTrailDTO.getEntityType()).isEqualTo("RULE_DEFINITION");
                    assertThat(auditTrailDTO.getEntityId()).isEqualTo(entityId);
                    assertThat(auditTrailDTO.getRuleCode()).isEqualTo(ruleCode);
                    assertThat(auditTrailDTO.getUserId()).isEqualTo(userId);
                    assertThat(auditTrailDTO.getSuccess()).isTrue();
                    assertThat(auditTrailDTO.getExecutionTimeMs()).isEqualTo(250L);
                })
                .verifyComplete();

        // Then - Retrieve audit trails with filter
        AuditTrailFilterDTO filterDTO = AuditTrailFilterDTO.builder()
                .operationType("RULE_DEFINITION_CREATE")
                .userId(userId)
                .page(0)
                .size(10)
                .build();

        StepVerifier.create(auditTrailService.getAuditTrails(filterDTO))
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response.getContent()).isNotEmpty();
                    assertThat(response.getContent().get(0).getRuleCode()).isEqualTo(ruleCode);
                    assertThat(response.getContent().get(0).getUserId()).isEqualTo(userId);
                })
                .verifyComplete();

        // And - Get recent audit trails for entity
        StepVerifier.create(auditTrailService.getRecentAuditTrailsForEntity(entityId, 5))
                .assertNext(auditTrails -> {
                    assertThat(auditTrails).isNotEmpty();
                    assertThat(auditTrails.get(0).getEntityId()).isEqualTo(entityId);
                    assertThat(auditTrails.get(0).getRuleCode()).isEqualTo(ruleCode);
                })
                .verifyComplete();
    }

    @Test
    void recordAuditEventWithMetadata_ShouldStoreMetadata() {
        // Given
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("evaluationType", "DIRECT");
        metadata.put("inputDataSize", 5);
        metadata.put("conditionResult", true);
        metadata.put("circuitBreakerTriggered", false);

        // When
        StepVerifier.create(auditTrailService.recordAuditEventWithMetadata(
                AuditEventType.RULE_EVALUATION_DIRECT,
                null, // entityId
                null, // ruleCode
                "test.user@company.com",
                "POST",
                "/api/v1/rules/evaluate/direct",
                "{\"inputData\":{\"creditScore\":750}}",
                "{\"success\":true,\"conditionResult\":true}",
                200,
                true,
                180L,
                metadata
        ))
                .assertNext(auditTrailDTO -> {
                    assertThat(auditTrailDTO).isNotNull();
                    assertThat(auditTrailDTO.getOperationType()).isEqualTo("RULE_EVALUATION_DIRECT");
                    assertThat(auditTrailDTO.getEntityType()).isEqualTo("RULE_EVALUATION");
                    assertThat(auditTrailDTO.getMetadata()).isNotNull();
                    assertThat(auditTrailDTO.getMetadata()).contains("evaluationType");
                    assertThat(auditTrailDTO.getMetadata()).contains("DIRECT");
                })
                .verifyComplete();
    }

    @Test
    void getAuditTrailStatistics_ShouldReturnCorrectStatistics() {
        // Given - Record some audit events first
        String userId = "stats.test@company.com";
        
        // Record successful operation
        auditTrailService.recordAuditEvent(
                AuditEventType.RULE_DEFINITION_CREATE,
                UUID.randomUUID(),
                "stats_test_rule_1",
                userId,
                "POST",
                "/api/v1/rules/definitions",
                "{}",
                "{}",
                201,
                true,
                100L
        ).block();

        // Record failed operation
        auditTrailService.recordAuditEvent(
                AuditEventType.RULE_DEFINITION_UPDATE,
                UUID.randomUUID(),
                "stats_test_rule_2",
                userId,
                "PUT",
                "/api/v1/rules/definitions/123",
                "{}",
                null,
                400,
                false,
                50L
        ).block();

        // When
        StepVerifier.create(auditTrailService.getAuditTrailStatistics())
                .assertNext(stats -> {
                    assertThat(stats).isNotNull();
                    assertThat(stats.get("totalCount")).isNotNull();
                    assertThat(stats.get("successCount")).isNotNull();
                    assertThat(stats.get("failureCount")).isNotNull();
                    assertThat(stats.get("successRate")).isNotNull();
                    assertThat(stats.get("operationCounts")).isNotNull();
                    
                    // Verify that our test data is included
                    Long totalCount = (Long) stats.get("totalCount");
                    assertThat(totalCount).isGreaterThanOrEqualTo(2L);
                })
                .verifyComplete();
    }

    @Test
    void filterAuditTrailsByDateRange_ShouldReturnCorrectResults() {
        // Given
        String userId = "daterange.test@company.com";
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startDate = now.minusHours(1);
        OffsetDateTime endDate = now.plusHours(1);

        // Record an audit event
        auditTrailService.recordAuditEvent(
                AuditEventType.RULE_EVALUATION_BY_CODE,
                null,
                "daterange_test_rule",
                userId,
                "POST",
                "/api/v1/rules/evaluate/by-code",
                "{}",
                "{}",
                200,
                true,
                120L
        ).block();

        // When
        AuditTrailFilterDTO filterDTO = AuditTrailFilterDTO.builder()
                .userId(userId)
                .startDate(startDate)
                .endDate(endDate)
                .page(0)
                .size(10)
                .build();

        StepVerifier.create(auditTrailService.getAuditTrails(filterDTO))
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response.getContent()).isNotEmpty();
                    
                    // Verify the audit trail is within the date range
                    AuditTrailDTO auditTrail = response.getContent().get(0);
                    assertThat(auditTrail.getUserId()).isEqualTo(userId);
                    assertThat(auditTrail.getCreatedAt()).isAfter(startDate);
                    assertThat(auditTrail.getCreatedAt()).isBefore(endDate);
                })
                .verifyComplete();
    }

    @Test
    void filterAuditTrailsByMultipleCriteria_ShouldReturnFilteredResults() {
        // Given
        String userId = "multicriteria.test@company.com";
        String ruleCode = "multicriteria_test_rule";
        
        // Record audit events with different criteria
        auditTrailService.recordAuditEvent(
                AuditEventType.RULE_DEFINITION_CREATE,
                UUID.randomUUID(),
                ruleCode,
                userId,
                "POST",
                "/api/v1/rules/definitions",
                "{}",
                "{}",
                201,
                true,
                150L
        ).block();

        auditTrailService.recordAuditEvent(
                AuditEventType.RULE_DEFINITION_UPDATE,
                UUID.randomUUID(),
                "different_rule",
                userId,
                "PUT",
                "/api/v1/rules/definitions/123",
                "{}",
                "{}",
                200,
                true,
                100L
        ).block();

        // When - Filter by operation type and rule code
        AuditTrailFilterDTO filterDTO = AuditTrailFilterDTO.builder()
                .operationType("RULE_DEFINITION_CREATE")
                .ruleCode(ruleCode)
                .userId(userId)
                .success(true)
                .page(0)
                .size(10)
                .build();

        StepVerifier.create(auditTrailService.getAuditTrails(filterDTO))
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response.getContent()).isNotEmpty();
                    
                    // Verify all results match the filter criteria
                    response.getContent().forEach(auditTrail -> {
                        assertThat(auditTrail.getOperationType()).isEqualTo("RULE_DEFINITION_CREATE");
                        assertThat(auditTrail.getRuleCode()).isEqualTo(ruleCode);
                        assertThat(auditTrail.getUserId()).isEqualTo(userId);
                        assertThat(auditTrail.getSuccess()).isTrue();
                    });
                })
                .verifyComplete();
    }
}
