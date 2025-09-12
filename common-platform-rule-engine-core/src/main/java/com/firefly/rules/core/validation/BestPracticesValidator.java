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
import com.firefly.rules.interfaces.dtos.validation.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates best practices and code quality aspects of rules
 */
@Component
@Slf4j
public class BestPracticesValidator {

    private static final int MIN_DESCRIPTION_LENGTH = 20;
    private static final int MAX_RULE_NAME_LENGTH = 100;
    private static final int MIN_RULE_NAME_LENGTH = 5;

    /**
     * Validate best practices and code quality
     *
     * @param rulesDSL the parsed rules DSL
     * @return list of validation issues
     */
    public List<ValidationResult.ValidationIssue> validate(RulesDSL rulesDSL) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // Validate documentation
        issues.addAll(validateDocumentation(rulesDSL));

        // Validate naming conventions
        issues.addAll(validateNamingBestPractices(rulesDSL));

        // Validate rule structure
        issues.addAll(validateRuleStructure(rulesDSL));

        // Validate maintainability
        issues.addAll(validateMaintainability(rulesDSL));

        return issues;
    }

    /**
     * Validate documentation quality
     */
    private List<ValidationResult.ValidationIssue> validateDocumentation(RulesDSL rulesDSL) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // Check for missing description
        if (rulesDSL.getDescription() == null || rulesDSL.getDescription().trim().isEmpty()) {
            issues.add(createBestPracticeIssue("BP_001", 
                "Missing rule description",
                "Rule should have a clear description explaining its purpose",
                "description", "Add a description field explaining what this rule does and when it applies"));
        } else if (rulesDSL.getDescription().length() < MIN_DESCRIPTION_LENGTH) {
            issues.add(createBestPracticeIssue("BP_002", 
                "Description too short",
                "Rule description should be more detailed (minimum " + MIN_DESCRIPTION_LENGTH + " characters)",
                "description", "Expand the description to explain the rule's purpose, inputs, and expected outcomes"));
        }

        // Check for version information
        if (rulesDSL.getVersion() == null || rulesDSL.getVersion().trim().isEmpty()) {
            issues.add(createBestPracticeIssue("BP_003", 
                "Missing version information",
                "Rule should include version information for tracking changes",
                "version", "Add a version field (e.g., '1.0.0') to track rule changes"));
        }

        return issues;
    }

    /**
     * Validate naming best practices
     */
    private List<ValidationResult.ValidationIssue> validateNamingBestPractices(RulesDSL rulesDSL) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // Check rule name quality
        if (rulesDSL.getName() != null) {
            String name = rulesDSL.getName();
            
            if (name.length() < MIN_RULE_NAME_LENGTH) {
                issues.add(createBestPracticeIssue("BP_004", 
                    "Rule name too short",
                    "Rule name should be descriptive (minimum " + MIN_RULE_NAME_LENGTH + " characters)",
                    "name", "Use a more descriptive name that explains the rule's purpose"));
            }
            
            if (name.length() > MAX_RULE_NAME_LENGTH) {
                issues.add(createBestPracticeIssue("BP_005", 
                    "Rule name too long",
                    "Rule name should be concise (maximum " + MAX_RULE_NAME_LENGTH + " characters)",
                    "name", "Shorten the rule name while keeping it descriptive"));
            }
            
            if (name.toLowerCase().equals(name) || name.toUpperCase().equals(name)) {
                issues.add(createBestPracticeIssue("BP_006", 
                    "Poor rule name formatting",
                    "Rule name should use proper capitalization",
                    "name", "Use Title Case or sentence case for rule names"));
            }
            
            if (name.contains("_") || name.contains("-")) {
                issues.add(createBestPracticeIssue("BP_007", 
                    "Rule name contains separators",
                    "Rule names should use spaces instead of underscores or hyphens",
                    "name", "Replace underscores/hyphens with spaces for better readability"));
            }
        }

        return issues;
    }

    /**
     * Validate rule structure best practices
     */
    private List<ValidationResult.ValidationIssue> validateRuleStructure(RulesDSL rulesDSL) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // Check for else clause
        if (rulesDSL.getWhenConditions() != null && !rulesDSL.getWhenConditions().isEmpty() &&
            rulesDSL.getThen() != null && rulesDSL.getElseAction() == null) {
            issues.add(createBestPracticeIssue("BP_008", 
                "Missing else clause",
                "Rules with conditions should consider what happens when conditions are not met",
                "else", "Add an else clause to handle cases when conditions are false"));
        }

        // Check for single responsibility
        if (rulesDSL.getThen() instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> actions = (List<String>) rulesDSL.getThen();
            
            if (actions.size() > 10) {
                issues.add(createBestPracticeIssue("BP_009", 
                    "Rule doing too many things",
                    "Rule has many actions which may violate single responsibility principle",
                    "then", "Consider breaking this rule into multiple smaller, focused rules"));
            }
        }

        // Check for magic numbers
        issues.addAll(validateMagicNumbers(rulesDSL));

        return issues;
    }

    /**
     * Validate maintainability aspects
     */
    private List<ValidationResult.ValidationIssue> validateMaintainability(RulesDSL rulesDSL) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // Check for hardcoded values
        if (rulesDSL.getWhenConditions() != null) {
            for (int i = 0; i < rulesDSL.getWhenConditions().size(); i++) {
                String condition = rulesDSL.getWhenConditions().get(i);
                issues.addAll(validateHardcodedValues(condition, "when[" + i + "]"));
            }
        }

        // Check for complex conditions
        if (rulesDSL.getWhenConditions() != null) {
            for (int i = 0; i < rulesDSL.getWhenConditions().size(); i++) {
                String condition = rulesDSL.getWhenConditions().get(i);
                if (isComplexCondition(condition)) {
                    issues.add(createBestPracticeIssue("BP_012", 
                        "Complex condition",
                        "Condition is complex and may be hard to understand and maintain",
                        "when[" + i + "]", "Consider breaking complex conditions into multiple simpler conditions"));
                }
            }
        }

        // Check for consistent formatting
        issues.addAll(validateFormatting(rulesDSL));

        return issues;
    }

    /**
     * Validate for magic numbers
     */
    private List<ValidationResult.ValidationIssue> validateMagicNumbers(RulesDSL rulesDSL) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        if (rulesDSL.getWhenConditions() != null) {
            for (int i = 0; i < rulesDSL.getWhenConditions().size(); i++) {
                String condition = rulesDSL.getWhenConditions().get(i);
                if (hasMagicNumbers(condition)) {
                    issues.add(createBestPracticeIssue("BP_010", 
                        "Magic numbers in condition",
                        "Condition contains hardcoded numbers that should be named constants",
                        "when[" + i + "]", "Replace magic numbers with named constants for better maintainability"));
                }
            }
        }

        return issues;
    }

    /**
     * Validate for hardcoded values
     */
    private List<ValidationResult.ValidationIssue> validateHardcodedValues(String text, String location) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // Check for hardcoded strings that might be configuration
        if (text.contains("\"") && (text.contains("APPROVED") || text.contains("REJECTED") || 
                                   text.contains("HIGH") || text.contains("LOW"))) {
            issues.add(createBestPracticeIssue("BP_011", 
                "Hardcoded configuration value",
                "Text contains hardcoded values that might be better as configuration",
                location, "Consider using constants or configuration for hardcoded values"));
        }

        return issues;
    }

    /**
     * Validate formatting consistency
     */
    private List<ValidationResult.ValidationIssue> validateFormatting(RulesDSL rulesDSL) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // Check for consistent operator spacing
        if (rulesDSL.getWhenConditions() != null) {
            for (int i = 0; i < rulesDSL.getWhenConditions().size(); i++) {
                String condition = rulesDSL.getWhenConditions().get(i);
                if (hasInconsistentSpacing(condition)) {
                    issues.add(createBestPracticeIssue("BP_013", 
                        "Inconsistent spacing",
                        "Condition has inconsistent spacing around operators",
                        "when[" + i + "]", "Use consistent spacing around operators for better readability"));
                }
            }
        }

        return issues;
    }

    // Helper methods

    private boolean hasMagicNumbers(String text) {
        // Look for numbers that aren't 0, 1, or 100 (common non-magic numbers)
        return text.matches(".*\\b(?!0\\b|1\\b|100\\b)\\d{2,}\\b.*");
    }

    private boolean isComplexCondition(String condition) {
        // Count logical operators
        int andCount = countOccurrences(condition, " and ");
        int orCount = countOccurrences(condition, " or ");
        int totalOperators = andCount + orCount;
        
        return totalOperators > 2 || condition.length() > 100;
    }

    private boolean hasInconsistentSpacing(String text) {
        // Simple check for inconsistent spacing around operators
        return text.contains("  ") || text.matches(".*\\w[<>=!]\\w.*") || text.matches(".*\\w [<>=!]\\w.*");
    }

    private int countOccurrences(String text, String substring) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }

    /**
     * Helper method to create best practice validation issues
     */
    private ValidationResult.ValidationIssue createBestPracticeIssue(
            String code, String message, String description, String location, String suggestion) {
        
        return ValidationResult.ValidationIssue.builder()
            .code(code)
            .severity(ValidationResult.ValidationSeverity.INFO)
            .message(message)
            .description(description)
            .location(ValidationResult.ValidationLocation.builder()
                .path(location)
                .context(location)
                .build())
            .suggestion(suggestion)
            .build();
    }
}
