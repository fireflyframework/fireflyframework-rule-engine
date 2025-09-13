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

import com.firefly.rules.core.dsl.ast.ASTVisitor;
import com.firefly.rules.core.dsl.ast.action.*;
import com.firefly.rules.core.dsl.ast.condition.ComparisonCondition;
import com.firefly.rules.core.dsl.ast.condition.ExpressionCondition;
import com.firefly.rules.core.dsl.ast.condition.LogicalCondition;
import com.firefly.rules.core.dsl.ast.expression.*;
import com.firefly.rules.core.dsl.ast.model.ASTRulesDSL;
import com.firefly.rules.core.dsl.ast.parser.ASTRulesDSLParser;
import com.firefly.rules.core.dsl.ast.visitor.ValidationError;
import com.firefly.rules.core.dsl.ast.visitor.ValidationVisitor;
import com.firefly.rules.interfaces.dtos.validation.ValidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Comprehensive YAML DSL validator that orchestrates all validation components.
 * Provides syntax, semantic, naming convention, and best practice validation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class YamlDslValidator {
    
    private final SyntaxValidator syntaxValidator;
    private final NamingConventionValidator namingValidator;
    private final ASTRulesDSLParser astParser;
    
    /**
     * Perform comprehensive validation of YAML DSL content
     *
     * @param yamlContent the YAML content to validate
     * @return comprehensive validation result
     */
    public ValidationResult validate(String yamlContent) {
        log.debug("Starting comprehensive YAML DSL validation");
        
        List<ValidationResult.ValidationIssue> allIssues = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. Syntax validation (must pass before other validations)
            List<ValidationResult.ValidationIssue> syntaxIssues = syntaxValidator.validate(yamlContent);
            allIssues.addAll(syntaxIssues);
            
            // If syntax validation fails critically, stop here
            boolean hasCriticalSyntaxErrors = syntaxIssues.stream()
                    .anyMatch(issue -> issue.getSeverity() == ValidationResult.ValidationSeverity.CRITICAL);
            
            if (!hasCriticalSyntaxErrors) {
                // 2. Parse to AST for semantic validation
                try {
                    ASTRulesDSL rulesDSL = astParser.parseRules(yamlContent);
                    
                    // 3. DSL Reference Compliance validation
                    allIssues.addAll(performDSLReferenceValidation(rulesDSL));

                    // 4. Semantic validation using AST
                    allIssues.addAll(performSemanticValidation(rulesDSL));

                    // 5. Naming convention validation
                    allIssues.addAll(performNamingValidation(rulesDSL));

                    // 6. Operator and function validation
                    allIssues.addAll(performOperatorValidation(rulesDSL));

                    // 7. Best practices validation
                    allIssues.addAll(performBestPracticesValidation(rulesDSL));
                    
                } catch (Exception e) {
                    log.warn("Failed to parse YAML for semantic validation: {}", e.getMessage());
                    allIssues.add(createIssue("PARSE_001", ValidationResult.ValidationSeverity.ERROR,
                            "Parsing failed", "Could not parse YAML for semantic validation: " + e.getMessage(),
                            "root", "Fix syntax errors first"));
                }
            }
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            // Build result
            return buildValidationResult(allIssues, executionTime);
            
        } catch (Exception e) {
            log.error("Validation failed with unexpected error", e);
            allIssues.add(createIssue("VAL_FATAL", ValidationResult.ValidationSeverity.CRITICAL,
                    "Validation failed", "Unexpected validation error: " + e.getMessage(),
                    "root", "Check YAML content and try again"));
            
            return buildValidationResult(allIssues, System.currentTimeMillis() - startTime);
        }
    }
    
    /**
     * Perform semantic validation using AST
     */
    private List<ValidationResult.ValidationIssue> performSemanticValidation(ASTRulesDSL rulesDSL) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();
        
        try {
            // Extract available variables and functions
            Set<String> availableVariables = extractAvailableVariables(rulesDSL);
            Set<String> availableFunctions = getBuiltInFunctions();
            
            // Create validation visitor
            ValidationVisitor validator = new ValidationVisitor(availableVariables, availableFunctions);
            
            // Validate all conditions
            if (rulesDSL.getWhenConditions() != null) {
                for (var condition : rulesDSL.getWhenConditions()) {
                    if (condition != null) {
                        List<ValidationError> errors = condition.accept(validator);
                        issues.addAll(convertValidationErrors(errors));
                    } else {
                        // Handle null condition - this indicates a parsing issue
                        issues.add(createIssue("PARSE_001", ValidationResult.ValidationSeverity.ERROR,
                                "Failed to parse condition", "One or more conditions could not be parsed",
                                "when", "Check condition syntax and ensure all operators are supported"));
                    }
                }
            }
            
            // Validate all actions
            if (rulesDSL.getThenActions() != null) {
                for (var action : rulesDSL.getThenActions()) {
                    if (action != null) {
                        List<ValidationError> errors = action.accept(validator);
                        issues.addAll(convertValidationErrors(errors));
                    } else {
                        issues.add(createIssue("PARSE_002", ValidationResult.ValidationSeverity.ERROR,
                                "Failed to parse action", "One or more actions could not be parsed",
                                "then", "Check action syntax and ensure all functions are supported"));
                    }
                }
            }

            if (rulesDSL.getElseActions() != null) {
                for (var action : rulesDSL.getElseActions()) {
                    if (action != null) {
                        List<ValidationError> errors = action.accept(validator);
                        issues.addAll(convertValidationErrors(errors));
                    } else {
                        issues.add(createIssue("PARSE_003", ValidationResult.ValidationSeverity.ERROR,
                                "Failed to parse action", "One or more else actions could not be parsed",
                                "else", "Check action syntax and ensure all functions are supported"));
                    }
                }
            }
            
        } catch (Exception e) {
            log.warn("Semantic validation failed: {}", e.getMessage());
            issues.add(createIssue("SEM_001", ValidationResult.ValidationSeverity.WARNING,
                    "Semantic validation incomplete", "Could not complete semantic validation: " + e.getMessage(),
                    "root", "Review rule structure"));
        }
        
        return issues;
    }
    
    /**
     * Perform naming convention validation
     */
    private List<ValidationResult.ValidationIssue> performNamingValidation(ASTRulesDSL rulesDSL) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();
        
        // Validate input variable names (should be camelCase)
        if (rulesDSL.getInput() != null) {
            for (String inputName : rulesDSL.getInput().keySet()) {
                if (!namingValidator.isValidInputVariableName(inputName)) {
                    issues.add(createIssue("NAMING_001", ValidationResult.ValidationSeverity.WARNING,
                            "Invalid input variable name", 
                            "Input variable '" + inputName + "' should use camelCase (e.g., creditScore)",
                            "input." + inputName, "Use camelCase naming"));
                }
            }
        }
        
        // Validate constant names (should be UPPER_CASE)
        if (rulesDSL.getConstants() != null) {
            for (var constant : rulesDSL.getConstants()) {
                if (constant.getCode() != null && !namingValidator.isValidConstantName(constant.getCode())) {
                    issues.add(createIssue("NAMING_002", ValidationResult.ValidationSeverity.WARNING,
                            "Invalid constant name",
                            "Constant '" + constant.getCode() + "' should use UPPER_CASE_WITH_UNDERSCORES",
                            "constants." + constant.getCode(), "Use UPPER_CASE naming"));
                }
            }
        }
        
        return issues;
    }
    
    /**
     * Perform best practices validation
     */
    private List<ValidationResult.ValidationIssue> performBestPracticesValidation(ASTRulesDSL rulesDSL) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();
        
        // Check for missing description
        if (rulesDSL.getDescription() == null || rulesDSL.getDescription().trim().isEmpty()) {
            issues.add(createIssue("BP_001", ValidationResult.ValidationSeverity.INFO,
                    "Missing description", "Rule should have a description for maintainability",
                    "description", "Add a meaningful description"));
        }
        
        // Check for missing version
        if (rulesDSL.getVersion() == null || rulesDSL.getVersion().trim().isEmpty()) {
            issues.add(createIssue("BP_002", ValidationResult.ValidationSeverity.INFO,
                    "Missing version", "Rule should have a version for tracking changes",
                    "version", "Add a version number"));
        }
        
        return issues;
    }
    
    /**
     * Extract available variables from the rule definition
     */
    private Set<String> extractAvailableVariables(ASTRulesDSL rulesDSL) {
        Set<String> variables = new HashSet<>();

        // Add input variables
        if (rulesDSL.getInput() != null) {
            variables.addAll(rulesDSL.getInput().keySet());
        }

        // Add explicitly declared constants
        if (rulesDSL.getConstants() != null) {
            Set<String> explicitConstants = rulesDSL.getConstants().stream()
                    .map(ASTRulesDSL.ASTConstantDefinition::getCode)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            variables.addAll(explicitConstants);
        }

        // Add database constants - auto-detect UPPER_CASE variables
        // These are constants that follow the UPPER_CASE_WITH_UNDERSCORES naming convention
        // and are automatically loaded from the database at runtime
        Set<String> detectedConstants = extractConstantReferences(rulesDSL);
        variables.addAll(detectedConstants);
        return variables;
    }

    /**
     * Extract constant references by scanning the AST for UPPER_CASE variable references
     * This mirrors the logic in ASTRulesEvaluationEngine.extractConstantReferences()
     */
    private Set<String> extractConstantReferences(ASTRulesDSL rulesDSL) {
        // Use a visitor to collect all variable references
        ValidationVariableCollector collector = new ValidationVariableCollector();

        // Collect from conditions
        if (rulesDSL.getWhenConditions() != null) {
            for (var condition : rulesDSL.getWhenConditions()) {
                if (condition != null) {
                    condition.accept(collector);
                }
            }
        }

        // Collect from then actions
        if (rulesDSL.getThenActions() != null) {
            for (var action : rulesDSL.getThenActions()) {
                if (action != null) {
                    action.accept(collector);
                }
            }
        }

        // Collect from else actions
        if (rulesDSL.getElseActions() != null) {
            for (var action : rulesDSL.getElseActions()) {
                if (action != null) {
                    action.accept(collector);
                }
            }
        }

        // Get all variable references and filter for UPPER_CASE constants (database constants)
        return collector.getVariableReferences().stream()
                .filter(this::isConstantReference)
                .collect(Collectors.toSet());
    }

    /**
     * Check if a variable reference is a constant (UPPER_CASE_WITH_UNDERSCORES)
     */
    private boolean isConstantReference(String variableName) {
        if (variableName == null || variableName.isEmpty()) {
            return false;
        }

        // Constants follow UPPER_CASE_WITH_UNDERSCORES naming convention
        // Must start with uppercase letter and contain only uppercase letters, numbers, and underscores
        return variableName.matches("^[A-Z][A-Z0-9_]*$");
    }

    /**
     * Simple visitor class to collect all variable references from AST nodes
     * This is used for validation purposes to detect database constants
     */
    private static class ValidationVariableCollector implements ASTVisitor<Void> {
        private final Set<String> variableReferences = new HashSet<>();

        public Set<String> getVariableReferences() {
            return variableReferences;
        }

        @Override
        public Void visitVariableExpression(VariableExpression node) {
            variableReferences.add(node.getVariableName());
            return null;
        }

        // Default implementations for all other visitor methods
        @Override
        public Void visitBinaryExpression(BinaryExpression node) {
            if (node.getLeft() != null) node.getLeft().accept(this);
            if (node.getRight() != null) node.getRight().accept(this);
            return null;
        }

        @Override
        public Void visitUnaryExpression(UnaryExpression node) {
            if (node.getOperand() != null) node.getOperand().accept(this);
            return null;
        }

        @Override
        public Void visitLiteralExpression(LiteralExpression node) {
            return null; // Literals don't contain variable references
        }

        @Override
        public Void visitFunctionCallExpression(FunctionCallExpression node) {
            if (node.getArguments() != null) {
                node.getArguments().forEach(arg -> arg.accept(this));
            }
            return null;
        }

        @Override
        public Void visitArithmeticExpression(ArithmeticExpression node) {
            if (node.getOperands() != null) {
                node.getOperands().forEach(operand -> operand.accept(this));
            }
            return null;
        }

        @Override
        public Void visitComparisonCondition(ComparisonCondition node) {
            if (node.getLeft() != null) node.getLeft().accept(this);
            if (node.getRight() != null) node.getRight().accept(this);
            return null;
        }

        @Override
        public Void visitLogicalCondition(LogicalCondition node) {
            if (node.getOperands() != null) {
                node.getOperands().forEach(operand -> operand.accept(this));
            }
            return null;
        }

        @Override
        public Void visitExpressionCondition(ExpressionCondition node) {
            if (node.getExpression() != null) node.getExpression().accept(this);
            return null;
        }

        @Override
        public Void visitAssignmentAction(AssignmentAction node) {
            if (node.getValue() != null) node.getValue().accept(this);
            return null;
        }

        @Override
        public Void visitFunctionCallAction(FunctionCallAction node) {
            if (node.getArguments() != null) {
                node.getArguments().forEach(arg -> arg.accept(this));
            }
            return null;
        }

        @Override
        public Void visitConditionalAction(ConditionalAction node) {
            if (node.getCondition() != null) node.getCondition().accept(this);
            if (node.getThenActions() != null) {
                node.getThenActions().forEach(action -> action.accept(this));
            }
            if (node.getElseActions() != null) {
                node.getElseActions().forEach(action -> action.accept(this));
            }
            return null;
        }

        @Override
        public Void visitCalculateAction(CalculateAction node) {
            if (node.getExpression() != null) node.getExpression().accept(this);
            return null;
        }

        @Override
        public Void visitSetAction(SetAction node) {
            if (node.getValue() != null) node.getValue().accept(this);
            return null;
        }

        @Override
        public Void visitArithmeticAction(ArithmeticAction node) {
            if (node.getValue() != null) node.getValue().accept(this);
            return null;
        }

        @Override
        public Void visitListAction(ListAction node) {
            if (node.getValue() != null) {
                node.getValue().accept(this);
            }
            return null;
        }

        @Override
        public Void visitCircuitBreakerAction(CircuitBreakerAction node) {
            // Circuit breaker actions don't contain variable references
            return null;
        }
    }
    
    /**
     * Get built-in function names
     */
    private Set<String> getBuiltInFunctions() {
        return Set.of(
                // Mathematical functions
                "max", "min", "abs", "round", "ceil", "floor", "sqrt", "pow",
                // String functions
                "length", "len", "substring", "substr", "upper", "lower", "trim",
                "contains", "startswith", "endswith", "replace",
                // Date/time functions
                "now", "today", "dateadd", "datediff", "time_hour",
                // List functions
                "size", "count", "sum", "avg", "average", "first", "last",
                // Type conversion functions
                "tonumber", "tostring", "toboolean",
                // Financial calculation functions
                "calculate_loan_payment", "calculate_compound_interest", "calculate_amortization",
                "debt_to_income_ratio", "credit_utilization", "loan_to_value", "calculate_apr",
                "calculate_credit_score", "calculate_risk_score", "payment_history_score",
                // Financial validation functions
                "is_valid_credit_score", "is_valid_ssn", "is_valid_account", "is_valid_routing",
                "is_business_day", "age_meets_requirement",
                // Utility functions
                "format_currency", "format_percentage", "generate_account_number",
                "generate_transaction_id", "distance_between", "is_valid", "in_range",
                // Logging/Auditing functions
                "audit", "audit_log", "send_notification",
                // Data Security functions
                "encrypt", "decrypt", "mask_data",
                // Advanced Financial functions
                "calculate_debt_ratio", "calculate_ltv", "calculate_payment_schedule"
        );
    }
    
    /**
     * Convert ValidationError objects to ValidationIssue objects
     */
    private List<ValidationResult.ValidationIssue> convertValidationErrors(List<ValidationError> errors) {
        return errors.stream()
                .map(error -> createIssue(error.getErrorCode(), ValidationResult.ValidationSeverity.ERROR,
                        "Semantic error", error.getMessage(), error.getLocation() != null ? error.getLocation().toString() : "unknown", "Fix the error"))
                .collect(Collectors.toList());
    }

    /**
     * Create a validation issue
     */
    private ValidationResult.ValidationIssue createIssue(String code, ValidationResult.ValidationSeverity severity,
                                                         String description, String message, String location, String suggestion) {
        return ValidationResult.ValidationIssue.builder()
                .code(code)
                .severity(severity)
                .description(description)
                .message(message)
                .location(ValidationResult.ValidationLocation.builder()
                        .path(location)
                        .build())
                .suggestion(suggestion)
                .build();
    }
    
    /**
     * Build the final validation result
     */
    private ValidationResult buildValidationResult(List<ValidationResult.ValidationIssue> issues, long executionTime) {
        // Determine overall status
        ValidationResult.ValidationStatus status = ValidationResult.ValidationStatus.VALID;
        if (issues.stream().anyMatch(i -> i.getSeverity() == ValidationResult.ValidationSeverity.CRITICAL)) {
            status = ValidationResult.ValidationStatus.CRITICAL_ERROR;
        } else if (issues.stream().anyMatch(i -> i.getSeverity() == ValidationResult.ValidationSeverity.ERROR)) {
            status = ValidationResult.ValidationStatus.ERROR;
        } else if (issues.stream().anyMatch(i -> i.getSeverity() == ValidationResult.ValidationSeverity.WARNING)) {
            status = ValidationResult.ValidationStatus.WARNING;
        }

        // Calculate quality score (0-100)
        double qualityScore = calculateQualityScore(issues);

        // Build summary
        ValidationResult.ValidationSummary summary = ValidationResult.ValidationSummary.builder()
                .totalIssues(issues.size())
                .criticalErrors((int) issues.stream().filter(i -> i.getSeverity() == ValidationResult.ValidationSeverity.CRITICAL).count())
                .errors((int) issues.stream().filter(i -> i.getSeverity() == ValidationResult.ValidationSeverity.ERROR).count())
                .warnings((int) issues.stream().filter(i -> i.getSeverity() == ValidationResult.ValidationSeverity.WARNING).count())
                .suggestions((int) issues.stream().filter(i -> i.getSeverity() == ValidationResult.ValidationSeverity.INFO).count())
                .qualityScore(qualityScore)
                .build();

        // Group issues by category
        ValidationResult.ValidationIssues groupedIssues = ValidationResult.ValidationIssues.builder()
                .syntax(issues.stream().filter(i -> i.getCode().startsWith("SYNTAX")).collect(Collectors.toList()))
                .naming(issues.stream().filter(i -> i.getCode().startsWith("NAMING")).collect(Collectors.toList()))
                .logic(issues.stream().filter(i -> i.getCode().startsWith("SEM") || i.getCode().startsWith("VAL") || i.getCode().startsWith("PARSE") || i.getCode().startsWith("DSL_REF") || i.getCode().startsWith("META")).collect(Collectors.toList()))
                .bestPractices(issues.stream().filter(i -> i.getCode().startsWith("BP")).collect(Collectors.toList()))
                .build();

        return ValidationResult.builder()
                .status(status)
                .summary(summary)
                .issues(groupedIssues)
                .build();
    }
    
    /**
     * Calculate quality score based on issues
     */
    private double calculateQualityScore(List<ValidationResult.ValidationIssue> issues) {
        if (issues.isEmpty()) {
            return 100.0;
        }

        double score = 100.0;
        for (ValidationResult.ValidationIssue issue : issues) {
            switch (issue.getSeverity()) {
                case CRITICAL -> score -= 25;
                case ERROR -> score -= 15;
                case WARNING -> score -= 5;
                case INFO -> score -= 1;
            }
        }

        return Math.max(0.0, score);
    }

    /**
     * Perform DSL Reference compliance validation
     */
    private List<ValidationResult.ValidationIssue> performDSLReferenceValidation(ASTRulesDSL rulesDSL) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // Validate required sections
        if (rulesDSL.getName() == null || rulesDSL.getName().trim().isEmpty()) {
            issues.add(createIssue("DSL_REF_001", ValidationResult.ValidationSeverity.ERROR,
                    "Missing required 'name' field", "Rule must have a descriptive name",
                    "name", "Add a meaningful rule name"));
        }

        if (rulesDSL.getDescription() == null || rulesDSL.getDescription().trim().isEmpty()) {
            issues.add(createIssue("DSL_REF_002", ValidationResult.ValidationSeverity.ERROR,
                    "Missing required 'description' field", "Rule must have a description explaining its purpose",
                    "description", "Add a clear description of what this rule does"));
        }

        // Note: The AST model uses 'input' (Map) but the YAML DSL uses 'inputs' (List)
        // We need to check if inputs were parsed and converted to the input map
        if (rulesDSL.getInput() == null || rulesDSL.getInput().isEmpty()) {
            issues.add(createIssue("DSL_REF_003", ValidationResult.ValidationSeverity.ERROR,
                    "Missing required 'inputs' section", "Rule must declare inputs variables as a list",
                    "inputs", "Add input variables that will be provided via API (e.g., inputs: [creditScore, annualIncome])"));
        }

        if (rulesDSL.getOutput() == null || rulesDSL.getOutput().isEmpty()) {
            issues.add(createIssue("DSL_REF_004", ValidationResult.ValidationSeverity.ERROR,
                    "Missing required 'output' section", "Rule must define output variables and their types",
                    "output", "Add output section mapping computed variables to types"));
        }

        // Validate rule logic structure
        boolean hasLogic = (rulesDSL.getWhenConditions() != null && !rulesDSL.getWhenConditions().isEmpty()) ||
                          (rulesDSL.getConditions() != null) ||
                          (rulesDSL.getRules() != null && !rulesDSL.getRules().isEmpty());

        if (!hasLogic) {
            issues.add(createIssue("DSL_REF_005", ValidationResult.ValidationSeverity.ERROR,
                    "Missing rule logic", "Rule must have 'when' conditions, 'conditions' block, or 'rules' array",
                    "root", "Add rule logic using 'when', 'conditions', or 'rules'"));
        }

        // Validate metadata section if present
        if (rulesDSL.getMetadata() != null) {
            issues.addAll(validateMetadataSection(rulesDSL.getMetadata()));
        }

        // Validate when/then structure
        if (rulesDSL.getWhenConditions() != null && !rulesDSL.getWhenConditions().isEmpty()) {
            if (rulesDSL.getThenActions() == null || rulesDSL.getThenActions().isEmpty()) {
                issues.add(createIssue("DSL_REF_006", ValidationResult.ValidationSeverity.ERROR,
                        "Missing 'then' actions", "When using 'when' conditions, 'then' actions are required",
                        "then", "Add actions to execute when conditions are met"));
            }
        }

        return issues;
    }

    /**
     * Validate metadata section
     */
    private List<ValidationResult.ValidationIssue> validateMetadataSection(Map<String, Object> metadata) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // Validate tags field
        if (metadata.containsKey("tags")) {
            Object tags = metadata.get("tags");
            if (!(tags instanceof List)) {
                issues.add(createIssue("META_001", ValidationResult.ValidationSeverity.WARNING,
                        "Invalid tags format", "Tags should be a list of strings",
                        "metadata.tags", "Use format: tags: [\"tag1\", \"tag2\"]"));
            } else {
                List<?> tagsList = (List<?>) tags;
                for (Object tag : tagsList) {
                    if (!(tag instanceof String)) {
                        issues.add(createIssue("META_002", ValidationResult.ValidationSeverity.WARNING,
                                "Invalid tag type", "All tags should be strings",
                                "metadata.tags", "Ensure all tags are quoted strings"));
                        break;
                    }
                }
            }
        }

        // Validate author field
        if (metadata.containsKey("author")) {
            Object author = metadata.get("author");
            if (!(author instanceof String) || ((String) author).trim().isEmpty()) {
                issues.add(createIssue("META_003", ValidationResult.ValidationSeverity.INFO,
                        "Invalid author format", "Author should be a non-empty string",
                        "metadata.author", "Use format: author: \"Team Name\""));
            }
        }

        // Validate category field
        if (metadata.containsKey("category")) {
            Object category = metadata.get("category");
            if (!(category instanceof String) || ((String) category).trim().isEmpty()) {
                issues.add(createIssue("META_004", ValidationResult.ValidationSeverity.INFO,
                        "Invalid category format", "Category should be a non-empty string",
                        "metadata.category", "Use format: category: \"Category Name\""));
            }
        }

        // Validate priority field
        if (metadata.containsKey("priority")) {
            Object priority = metadata.get("priority");
            if (!(priority instanceof Number)) {
                issues.add(createIssue("META_005", ValidationResult.ValidationSeverity.INFO,
                        "Invalid priority format", "Priority should be a number",
                        "metadata.priority", "Use format: priority: 1"));
            }
        }

        // Validate riskLevel field
        if (metadata.containsKey("riskLevel")) {
            Object riskLevel = metadata.get("riskLevel");
            if (!(riskLevel instanceof String)) {
                issues.add(createIssue("META_006", ValidationResult.ValidationSeverity.INFO,
                        "Invalid riskLevel format", "Risk level should be a string",
                        "metadata.riskLevel", "Use format: riskLevel: \"HIGH\""));
            } else {
                String level = ((String) riskLevel).toUpperCase();
                if (!Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(level)) {
                    issues.add(createIssue("META_007", ValidationResult.ValidationSeverity.INFO,
                            "Unknown risk level", "Risk level should be LOW, MEDIUM, HIGH, or CRITICAL",
                            "metadata.riskLevel", "Use one of: LOW, MEDIUM, HIGH, CRITICAL"));
                }
            }
        }

        return issues;
    }

    /**
     * Perform operator and function validation
     */
    private List<ValidationResult.ValidationIssue> performOperatorValidation(ASTRulesDSL rulesDSL) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // Get all documented operators from DSL reference
        Set<String> validOperators = getValidOperators();
        Set<String> validFunctions = getBuiltInFunctions();

        // Validate operators in conditions
        if (rulesDSL.getWhenConditions() != null) {
            for (var condition : rulesDSL.getWhenConditions()) {
                issues.addAll(validateOperatorsInCondition(condition, validOperators));
            }
        }

        // Validate functions in actions
        if (rulesDSL.getThenActions() != null) {
            for (var action : rulesDSL.getThenActions()) {
                issues.addAll(validateFunctionsInAction(action, validFunctions));
            }
        }

        if (rulesDSL.getElseActions() != null) {
            for (var action : rulesDSL.getElseActions()) {
                issues.addAll(validateFunctionsInAction(action, validFunctions));
            }
        }

        return issues;
    }

    /**
     * Get all valid operators from DSL reference
     */
    private Set<String> getValidOperators() {
        return Set.of(
                // Numeric comparisons
                "greater_than", ">", "less_than", "<", "at_least", ">=", "greater_than_or_equal",
                "less_than_or_equal", "<=", "at_most", "equals", "==", "not_equals", "!=",

                // Range comparisons
                "between", "not_between",

                // String comparisons
                "contains", "not_contains", "starts_with", "ends_with", "matches", "not_matches",

                // List comparisons
                "in_list", "in", "not_in_list", "not_in",

                // Validation operators
                "is_null", "is_not_null", "is_empty", "is_not_empty", "is_numeric", "is_not_numeric",
                "is_email", "is_phone", "is_date",

                // Length operators
                "length_equals", "length_greater_than", "length_less_than",

                // Financial operators
                "is_positive", "is_negative", "is_zero", "is_non_zero", "is_percentage", "is_currency",
                "is_credit_score", "is_ssn", "is_account_number", "is_routing_number", "is_business_day",
                "is_weekend", "age_at_least", "age_less_than",

                // Special operators
                "exists",

                // Logical operators
                "AND", "OR", "NOT", "and", "or", "not"
        );
    }

    /**
     * Validate operators in a condition
     */
    private List<ValidationResult.ValidationIssue> validateOperatorsInCondition(
            com.firefly.rules.core.dsl.ast.condition.Condition condition, Set<String> validOperators) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // This would need to be implemented based on the specific condition structure
        // For now, we'll do basic validation through the AST visitor

        return issues;
    }

    /**
     * Validate functions in an action
     */
    private List<ValidationResult.ValidationIssue> validateFunctionsInAction(
            com.firefly.rules.core.dsl.ast.action.Action action, Set<String> validFunctions) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // This would need to be implemented based on the specific action structure
        // For now, we'll do basic validation through the AST visitor

        return issues;
    }
}
