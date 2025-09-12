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

import com.firefly.common.core.filters.FilterRequest;
import com.firefly.common.core.queries.PaginationResponse;
import com.firefly.rules.core.dsl.evaluation.RulesEvaluationEngine;
import com.firefly.rules.core.dsl.evaluation.RulesEvaluationResult;
import com.firefly.rules.core.mappers.RuleDefinitionMapper;
import com.firefly.rules.core.services.RuleDefinitionService;
import com.firefly.rules.core.validation.YamlDslValidator;
import com.firefly.rules.interfaces.dtos.crud.RuleDefinitionDTO;
import com.firefly.rules.interfaces.dtos.validation.ValidationResult;
import com.firefly.rules.models.entities.RuleDefinition;
import com.firefly.rules.models.repositories.RuleDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Implementation of RuleDefinitionService.
 * Provides CRUD operations for YAML DSL rule definitions with validation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleDefinitionServiceImpl implements RuleDefinitionService {

    private final RuleDefinitionRepository ruleDefinitionRepository;
    private final RuleDefinitionMapper ruleDefinitionMapper;
    private final YamlDslValidator yamlDslValidator;
    private final RulesEvaluationEngine rulesEvaluationEngine;

    @Override
    public Mono<PaginationResponse<RuleDefinitionDTO>> filterRuleDefinitions(
            FilterRequest<RuleDefinitionDTO> filterRequest) {
        log.info("Filtering rule definitions with criteria: {}", filterRequest);
        
        // For now, return all rule definitions
        // TODO: Implement proper filtering based on filterRequest criteria
        return ruleDefinitionRepository.findAll()
                .map(ruleDefinitionMapper::toDTO)
                .collectList()
                .map(list -> PaginationResponse.<RuleDefinitionDTO>builder()
                        .content(list)
                        .totalElements((long) list.size())
                        .totalPages(1)
                        .currentPage(0)
                        .build());
    }

    @Override
    public Mono<RuleDefinitionDTO> createRuleDefinition(RuleDefinitionDTO ruleDefinitionDTO) {
        log.info("Creating rule definition with code: {}", ruleDefinitionDTO.getCode());
        
        return validateRuleDefinition(ruleDefinitionDTO.getYamlContent())
                .flatMap(validationResult -> {
                    if (!validationResult.isValid()) {
                        return Mono.error(new IllegalArgumentException(
                                "Rule definition validation failed: " + validationResult.getErrorSummary()));
                    }
                    
                    // Check if code already exists
                    return ruleDefinitionRepository.existsByCode(ruleDefinitionDTO.getCode())
                            .flatMap(exists -> {
                                if (exists) {
                                    return Mono.error(new IllegalArgumentException(
                                            "Rule definition with code '" + ruleDefinitionDTO.getCode() + "' already exists"));
                                }
                                
                                RuleDefinition entity = ruleDefinitionMapper.toEntity(ruleDefinitionDTO);
                                entity.setId(UUID.randomUUID());
                                entity.setCreatedAt(OffsetDateTime.now());
                                entity.setUpdatedAt(OffsetDateTime.now());
                                
                                return ruleDefinitionRepository.save(entity)
                                        .map(ruleDefinitionMapper::toDTO);
                            });
                });
    }

    @Override
    public Mono<RuleDefinitionDTO> updateRuleDefinition(UUID ruleDefinitionId, RuleDefinitionDTO ruleDefinitionDTO) {
        log.info("Updating rule definition with ID: {}", ruleDefinitionId);
        
        return validateRuleDefinition(ruleDefinitionDTO.getYamlContent())
                .flatMap(validationResult -> {
                    if (!validationResult.isValid()) {
                        return Mono.error(new IllegalArgumentException(
                                "Rule definition validation failed: " + validationResult.getErrorSummary()));
                    }
                    
                    return ruleDefinitionRepository.findById(ruleDefinitionId)
                            .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                    "Rule definition not found with ID: " + ruleDefinitionId)))
                            .flatMap(existingEntity -> {
                                // Update entity with new data
                                RuleDefinition updatedEntity = ruleDefinitionMapper.toEntity(ruleDefinitionDTO);
                                updatedEntity.setId(existingEntity.getId());
                                updatedEntity.setCreatedAt(existingEntity.getCreatedAt());
                                updatedEntity.setCreatedBy(existingEntity.getCreatedBy());
                                updatedEntity.setUpdatedAt(OffsetDateTime.now());
                                
                                return ruleDefinitionRepository.save(updatedEntity)
                                        .map(ruleDefinitionMapper::toDTO);
                            });
                });
    }

    @Override
    public Mono<Void> deleteRuleDefinition(UUID ruleDefinitionId) {
        log.info("Deleting rule definition with ID: {}", ruleDefinitionId);
        
        return ruleDefinitionRepository.findById(ruleDefinitionId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Rule definition not found with ID: " + ruleDefinitionId)))
                .flatMap(entity -> ruleDefinitionRepository.delete(entity));
    }

    @Override
    public Mono<RuleDefinitionDTO> getRuleDefinitionById(UUID ruleDefinitionId) {
        log.info("Getting rule definition by ID: {}", ruleDefinitionId);
        
        return ruleDefinitionRepository.findById(ruleDefinitionId)
                .map(ruleDefinitionMapper::toDTO);
    }

    @Override
    public Mono<RuleDefinitionDTO> getRuleDefinitionByCode(String code) {
        log.info("Getting rule definition by code: {}", code);
        
        return ruleDefinitionRepository.findByCode(code)
                .map(ruleDefinitionMapper::toDTO);
    }

    @Override
    public Mono<ValidationResult> validateRuleDefinition(String yamlContent) {
        log.info("Validating YAML DSL rule definition");

        try {
            ValidationResult result = yamlDslValidator.validate(yamlContent);
            log.info("Validation completed. Valid: {}, Errors: {}",
                    result.isValid(), result.getErrors().size());
            return Mono.just(result);
        } catch (Exception e) {
            log.error("Error during rule definition validation", e);
            return Mono.error(new IllegalArgumentException("Validation failed: " + e.getMessage()));
        }
    }



    @Override
    public Mono<RulesEvaluationResult> evaluateRuleByCode(String code, java.util.Map<String, Object> inputData) {
        log.info("Evaluating rule definition by code: {} with input data: {}", code, inputData);

        return getRuleDefinitionByCode(code)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Rule definition not found with code: " + code)))
                .flatMap(ruleDefinition -> {
                    if (!ruleDefinition.getIsActive()) {
                        return Mono.error(new IllegalArgumentException(
                                "Rule definition is not active: " + code));
                    }

                    return rulesEvaluationEngine.evaluateRulesReactive(
                            ruleDefinition.getYamlContent(), inputData);
                });
    }
}
