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

package com.firefly.rules.web.controllers;

import com.firefly.common.core.filters.FilterRequest;
import com.firefly.common.core.queries.PaginationResponse;
import com.firefly.rules.core.services.RuleDefinitionService;
import com.firefly.rules.interfaces.dtos.crud.RuleDefinitionDTO;
import com.firefly.rules.interfaces.dtos.validation.ValidationResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import java.util.UUID;

/**
 * REST controller for managing YAML DSL rule definitions.
 * Provides CRUD operations for storing and managing rule definitions.
 */
@RestController
@RequestMapping("/api/v1/rules/definitions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Rule Definitions", description = "Operations for managing YAML DSL rule definitions")
public class RuleDefinitionController {

    private final RuleDefinitionService ruleDefinitionService;

    @Operation(summary = "Get all rule definitions", description = "Retrieve rule definitions with optional filtering and pagination")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Rule definitions retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid filter criteria"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/filter")
    public Mono<ResponseEntity<PaginationResponse<RuleDefinitionDTO>>> filterRuleDefinitions(
            @Parameter(description = "Filter criteria and pagination parameters", required = true)
            @RequestBody FilterRequest<RuleDefinitionDTO> filterRequest) {
        
        log.info("Filtering rule definitions with criteria: {}", filterRequest);
        
        return ruleDefinitionService.filterRuleDefinitions(filterRequest)
                .map(ResponseEntity::ok)
                .doOnSuccess(result -> log.info("Rule definitions filtered successfully"))
                .doOnError(error -> log.error("Error filtering rule definitions", error));
    }

    @Operation(summary = "Create a new rule definition",
               description = "Create and store a new YAML DSL rule definition with comprehensive validation. " +
                           "YAML content must follow naming conventions: camelCase for inputs (creditScore, annualIncome), " +
                           "snake_case for computed variables (debt_to_income, is_eligible), " +
                           "UPPER_CASE for constants (MIN_CREDIT_SCORE).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Rule definition created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid rule definition, validation failed, or naming convention violations"),
            @ApiResponse(responseCode = "409", description = "Rule definition with the same code already exists"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping
    public Mono<ResponseEntity<RuleDefinitionDTO>> createRuleDefinition(
            @Parameter(description = "Rule definition to create with YAML content following naming conventions", required = true)
            @Valid @RequestBody RuleDefinitionDTO ruleDefinitionDTO) {
        
        log.info("Creating rule definition with code: {}", ruleDefinitionDTO.getCode());
        
        return ruleDefinitionService.createRuleDefinition(ruleDefinitionDTO)
                .map(created -> ResponseEntity.status(HttpStatus.CREATED).body(created))
                .doOnSuccess(result -> log.info("Rule definition created successfully"))
                .doOnError(error -> log.error("Error creating rule definition", error));
    }

    @Operation(summary = "Update a rule definition", description = "Update an existing YAML DSL rule definition with validation")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Rule definition updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid rule definition or validation failed"),
            @ApiResponse(responseCode = "404", description = "Rule definition not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PutMapping("/{id}")
    public Mono<ResponseEntity<RuleDefinitionDTO>> updateRuleDefinition(
            @Parameter(description = "Rule definition ID", required = true)
            @PathVariable UUID id,
            @Parameter(description = "Updated rule definition", required = true)
            @Valid @RequestBody RuleDefinitionDTO ruleDefinitionDTO) {
        
        log.info("Updating rule definition with ID: {}", id);
        
        return ruleDefinitionService.updateRuleDefinition(id, ruleDefinitionDTO)
                .map(ResponseEntity::ok)
                .doOnSuccess(result -> log.info("Rule definition updated successfully"))
                .doOnError(error -> log.error("Error updating rule definition", error));
    }

    @Operation(summary = "Delete a rule definition", description = "Delete a rule definition by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Rule definition deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Rule definition not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteRuleDefinition(
            @Parameter(description = "Rule definition ID", required = true)
            @PathVariable UUID id) {
        
        log.info("Deleting rule definition with ID: {}", id);
        
        return ruleDefinitionService.deleteRuleDefinition(id)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .doOnSuccess(result -> log.info("Rule definition deleted successfully"))
                .doOnError(error -> log.error("Error deleting rule definition", error));
    }

    @Operation(summary = "Get a rule definition by ID", description = "Retrieve a specific rule definition by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Rule definition retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Rule definition not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/{id}")
    public Mono<ResponseEntity<RuleDefinitionDTO>> getRuleDefinitionById(
            @Parameter(description = "Rule definition ID", required = true)
            @PathVariable UUID id) {
        
        log.info("Getting rule definition by ID: {}", id);
        
        return ruleDefinitionService.getRuleDefinitionById(id)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
                .doOnSuccess(result -> log.info("Rule definition retrieved successfully"))
                .doOnError(error -> log.error("Error retrieving rule definition", error));
    }

    @Operation(summary = "Get a rule definition by code", description = "Retrieve a specific rule definition by its unique code")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Rule definition retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Rule definition not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/by-code/{code}")
    public Mono<ResponseEntity<RuleDefinitionDTO>> getRuleDefinitionByCode(
            @Parameter(description = "Rule definition code", required = true)
            @PathVariable String code) {
        
        log.info("Getting rule definition by code: {}", code);
        
        return ruleDefinitionService.getRuleDefinitionByCode(code)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
                .doOnSuccess(result -> log.info("Rule definition retrieved successfully"))
                .doOnError(error -> log.error("Error retrieving rule definition", error));
    }

    @Operation(summary = "Validate a YAML DSL rule definition",
               description = "Validate YAML DSL content without storing it. " +
                           "Checks syntax, naming conventions (camelCase inputs, snake_case computed, UPPER_CASE constants), " +
                           "dependencies, logic, and best practices.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Validation completed (check response for validation results)"),
            @ApiResponse(responseCode = "400", description = "Invalid YAML content or malformed request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/validate")
    public Mono<ResponseEntity<ValidationResult>> validateRuleDefinition(
            @Parameter(description = "YAML DSL content to validate. Must follow naming conventions: camelCase for inputs, snake_case for computed variables, UPPER_CASE for constants.", required = true)
            @RequestBody String yamlContent) {
        
        log.info("Validating YAML DSL rule definition");
        
        return ruleDefinitionService.validateRuleDefinition(yamlContent)
                .map(ResponseEntity::ok)
                .doOnSuccess(result -> log.info("Rule definition validation completed"))
                .doOnError(error -> log.error("Error validating rule definition", error));
    }
}
