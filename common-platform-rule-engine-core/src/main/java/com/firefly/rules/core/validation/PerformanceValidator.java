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
 * Validates performance-related aspects of rules
 */
@Component
@Slf4j
public class PerformanceValidator {

    private static final int MAX_CONDITIONS = 20;
    private static final int MAX_ACTIONS = 50;
    private static final int MAX_NESTED_DEPTH = 5;

    /**
     * Validate performance aspects of the rule
     *
     * @param rulesDSL the parsed rules DSL
     * @return list of validation issues
     */
    public List<ValidationResult.ValidationIssue> validate(RulesDSL rulesDSL) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // Check rule complexity
        issues.addAll(validateComplexity(rulesDSL));

        // Check for expensive operations
        issues.addAll(validateExpensiveOperations(rulesDSL));

        // Check for optimization opportunities
        issues.addAll(validateOptimizationOpportunities(rulesDSL));

        return issues;
    }

    /**
     * Validate rule complexity
     */
    private List<ValidationResult.ValidationIssue> validateComplexity(RulesDSL rulesDSL) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // Check number of conditions
        if (rulesDSL.getWhenConditions() != null && rulesDSL.getWhenConditions().size() > MAX_CONDITIONS) {
            issues.add(createPerformanceIssue("PERF_001", 
                "Too many conditions",
                "Rule has " + rulesDSL.getWhenConditions().size() + " conditions (max recommended: " + MAX_CONDITIONS + ")",
                "when", "Consider breaking into multiple rules or simplifying logic"));
        }

        // Check number of actions
        int actionCount = countActions(rulesDSL);
        if (actionCount > MAX_ACTIONS) {
            issues.add(createPerformanceIssue("PERF_002", 
                "Too many actions",
                "Rule has " + actionCount + " actions (max recommended: " + MAX_ACTIONS + ")",
                "then/else", "Consider breaking into multiple rules or using function calls"));
        }

        return issues;
    }

    /**
     * Validate for expensive operations
     */
    private List<ValidationResult.ValidationIssue> validateExpensiveOperations(RulesDSL rulesDSL) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // Check conditions for expensive operations
        if (rulesDSL.getWhenConditions() != null) {
            for (int i = 0; i < rulesDSL.getWhenConditions().size(); i++) {
                String condition = rulesDSL.getWhenConditions().get(i);
                issues.addAll(validateExpensiveCondition(condition, "when[" + i + "]"));
            }
        }

        // Check actions for expensive operations
        if (rulesDSL.getThen() instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> actions = (List<String>) rulesDSL.getThen();
            
            for (int i = 0; i < actions.size(); i++) {
                String action = actions.get(i);
                issues.addAll(validateExpensiveAction(action, "then[" + i + "]"));
            }
        }

        return issues;
    }

    /**
     * Validate expensive operations in conditions
     */
    private List<ValidationResult.ValidationIssue> validateExpensiveCondition(String condition, String location) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // Check for regex operations
        if (condition.contains("matches") || condition.contains("regex")) {
            issues.add(createPerformanceIssue("PERF_003", 
                "Regex operation in condition",
                "Regular expression matching can be expensive in high-volume scenarios",
                location, "Consider using simpler string operations or caching regex patterns"));
        }

        // Check for complex string operations
        if (condition.contains("contains") && condition.contains("and")) {
            issues.add(createPerformanceIssue("PERF_004", 
                "Multiple string operations",
                "Multiple string operations in a single condition may impact performance",
                location, "Consider combining into a single operation or pre-processing"));
        }

        return issues;
    }

    /**
     * Validate expensive operations in actions
     */
    private List<ValidationResult.ValidationIssue> validateExpensiveAction(String action, String location) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // Check for function calls
        if (action.contains("call ")) {
            issues.add(createPerformanceIssue("PERF_005", 
                "Function call in action",
                "Function calls may have performance implications depending on implementation",
                location, "Ensure function is optimized for high-volume usage"));
        }

        // Check for complex calculations
        if (countMathOperations(action) > 3) {
            issues.add(createPerformanceIssue("PERF_006", 
                "Complex calculation",
                "Action contains multiple mathematical operations",
                location, "Consider pre-calculating complex formulas or using lookup tables"));
        }

        return issues;
    }

    /**
     * Validate optimization opportunities
     */
    private List<ValidationResult.ValidationIssue> validateOptimizationOpportunities(RulesDSL rulesDSL) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // Check for condition ordering
        if (rulesDSL.getWhenConditions() != null && rulesDSL.getWhenConditions().size() > 1) {
            issues.addAll(validateConditionOrdering(rulesDSL.getWhenConditions()));
        }

        // Check for redundant calculations
        issues.addAll(validateRedundantCalculations(rulesDSL));

        return issues;
    }

    /**
     * Validate condition ordering for performance
     */
    private List<ValidationResult.ValidationIssue> validateConditionOrdering(List<String> conditions) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // Check if expensive operations are at the beginning
        for (int i = 0; i < Math.min(2, conditions.size()); i++) {
            String condition = conditions.get(i);
            if (isExpensiveCondition(condition)) {
                issues.add(createPerformanceIssue("PERF_007", 
                    "Expensive condition early in evaluation",
                    "Expensive operations should be placed later in condition list for short-circuit evaluation",
                    "when[" + i + "]", "Move expensive conditions (regex, complex string operations) to the end"));
            }
        }

        return issues;
    }

    /**
     * Validate for redundant calculations
     */
    private List<ValidationResult.ValidationIssue> validateRedundantCalculations(RulesDSL rulesDSL) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        if (rulesDSL.getThen() instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> actions = (List<String>) rulesDSL.getThen();
            
            // Look for similar calculations
            for (int i = 0; i < actions.size(); i++) {
                for (int j = i + 1; j < actions.size(); j++) {
                    if (hasSimilarCalculation(actions.get(i), actions.get(j))) {
                        issues.add(createPerformanceIssue("PERF_008", 
                            "Similar calculations detected",
                            "Actions " + i + " and " + j + " perform similar calculations",
                            "then", "Consider extracting common calculations into variables"));
                    }
                }
            }
        }

        return issues;
    }

    // Helper methods

    private int countActions(RulesDSL rulesDSL) {
        int count = 0;
        
        if (rulesDSL.getThen() instanceof List) {
            count += ((List<?>) rulesDSL.getThen()).size();
        }
        
        if (rulesDSL.getElseAction() instanceof List) {
            count += ((List<?>) rulesDSL.getElseAction()).size();
        }
        
        return count;
    }

    private int countMathOperations(String action) {
        int count = 0;
        String[] operators = {"add", "subtract", "multiply", "divide", "+", "-", "*", "/"};
        
        for (String operator : operators) {
            count += countOccurrences(action, operator);
        }
        
        return count;
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

    private boolean isExpensiveCondition(String condition) {
        return condition.contains("matches") || 
               condition.contains("regex") || 
               condition.contains("contains") ||
               countOccurrences(condition, "and") > 2;
    }

    private boolean hasSimilarCalculation(String action1, String action2) {
        // Simple heuristic: check if both actions contain similar mathematical operations
        if (action1.contains("calculate") && action2.contains("calculate")) {
            String calc1 = extractCalculationPart(action1);
            String calc2 = extractCalculationPart(action2);
            
            // Check for similar patterns (this is a simplified check)
            return calc1 != null && calc2 != null && 
                   (calc1.contains(calc2.substring(0, Math.min(calc2.length(), 10))) ||
                    calc2.contains(calc1.substring(0, Math.min(calc1.length(), 10))));
        }
        return false;
    }

    private String extractCalculationPart(String action) {
        if (action.contains(" as ")) {
            String[] parts = action.split(" as ", 2);
            return parts.length > 1 ? parts[1] : null;
        }
        return null;
    }

    /**
     * Helper method to create performance validation issues
     */
    private ValidationResult.ValidationIssue createPerformanceIssue(
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
