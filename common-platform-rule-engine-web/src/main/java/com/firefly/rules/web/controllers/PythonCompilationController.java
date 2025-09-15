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

import com.firefly.rules.core.dsl.compiler.PythonCompilationService;
import com.firefly.rules.core.dsl.compiler.PythonCompiledRule;
import com.firefly.rules.core.services.RuleDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;

import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for Python compilation functionality.
 *
 * This controller provides endpoints to compile YAML DSL rules to Python code,
 * manage compilation cache, and retrieve compilation statistics.
 */
@RestController
@RequestMapping("/api/v1/python")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Python Compilation", description = "Compile DSL rules to Python code")
public class PythonCompilationController {

    private final PythonCompilationService pythonCompilationService;
    private final RuleDefinitionService ruleDefinitionService;

    @Operation(
        summary = "Compile DSL rule to Python",
        description = "Compiles a YAML DSL rule definition to executable Python code"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Rule compiled successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = PythonCompiledRule.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid DSL or compilation error",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        )
    })
    @PostMapping("/compile")
    public ResponseEntity<?> compileRule(
            @Parameter(description = "YAML DSL rule definition", required = true)
            @RequestBody String yamlDsl,

            @Parameter(description = "Optional rule name")
            @RequestParam(required = false) String ruleName,

            @Parameter(description = "Whether to use compilation cache")
            @RequestParam(defaultValue = "true") boolean useCache) {

        try {
            log.info("Compiling rule '{}' to Python (cache: {})", ruleName, useCache);

            PythonCompiledRule compiledRule = pythonCompilationService.compileRule(yamlDsl, ruleName, useCache);

            return ResponseEntity.ok(compiledRule);

        } catch (PythonCompilationService.PythonCompilationException e) {
            log.error("Compilation failed for rule '{}': {}", ruleName, e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of(
                    "error", "Compilation failed",
                    "message", e.getMessage(),
                    "ruleName", ruleName != null ? ruleName : "unknown"
                ));

        } catch (Exception e) {
            log.error("Unexpected error compiling rule '{}': {}", ruleName, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error", "Internal server error",
                    "message", e.getMessage()
                ));
        }
    }

    @Operation(
        summary = "Compile rule from database by ID",
        description = "Compiles a rule definition stored in the database to Python code using the rule ID"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Rule compiled successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = PythonCompiledRule.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Rule definition not found",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid rule ID or compilation error",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        )
    })
    @PostMapping("/compile/rule/{ruleId}")
    public ResponseEntity<?> compileRuleById(
            @Parameter(description = "Rule definition ID", required = true)
            @PathVariable UUID ruleId,

            @Parameter(description = "Whether to use compilation cache")
            @RequestParam(defaultValue = "true") boolean useCache,

            ServerWebExchange exchange) {

        try {
            log.info("Compiling rule with ID '{}' to Python (cache: {})", ruleId, useCache);

            // Fetch rule definition from database
            return ruleDefinitionService.getRuleDefinitionByIdWithAudit(ruleId, exchange)
                .map(ruleDefinition -> {
                    try {
                        // Compile the rule using its YAML DSL
                        PythonCompiledRule compiledRule = pythonCompilationService.compileRule(
                            ruleDefinition.getYamlContent(),
                            ruleDefinition.getName(),
                            useCache
                        );

                        log.info("Successfully compiled rule '{}' (ID: {}) to Python",
                            ruleDefinition.getName(), ruleId);

                        return ResponseEntity.ok(compiledRule);

                    } catch (PythonCompilationService.PythonCompilationException e) {
                        log.error("Compilation failed for rule '{}' (ID: {}): {}",
                            ruleDefinition.getName(), ruleId, e.getMessage());
                        return ResponseEntity.badRequest()
                            .body(Map.of(
                                "error", "Compilation failed",
                                "message", e.getMessage(),
                                "ruleId", ruleId.toString(),
                                "ruleName", ruleDefinition.getName()
                            ));
                    } catch (Exception e) {
                        log.error("Unexpected error compiling rule '{}' (ID: {}): {}",
                            ruleDefinition.getName(), ruleId, e.getMessage(), e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of(
                                "error", "Internal server error",
                                "message", e.getMessage(),
                                "ruleId", ruleId.toString()
                            ));
                    }
                })
                .switchIfEmpty(
                    // Rule not found
                    reactor.core.publisher.Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                            "error", "Rule definition not found",
                            "message", "No rule definition found with ID: " + ruleId,
                            "ruleId", ruleId.toString()
                        )))
                )
                .block(); // Block to convert Mono to ResponseEntity

        } catch (Exception e) {
            log.error("Unexpected error fetching/compiling rule with ID '{}': {}", ruleId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error", "Internal server error",
                    "message", e.getMessage(),
                    "ruleId", ruleId.toString()
                ));
        }
    }

    @Operation(
        summary = "Compile rule from database by code",
        description = "Compiles a rule definition stored in the database to Python code using the rule code"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Rule compiled successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = PythonCompiledRule.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Rule definition not found",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid rule code or compilation error",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        )
    })
    @PostMapping("/compile/rule/code/{ruleCode}")
    public ResponseEntity<?> compileRuleByCode(
            @Parameter(description = "Rule definition code", required = true)
            @PathVariable String ruleCode,

            @Parameter(description = "Whether to use compilation cache")
            @RequestParam(defaultValue = "true") boolean useCache,

            ServerWebExchange exchange) {

        try {
            log.info("Compiling rule with code '{}' to Python (cache: {})", ruleCode, useCache);

            // Fetch rule definition from database
            return ruleDefinitionService.getRuleDefinitionByCodeWithAudit(ruleCode, exchange)
                .map(ruleDefinition -> {
                    try {
                        // Compile the rule using its YAML DSL
                        PythonCompiledRule compiledRule = pythonCompilationService.compileRule(
                            ruleDefinition.getYamlContent(),
                            ruleDefinition.getName(),
                            useCache
                        );

                        log.info("Successfully compiled rule '{}' (code: {}) to Python",
                            ruleDefinition.getName(), ruleCode);

                        return ResponseEntity.ok(compiledRule);

                    } catch (PythonCompilationService.PythonCompilationException e) {
                        log.error("Compilation failed for rule '{}' (code: {}): {}",
                            ruleDefinition.getName(), ruleCode, e.getMessage());
                        return ResponseEntity.badRequest()
                            .body(Map.of(
                                "error", "Compilation failed",
                                "message", e.getMessage(),
                                "ruleCode", ruleCode,
                                "ruleName", ruleDefinition.getName()
                            ));
                    } catch (Exception e) {
                        log.error("Unexpected error compiling rule '{}' (code: {}): {}",
                            ruleDefinition.getName(), ruleCode, e.getMessage(), e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of(
                                "error", "Internal server error",
                                "message", e.getMessage(),
                                "ruleCode", ruleCode
                            ));
                    }
                })
                .switchIfEmpty(
                    // Rule not found
                    reactor.core.publisher.Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                            "error", "Rule definition not found",
                            "message", "No rule definition found with code: " + ruleCode,
                            "ruleCode", ruleCode
                        )))
                )
                .block(); // Block to convert Mono to ResponseEntity

        } catch (Exception e) {
            log.error("Unexpected error fetching/compiling rule with code '{}': {}", ruleCode, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error", "Internal server error",
                    "message", e.getMessage(),
                    "ruleCode", ruleCode
                ));
        }
    }

    @Operation(
        summary = "Batch compile multiple DSL rules",
        description = "Compiles multiple YAML DSL rules to Python code in parallel"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Batch compilation completed",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request or compilation errors",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        )
    })
    @PostMapping("/compile/batch")
    public ResponseEntity<?> compileRules(
            @Parameter(description = "Map of rule names to YAML DSL definitions", required = true)
            @RequestBody Map<String, String> rules,

            @Parameter(description = "Whether to use compilation cache")
            @RequestParam(defaultValue = "true") boolean useCache) {

        try {
            log.info("Batch compiling {} rules to Python (cache: {})", rules.size(), useCache);

            Map<String, PythonCompiledRule> compiledRules = pythonCompilationService.compileRules(rules, useCache);

            return ResponseEntity.ok(Map.of(
                "compiledRules", compiledRules,
                "totalRules", rules.size(),
                "successfulCompilations", compiledRules.size(),
                "failedCompilations", rules.size() - compiledRules.size()
            ));

        } catch (Exception e) {
            log.error("Unexpected error in batch compilation: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error", "Batch compilation failed",
                    "message", e.getMessage()
                ));
        }
    }

    @Operation(
        summary = "Get compilation statistics",
        description = "Retrieves compilation statistics including success rate and cache performance"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Statistics retrieved successfully",
        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
    )
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getCompilationStats() {
        Map<String, Object> stats = pythonCompilationService.getCompilationStats();
        return ResponseEntity.ok(stats);
    }

    @Operation(
        summary = "Clear compilation cache",
        description = "Clears all cached compiled rules"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Cache cleared successfully",
        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
    )
    @DeleteMapping("/cache")
    public ResponseEntity<Map<String, String>> clearCache() {
        pythonCompilationService.clearCache();
        return ResponseEntity.ok(Map.of(
            "message", "Compilation cache cleared successfully"
        ));
    }

    @Operation(
        summary = "Check if rule is cached",
        description = "Checks if a specific rule is already compiled and cached"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Cache status retrieved",
        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
    )
    @PostMapping("/cache/check")
    public ResponseEntity<Map<String, Object>> checkCache(
            @Parameter(description = "YAML DSL rule definition", required = true)
            @RequestBody String yamlDsl,

            @Parameter(description = "Optional rule name")
            @RequestParam(required = false) String ruleName) {

        boolean isCached = pythonCompilationService.isRuleCached(yamlDsl, ruleName);

        return ResponseEntity.ok(Map.of(
            "cached", isCached,
            "ruleName", ruleName != null ? ruleName : "unknown"
        ));
    }

    @Operation(
        summary = "Remove rule from cache",
        description = "Removes a specific compiled rule from the cache using rule name or cache key"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Rule removed from cache",
        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
    )
    @DeleteMapping("/cache/rule")
    public ResponseEntity<Map<String, Object>> removeCachedRule(
            @Parameter(description = "Rule name or cache key to remove", required = true)
            @RequestParam String ruleName) {

        // For DELETE requests, we use query parameters instead of request body
        // This follows HTTP best practices where DELETE should not have a body
        boolean removed = pythonCompilationService.removeCachedRuleByName(ruleName);

        return ResponseEntity.ok(Map.of(
            "removed", removed,
            "ruleName", ruleName
        ));
    }

    @Operation(
        summary = "Get cached compiled rule",
        description = "Retrieves a compiled rule from cache if it exists"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Cached rule found",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = PythonCompiledRule.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Rule not found in cache",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        )
    })
    @PostMapping("/cache/get")
    public ResponseEntity<?> getCachedRule(
            @Parameter(description = "YAML DSL rule definition", required = true)
            @RequestBody String yamlDsl,

            @Parameter(description = "Optional rule name")
            @RequestParam(required = false) String ruleName) {

        PythonCompiledRule cachedRule = pythonCompilationService.getCachedRule(yamlDsl, ruleName);

        if (cachedRule != null) {
            return ResponseEntity.ok(cachedRule);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                    "error", "Rule not found in cache",
                    "ruleName", ruleName != null ? ruleName : "unknown"
                ));
        }
    }
}
