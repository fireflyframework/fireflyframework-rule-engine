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

package com.firefly.rules.core.validation;

import com.firefly.rules.core.dsl.model.RulesDSL;
import com.firefly.rules.core.dsl.parser.RulesDSLParser;
import com.firefly.rules.interfaces.dtos.validation.ValidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive YAML DSL validator that acts as a static code analyzer.
 * Validates syntax, naming conventions, dependencies, logic, and best practices.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class YamlDslValidator {

    private final RulesDSLParser rulesDSLParser;
    private final NamingConventionValidator namingValidator;
    private final SyntaxValidator syntaxValidator;
    private final DependencyValidator dependencyValidator;
    private final LogicValidator logicValidator;
    private final PerformanceValidator performanceValidator;
    private final BestPracticesValidator bestPracticesValidator;

    private static final String VALIDATOR_VERSION = "1.0.0";

    /**
     * Validate a YAML DSL rule comprehensively
     *
     * @param yamlContent the YAML content to validate
     * @return comprehensive validation result
     */
    public ValidationResult validate(String yamlContent) {
        long startTime = System.currentTimeMillis();
        
        ValidationResult.ValidationResultBuilder resultBuilder = ValidationResult.builder();
        List<ValidationResult.ValidationIssue> allIssues = new ArrayList<>();

        try {
            // Step 1: Basic syntax validation
            List<ValidationResult.ValidationIssue> syntaxIssues = syntaxValidator.validate(yamlContent);
            allIssues.addAll(syntaxIssues);

            // If syntax is invalid, stop here
            if (hasCriticalErrors(syntaxIssues)) {
                return buildResult(resultBuilder, allIssues, startTime, yamlContent, ValidationResult.ValidationStatus.CRITICAL_ERROR);
            }

            // Step 2: Parse the YAML to get structured data
            RulesDSL rulesDSL = null;
            try {
                rulesDSL = rulesDSLParser.parseRules(yamlContent);
            } catch (Exception e) {
                ValidationResult.ValidationIssue parseError = ValidationResult.ValidationIssue.builder()
                    .code("PARSE_001")
                    .severity(ValidationResult.ValidationSeverity.CRITICAL)
                    .message("Failed to parse YAML DSL")
                    .description("The YAML content could not be parsed: " + e.getMessage())
                    .location(ValidationResult.ValidationLocation.builder()
                        .section("root")
                        .context("Entire YAML document")
                        .build())
                    .suggestion("Check YAML syntax and structure")
                    .build();
                allIssues.add(parseError);
                return buildResult(resultBuilder, allIssues, startTime, yamlContent, ValidationResult.ValidationStatus.CRITICAL_ERROR);
            }

            // Step 3: Naming convention validation
            List<ValidationResult.ValidationIssue> namingIssues = validateNamingConventions(rulesDSL);
            allIssues.addAll(namingIssues);

            // Step 4: Dependency analysis
            List<ValidationResult.ValidationIssue> dependencyIssues = dependencyValidator.validate(rulesDSL);
            allIssues.addAll(dependencyIssues);

            // Step 5: Logic validation
            List<ValidationResult.ValidationIssue> logicIssues = logicValidator.validate(rulesDSL);
            allIssues.addAll(logicIssues);

            // Step 6: Performance analysis
            List<ValidationResult.ValidationIssue> performanceIssues = performanceValidator.validate(rulesDSL);
            allIssues.addAll(performanceIssues);

            // Step 7: Best practices validation
            List<ValidationResult.ValidationIssue> bestPracticesIssues = bestPracticesValidator.validate(rulesDSL);
            allIssues.addAll(bestPracticesIssues);

            // Determine overall status
            ValidationResult.ValidationStatus status = determineOverallStatus(allIssues);

            return buildResult(resultBuilder, allIssues, startTime, yamlContent, status);

        } catch (Exception e) {
            log.error("Unexpected error during validation", e);
            ValidationResult.ValidationIssue internalError = ValidationResult.ValidationIssue.builder()
                .code("INTERNAL_001")
                .severity(ValidationResult.ValidationSeverity.CRITICAL)
                .message("Internal validation error")
                .description("An unexpected error occurred during validation: " + e.getMessage())
                .suggestion("Please report this issue to the development team")
                .build();
            allIssues.add(internalError);
            return buildResult(resultBuilder, allIssues, startTime, yamlContent, ValidationResult.ValidationStatus.CRITICAL_ERROR);
        }
    }

    /**
     * Validate naming conventions using the existing validator
     */
    private List<ValidationResult.ValidationIssue> validateNamingConventions(RulesDSL rulesDSL) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // Validate input variable names
        if (rulesDSL.getInputs() != null) {
            for (String inputVar : rulesDSL.getInputs()) {
                if (!namingValidator.isValidInputVariableName(inputVar)) {
                    issues.add(ValidationResult.ValidationIssue.builder()
                        .code("NAMING_001")
                        .severity(ValidationResult.ValidationSeverity.ERROR)
                        .message("Invalid input variable naming")
                        .description(String.format("Input variable '%s' must follow camelCase naming convention", inputVar))
                        .location(ValidationResult.ValidationLocation.builder()
                            .section("inputs")
                            .context("Input variable: " + inputVar)
                            .build())
                        .suggestion("Use camelCase naming (e.g., creditScore, annualIncome)")
                        .examples(List.of("creditScore", "annualIncome", "employmentYears"))
                        .build());
                }
            }
        }

        return issues;
    }

    /**
     * Check if there are any critical errors that prevent further validation
     */
    private boolean hasCriticalErrors(List<ValidationResult.ValidationIssue> issues) {
        return issues.stream()
            .anyMatch(issue -> issue.getSeverity() == ValidationResult.ValidationSeverity.CRITICAL);
    }

    /**
     * Determine the overall validation status based on all issues
     */
    private ValidationResult.ValidationStatus determineOverallStatus(List<ValidationResult.ValidationIssue> allIssues) {
        boolean hasCritical = false;
        boolean hasErrors = false;
        boolean hasWarnings = false;

        for (ValidationResult.ValidationIssue issue : allIssues) {
            switch (issue.getSeverity()) {
                case CRITICAL:
                    hasCritical = true;
                    break;
                case ERROR:
                    hasErrors = true;
                    break;
                case WARNING:
                    hasWarnings = true;
                    break;
            }
        }

        if (hasCritical) {
            return ValidationResult.ValidationStatus.CRITICAL_ERROR;
        } else if (hasErrors) {
            return ValidationResult.ValidationStatus.ERROR;
        } else if (hasWarnings) {
            return ValidationResult.ValidationStatus.WARNING;
        } else {
            return ValidationResult.ValidationStatus.VALID;
        }
    }

    /**
     * Build the final validation result
     */
    private ValidationResult buildResult(
            ValidationResult.ValidationResultBuilder resultBuilder,
            List<ValidationResult.ValidationIssue> allIssues,
            long startTime,
            String yamlContent,
            ValidationResult.ValidationStatus status) {

        long validationTime = System.currentTimeMillis() - startTime;

        // Group issues by category
        ValidationResult.ValidationIssues groupedIssues = groupIssuesByCategory(allIssues);

        // Calculate summary statistics
        ValidationResult.ValidationSummary summary = calculateSummary(allIssues);

        // Generate suggestions
        List<ValidationResult.ValidationSuggestion> suggestions = generateSuggestions(allIssues);

        // Create metadata
        ValidationResult.ValidationMetadata metadata = ValidationResult.ValidationMetadata.builder()
            .validatorVersion(VALIDATOR_VERSION)
            .validatedAt(Instant.now())
            .validationTimeMs(validationTime)
            .statistics(calculateStatistics(yamlContent, allIssues))
            .build();

        return resultBuilder
            .status(status)
            .summary(summary)
            .issues(groupedIssues)
            .suggestions(suggestions)
            .metadata(metadata)
            .build();
    }

    /**
     * Group validation issues by category
     */
    private ValidationResult.ValidationIssues groupIssuesByCategory(List<ValidationResult.ValidationIssue> allIssues) {
        List<ValidationResult.ValidationIssue> syntax = new ArrayList<>();
        List<ValidationResult.ValidationIssue> naming = new ArrayList<>();
        List<ValidationResult.ValidationIssue> dependencies = new ArrayList<>();
        List<ValidationResult.ValidationIssue> logic = new ArrayList<>();
        List<ValidationResult.ValidationIssue> performance = new ArrayList<>();
        List<ValidationResult.ValidationIssue> bestPractices = new ArrayList<>();

        for (ValidationResult.ValidationIssue issue : allIssues) {
            String code = issue.getCode();
            if (code.startsWith("SYNTAX_") || code.startsWith("PARSE_")) {
                syntax.add(issue);
            } else if (code.startsWith("NAMING_")) {
                naming.add(issue);
            } else if (code.startsWith("DEP_")) {
                dependencies.add(issue);
            } else if (code.startsWith("LOGIC_")) {
                logic.add(issue);
            } else if (code.startsWith("PERF_")) {
                performance.add(issue);
            } else if (code.startsWith("BP_")) {
                bestPractices.add(issue);
            } else {
                syntax.add(issue); // Default category
            }
        }

        return ValidationResult.ValidationIssues.builder()
            .syntax(syntax)
            .naming(naming)
            .dependencies(dependencies)
            .logic(logic)
            .performance(performance)
            .bestPractices(bestPractices)
            .build();
    }

    /**
     * Calculate summary statistics
     */
    private ValidationResult.ValidationSummary calculateSummary(List<ValidationResult.ValidationIssue> allIssues) {
        int critical = 0, errors = 0, warnings = 0, info = 0;

        for (ValidationResult.ValidationIssue issue : allIssues) {
            switch (issue.getSeverity()) {
                case CRITICAL: critical++; break;
                case ERROR: errors++; break;
                case WARNING: warnings++; break;
                case INFO: info++; break;
            }
        }

        // Calculate quality score (100 - penalty for issues)
        double qualityScore = Math.max(0, 100 - (critical * 25 + errors * 10 + warnings * 5 + info * 1));

        return ValidationResult.ValidationSummary.builder()
            .totalIssues(allIssues.size())
            .criticalErrors(critical)
            .errors(errors)
            .warnings(warnings)
            .suggestions(info)
            .qualityScore(qualityScore)
            .build();
    }

    /**
     * Generate improvement suggestions based on issues found
     */
    private List<ValidationResult.ValidationSuggestion> generateSuggestions(List<ValidationResult.ValidationIssue> allIssues) {
        List<ValidationResult.ValidationSuggestion> suggestions = new ArrayList<>();
        
        // Add suggestions based on common patterns in issues
        // This is a simplified implementation - could be much more sophisticated
        
        return suggestions;
    }

    /**
     * Calculate additional statistics about the rule
     */
    private Map<String, Object> calculateStatistics(String yamlContent, List<ValidationResult.ValidationIssue> allIssues) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("yamlLines", yamlContent.split("\n").length);
        stats.put("yamlSize", yamlContent.length());
        stats.put("issuesByCategory", groupIssuesByCategory(allIssues));
        return stats;
    }
}
