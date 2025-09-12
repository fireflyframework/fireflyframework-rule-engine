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
import java.util.regex.Pattern;

/**
 * Validates business logic and rule semantics
 */
@Component
@Slf4j
public class LogicValidator {

    // Pattern for numeric values
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)?");

    /**
     * Validate business logic and rule semantics
     *
     * @param rulesDSL the parsed rules DSL
     * @return list of validation issues
     */
    public List<ValidationResult.ValidationIssue> validate(RulesDSL rulesDSL) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // Validate condition logic
        issues.addAll(validateConditionLogic(rulesDSL));

        // Validate action logic
        issues.addAll(validateActionLogic(rulesDSL));

        // Validate mathematical operations
        issues.addAll(validateMathematicalOperations(rulesDSL));

        // Validate comparison operations
        issues.addAll(validateComparisonOperations(rulesDSL));

        return issues;
    }

    /**
     * Validate condition logic
     */
    private List<ValidationResult.ValidationIssue> validateConditionLogic(RulesDSL rulesDSL) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        if (rulesDSL.getWhenConditions() != null) {
            for (int i = 0; i < rulesDSL.getWhenConditions().size(); i++) {
                String condition = rulesDSL.getWhenConditions().get(i);
                issues.addAll(validateSingleCondition(condition, "when[" + i + "]"));
            }
        }

        return issues;
    }

    /**
     * Validate a single condition
     */
    private List<ValidationResult.ValidationIssue> validateSingleCondition(String condition, String location) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        if (condition == null || condition.trim().isEmpty()) {
            return issues;
        }

        // Check for impossible conditions
        if (condition.contains("equals") && condition.contains("not_equals")) {
            issues.add(createLogicIssue("LOGIC_001", 
                "Contradictory condition",
                "Condition contains both 'equals' and 'not_equals' which is contradictory",
                location, "Use either 'equals' or 'not_equals', not both"));
        }

        // Check for redundant conditions
        if (condition.contains("at_least") && condition.contains("greater_than")) {
            issues.add(createLogicIssue("LOGIC_002", 
                "Redundant condition operators",
                "Using both 'at_least' and 'greater_than' may be redundant",
                location, "Use either 'at_least' (>=) or 'greater_than' (>)"));
        }

        // Check for type mismatches in comparisons
        if (containsNumericComparison(condition) && containsStringValue(condition)) {
            issues.add(createLogicIssue("LOGIC_003", 
                "Type mismatch in comparison",
                "Numeric comparison operators used with string values",
                location, "Ensure consistent data types in comparisons"));
        }

        return issues;
    }

    /**
     * Validate action logic
     */
    private List<ValidationResult.ValidationIssue> validateActionLogic(RulesDSL rulesDSL) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        if (rulesDSL.getThen() instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> actions = (List<String>) rulesDSL.getThen();
            
            for (int i = 0; i < actions.size(); i++) {
                String action = actions.get(i);
                issues.addAll(validateSingleAction(action, "then[" + i + "]"));
            }
        }

        if (rulesDSL.getElseAction() instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> actions = (List<String>) rulesDSL.getElseAction();
            
            for (int i = 0; i < actions.size(); i++) {
                String action = actions.get(i);
                issues.addAll(validateSingleAction(action, "else[" + i + "]"));
            }
        }

        return issues;
    }

    /**
     * Validate a single action
     */
    private List<ValidationResult.ValidationIssue> validateSingleAction(String action, String location) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        if (action == null || action.trim().isEmpty()) {
            return issues;
        }

        // Check for variable assignment to itself
        if (action.contains(" to ") || action.contains(" as ")) {
            String[] parts = action.split(" (to|as) ");
            if (parts.length == 2) {
                String leftSide = extractVariableName(parts[0]);
                String rightSide = parts[1].trim();
                
                if (leftSide != null && rightSide.equals(leftSide)) {
                    issues.add(createLogicIssue("LOGIC_004", 
                        "Self-assignment detected",
                        "Variable '" + leftSide + "' is assigned to itself",
                        location, "Remove redundant self-assignment or fix the logic"));
                }
            }
        }

        // Check for division by zero
        if (action.contains("divide") && action.contains(" 0")) {
            issues.add(createLogicIssue("LOGIC_005", 
                "Potential division by zero",
                "Action may involve division by zero",
                location, "Add validation to prevent division by zero"));
        }

        return issues;
    }

    /**
     * Validate mathematical operations
     */
    private List<ValidationResult.ValidationIssue> validateMathematicalOperations(RulesDSL rulesDSL) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        if (rulesDSL.getThen() instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> actions = (List<String>) rulesDSL.getThen();
            
            for (int i = 0; i < actions.size(); i++) {
                String action = actions.get(i);
                issues.addAll(validateMathInAction(action, "then[" + i + "]"));
            }
        }

        return issues;
    }

    /**
     * Validate mathematical operations in an action
     */
    private List<ValidationResult.ValidationIssue> validateMathInAction(String action, String location) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // Check for potential overflow in calculations
        if (action.contains("multiply") && containsLargeNumber(action)) {
            issues.add(createLogicIssue("LOGIC_006", 
                "Potential numeric overflow",
                "Multiplication with large numbers may cause overflow",
                location, "Consider using BigDecimal for large number calculations"));
        }

        // Check for precision issues
        if (action.contains("divide") && !action.contains("round")) {
            issues.add(createLogicIssue("LOGIC_007", 
                "Potential precision loss",
                "Division without rounding may cause precision issues",
                location, "Consider adding rounding for financial calculations"));
        }

        return issues;
    }

    /**
     * Validate comparison operations
     */
    private List<ValidationResult.ValidationIssue> validateComparisonOperations(RulesDSL rulesDSL) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        if (rulesDSL.getWhenConditions() != null) {
            for (int i = 0; i < rulesDSL.getWhenConditions().size(); i++) {
                String condition = rulesDSL.getWhenConditions().get(i);
                issues.addAll(validateComparisonsInCondition(condition, "when[" + i + "]"));
            }
        }

        return issues;
    }

    /**
     * Validate comparisons in a condition
     */
    private List<ValidationResult.ValidationIssue> validateComparisonsInCondition(String condition, String location) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // Check for impossible ranges
        if (condition.contains("between") && containsImpossibleRange(condition)) {
            issues.add(createLogicIssue("LOGIC_008", 
                "Impossible range in between condition",
                "The range in 'between' condition is impossible (min > max)",
                location, "Fix the range values in the between condition"));
        }

        // Check for floating point equality
        if (condition.contains("equals") && containsFloatingPoint(condition)) {
            issues.add(createLogicIssue("LOGIC_009", 
                "Floating point equality comparison",
                "Direct equality comparison with floating point numbers may be unreliable",
                location, "Use range comparison (between) instead of exact equality for floating point numbers"));
        }

        return issues;
    }

    // Helper methods

    private boolean containsNumericComparison(String condition) {
        return condition.contains("greater_than") || condition.contains("less_than") || 
               condition.contains("at_least") || condition.contains("at_most") || 
               condition.contains("between");
    }

    private boolean containsStringValue(String condition) {
        return condition.contains("\"") || condition.contains("'");
    }

    private boolean containsLargeNumber(String action) {
        return NUMERIC_PATTERN.matcher(action).results()
            .anyMatch(match -> {
                try {
                    double value = Double.parseDouble(match.group());
                    return Math.abs(value) > 1_000_000;
                } catch (NumberFormatException e) {
                    return false;
                }
            });
    }

    private boolean containsImpossibleRange(String condition) {
        // Simple check for "between X and Y" where X > Y
        if (condition.contains("between") && condition.contains("and")) {
            String[] parts = condition.split("between")[1].split("and");
            if (parts.length == 2) {
                try {
                    double min = Double.parseDouble(parts[0].trim());
                    double max = Double.parseDouble(parts[1].trim());
                    return min > max;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        }
        return false;
    }

    private boolean containsFloatingPoint(String condition) {
        return NUMERIC_PATTERN.matcher(condition).results()
            .anyMatch(match -> match.group().contains("."));
    }

    private String extractVariableName(String expression) {
        if (expression.startsWith("calculate ")) {
            return expression.substring(10).trim();
        } else if (expression.startsWith("set ")) {
            return expression.substring(4).trim();
        }
        return null;
    }

    /**
     * Helper method to create logic validation issues
     */
    private ValidationResult.ValidationIssue createLogicIssue(
            String code, String message, String description, String location, String suggestion) {
        
        return ValidationResult.ValidationIssue.builder()
            .code(code)
            .severity(ValidationResult.ValidationSeverity.WARNING)
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
