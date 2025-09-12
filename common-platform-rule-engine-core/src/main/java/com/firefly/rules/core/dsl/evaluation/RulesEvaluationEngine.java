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

package com.firefly.rules.core.dsl.evaluation;

import com.firefly.rules.core.dsl.model.*;
import com.firefly.rules.core.dsl.parser.RulesDSLParser;
import com.firefly.rules.core.services.ConstantService;
import com.firefly.rules.core.utils.JsonLogger;
import com.firefly.rules.interfaces.dtos.crud.ConstantDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core engine for evaluating rules defined in the YAML DSL format.
 * Handles the execution of rule logic against provided input data.
 * Supports both single rule and multiple rules evaluation scenarios.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RulesEvaluationEngine {

    private final RulesDSLParser parser;
    private final ConditionEvaluator conditionEvaluator;
    private final ActionExecutor actionExecutor;
    private final VariableResolver variableResolver;
    private final ConstantService constantService;

    /**
     * Evaluate rules against the provided input data
     *
     * @param rulesDefinition the YAML rules definition
     * @param inputData the input data for evaluation
     * @return the evaluation result
     */
    public Mono<RulesEvaluationResult> evaluateRulesReactive(String rulesDefinition, Map<String, Object> inputData) {
        return Mono.fromCallable(() -> {
            JsonLogger.debug(log, "Starting rules evaluation with input data: " + inputData);

            // Parse the rules definition
            RulesDSL rulesDSL = parser.parseRules(rulesDefinition);
            return rulesDSL;
        })
        .flatMap(rulesDSL -> createEvaluationContextReactive(rulesDSL, inputData)
                .map(context -> evaluateRules(rulesDSL, context)))
        .onErrorResume(error -> {
            JsonLogger.error(log, "Rules evaluation failed", error);
            return Mono.just(RulesEvaluationResult.builder()
                    .success(false)
                    .error(error.getMessage())
                    .build());
        });
    }

    /**
     * Evaluate rules against the provided input data (synchronous version)
     *
     * @param rulesDefinition the YAML rules definition
     * @param inputData the input data for evaluation
     * @return the evaluation result
     */
    public RulesEvaluationResult evaluateRules(String rulesDefinition, Map<String, Object> inputData) {
        return evaluateRulesReactive(rulesDefinition, inputData).block();
    }

    /**
     * Evaluate a parsed RulesDSL against the provided input data
     *
     * @param rulesDSL the parsed rules DSL
     * @param inputData the input data for evaluation
     * @return the evaluation result
     */
    public Mono<RulesEvaluationResult> evaluateRulesReactive(RulesDSL rulesDSL, Map<String, Object> inputData) {
        return createEvaluationContextReactive(rulesDSL, inputData)
                .map(context -> evaluateRules(rulesDSL, context))
                .onErrorResume(error -> {
                    JsonLogger.error(log, "Rules evaluation failed", error);
                    return Mono.just(RulesEvaluationResult.builder()
                            .success(false)
                            .error(error.getMessage())
                            .build());
                });
    }

    /**
     * Evaluate parsed rules against the evaluation context
     *
     * @param rulesDSL the parsed rules
     * @param context the evaluation context
     * @return the evaluation result
     */
    private RulesEvaluationResult evaluateRules(RulesDSL rulesDSL, EvaluationContext context) {
        String operationId = context.getOperationId();
        JsonLogger.logRuleStart(log, operationId, rulesDSL.getName(), context.getInputVariables());

        try {
            boolean conditionResult = false;

            // Handle simplified syntax (when/then/else)
            if (rulesDSL.getWhenConditions() != null && !rulesDSL.getWhenConditions().isEmpty()) {
                conditionResult = evaluateSimplifiedConditions(rulesDSL.getWhenConditions(), context);

                if (conditionResult && rulesDSL.getThen() != null) {
                    executeSimplifiedActions(rulesDSL.getThen(), context);
                } else if (!conditionResult && rulesDSL.getElseAction() != null) {
                    executeSimplifiedActions(rulesDSL.getElseAction(), context);
                }
            }
            // Handle multiple rules syntax
            else if (rulesDSL.getRules() != null && !rulesDSL.getRules().isEmpty()) {
                conditionResult = evaluateMultipleRules(rulesDSL.getRules(), context);
            }
            // Handle complex conditions syntax
            else if (rulesDSL.getConditions() != null) {
                conditionResult = conditionEvaluator.evaluate(rulesDSL.getConditions().getIfCondition(), context);

                ActionBlock actionBlock = conditionResult ?
                        rulesDSL.getConditions().getThenBlock() :
                        rulesDSL.getConditions().getElseBlock();

                if (actionBlock != null) {
                    actionExecutor.execute(actionBlock, context);
                }
            }

            JsonLogger.logConditionEvaluation(log, operationId, "rule_condition", conditionResult);
            Map<String, Object> logData = new HashMap<>();
            logData.put("computed_variables", context.getComputedVariables().keySet());
            JsonLogger.info(log, operationId, "Computed variables created", logData);

            // Check for circuit breaker
            if (context.isCircuitBreakerTriggered()) {
                JsonLogger.logCircuitBreaker(log, operationId, context.getCircuitBreakerMessage(), rulesDSL.getName());
                return RulesEvaluationResult.builder()
                        .success(false)
                        .circuitBreakerTriggered(true)
                        .circuitBreakerMessage(context.getCircuitBreakerMessage())
                        .conditionResult(conditionResult)
                        .outputData(generateOutput(rulesDSL, context, conditionResult))
                        .build();
            }

            // Generate output
            Map<String, Object> outputData = generateOutput(rulesDSL, context, conditionResult);
            long executionTime = System.currentTimeMillis() - context.getStartTime();

            JsonLogger.logRuleComplete(log, operationId, rulesDSL.getName(), true, outputData, executionTime);

            return RulesEvaluationResult.builder()
                    .success(true)
                    .conditionResult(conditionResult)
                    .outputData(outputData)
                    .executionTimeMs(executionTime)
                    .build();

        } catch (Exception e) {
            JsonLogger.error(log, operationId, "Error during rules evaluation", e);
            log.error("Full stack trace for rules evaluation error:", e);
            return RulesEvaluationResult.builder()
                    .success(false)
                    .error(e.getMessage())
                    .build();
        }
    }

    /**
     * Create an evaluation context for the rule
     *
     * @param ruleDSL the rule definition
     * @param inputData the input data
     * @return the evaluation context
     */
    private EvaluationContext createEvaluationContext(RulesDSL ruleDSL, Map<String, Object> inputData) {
        EvaluationContext context = new EvaluationContext();
        context.setStartTime(System.currentTimeMillis());
        context.setRuleName(ruleDSL.getName());

        // Set input variables provided by the controller
        for (Map.Entry<String, Object> entry : inputData.entrySet()) {
            context.setInputVariable(entry.getKey(), entry.getValue());
        }

        // Add default values for missing input variables defined in the rule
        if (ruleDSL.getVariables() != null) {
            for (VariableDefinition varDef : ruleDSL.getVariables()) {
                if (!inputData.containsKey(varDef.getName()) && varDef.getDefaultValue() != null) {
                    context.setInputVariable(varDef.getName(), varDef.getDefaultValue());
                }
            }
        }

        // Add inline constants defined in the rule DSL
        if (ruleDSL.getConstants() != null) {
            for (ConstantDefinition constDef : ruleDSL.getConstants()) {
                context.setSystemConstant(constDef.getName(), constDef.getValue());
            }
        }

        return context;
    }

    /**
     * Create an evaluation context for the rule with reactive variable resolution
     *
     * @param ruleDSL the rule definition
     * @param inputData the input data
     * @return the evaluation context
     */
    private Mono<EvaluationContext> createEvaluationContextReactive(RulesDSL ruleDSL, Map<String, Object> inputData) {
        EvaluationContext context = new EvaluationContext();
        context.setStartTime(System.currentTimeMillis());
        context.setRuleName(ruleDSL.getName());

        // Set input variables provided by the controller
        for (Map.Entry<String, Object> entry : inputData.entrySet()) {
            context.setInputVariable(entry.getKey(), entry.getValue());
        }

        // Add default values for missing input variables defined in the rule
        if (ruleDSL.getVariables() != null) {
            for (VariableDefinition varDef : ruleDSL.getVariables()) {
                if (!inputData.containsKey(varDef.getName()) && varDef.getDefaultValue() != null) {
                    context.setInputVariable(varDef.getName(), varDef.getDefaultValue());
                }
            }
        }

        // Add inline constants defined in the rule DSL
        if (ruleDSL.getConstants() != null) {
            for (ConstantDefinition constDef : ruleDSL.getConstants()) {
                context.setSystemConstant(constDef.getName(), constDef.getValue());
            }
        }

        // Load system constants from database for any constants referenced in the rule
        return loadSystemConstantsFromDatabase(ruleDSL, context)
                .thenReturn(context);
    }

    /**
     * Load system constants from the database that are referenced in the rules
     *
     * @param rulesDSL the rules definition
     * @param context the evaluation context
     * @return completion signal
     */
    private Mono<Void> loadSystemConstantsFromDatabase(RulesDSL rulesDSL, EvaluationContext context) {
        // Extract variable references from the rules
        java.util.Set<String> referencedVariables = extractVariableReferences(rulesDSL);

        // Find constants that are not already in the context and follow constant naming convention
        java.util.List<String> missingConstants = referencedVariables.stream()
                .filter(varName -> !context.hasValue(varName))
                .filter(this::isConstantName) // Only try to load values that look like constants
                .collect(java.util.stream.Collectors.toList());

        if (missingConstants.isEmpty()) {
            return Mono.empty();
        }

        String operationId = context.getOperationId();
        Map<String, Object> logData = new HashMap<>();
        logData.put("count", missingConstants.size());
        logData.put("constants", missingConstants);
        JsonLogger.info(log, operationId, "Loading system constants from database", logData);

        // Load each missing constant from the database
        return reactor.core.publisher.Flux.fromIterable(missingConstants)
                .flatMap(constantName ->
                    constantService.getConstantByCode(constantName)
                            .doOnNext(constantDTO -> {
                                // Use current value from the constant
                                Object value = constantDTO.getCurrentValue();
                                context.setSystemConstant(constantName, value);
                                Map<String, Object> constantData = new HashMap<>();
                                constantData.put("constantName", constantName);
                                constantData.put("value", value);
                                JsonLogger.info(log, operationId, "Loaded system constant", constantData);
                            })
                            .onErrorResume(error -> {
                                Map<String, Object> errorData = new HashMap<>();
                                errorData.put("constantName", constantName);
                                errorData.put("error", error.getMessage());
                                JsonLogger.warn(log, operationId, "Could not load system constant from database", errorData);
                                return Mono.empty();
                            })
                )
                .then();
    }

    /**
     * Check if a variable name follows constant naming convention (uppercase with underscores)
     *
     * @param name the variable name
     * @return true if it looks like a constant name
     */
    private boolean isConstantName(String name) {
        // Constants typically use UPPER_CASE_WITH_UNDERSCORES naming convention
        return name.matches("^[A-Z][A-Z0-9_]*$");
    }

    /**
     * Generate output data based on the rule's output configuration
     *
     * @param ruleDSL the rule definition
     * @param context the evaluation context
     * @return the output data
     */
    private Map<String, Object> generateOutput(RulesDSL ruleDSL, EvaluationContext context, Boolean conditionResult) {
        Map<String, Object> output = new HashMap<>();

        // Handle complex DSL output
        if (ruleDSL.getOutput() != null) {
            for (Map.Entry<String, Object> entry : ruleDSL.getOutput().entrySet()) {
                String outputKey = entry.getKey();

                // Only include variables that were actually computed during rule execution
                Object actualValue = context.getValue(outputKey);
                if (actualValue != null) {
                    output.put(outputKey, actualValue);
                }
                // Don't include schema-only values that weren't computed
            }
        }

        // Handle simplified DSL then/else actions as output
        if (ruleDSL.getThen() != null && conditionResult != null && conditionResult) {
            // Condition was true, use 'then' action as output
            if (ruleDSL.getThen() instanceof Map) {
                output.putAll((Map<String, Object>) ruleDSL.getThen());
            }
        } else if (ruleDSL.getElseAction() != null && conditionResult != null && !conditionResult) {
            // Condition was false, use 'else' action as output
            if (ruleDSL.getElseAction() instanceof Map) {
                output.putAll((Map<String, Object>) ruleDSL.getElseAction());
            }
        }

        // Always include all computed variables in the output
        // These are variables that were calculated during rule execution
        output.putAll(context.getComputedVariables());

        return output;
    }

    /**
     * Evaluate simplified when conditions
     *
     * @param conditions the list of condition strings
     * @param context the evaluation context
     * @return true if all conditions are met
     */
    private boolean evaluateSimplifiedConditions(java.util.List<String> conditions, EvaluationContext context) {
        for (String condition : conditions) {
            if (!conditionEvaluator.evaluateExpression(condition, context)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Execute simplified action strings
     *
     * @param actions the list of action strings
     * @param context the evaluation context
     */
    private void executeSimplifiedActions(java.util.List<String> actions, EvaluationContext context) {
        for (String action : actions) {
            actionExecutor.executeAction(action, context);
        }
    }

    /**
     * Execute simplified action map
     *
     * @param actionMap the map of actions
     * @param context the evaluation context
     */
    private void executeSimplifiedActions(Map<String, Object> actionMap, EvaluationContext context) {
        if (actionMap == null) {
            return;
        }

        for (Map.Entry<String, Object> entry : actionMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // Set variables based on the action map
            if (value != null) {
                context.setComputedVariable(key, value);
            }
        }
    }

    /**
     * Execute simplified actions (handles both List<String> and Map<String, Object>)
     *
     * @param actions the actions to execute (can be List<String> or Map<String, Object>)
     * @param context the evaluation context
     */
    @SuppressWarnings("unchecked")
    private void executeSimplifiedActions(Object actions, EvaluationContext context) {
        if (actions == null) {
            return;
        }

        if (actions instanceof java.util.List) {
            // Handle list of action strings
            java.util.List<String> actionList = (java.util.List<String>) actions;
            executeSimplifiedActions(actionList, context);
        } else if (actions instanceof Map) {
            // Handle action map
            Map<String, Object> actionMap = (Map<String, Object>) actions;
            executeSimplifiedActions(actionMap, context);
        } else {
            String operationId = context.getOperationId();
            JsonLogger.warn(log, operationId, "Unsupported action type: " + actions.getClass().getSimpleName());
        }
    }

    /**
     * Evaluate multiple rules in sequence
     *
     * @param rules the list of sub-rules
     * @param context the evaluation context
     * @return true if any rule condition was met
     */
    private boolean evaluateMultipleRules(java.util.List<RulesDSL.SubRule> rules, EvaluationContext context) {
        boolean anyConditionMet = false;

        for (RulesDSL.SubRule rule : rules) {
            boolean ruleConditionMet = false;

            // Check if rule has complex conditions block
            if (rule.getConditions() != null) {
                // Handle complex conditions syntax
                ruleConditionMet = conditionEvaluator.evaluate(rule.getConditions().getIfCondition(), context);

                ActionBlock actionBlock = ruleConditionMet ?
                        rule.getConditions().getThenBlock() :
                        rule.getConditions().getElseBlock();

                if (actionBlock != null) {
                    actionExecutor.execute(actionBlock, context);
                }

                if (ruleConditionMet) {
                    anyConditionMet = true;
                }
            } else {
                // Handle simple when/then/else syntax
                java.util.List<String> whenConditions = rule.getWhenAsList();
                if (!whenConditions.isEmpty()) {
                    // Rule has conditions - evaluate them
                    ruleConditionMet = evaluateSimplifiedConditions(whenConditions, context);

                    if (ruleConditionMet) {
                        anyConditionMet = true;
                        java.util.List<String> thenActions = rule.getThenAsList();
                        if (!thenActions.isEmpty()) {
                            executeSimplifiedActions(thenActions, context);
                        }
                    } else {
                        java.util.List<String> elseActions = rule.getElseActionsAsList();
                        if (!elseActions.isEmpty()) {
                            executeSimplifiedActions(elseActions, context);
                        }
                    }
                } else {
                    // Rule has no conditions - execute then actions unconditionally
                    java.util.List<String> thenActions = rule.getThenAsList();
                    if (!thenActions.isEmpty()) {
                        executeSimplifiedActions(thenActions, context);
                        anyConditionMet = true; // Consider unconditional rules as "met"
                    }
                }
            }
        }

        return anyConditionMet;
    }

    /**
     * Extract variable references from the rules definition
     *
     * @param rulesDSL the rules definition
     * @return set of variable names referenced in the rules
     */
    private java.util.Set<String> extractVariableReferences(RulesDSL rulesDSL) {
        java.util.Set<String> variables = new java.util.HashSet<>();

        // Extract from simplified when conditions
        if (rulesDSL.getWhenConditions() != null) {
            for (String condition : rulesDSL.getWhenConditions()) {
                variables.addAll(extractVariablesFromString(condition));
            }
        }

        // Extract from simplified then actions
        if (rulesDSL.getThen() != null) {
            if (rulesDSL.getThen() instanceof Map) {
                variables.addAll(extractVariablesFromMap((Map<String, Object>) rulesDSL.getThen()));
            } else if (rulesDSL.getThen() instanceof List) {
                List<String> thenActions = (List<String>) rulesDSL.getThen();
                for (String action : thenActions) {
                    variables.addAll(extractVariablesFromString(action));
                }
            }
        }

        // Extract from simplified else actions
        if (rulesDSL.getElseAction() != null) {
            if (rulesDSL.getElseAction() instanceof Map) {
                variables.addAll(extractVariablesFromMap((Map<String, Object>) rulesDSL.getElseAction()));
            } else if (rulesDSL.getElseAction() instanceof List) {
                List<String> elseActions = (List<String>) rulesDSL.getElseAction();
                for (String action : elseActions) {
                    variables.addAll(extractVariablesFromString(action));
                }
            }
        }

        // Extract from sub-rules
        if (rulesDSL.getRules() != null) {
            for (RulesDSL.SubRule subRule : rulesDSL.getRules()) {
                for (String condition : subRule.getWhenAsList()) {
                    variables.addAll(extractVariablesFromString(condition));
                }
                for (String action : subRule.getThenAsList()) {
                    variables.addAll(extractVariablesFromString(action));
                }
                for (String action : subRule.getElseActionsAsList()) {
                    variables.addAll(extractVariablesFromString(action));
                }

                // Extract from complex conditions in sub-rules
                if (subRule.getConditions() != null) {
                    variables.addAll(extractVariablesFromCondition(subRule.getConditions().getIfCondition()));
                    if (subRule.getConditions().getThenBlock() != null) {
                        variables.addAll(extractVariablesFromActionBlock(subRule.getConditions().getThenBlock()));
                    }
                    if (subRule.getConditions().getElseBlock() != null) {
                        variables.addAll(extractVariablesFromActionBlock(subRule.getConditions().getElseBlock()));
                    }
                }
            }
        }

        // Extract from main complex conditions block
        if (rulesDSL.getConditions() != null) {
            variables.addAll(extractVariablesFromCondition(rulesDSL.getConditions().getIfCondition()));
            if (rulesDSL.getConditions().getThenBlock() != null) {
                variables.addAll(extractVariablesFromActionBlock(rulesDSL.getConditions().getThenBlock()));
            }
            if (rulesDSL.getConditions().getElseBlock() != null) {
                variables.addAll(extractVariablesFromActionBlock(rulesDSL.getConditions().getElseBlock()));
            }
        }

        // Extract from inputs list
        if (rulesDSL.getInputs() != null) {
            variables.addAll(rulesDSL.getInputs());
        }

        return variables;
    }

    /**
     * Extract variable references from a map of actions
     *
     * @param actionMap the action map to analyze
     * @return set of variable names found
     */
    private java.util.Set<String> extractVariablesFromMap(java.util.Map<String, Object> actionMap) {
        java.util.Set<String> variables = new java.util.HashSet<>();

        if (actionMap == null) {
            return variables;
        }

        for (Object value : actionMap.values()) {
            if (value instanceof String) {
                variables.addAll(extractVariablesFromString((String) value));
            }
        }

        return variables;
    }

    /**
     * Extract variable names from a string expression
     *
     * @param expression the expression string
     * @return set of variable names found in the expression
     */
    private java.util.Set<String> extractVariablesFromString(String expression) {
        java.util.Set<String> variables = new java.util.HashSet<>();

        // Simple regex to find uppercase words (common variable naming convention)
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\b[A-Z][A-Z_0-9]*\\b");
        java.util.regex.Matcher matcher = pattern.matcher(expression);

        while (matcher.find()) {
            String variable = matcher.group();
            // Exclude common keywords
            if (!isKeyword(variable)) {
                variables.add(variable);
            }
        }

        return variables;
    }

    /**
     * Extract variables from a complex condition
     *
     * @param condition the condition to analyze
     * @return set of variable names found
     */
    private java.util.Set<String> extractVariablesFromCondition(Condition condition) {
        java.util.Set<String> variables = new java.util.HashSet<>();

        if (condition == null) {
            return variables;
        }

        // Extract from comparison conditions
        if (condition.getCompare() != null) {
            Condition.ComparisonCondition comp = condition.getCompare();
            if (comp.getLeft() instanceof String) {
                String left = (String) comp.getLeft();
                if (isConstantName(left)) {
                    variables.add(left);
                }
            }
            if (comp.getRight() instanceof String) {
                String right = (String) comp.getRight();
                if (isConstantName(right)) {
                    variables.add(right);
                }
            }
        }

        // Extract from AND conditions
        if (condition.getAnd() != null) {
            for (Condition subCondition : condition.getAnd()) {
                variables.addAll(extractVariablesFromCondition(subCondition));
            }
        }

        // Extract from OR conditions
        if (condition.getOr() != null) {
            for (Condition subCondition : condition.getOr()) {
                variables.addAll(extractVariablesFromCondition(subCondition));
            }
        }

        // Extract from NOT conditions
        if (condition.getNot() != null) {
            variables.addAll(extractVariablesFromCondition(condition.getNot()));
        }

        return variables;
    }

    /**
     * Extract variables from an action block
     *
     * @param actionBlock the action block to analyze
     * @return set of variable names found
     */
    private java.util.Set<String> extractVariablesFromActionBlock(ActionBlock actionBlock) {
        java.util.Set<String> variables = new java.util.HashSet<>();

        if (actionBlock == null || actionBlock.getActions() == null) {
            return variables;
        }

        for (ActionBlock.Action action : actionBlock.getActions()) {
            // Extract from set actions
            if (action.getSet() != null) {
                ActionBlock.Action.SetAction setAction = action.getSet();
                if (setAction.getValue() instanceof String) {
                    String value = (String) setAction.getValue();
                    if (isConstantName(value)) {
                        variables.add(value);
                    }
                }
            }

            // Extract from calculate actions
            if (action.getCalculate() != null) {
                ActionBlock.Action.CalculateAction calcAction = action.getCalculate();
                if (calcAction.getExpression() != null) {
                    variables.addAll(extractVariablesFromString(calcAction.getExpression()));
                }
            }
        }

        return variables;
    }

    /**
     * Check if a word is a keyword and not a variable
     *
     * @param word the word to check
     * @return true if it's a keyword
     */
    private boolean isKeyword(String word) {
        java.util.Set<String> keywords = java.util.Set.of(
                "TRUE", "FALSE", "NULL", "AND", "OR", "NOT", "IF", "THEN", "ELSE",
                "EQUALS", "GREATER_THAN", "LESS_THAN", "AT_LEAST", "BETWEEN", "IN_LIST"
        );
        return keywords.contains(word);
    }

    /**
     * Get the default value for a constant
     *
     * @param constantDTO the constant definition
     * @return the current value or null if not set
     */
    private Object getConstantDefaultValue(ConstantDTO constantDTO) {
        // Return the current value if available, otherwise null
        return constantDTO.getCurrentValue();
    }
}