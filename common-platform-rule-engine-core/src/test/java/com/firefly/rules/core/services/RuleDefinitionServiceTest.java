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

package com.firefly.rules.core.services;

import com.firefly.common.core.filters.FilterRequest;
import com.firefly.common.core.queries.PaginationResponse;
import com.firefly.rules.core.dsl.evaluation.RulesEvaluationEngine;
import com.firefly.rules.core.dsl.evaluation.RulesEvaluationResult;
import com.firefly.rules.core.mappers.RuleDefinitionMapper;
import com.firefly.rules.core.services.impl.RuleDefinitionServiceImpl;
import com.firefly.rules.core.validation.YamlDslValidator;
import com.firefly.rules.interfaces.dtos.crud.RuleDefinitionDTO;
import com.firefly.rules.interfaces.dtos.evaluation.RulesEvaluationResponseDTO;
import com.firefly.rules.interfaces.dtos.validation.ValidationResult;
import com.firefly.rules.models.entities.RuleDefinition;
import com.firefly.rules.models.repositories.RuleDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RuleDefinitionService
 */
@ExtendWith(MockitoExtension.class)
class RuleDefinitionServiceTest {

    @Mock
    private RuleDefinitionRepository ruleDefinitionRepository;

    @Mock
    private RuleDefinitionMapper ruleDefinitionMapper;

    @Mock
    private YamlDslValidator yamlDslValidator;

    @Mock
    private RulesEvaluationEngine rulesEvaluationEngine;

    private RuleDefinitionServiceImpl ruleDefinitionService;

    private RuleDefinitionDTO sampleRuleDefinitionDTO;
    private RuleDefinition sampleRuleDefinition;
    private ValidationResult validValidationResult;

    @BeforeEach
    void setUp() {
        ruleDefinitionService = new RuleDefinitionServiceImpl(
                ruleDefinitionRepository,
                ruleDefinitionMapper,
                yamlDslValidator,
                rulesEvaluationEngine
        );

        // Create sample data
        sampleRuleDefinitionDTO = RuleDefinitionDTO.builder()
                .id(UUID.randomUUID())
                .code("CREDIT_SCORING_BASIC")
                .name("Basic Credit Scoring Rule")
                .description("Evaluates basic credit eligibility")
                .yamlContent("""
                    name: "Basic Credit Scoring"
                    description: "Evaluates basic credit eligibility"
                    
                    inputs:
                      - creditScore
                      - annualIncome
                    
                    when:
                      - creditScore at_least MIN_CREDIT_SCORE
                      - annualIncome at_least MIN_ANNUAL_INCOME
                    
                    then:
                      - set eligible to true
                      - set tier to "STANDARD"
                    
                    else:
                      - set eligible to false
                      - set tier to "NONE"
                    
                    output:
                      eligible: boolean
                      tier: text
                    """)
                .version("1.0.0")
                .isActive(true)
                .tags("credit,scoring,basic")
                .createdAt(java.time.OffsetDateTime.now())
                .updatedAt(java.time.OffsetDateTime.now())
                .build();

        sampleRuleDefinition = RuleDefinition.builder()
                .id(sampleRuleDefinitionDTO.getId())
                .code(sampleRuleDefinitionDTO.getCode())
                .name(sampleRuleDefinitionDTO.getName())
                .description(sampleRuleDefinitionDTO.getDescription())
                .yamlContent(sampleRuleDefinitionDTO.getYamlContent())
                .version(sampleRuleDefinitionDTO.getVersion())
                .isActive(sampleRuleDefinitionDTO.getIsActive())
                .tags(sampleRuleDefinitionDTO.getTags())
                .createdAt(sampleRuleDefinitionDTO.getCreatedAt())
                .updatedAt(sampleRuleDefinitionDTO.getUpdatedAt())
                .build();

        validValidationResult = ValidationResult.builder()
                .status(ValidationResult.ValidationStatus.VALID)
                .summary(ValidationResult.ValidationSummary.builder()
                        .totalIssues(0)
                        .criticalErrors(0)
                        .errors(0)
                        .warnings(0)
                        .suggestions(0)
                        .qualityScore(100.0)
                        .build())
                .issues(ValidationResult.ValidationIssues.builder()
                        .syntax(List.of())
                        .naming(List.of())
                        .dependencies(List.of())
                        .logic(List.of())
                        .performance(List.of())
                        .bestPractices(List.of())
                        .build())
                .build();
    }

    @Test
    void testFilterRuleDefinitions_ShouldReturnPaginatedResults() {
        // Given
        FilterRequest<RuleDefinitionDTO> filterRequest = new FilterRequest<>();
        
        when(ruleDefinitionRepository.findAll()).thenReturn(Flux.just(sampleRuleDefinition));
        when(ruleDefinitionMapper.toDTO(any(RuleDefinition.class))).thenReturn(sampleRuleDefinitionDTO);

        // When
        Mono<PaginationResponse<RuleDefinitionDTO>> result = ruleDefinitionService.filterRuleDefinitions(filterRequest);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getContent()).hasSize(1);
                    assertThat(response.getContent().get(0).getCode()).isEqualTo("CREDIT_SCORING_BASIC");
                    assertThat(response.getTotalElements()).isEqualTo(1L);
                    assertThat(response.getTotalPages()).isEqualTo(1);
                    assertThat(response.getCurrentPage()).isEqualTo(0);
                })
                .verifyComplete();
    }

    @Test
    void testCreateRuleDefinition_WithValidYaml_ShouldSucceed() {
        // Given
        when(yamlDslValidator.validate(anyString())).thenReturn(validValidationResult);
        when(ruleDefinitionRepository.existsByCode(anyString())).thenReturn(Mono.just(false));
        when(ruleDefinitionMapper.toEntity(any(RuleDefinitionDTO.class))).thenReturn(sampleRuleDefinition);
        when(ruleDefinitionRepository.save(any(RuleDefinition.class))).thenReturn(Mono.just(sampleRuleDefinition));
        when(ruleDefinitionMapper.toDTO(any(RuleDefinition.class))).thenReturn(sampleRuleDefinitionDTO);

        // When
        Mono<RuleDefinitionDTO> result = ruleDefinitionService.createRuleDefinition(sampleRuleDefinitionDTO);

        // Then
        StepVerifier.create(result)
                .assertNext(dto -> {
                    assertThat(dto.getCode()).isEqualTo("CREDIT_SCORING_BASIC");
                    assertThat(dto.getName()).isEqualTo("Basic Credit Scoring Rule");
                    assertThat(dto.getIsActive()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void testCreateRuleDefinition_WithInvalidYaml_ShouldFail() {
        // Given
        ValidationResult invalidResult = ValidationResult.builder()
                .status(ValidationResult.ValidationStatus.ERROR)
                .summary(ValidationResult.ValidationSummary.builder()
                        .totalIssues(1)
                        .errors(1)
                        .build())
                .issues(ValidationResult.ValidationIssues.builder()
                    .syntax(java.util.Arrays.asList(
                        ValidationResult.ValidationIssue.builder()
                            .severity(ValidationResult.ValidationSeverity.ERROR)
                            .message("Invalid YAML syntax")
                            .build()
                    ))
                    .build())
                .build();

        when(yamlDslValidator.validate(anyString())).thenReturn(invalidResult);

        // When
        Mono<RuleDefinitionDTO> result = ruleDefinitionService.createRuleDefinition(sampleRuleDefinitionDTO);

        // Then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                    throwable instanceof IllegalArgumentException &&
                    throwable.getMessage().contains("Rule definition validation failed"))
                .verify();
    }



    @Test
    void testEvaluateRuleByCode_WithValidRule_ShouldReturnResult() {
        // Given
        String ruleCode = "CREDIT_SCORING_BASIC";
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("creditScore", 750);
        inputData.put("annualIncome", 75000);

        RulesEvaluationResult evaluationResult = RulesEvaluationResult.builder()
                .success(true)
                .conditionResult(true)
                .outputData(Map.of("eligible", true, "tier", "STANDARD"))
                .executionTimeMs(25L)
                .build();

        when(ruleDefinitionRepository.findByCode(ruleCode)).thenReturn(Mono.just(sampleRuleDefinition));
        when(rulesEvaluationEngine.evaluateRulesReactive(anyString(), any(Map.class))).thenReturn(Mono.just(evaluationResult));
        when(ruleDefinitionMapper.toDTO(any(RuleDefinition.class))).thenReturn(sampleRuleDefinitionDTO);

        // When
        Mono<com.firefly.rules.core.dsl.evaluation.RulesEvaluationResult> result = ruleDefinitionService.evaluateRuleByCode(ruleCode, inputData);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getConditionResult()).isTrue();
                    assertThat(response.getOutputData()).containsEntry("eligible", true);
                    assertThat(response.getOutputData()).containsEntry("tier", "STANDARD");
                })
                .verifyComplete();
    }

    @Test
    void testValidateYamlContent_WithValidYaml_ShouldReturnValidResult() {
        // Given
        String yamlContent = sampleRuleDefinitionDTO.getYamlContent();
        when(yamlDslValidator.validate(yamlContent)).thenReturn(validValidationResult);

        // When
        ValidationResult result = yamlDslValidator.validate(yamlContent);

        // Then
        assertThat(result.getStatus()).isEqualTo(ValidationResult.ValidationStatus.VALID);
        assertThat(result.isValid()).isTrue();
    }
}
