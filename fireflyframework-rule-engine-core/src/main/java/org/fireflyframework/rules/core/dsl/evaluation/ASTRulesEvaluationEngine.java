/*
 * Copyright 2024-2026 Firefly Software Solutions Inc
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

package org.fireflyframework.rules.core.dsl.evaluation;

import org.fireflyframework.rules.core.dsl.ASTVisitor;
import org.fireflyframework.rules.core.dsl.action.*;
import org.fireflyframework.rules.core.dsl.condition.ComparisonCondition;
import org.fireflyframework.rules.core.dsl.condition.Condition;
import org.fireflyframework.rules.core.dsl.condition.ExpressionCondition;
import org.fireflyframework.rules.core.dsl.condition.LogicalCondition;
import org.fireflyframework.rules.core.dsl.expression.*;
import org.fireflyframework.rules.core.dsl.model.ASTRulesDSL;
import org.fireflyframework.rules.core.dsl.parser.ASTRulesDSLParser;
import org.fireflyframework.rules.core.dsl.visitor.ActionExecutor;
import org.fireflyframework.rules.core.dsl.visitor.EvaluationContext;
import org.fireflyframework.rules.core.dsl.visitor.ExpressionEvaluator;
import org.fireflyframework.rules.core.services.ConstantService;
import org.fireflyframework.rules.core.services.JsonPathService;
import org.fireflyframework.rules.core.services.RestCallService;
import org.fireflyframework.rules.core.services.impl.JsonPathServiceImpl;
import org.fireflyframework.rules.core.services.impl.RestCallServiceImpl;
import org.fireflyframework.rules.core.utils.JsonLogger;
import org.fireflyframework.rules.interfaces.dtos.crud.ConstantDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Pure AST-based rules evaluation engine with no legacy dependencies.
 * Handles all rule evaluation using AST parsing and visitor patterns.
 */
@Component
@Slf4j
public class ASTRulesEvaluationEngine {

    private final ASTRulesDSLParser parser;
    private final ConstantService constantService;
    private final RestCallService restCallService;
    private final JsonPathService jsonPathService;

    /**
     * Primary constructor for Spring dependency injection
     * RestCallService and JsonPathService are optional and will use defaults if not provided
     */
    @Autowired
    public ASTRulesEvaluationEngine(ASTRulesDSLParser parser,
                                   ConstantService constantService,
                                   @Autowired(required = false) RestCallService restCallService,
                                   @Autowired(required = false) JsonPathService jsonPathService) {
        this.parser = Objects.requireNonNull(parser, "ASTRulesDSLParser cannot be null");
        this.constantService = Objects.requireNonNull(constantService, "ConstantService cannot be null");
        this.restCallService = restCallService != null ? restCallService : new RestCallServiceImpl();
        this.jsonPathService = jsonPathService != null ? jsonPathService : new JsonPathServiceImpl();
    }

    /**
     * Constructor with default REST and JSON services (for testing)
     */
    public ASTRulesEvaluationEngine(ASTRulesDSLParser parser, ConstantService constantService) {
        this.parser = Objects.requireNonNull(parser, "ASTRulesDSLParser cannot be null");
        this.constantService = Objects.requireNonNull(constantService, "ConstantService cannot be null");
        this.restCallService = new RestCallServiceImpl();
        this.jsonPathService = new JsonPathServiceImpl();
    }
    
    /**
     * Evaluate rules against the provided input data
     */
    public Mono<ASTRulesEvaluationResult> evaluateRulesReactive(String rulesDefinition, Map<String, Object> inputData) {
        long startTime = System.currentTimeMillis();
        return parser.parseRulesReactive(rulesDefinition)
                .flatMap(rulesDSL -> createEvaluationContextReactive(rulesDSL, inputData)
                        .map(context -> evaluateRules(rulesDSL, context)))
                .onErrorResume(error -> {
                    long executionTime = System.currentTimeMillis() - startTime;
                    JsonLogger.error(log, "Rules evaluation failed", error);
                    String errorMessage = error.getMessage();
                    if (errorMessage == null || errorMessage.trim().isEmpty()) {
                        errorMessage = "Unknown error occurred during rule evaluation: " + error.getClass().getSimpleName();
                    }
                    JsonLogger.info(log, "Creating error result with message: " + errorMessage);
                    return Mono.just(ASTRulesEvaluationResult.builder()
                            .success(false)
                            .error(errorMessage)
                            .executionTimeMs(executionTime)
                            .build());
                });
    }
    
    /**
     * Evaluate rules against the provided input data (synchronous version)
     */
    public ASTRulesEvaluationResult evaluateRules(String rulesDefinition, Map<String, Object> inputData) {
        return evaluateRulesReactive(rulesDefinition, inputData).block();
    }
    
    /**
     * Evaluate a parsed ASTRulesDSL against the provided input data
     */
    public Mono<ASTRulesEvaluationResult> evaluateRulesReactive(ASTRulesDSL rulesDSL, Map<String, Object> inputData) {
        long startTime = System.currentTimeMillis();
        return createEvaluationContextReactive(rulesDSL, inputData)
                .map(context -> evaluateRules(rulesDSL, context))
                .onErrorResume(error -> {
                    long executionTime = System.currentTimeMillis() - startTime;
                    JsonLogger.error(log, "Rules evaluation failed", error);

                    String errorMessage = error.getMessage();
                    if (errorMessage == null || errorMessage.trim().isEmpty()) {
                        errorMessage = "Unknown error occurred during rule evaluation: " + error.getClass().getSimpleName();
                    }
                    JsonLogger.info(log, "Creating error result with message: " + errorMessage);

                    return Mono.just(ASTRulesEvaluationResult.builder()
                            .success(false)
                            .error(errorMessage)
                            .executionTimeMs(executionTime)
                            .build());
                });
    }
    
    /**
     * Core rule evaluation logic using pure AST
     */
    private ASTRulesEvaluationResult evaluateRules(ASTRulesDSL rulesDSL, EvaluationContext context) {
        String operationId = context.getOperationId();

        // Log input data
        JsonLogger.info(log, operationId, "Input data received: " + context.getInputVariables());

        // Log constants loaded
        if (!context.getSystemConstants().isEmpty()) {
            JsonLogger.info(log, operationId, "Constants loaded from database: " + context.getSystemConstants().keySet());
        }

        boolean conditionResult = false;  // Declare outside try block for circuit breaker catch

        try {
            
            // Handle simplified syntax (when/then/else)
            if (rulesDSL.isSimpleSyntax()) {
                conditionResult = evaluateConditions(rulesDSL.getWhenConditions(), context);

                if (conditionResult && rulesDSL.getThenActions() != null) {
                    executeActions(rulesDSL.getThenActions(), context);
                } else if (!conditionResult && rulesDSL.getElseActions() != null) {
                    executeActions(rulesDSL.getElseActions(), context);
                }
            }
            // Handle rules with only then actions (no when conditions)
            else if (rulesDSL.getThenActions() != null && !rulesDSL.getThenActions().isEmpty()) {
                conditionResult = true; // Always true for unconditional actions
                executeActions(rulesDSL.getThenActions(), context);
            }
            // Handle multiple rules syntax
            else if (rulesDSL.isMultipleRulesSyntax()) {
                conditionResult = evaluateMultipleRules(rulesDSL.getRules(), context);
            }
            // Handle complex conditions syntax
            else if (rulesDSL.isComplexConditionsSyntax()) {
                conditionResult = evaluateConditionalBlock(rulesDSL.getConditions(), context);
            }
            
            // Generate output
            Map<String, Object> outputData = generateOutput(rulesDSL, context, conditionResult);
            long executionTime = System.currentTimeMillis() - context.getStartTime();

            // Log final output data
            JsonLogger.info(log, operationId, "Rule evaluation output: " + outputData);
            JsonLogger.info(log, operationId, "Computed variables: " + context.getComputedVariables());
            JsonLogger.info(log, operationId, "Condition result: " + conditionResult);
            JsonLogger.info(log, operationId, "Execution time: " + executionTime + "ms");

            JsonLogger.info(log, operationId, "AST-based rule evaluation completed successfully");

            return ASTRulesEvaluationResult.builder()
                    .success(true)
                    .conditionResult(conditionResult)
                    .outputData(outputData)
                    .executionTimeMs(executionTime)
                    .circuitBreakerTriggered(context.isCircuitBreakerTriggered())
                    .circuitBreakerMessage(context.getCircuitBreakerMessage())
                    .build();

        } catch (org.fireflyframework.rules.core.dsl.exception.CircuitBreakerException e) {
            // Circuit breaker is a controlled stop, not an error
            long executionTime = System.currentTimeMillis() - context.getStartTime();
            Map<String, Object> outputData = generateOutput(rulesDSL, context, conditionResult);

            JsonLogger.info(log, operationId, "Rule evaluation stopped by circuit breaker: " + e.getCircuitBreakerMessage());
            JsonLogger.info(log, operationId, "Execution time: " + executionTime + "ms");

            return ASTRulesEvaluationResult.builder()
                    .success(true)  // Circuit breaker is a controlled stop, not an error
                    .conditionResult(conditionResult)
                    .outputData(outputData)
                    .executionTimeMs(executionTime)
                    .circuitBreakerTriggered(true)
                    .circuitBreakerMessage(e.getCircuitBreakerMessage())
                    .build();

        } catch (Exception e) {
            JsonLogger.error(log, operationId, "Error during AST-based rules evaluation", e);
            return ASTRulesEvaluationResult.builder()
                    .success(false)
                    .error(e.getMessage())
                    .build();
        }
    }
    
    /**
     * Evaluate a list of conditions using AST
     */
    private boolean evaluateConditions(List<Condition> conditions, EvaluationContext context) {
        if (conditions == null || conditions.isEmpty()) {
            JsonLogger.debug(log, context.getOperationId(), "No conditions to evaluate - returning true");
            return true;
        }

        JsonLogger.info(log, context.getOperationId(), "Evaluating " + conditions.size() + " condition(s)");

        for (int i = 0; i < conditions.size(); i++) {
            Condition condition = conditions.get(i);
            try {
                ExpressionEvaluator evaluator = new ExpressionEvaluator(context, restCallService, jsonPathService);
                Object result = condition.accept(evaluator);
                boolean boolResult = toBoolean(result);

                JsonLogger.info(log, context.getOperationId(),
                    String.format("Condition %d evaluation: %s = %s", i + 1, condition.toDebugString(), boolResult));

                if (!boolResult) {
                    JsonLogger.info(log, context.getOperationId(), "Condition failed - short-circuiting evaluation");
                    return false;
                }
            } catch (Exception e) {
                String operationId = context.getOperationId();
                JsonLogger.error(log, operationId, "Error evaluating condition: " + condition.toDebugString(), e);
                return false;
            }
        }
        JsonLogger.info(log, context.getOperationId(), "All conditions passed");
        return true;
    }
    
    /**
     * Execute a list of actions using AST
     */
    private void executeActions(List<Action> actions, EvaluationContext context) {
        if (actions == null || actions.isEmpty()) {
            JsonLogger.debug(log, context.getOperationId(), "No actions to execute");
            return;
        }

        JsonLogger.info(log, context.getOperationId(), "Executing " + actions.size() + " action(s)");

        for (int i = 0; i < actions.size(); i++) {
            Action action = actions.get(i);
            try {
                JsonLogger.info(log, context.getOperationId(),
                    String.format("Executing action %d: %s", i + 1, action.toDebugString()));

                ActionExecutor executor = new ActionExecutor(context, restCallService, jsonPathService);
                action.accept(executor);

                JsonLogger.info(log, context.getOperationId(),
                    String.format("Action %d completed successfully", i + 1));
            } catch (org.fireflyframework.rules.core.dsl.exception.CircuitBreakerException e) {
                // Circuit breaker is a controlled stop, not an error
                JsonLogger.info(log, context.getOperationId(),
                    "Circuit breaker triggered: " + e.getCircuitBreakerMessage() + " - stopping execution");
                // Re-throw to stop execution immediately
                throw e;
            } catch (Exception e) {
                String operationId = context.getOperationId();
                JsonLogger.error(log, operationId, "Error executing action: " + action.toDebugString(), e);
            }
        }
        JsonLogger.info(log, context.getOperationId(), "All actions completed");
    }
    
    /**
     * Evaluate multiple rules using AST
     */
    private boolean evaluateMultipleRules(List<ASTRulesDSL.ASTSubRule> rules, EvaluationContext context) {
        boolean anyConditionMet = false;

        JsonLogger.info(log, context.getOperationId(), "Evaluating " + rules.size() + " sub-rule(s)");

        for (int i = 0; i < rules.size(); i++) {
            ASTRulesDSL.ASTSubRule rule = rules.get(i);
            boolean ruleConditionMet = false;

            String ruleName = rule.getName() != null ? rule.getName() : "Rule " + (i + 1);
            JsonLogger.info(log, context.getOperationId(), "Evaluating sub-rule: " + ruleName);

            // Check if rule has simplified conditions
            if (rule.getWhenConditions() != null && !rule.getWhenConditions().isEmpty()) {
                ruleConditionMet = evaluateConditions(rule.getWhenConditions(), context);

                if (ruleConditionMet && rule.getThenActions() != null) {
                    JsonLogger.info(log, context.getOperationId(), "Sub-rule " + ruleName + " conditions met - executing THEN actions");
                    executeActions(rule.getThenActions(), context);
                } else if (!ruleConditionMet && rule.getElseActions() != null) {
                    JsonLogger.info(log, context.getOperationId(), "Sub-rule " + ruleName + " conditions not met - executing ELSE actions");
                    executeActions(rule.getElseActions(), context);
                }
            }
            // Check if rule has complex conditions block
            else if (rule.getConditions() != null) {
                JsonLogger.info(log, context.getOperationId(), "Sub-rule " + ruleName + " using complex conditions syntax");
                ruleConditionMet = evaluateConditionalBlock(rule.getConditions(), context);
            }
            // Handle rules with only then actions (no when conditions) - execute unconditionally
            else if (rule.getThenActions() != null && !rule.getThenActions().isEmpty()) {
                JsonLogger.info(log, context.getOperationId(), "Sub-rule " + ruleName + " has no conditions - executing THEN actions unconditionally");
                executeActions(rule.getThenActions(), context);
                ruleConditionMet = true; // Always true for unconditional actions
            }

            JsonLogger.info(log, context.getOperationId(), "Sub-rule " + ruleName + " result: " + ruleConditionMet);

            if (ruleConditionMet) {
                anyConditionMet = true;
            }
        }

        JsonLogger.info(log, context.getOperationId(), "Multiple rules evaluation complete - any condition met: " + anyConditionMet);
        return anyConditionMet;
    }
    
    /**
     * Evaluate a conditional block using AST - this handles nested conditions properly
     */
    private boolean evaluateConditionalBlock(ASTRulesDSL.ASTConditionalBlock conditionalBlock, EvaluationContext context) {
        if (conditionalBlock == null || conditionalBlock.getIfCondition() == null) {
            return false;
        }
        
        try {
            // Evaluate the condition using AST
            ExpressionEvaluator evaluator = new ExpressionEvaluator(context, restCallService, jsonPathService);
            Object result = conditionalBlock.getIfCondition().accept(evaluator);
            boolean conditionResult = toBoolean(result);
            
            // Execute appropriate action block
            ASTRulesDSL.ASTActionBlock actionBlock = conditionResult ? 
                    conditionalBlock.getThenBlock() : 
                    conditionalBlock.getElseBlock();
            
            if (actionBlock != null) {
                executeActionBlock(actionBlock, context);
            }
            
            return conditionResult;
            
        } catch (Exception e) {
            String operationId = context.getOperationId();
            JsonLogger.error(log, operationId, "Error evaluating conditional block", e);
            return false;
        }
    }
    
    /**
     * Execute an action block with full AST support including nested conditions
     */
    private void executeActionBlock(ASTRulesDSL.ASTActionBlock actionBlock, EvaluationContext context) {
        if (actionBlock == null) {
            return;
        }
        
        String operationId = context.getOperationId();
        JsonLogger.info(log, operationId, "Executing AST action block");
        
        // Execute individual actions
        if (actionBlock.getActions() != null) {
            executeActions(actionBlock.getActions(), context);
        }
        
        // Handle nested conditional blocks - this was the TODO that's now implemented!
        if (actionBlock.getNestedConditions() != null) {
            evaluateConditionalBlock(actionBlock.getNestedConditions(), context);
        }
    }
    
    /**
     * Create evaluation context with AST support
     */
    private Mono<EvaluationContext> createEvaluationContextReactive(ASTRulesDSL rulesDSL, Map<String, Object> inputData) {
        JsonLogger.info(log, "createEvaluationContextReactive called for rule: " + rulesDSL.getName());
        String operationId = UUID.randomUUID().toString();
        EvaluationContext context = new EvaluationContext(operationId, inputData != null ? inputData : new HashMap<>());

        // Log the start of rule evaluation early so it appears even if constants loading fails
        JsonLogger.info(log, operationId, "Starting AST-based rule evaluation: " + rulesDSL.getName());

        // Load system constants and wait for completion
        return loadSystemConstants(rulesDSL)
                .map(constants -> {

                    JsonLogger.info(log, "Setting " + constants.size() + " constants in evaluation context");
                    constants.forEach(context::setConstant);
                    return context;
                })
                .doOnError(error -> JsonLogger.error(log, "Error in loadSystemConstants", error));
    }
    
    /**
     * Load system constants from database using ConstantService
     * Automatically detects constants by scanning for UPPER_CASE variable references
     */
    private Mono<Map<String, Object>> loadSystemConstants(ASTRulesDSL rulesDSL) {

        JsonLogger.info(log, "loadSystemConstants called for rule: " + rulesDSL.getName());
        Map<String, Object> constants = new HashMap<>();

        // Auto-detect constants by scanning the AST for UPPER_CASE variable references
        Set<String> detectedConstants = extractConstantReferences(rulesDSL);

        // Also include explicitly declared constants (for backward compatibility)
        if (rulesDSL.getConstants() != null && !rulesDSL.getConstants().isEmpty()) {
            rulesDSL.getConstants().stream()
                    .map(ASTRulesDSL.ASTConstantDefinition::getCode)
                    .filter(code -> code != null && !code.trim().isEmpty())
                    .forEach(detectedConstants::add);
        }

        if (!detectedConstants.isEmpty()) {
            List<String> constantCodes = new ArrayList<>(detectedConstants);

            JsonLogger.info(log, "Loading constants from database: " + constantCodes);

            JsonLogger.info(log, "About to call constantService.getConstantsByCodes with: " + constantCodes);

            return constantService.getConstantsByCodes(constantCodes)
                    .doOnNext(constantDTO -> JsonLogger.info(log, "Found constant in DB: " + constantDTO.getCode()))
                    .doOnComplete(() -> JsonLogger.info(log, "ConstantService flux completed"))
                    .collectList()
                    .doOnNext(constantDTOs -> JsonLogger.info(log, "ConstantService returned " + constantDTOs.size() + " constants"))
                    .flatMap(constantDTOs -> {
                        Map<String, Object> loadedConstants = new HashMap<>();
                        Set<String> foundConstants = new HashSet<>();

                        // Map database constants to evaluation context
                        for (ConstantDTO constantDTO : constantDTOs) {
                            if (constantDTO.getCode() != null) {
                                loadedConstants.put(constantDTO.getCode(), constantDTO.getCurrentValue());
                                foundConstants.add(constantDTO.getCode());
                                JsonLogger.info(log, "Loaded constant: " + constantDTO.getCode() + " = " + constantDTO.getCurrentValue());
                            }
                        }

                        // Check for missing constants
                        Set<String> missingConstants = new HashSet<>(detectedConstants);
                        missingConstants.removeAll(foundConstants);
                        JsonLogger.info(log, "Detected constants: " + detectedConstants + ", Found constants: " + foundConstants + ", Missing constants: " + missingConstants);

                        // Add default values for constants not found in database (if explicitly declared)
                        if (rulesDSL.getConstants() != null) {
                            for (ASTRulesDSL.ASTConstantDefinition constantDef : rulesDSL.getConstants()) {
                                if (constantDef.getCode() != null &&
                                    !loadedConstants.containsKey(constantDef.getCode()) &&
                                    constantDef.getDefaultValue() != null) {
                                    loadedConstants.put(constantDef.getCode(), constantDef.getDefaultValue());
                                    missingConstants.remove(constantDef.getCode());
                                    JsonLogger.warn(log, "Using default value for constant: " + constantDef.getCode());
                                }
                            }
                        }

                        // If there are still missing constants without default values, fail the evaluation
                        if (!missingConstants.isEmpty()) {
                            String missingConstantsList = String.join(", ", missingConstants);
                            String errorMessage = "Required constants not found in database and no default values provided: " + missingConstantsList;

                            IllegalArgumentException exception = new IllegalArgumentException(errorMessage);
                            JsonLogger.error(log, errorMessage, exception);
                            return Mono.error(exception);
                        }

                        return Mono.just(loadedConstants);
                    });
        }

        return Mono.just(constants);
    }

    /**
     * Extract constant references from the AST by scanning for UPPER_CASE variable names
     */
    private Set<String> extractConstantReferences(ASTRulesDSL rulesDSL) {
        VariableReferenceCollector collector = new VariableReferenceCollector();

        // Collect from simple syntax conditions
        if (rulesDSL.getWhenConditions() != null) {
            rulesDSL.getWhenConditions().forEach(condition -> condition.accept(collector));
        }

        // Collect from simple syntax actions
        if (rulesDSL.getThenActions() != null) {
            rulesDSL.getThenActions().forEach(action -> action.accept(collector));
        }
        if (rulesDSL.getElseActions() != null) {
            rulesDSL.getElseActions().forEach(action -> action.accept(collector));
        }

        // Collect from complex syntax (multiple rules)
        if (rulesDSL.getRules() != null) {
            for (ASTRulesDSL.ASTSubRule subRule : rulesDSL.getRules()) {
                if (subRule.getWhenConditions() != null) {
                    subRule.getWhenConditions().forEach(condition -> condition.accept(collector));
                }
                if (subRule.getThenActions() != null) {
                    subRule.getThenActions().forEach(action -> action.accept(collector));
                }
                if (subRule.getElseActions() != null) {
                    subRule.getElseActions().forEach(action -> action.accept(collector));
                }
                if (subRule.getConditions() != null) {
                    collectFromConditionalBlock(subRule.getConditions(), collector);
                }
            }
        }

        // Collect from complex conditions block
        if (rulesDSL.getConditions() != null) {
            collectFromConditionalBlock(rulesDSL.getConditions(), collector);
        }

        // Debug logging
        Set<String> allVariables = collector.getVariableReferences();
        JsonLogger.info(log, "All variable references found: " + allVariables);

        // Filter for UPPER_CASE constants only
        Set<String> constants = collector.getVariableReferences().stream()
                .filter(this::isConstantName)
                .collect(Collectors.toSet());

        JsonLogger.info(log, "Filtered constants (UPPER_CASE): " + constants);
        return constants;
    }

    /**
     * Collect variable references from a conditional block
     */
    private void collectFromConditionalBlock(ASTRulesDSL.ASTConditionalBlock conditionalBlock, VariableReferenceCollector collector) {
        if (conditionalBlock.getIfCondition() != null) {
            conditionalBlock.getIfCondition().accept(collector);
        }
        if (conditionalBlock.getThenBlock() != null && conditionalBlock.getThenBlock().getActions() != null) {
            conditionalBlock.getThenBlock().getActions().forEach(action -> action.accept(collector));
        }
        if (conditionalBlock.getElseBlock() != null && conditionalBlock.getElseBlock().getActions() != null) {
            conditionalBlock.getElseBlock().getActions().forEach(action -> action.accept(collector));
        }
    }

    /**
     * Check if a variable name follows the UPPER_CASE constant naming convention
     */
    private boolean isConstantName(String name) {
        return name != null && name.matches("^[A-Z][A-Z0-9_]*$");
    }
    
    /**
     * Convert object to boolean
     */
    private boolean toBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0.0;
        }
        if (value instanceof String) {
            String str = ((String) value).toLowerCase().trim();
            return "true".equals(str) || "yes".equals(str) || "1".equals(str);
        }
        return false;
    }
    
    /**
     * Generate output data
     */
    private Map<String, Object> generateOutput(ASTRulesDSL rulesDSL, EvaluationContext context, boolean conditionResult) {
        Map<String, Object> outputData = new HashMap<>();
        
        // Add all variables to output
        outputData.putAll(context.getAllVariables());
        
        // Add condition result
        outputData.put("conditionResult", conditionResult);
        
        // Add any specific output variables defined in the rule
        if (rulesDSL.getOutput() != null) {
            rulesDSL.getOutput().forEach((key, type) -> {
                if (context.hasVariable(key)) {
                    outputData.put(key, context.getVariable(key));
                }
            });
        }
        
        return outputData;
    }

    /**
     * Visitor class to collect all variable references from AST nodes
     */
    private static class VariableReferenceCollector implements ASTVisitor<Void> {
        private final Set<String> variableReferences = new HashSet<>();

        public Set<String> getVariableReferences() {
            return variableReferences;
        }

        @Override
        public Void visitVariableExpression(VariableExpression node) {
            variableReferences.add(node.getVariableName());
            return null;
        }

        @Override
        public Void visitComparisonCondition(ComparisonCondition node) {
            node.getLeft().accept(this);
            if (node.getRight() != null) {
                node.getRight().accept(this);
            }
            if (node.getRangeEnd() != null) {
                node.getRangeEnd().accept(this);
            }
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
            node.getExpression().accept(this);
            return null;
        }

        @Override
        public Void visitAssignmentAction(AssignmentAction node) {
            node.getValue().accept(this);
            return null;
        }

        @Override
        public Void visitCalculateAction(CalculateAction node) {
            node.getExpression().accept(this);
            return null;
        }

        @Override
        public Void visitRunAction(RunAction node) {
            node.getExpression().accept(this);
            return null;
        }

        @Override
        public Void visitSetAction(SetAction node) {
            node.getValue().accept(this);
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
            node.getCondition().accept(this);
            if (node.getThenActions() != null) {
                node.getThenActions().forEach(action -> action.accept(this));
            }
            if (node.getElseActions() != null) {
                node.getElseActions().forEach(action -> action.accept(this));
            }
            return null;
        }

        @Override
        public Void visitBinaryExpression(BinaryExpression node) {
            node.getLeft().accept(this);
            node.getRight().accept(this);
            return null;
        }

        @Override
        public Void visitUnaryExpression(UnaryExpression node) {
            node.getOperand().accept(this);
            return null;
        }

        @Override
        public Void visitLiteralExpression(LiteralExpression node) {
            // Literals don't contain variable references
            return null;
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
        public Void visitArithmeticAction(ArithmeticAction node) {
            if (node.getValue() != null) {
                node.getValue().accept(this);
            }
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

        @Override
        public Void visitForEachAction(ForEachAction node) {
            // Collect variable references from list expression
            if (node.getListExpression() != null) {
                node.getListExpression().accept(this);
            }

            // Collect variable references from body actions
            if (node.getBodyActions() != null) {
                for (Action bodyAction : node.getBodyActions()) {
                    bodyAction.accept(this);
                }
            }

            return null;
        }

        @Override
        public Void visitWhileAction(WhileAction node) {
            // Collect variable references from condition
            if (node.getCondition() != null) {
                node.getCondition().accept(this);
            }

            // Collect variable references from body actions
            if (node.getBodyActions() != null) {
                for (Action bodyAction : node.getBodyActions()) {
                    bodyAction.accept(this);
                }
            }

            return null;
        }

        @Override
        public Void visitDoWhileAction(DoWhileAction node) {
            // Collect variable references from body actions
            if (node.getBodyActions() != null) {
                for (Action bodyAction : node.getBodyActions()) {
                    bodyAction.accept(this);
                }
            }

            // Collect variable references from condition
            if (node.getCondition() != null) {
                node.getCondition().accept(this);
            }

            return null;
        }

        @Override
        public Void visitJsonPathExpression(JsonPathExpression node) {
            // Visit the source expression to collect any variable references
            if (node.getSourceExpression() != null) {
                node.getSourceExpression().accept(this);
            }
            return null;
        }

        @Override
        public Void visitRestCallExpression(RestCallExpression node) {
            // Visit all expressions to collect any variable references
            if (node.getUrlExpression() != null) {
                node.getUrlExpression().accept(this);
            }
            if (node.getBodyExpression() != null) {
                node.getBodyExpression().accept(this);
            }
            if (node.getHeadersExpression() != null) {
                node.getHeadersExpression().accept(this);
            }
            if (node.getTimeoutExpression() != null) {
                node.getTimeoutExpression().accept(this);
            }
            return null;
        }
    }
}
