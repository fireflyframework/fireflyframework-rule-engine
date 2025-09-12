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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates variable dependencies and order-of-operations issues.
 * Detects problems like using computed variables before they're calculated.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DependencyValidator {

    private final NamingConventionValidator namingValidator;

    // Pattern to extract variable names from expressions
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b");

    /**
     * Validate variable dependencies and order-of-operations
     *
     * @param rulesDSL the parsed rules DSL
     * @return list of validation issues
     */
    public List<ValidationResult.ValidationIssue> validate(RulesDSL rulesDSL) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // Track available variables at each stage
        Set<String> inputVariables = extractInputVariables(rulesDSL);
        Set<String> computedVariables = new HashSet<>();
        Set<String> constantVariables = new HashSet<>(); // Will be resolved at runtime

        // Validate 'when' conditions
        if (rulesDSL.getWhenConditions() != null) {
            for (int i = 0; i < rulesDSL.getWhenConditions().size(); i++) {
                String condition = rulesDSL.getWhenConditions().get(i);
                issues.addAll(validateConditionDependencies(
                    condition, inputVariables, computedVariables, constantVariables, 
                    "when[" + i + "]", "condition"));
            }
        }

        // Validate 'then' actions and track computed variables
        if (rulesDSL.getThen() instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> thenActions = (List<String>) rulesDSL.getThen();
            
            for (int i = 0; i < thenActions.size(); i++) {
                String action = thenActions.get(i);
                
                // Check dependencies for this action
                issues.addAll(validateActionDependencies(
                    action, inputVariables, computedVariables, constantVariables,
                    "then[" + i + "]", "action"));
                
                // Track new computed variables created by this action
                String newVariable = extractComputedVariableFromAction(action);
                if (newVariable != null) {
                    computedVariables.add(newVariable);
                }
            }
        }

        // Validate 'else' actions
        if (rulesDSL.getElseAction() instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> elseActions = (List<String>) rulesDSL.getElseAction();
            
            for (int i = 0; i < elseActions.size(); i++) {
                String action = elseActions.get(i);
                issues.addAll(validateActionDependencies(
                    action, inputVariables, computedVariables, constantVariables,
                    "else[" + i + "]", "action"));
            }
        }

        // Check for unused input variables
        issues.addAll(validateUnusedVariables(rulesDSL, inputVariables, computedVariables));

        // Check for circular dependencies
        issues.addAll(validateCircularDependencies(rulesDSL));

        return issues;
    }

    /**
     * Extract input variables from the DSL
     */
    private Set<String> extractInputVariables(RulesDSL rulesDSL) {
        Set<String> inputs = new HashSet<>();
        if (rulesDSL.getInputs() != null) {
            inputs.addAll(rulesDSL.getInputs());
        }
        return inputs;
    }

    /**
     * Validate dependencies in a condition
     */
    private List<ValidationResult.ValidationIssue> validateConditionDependencies(
            String condition, Set<String> inputVars, Set<String> computedVars, 
            Set<String> constantVars, String location, String context) {
        
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();
        Set<String> referencedVars = extractVariablesFromExpression(condition);

        for (String variable : referencedVars) {
            NamingConventionValidator.VariableType varType = namingValidator.detectVariableType(variable);
            
            switch (varType) {
                case INPUT:
                    if (!inputVars.contains(variable)) {
                        issues.add(createDependencyIssue("DEP_001", 
                            "Undefined input variable: " + variable,
                            "Input variable '" + variable + "' is referenced but not declared in inputs",
                            location, "Add '" + variable + "' to the inputs list"));
                    }
                    break;
                    
                case COMPUTED:
                    if (!computedVars.contains(variable)) {
                        issues.add(createDependencyIssue("DEP_002", 
                            "Order-of-operations error: " + variable,
                            "Computed variable '" + variable + "' is used before it's calculated. " +
                            "This is the same bug we just fixed!",
                            location, "Move the calculation of '" + variable + "' before this condition, " +
                            "or use input variables in conditions instead"));
                    }
                    break;
                    
                case CONSTANT:
                    // Constants are resolved at runtime, so we just note them
                    constantVars.add(variable);
                    break;
                    
                case INVALID:
                    issues.add(createDependencyIssue("DEP_003", 
                        "Invalid variable naming: " + variable,
                        "Variable '" + variable + "' doesn't follow any recognized naming convention",
                        location, "Use camelCase for inputs, snake_case for computed, or UPPER_CASE for constants"));
                    break;
            }
        }

        return issues;
    }

    /**
     * Validate dependencies in an action
     */
    private List<ValidationResult.ValidationIssue> validateActionDependencies(
            String action, Set<String> inputVars, Set<String> computedVars, 
            Set<String> constantVars, String location, String context) {
        
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();
        
        // Extract variables used in the action (right side of assignments)
        Set<String> referencedVars = extractVariablesFromAction(action);

        for (String variable : referencedVars) {
            NamingConventionValidator.VariableType varType = namingValidator.detectVariableType(variable);
            
            switch (varType) {
                case INPUT:
                    if (!inputVars.contains(variable)) {
                        issues.add(createDependencyIssue("DEP_004", 
                            "Undefined input variable: " + variable,
                            "Input variable '" + variable + "' is referenced but not declared in inputs",
                            location, "Add '" + variable + "' to the inputs list"));
                    }
                    break;
                    
                case COMPUTED:
                    if (!computedVars.contains(variable)) {
                        issues.add(createDependencyIssue("DEP_005", 
                            "Computed variable used before calculation: " + variable,
                            "Computed variable '" + variable + "' is used before it's calculated",
                            location, "Calculate '" + variable + "' in an earlier action"));
                    }
                    break;
                    
                case CONSTANT:
                    constantVars.add(variable);
                    break;
                    
                case INVALID:
                    issues.add(createDependencyIssue("DEP_006", 
                        "Invalid variable naming: " + variable,
                        "Variable '" + variable + "' doesn't follow any recognized naming convention",
                        location, "Use camelCase for inputs, snake_case for computed, or UPPER_CASE for constants"));
                    break;
            }
        }

        return issues;
    }

    /**
     * Extract variables referenced in an expression
     */
    private Set<String> extractVariablesFromExpression(String expression) {
        Set<String> variables = new HashSet<>();

        // Remove string literals first to avoid extracting variables from within quotes
        String cleanExpression = removeStringLiterals(expression);

        Matcher matcher = VARIABLE_PATTERN.matcher(cleanExpression);

        while (matcher.find()) {
            String variable = matcher.group(1);
            // Skip operators, keywords, literals, and numeric values
            if (!isOperatorOrKeyword(variable) && !isNumericLiteral(variable)) {
                variables.add(variable);
            }
        }

        return variables;
    }

    /**
     * Remove string literals from expression to avoid false variable detection
     */
    private String removeStringLiterals(String expression) {
        // Remove double-quoted strings
        String result = expression.replaceAll("\"[^\"]*\"", "\"\"");
        // Remove single-quoted strings
        result = result.replaceAll("'[^']*'", "''");
        return result;
    }

    /**
     * Check if a string represents a numeric literal
     */
    private boolean isNumericLiteral(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Extract variables used in an action (excluding the assigned variable)
     */
    private Set<String> extractVariablesFromAction(String action) {
        Set<String> variables = new HashSet<>();
        
        // For "calculate X as Y + Z", we want Y and Z, not X
        if (action.contains(" as ")) {
            String[] parts = action.split(" as ", 2);
            if (parts.length == 2) {
                variables.addAll(extractVariablesFromExpression(parts[1]));
            }
        } else if (action.contains(" to ")) {
            // For "set X to Y", we want Y, not X
            String[] parts = action.split(" to ", 2);
            if (parts.length == 2) {
                variables.addAll(extractVariablesFromExpression(parts[1]));
            }
        } else {
            // For other actions, extract all variables
            variables.addAll(extractVariablesFromExpression(action));
        }
        
        return variables;
    }

    /**
     * Extract the computed variable name from an action
     */
    private String extractComputedVariableFromAction(String action) {
        if (action.startsWith("calculate ") && action.contains(" as ")) {
            String[] parts = action.substring(10).split(" as ", 2);
            if (parts.length == 2) {
                return parts[0].trim();
            }
        } else if (action.startsWith("set ") && action.contains(" to ")) {
            String[] parts = action.substring(4).split(" to ", 2);
            if (parts.length == 2) {
                return parts[0].trim();
            }
        }
        return null;
    }

    /**
     * Check if a word is an operator, keyword, or literal value
     */
    private boolean isOperatorOrKeyword(String word) {
        Set<String> keywords = Set.of(
            // Logical operators
            "and", "or", "not",
            // Control flow
            "if", "then", "else",
            // Assignment operators
            "as", "to", "from", "by", "with",
            // Comparison operators
            "at_least", "at_most", "greater_than", "less_than", "equals", "not_equals",
            "between", "not_between", "contains", "not_contains", "starts_with", "ends_with",
            "matches", "not_matches", "in_list", "not_in_list", "in", "not_in",
            "is_null", "is_not_null", "is_empty", "is_not_empty", "is_numeric", "is_not_numeric",
            "is_email", "is_phone", "is_date", "length_equals", "length_greater_than", "length_less_than",
            "exists", "within_range", "outside_range",
            // Action keywords
            "calculate", "set", "add", "subtract", "multiply", "divide", "call", "append", "prepend", "remove",
            // Boolean literals
            "true", "false",
            // Null literal
            "null"
        );
        return keywords.contains(word.toLowerCase());
    }

    /**
     * Validate for unused variables
     */
    private List<ValidationResult.ValidationIssue> validateUnusedVariables(
            RulesDSL rulesDSL, Set<String> inputVars, Set<String> computedVars) {
        
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();
        
        // Check for unused input variables
        Set<String> usedInputs = new HashSet<>();
        
        // Collect all variable references
        if (rulesDSL.getWhenConditions() != null) {
            for (String condition : rulesDSL.getWhenConditions()) {
                usedInputs.addAll(extractVariablesFromExpression(condition));
            }
        }
        
        if (rulesDSL.getThen() instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> actions = (List<String>) rulesDSL.getThen();
            for (String action : actions) {
                usedInputs.addAll(extractVariablesFromAction(action));
            }
        }
        
        // Find unused inputs
        for (String inputVar : inputVars) {
            if (!usedInputs.contains(inputVar)) {
                issues.add(createDependencyIssue("DEP_007", 
                    "Unused input variable: " + inputVar,
                    "Input variable '" + inputVar + "' is declared but never used",
                    "inputs", "Remove unused variable or use it in conditions/actions"));
            }
        }
        
        return issues;
    }

    /**
     * Validate for circular dependencies
     */
    private List<ValidationResult.ValidationIssue> validateCircularDependencies(RulesDSL rulesDSL) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();
        
        // Build dependency graph
        Map<String, Set<String>> dependencies = new HashMap<>();
        
        if (rulesDSL.getThen() instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> actions = (List<String>) rulesDSL.getThen();
            
            for (String action : actions) {
                String computedVar = extractComputedVariableFromAction(action);
                if (computedVar != null) {
                    Set<String> deps = extractVariablesFromAction(action);
                    dependencies.put(computedVar, deps);
                }
            }
        }
        
        // Check for circular dependencies using DFS
        for (String variable : dependencies.keySet()) {
            if (hasCircularDependency(variable, dependencies, new HashSet<>(), new HashSet<>())) {
                issues.add(createDependencyIssue("DEP_008", 
                    "Circular dependency detected: " + variable,
                    "Variable '" + variable + "' has a circular dependency",
                    "then", "Remove circular dependency by restructuring calculations"));
            }
        }
        
        return issues;
    }

    /**
     * Check for circular dependency using DFS
     */
    private boolean hasCircularDependency(String variable, Map<String, Set<String>> dependencies, 
                                        Set<String> visiting, Set<String> visited) {
        if (visiting.contains(variable)) {
            return true; // Circular dependency found
        }
        if (visited.contains(variable)) {
            return false; // Already processed
        }
        
        visiting.add(variable);
        
        Set<String> deps = dependencies.get(variable);
        if (deps != null) {
            for (String dep : deps) {
                if (dependencies.containsKey(dep)) { // Only check computed variables
                    if (hasCircularDependency(dep, dependencies, visiting, visited)) {
                        return true;
                    }
                }
            }
        }
        
        visiting.remove(variable);
        visited.add(variable);
        return false;
    }

    /**
     * Helper method to create dependency validation issues
     */
    private ValidationResult.ValidationIssue createDependencyIssue(
            String code, String message, String description, String location, String suggestion) {
        
        return ValidationResult.ValidationIssue.builder()
            .code(code)
            .severity(ValidationResult.ValidationSeverity.ERROR)
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
