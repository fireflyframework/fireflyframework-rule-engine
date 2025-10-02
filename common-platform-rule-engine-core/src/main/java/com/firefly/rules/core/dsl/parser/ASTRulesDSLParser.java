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

package com.firefly.rules.core.dsl.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.firefly.rules.core.dsl.action.Action;
import com.firefly.rules.core.dsl.condition.Condition;
import com.firefly.rules.core.dsl.exception.ASTException;
import com.firefly.rules.core.dsl.model.ASTRulesDSL;
import com.firefly.rules.core.services.CacheService;
import com.firefly.rules.core.utils.JsonLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Pure AST-based rules DSL parser that converts YAML directly to AST nodes.
 * This completely replaces the legacy parser with no dependencies on legacy models.
 * Now includes high-performance caching for parsed AST models.
 */
@Component
@Slf4j
public class ASTRulesDSLParser {

    private final DSLParser dslParser;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final ComplexConditionsParser complexConditionsParser;

    @Autowired(required = false)
    private CacheService cacheService;

    public ASTRulesDSLParser(DSLParser dslParser) {
        this.dslParser = dslParser;
        this.complexConditionsParser = new ComplexConditionsParser(dslParser);
    }
    
    /**
     * Parse rules definition from YAML string to AST model
     */
    public Mono<ASTRulesDSL> parseRulesReactive(String rulesDefinition) {
        return Mono.fromCallable(() -> parseRules(rulesDefinition))
                .onErrorMap(e -> new ASTException("Failed to parse rules definition: " + e.getMessage()));
    }

    /**
     * Parse rules definition from YAML string to AST model (synchronous)
     * Uses caching to avoid re-parsing identical YAML content.
     */
    public ASTRulesDSL parseRules(String rulesDefinition) {
        long startTime = System.currentTimeMillis();
        try {
            // Check cache first if caching is enabled
            if (cacheService != null) {
                String cacheKey = cacheService.generateCacheKey(rulesDefinition);
                Optional<ASTRulesDSL> cachedAST = cacheService.getCachedAST(cacheKey);

                if (cachedAST.isPresent()) {
                    JsonLogger.info(log, "AST cache hit for rules definition");
                    return cachedAST.get();
                }

                // Cache miss - parse and cache the result
                JsonLogger.info(log, "AST cache miss - parsing rules definition with AST parser");
                ASTRulesDSL parsedAST = parseRulesInternal(rulesDefinition);
                cacheService.cacheAST(cacheKey, parsedAST);
                return parsedAST;
            } else {
                // No caching available - parse directly
                JsonLogger.info(log, "Parsing rules definition with AST parser (no caching)");
                ASTRulesDSL parsedAST = parseRulesInternal(rulesDefinition);
                return parsedAST;
            }

        } catch (Exception e) {
            JsonLogger.error(log, "Error parsing rules definition", e);
            throw new ASTException("Failed to parse rules definition: " + e.getMessage());
        } finally {
            long parseTime = System.currentTimeMillis() - startTime;
        }
    }

    /**
     * Internal method to parse rules definition without caching logic
     */
    private ASTRulesDSL parseRulesInternal(String rulesDefinition) throws Exception {
        // Parse YAML to Map first
        @SuppressWarnings("unchecked")
        Map<String, Object> yamlMap = yamlMapper.readValue(rulesDefinition, Map.class);

        // Convert to AST model
        return convertToASTModel(yamlMap);
    }

    /**
     * Invalidate cached AST for specific YAML content.
     * Useful when rule definitions are updated.
     */
    public void invalidateCache(String rulesDefinition) {
        if (cacheService != null) {
            String cacheKey = cacheService.generateCacheKey(rulesDefinition);
            cacheService.invalidateAST(cacheKey);
            JsonLogger.info(log, "Invalidated AST cache for rules definition");
        }
    }

    /**
     * Clear all cached AST models.
     * Useful for cache management and testing.
     */
    public void clearCache() {
        if (cacheService != null) {
            cacheService.clearASTCache();
            JsonLogger.info(log, "Cleared all AST cache entries");
        }
    }
    
    /**
     * Convert YAML map to AST model
     */
    @SuppressWarnings("unchecked")
    private ASTRulesDSL convertToASTModel(Map<String, Object> yamlMap) {
        ASTRulesDSL.ASTRulesDSLBuilder builder = ASTRulesDSL.builder();
        
        // Basic metadata
        builder.name((String) yamlMap.get("name"));
        builder.description((String) yamlMap.get("description"));
        builder.version((String) yamlMap.get("version"));

        // Metadata
        if (yamlMap.containsKey("metadata")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) yamlMap.get("metadata");
            builder.metadata(metadata);
        }
        
        // Input/Output definitions
        // Handle both 'input' and 'inputs' (both can be map format)
        if (yamlMap.containsKey("input")) {
            builder.input((Map<String, String>) yamlMap.get("input"));
        } else if (yamlMap.containsKey("inputs")) {
            Object inputsObj = yamlMap.get("inputs");
            if (inputsObj instanceof Map) {
                // inputs is a map format: inputs: {income: "number", debt: "number"}
                builder.input((Map<String, String>) inputsObj);
            } else {
                // inputs is a list format: inputs: [income, debt, age]
                List<String> inputsList = convertToStringList(inputsObj);
                Map<String, String> inputsMap = inputsList.stream()
                        .collect(Collectors.toMap(
                                inputName -> inputName,
                                inputName -> "Object" // Default type since inputs list doesn't specify types
                        ));
                builder.input(inputsMap);
            }
        }

        // Handle both 'output' and 'outputs' (both can be map format)
        if (yamlMap.containsKey("output")) {
            builder.output((Map<String, String>) yamlMap.get("output"));
        } else if (yamlMap.containsKey("outputs")) {
            Object outputsObj = yamlMap.get("outputs");
            if (outputsObj instanceof Map) {
                // outputs is a map format: outputs: {result: "string", score: "number"}
                builder.output((Map<String, String>) outputsObj);
            } else {
                // outputs is a list format: outputs: [result, score]
                List<String> outputsList = convertToStringList(outputsObj);
                Map<String, String> outputsMap = outputsList.stream()
                        .collect(Collectors.toMap(
                                outputName -> outputName,
                                outputName -> "Object" // Default type since outputs list doesn't specify types
                        ));
                builder.output(outputsMap);
            }
        }
        
        // Constants
        if (yamlMap.containsKey("constants")) {
            List<Map<String, Object>> constantsList = (List<Map<String, Object>>) yamlMap.get("constants");
            List<ASTRulesDSL.ASTConstantDefinition> constants = constantsList.stream()
                    .map(this::convertToConstantDefinition)
                    .collect(Collectors.toList());
            builder.constants(constants);
        }
        
        // Simple syntax (when/then/else)
        if (yamlMap.containsKey("when")) {
            List<String> whenStrings = convertToStringList(yamlMap.get("when"));
            List<Condition> whenConditions = whenStrings.stream()
                    .map(dslParser::parseCondition)
                    .collect(Collectors.toList());
            builder.whenConditions(whenConditions);
        }
        
        if (yamlMap.containsKey("then")) {
            List<Action> thenActions = parseActionList(yamlMap.get("then"));
            builder.thenActions(thenActions);
        }

        if (yamlMap.containsKey("else")) {
            List<Action> elseActions = parseActionList(yamlMap.get("else"));
            builder.elseActions(elseActions);
        }
        
        // Multiple rules syntax
        if (yamlMap.containsKey("rules")) {
            List<Map<String, Object>> rulesList = (List<Map<String, Object>>) yamlMap.get("rules");
            List<ASTRulesDSL.ASTSubRule> rules = rulesList.stream()
                    .map(this::convertToSubRule)
                    .collect(Collectors.toList());
            builder.rules(rules);
        }
        
        // Complex conditions syntax
        if (yamlMap.containsKey("conditions")) {
            Map<String, Object> conditionsMap = (Map<String, Object>) yamlMap.get("conditions");
            ASTRulesDSL.ASTConditionalBlock conditions = convertToConditionalBlock(conditionsMap);
            builder.conditions(conditions);
        }

        // Circuit breaker configuration
        if (yamlMap.containsKey("circuit_breaker")) {
            Map<String, Object> circuitBreakerMap = (Map<String, Object>) yamlMap.get("circuit_breaker");
            ASTRulesDSL.ASTCircuitBreakerConfig circuitBreaker = convertToCircuitBreakerConfig(circuitBreakerMap);
            builder.circuitBreaker(circuitBreaker);
        }

        return builder.build();
    }
    
    /**
     * Convert to constant definition
     */
    private ASTRulesDSL.ASTConstantDefinition convertToConstantDefinition(Map<String, Object> constantMap) {
        return ASTRulesDSL.ASTConstantDefinition.builder()
                .name((String) constantMap.get("name"))
                .code((String) constantMap.get("code"))
                .type((String) constantMap.get("type"))
                .defaultValue(constantMap.get("defaultValue"))
                .build();
    }
    
    /**
     * Convert to sub-rule
     */
    @SuppressWarnings("unchecked")
    private ASTRulesDSL.ASTSubRule convertToSubRule(Map<String, Object> ruleMap) {
        ASTRulesDSL.ASTSubRule.ASTSubRuleBuilder builder = ASTRulesDSL.ASTSubRule.builder();
        
        builder.name((String) ruleMap.get("name"));
        builder.description((String) ruleMap.get("description"));
        
        // Simple syntax
        if (ruleMap.containsKey("when")) {
            List<String> whenStrings = convertToStringList(ruleMap.get("when"));
            List<Condition> whenConditions = whenStrings.stream()
                    .map(dslParser::parseCondition)
                    .collect(Collectors.toList());
            builder.whenConditions(whenConditions);
        }
        
        if (ruleMap.containsKey("then")) {
            List<String> thenStrings = convertToStringList(ruleMap.get("then"));
            List<Action> thenActions = thenStrings.stream()
                    .map(dslParser::parseAction)
                    .collect(Collectors.toList());
            builder.thenActions(thenActions);
        }
        
        if (ruleMap.containsKey("else")) {
            List<String> elseStrings = convertToStringList(ruleMap.get("else"));
            List<Action> elseActions = elseStrings.stream()
                    .map(dslParser::parseAction)
                    .collect(Collectors.toList());
            builder.elseActions(elseActions);
        }
        
        // Complex syntax
        if (ruleMap.containsKey("conditions")) {
            Map<String, Object> conditionsMap = (Map<String, Object>) ruleMap.get("conditions");
            ASTRulesDSL.ASTConditionalBlock conditions = convertToConditionalBlock(conditionsMap);
            builder.conditions(conditions);
        }
        
        return builder.build();
    }
    
    /**
     * Convert to conditional block
     */
    @SuppressWarnings("unchecked")
    private ASTRulesDSL.ASTConditionalBlock convertToConditionalBlock(Map<String, Object> conditionsMap) {
        ASTRulesDSL.ASTConditionalBlock.ASTConditionalBlockBuilder builder =
                ASTRulesDSL.ASTConditionalBlock.builder();

        // Parse if condition using complex conditions parser
        if (conditionsMap.containsKey("if")) {
            Object ifConditionObj = conditionsMap.get("if");
            Condition ifCondition = complexConditionsParser.parseComplexCondition(ifConditionObj);
            builder.ifCondition(ifCondition);
        }
        
        // Parse then block
        if (conditionsMap.containsKey("then")) {
            Map<String, Object> thenMap = (Map<String, Object>) conditionsMap.get("then");
            ASTRulesDSL.ASTActionBlock thenBlock = convertToActionBlock(thenMap);
            builder.thenBlock(thenBlock);
        }
        
        // Parse else block
        if (conditionsMap.containsKey("else")) {
            Map<String, Object> elseMap = (Map<String, Object>) conditionsMap.get("else");
            ASTRulesDSL.ASTActionBlock elseBlock = convertToActionBlock(elseMap);
            builder.elseBlock(elseBlock);
        }
        
        return builder.build();
    }
    
    /**
     * Convert to action block with support for nested conditions
     */
    @SuppressWarnings("unchecked")
    private ASTRulesDSL.ASTActionBlock convertToActionBlock(Map<String, Object> actionMap) {
        ASTRulesDSL.ASTActionBlock.ASTActionBlockBuilder builder = 
                ASTRulesDSL.ASTActionBlock.builder();
        
        // Parse actions
        if (actionMap.containsKey("actions")) {
            Object actionsObj = actionMap.get("actions");
            List<Action> actions = parseActionsList(actionsObj);
            builder.actions(actions);
        }
        
        // Parse nested conditions - this was the TODO that's now implemented!
        if (actionMap.containsKey("conditions")) {
            Map<String, Object> nestedConditionsMap = (Map<String, Object>) actionMap.get("conditions");
            ASTRulesDSL.ASTConditionalBlock nestedConditions = convertToConditionalBlock(nestedConditionsMap);
            builder.nestedConditions(nestedConditions);
        }
        
        return builder.build();
    }
    
    /**
     * Parse actions list that can contain either simple syntax strings or complex syntax maps
     */
    @SuppressWarnings("unchecked")
    private List<Action> parseActionsList(Object actionsObj) {
        if (actionsObj instanceof List) {
            List<Object> actionsList = (List<Object>) actionsObj;
            return actionsList.stream()
                    .map(this::parseActionItem)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } else if (actionsObj instanceof String) {
            // Single action as string (simple syntax)
            try {
                return List.of(dslParser.parseAction((String) actionsObj));
            } catch (Exception e) {
                log.warn("Failed to parse action: {}", actionsObj, e);
                return List.of();
            }
        } else {
            log.warn("Unexpected actions object type: {}", actionsObj.getClass());
            return List.of();
        }
    }

    /**
     * Parse a single action item (either string or map)
     */
    @SuppressWarnings("unchecked")
    private Action parseActionItem(Object actionObj) {
        if (actionObj instanceof String) {
            // Simple syntax: "set variable to value"
            try {
                return dslParser.parseAction((String) actionObj);
            } catch (Exception e) {
                log.warn("Failed to parse simple action: {}", actionObj, e);
                return null;
            }
        } else if (actionObj instanceof Map) {
            Map<String, Object> actionMap = (Map<String, Object>) actionObj;

            // Check if this is a single-entry map that might be a forEach/while/do-while action
            // YAML interprets "forEach item in items: action" as {"forEach item in items": "action"}
            // Same for "while condition: action" as {"while condition": "action"}
            // Same for "do: action while condition" as {"do": "action while condition"}
            if (actionMap.size() == 1) {
                Map.Entry<String, Object> entry = actionMap.entrySet().iterator().next();
                String key = entry.getKey();
                Object value = entry.getValue();

                // Check if key starts with forEach, while, or do
                if (key.startsWith("forEach ") || key.startsWith("while ") || key.equals("do")) {
                    // Reconstruct the action string
                    String actionString = key + ": " + formatActionValue(value);
                    try {
                        Action action = dslParser.parseAction(actionString);
                        return action;
                    } catch (Exception e) {
                        log.warn("Failed to parse reconstructed loop action: {}", actionString, e);
                        // Fall through to complex action parsing
                    }
                }
            }

            // Complex syntax: { set: { variable: "name", value: "value" } }
            return parseComplexAction(actionMap);
        } else {
            log.warn("Unexpected action object type: {}", actionObj.getClass());
            return null;
        }
    }

    /**
     * Format action value for reconstruction (handles strings, lists, etc.)
     */
    @SuppressWarnings("unchecked")
    private String formatActionValue(Object value) {
        if (value instanceof String) {
            return (String) value;
        } else if (value instanceof List) {
            List<Object> list = (List<Object>) value;
            return list.stream()
                    .map(item -> item instanceof String ? (String) item : item.toString())
                    .collect(Collectors.joining("; "));
        } else {
            return value.toString();
        }
    }

    /**
     * Parse complex syntax action from map
     */
    @SuppressWarnings("unchecked")
    private Action parseComplexAction(Map<String, Object> actionMap) {
        // Handle different action types
        if (actionMap.containsKey("set")) {
            Map<String, Object> setMap = (Map<String, Object>) actionMap.get("set");
            String variable = (String) setMap.get("variable");
            Object value = setMap.get("value");

            // Convert to simple syntax and parse
            String simpleSyntax = "set " + variable + " to " + formatValue(value);
            try {
                return dslParser.parseAction(simpleSyntax);
            } catch (Exception e) {
                log.warn("Failed to parse complex set action: {}", actionMap, e);
                return null;
            }
        } else if (actionMap.containsKey("calculate")) {
            Map<String, Object> calcMap = (Map<String, Object>) actionMap.get("calculate");
            String variable = (String) calcMap.get("variable");
            String expression = (String) calcMap.get("expression");

            // Convert to simple syntax and parse
            String simpleSyntax = "calculate " + variable + " as " + expression;
            try {
                return dslParser.parseAction(simpleSyntax);
            } catch (Exception e) {
                log.warn("Failed to parse complex calculate action: {}", actionMap, e);
                return null;
            }
        } else if (actionMap.containsKey("run")) {
            Map<String, Object> runMap = (Map<String, Object>) actionMap.get("run");
            String variable = (String) runMap.get("variable");
            String expression = (String) runMap.get("expression");

            // Convert to simple syntax and parse
            String simpleSyntax = "run " + variable + " as " + expression;
            try {
                return dslParser.parseAction(simpleSyntax);
            } catch (Exception e) {
                log.warn("Failed to parse complex run action: {}", actionMap, e);
                return null;
            }
        } else if (actionMap.containsKey("call")) {
            Map<String, Object> callMap = (Map<String, Object>) actionMap.get("call");
            String function = (String) callMap.get("function");
            List<Object> parameters = (List<Object>) callMap.get("parameters");

            // Convert to simple syntax and parse
            String paramStr = parameters.stream()
                    .map(this::formatValue)
                    .collect(Collectors.joining(", "));
            String simpleSyntax = "call " + function + " with [" + paramStr + "]";
            try {
                return dslParser.parseAction(simpleSyntax);
            } catch (Exception e) {
                log.warn("Failed to parse complex call action: {}", actionMap, e);
                return null;
            }
        } else if (actionMap.containsKey("forEach")) {
            Map<String, Object> forEachMap = (Map<String, Object>) actionMap.get("forEach");
            String variable = (String) forEachMap.get("variable");
            String index = (String) forEachMap.get("index");  // optional
            Object inValue = forEachMap.get("in");
            Object doValue = forEachMap.get("do");

            // Build simple syntax
            StringBuilder simpleSyntax = new StringBuilder("forEach ");
            simpleSyntax.append(variable);

            if (index != null && !index.trim().isEmpty()) {
                simpleSyntax.append(", ").append(index);
            }

            simpleSyntax.append(" in ").append(formatValue(inValue));
            simpleSyntax.append(": ");

            // Parse body actions
            if (doValue instanceof List) {
                List<Object> doList = (List<Object>) doValue;
                List<String> bodyActionStrings = new ArrayList<>();

                for (Object actionObj : doList) {
                    if (actionObj instanceof String) {
                        bodyActionStrings.add((String) actionObj);
                    } else if (actionObj instanceof Map) {
                        // Recursively parse complex action and convert to string
                        Action bodyAction = parseComplexAction((Map<String, Object>) actionObj);
                        if (bodyAction != null) {
                            bodyActionStrings.add(bodyAction.toDebugString());
                        }
                    }
                }

                simpleSyntax.append(String.join("; ", bodyActionStrings));
            } else if (doValue instanceof String) {
                simpleSyntax.append((String) doValue);
            }

            try {
                return dslParser.parseAction(simpleSyntax.toString());
            } catch (Exception e) {
                log.warn("Failed to parse complex forEach action: {}", actionMap, e);
                return null;
            }
        } else if (actionMap.containsKey("while")) {
            Map<String, Object> whileMap = (Map<String, Object>) actionMap.get("while");
            String condition = (String) whileMap.get("condition");
            Object doValue = whileMap.get("do");

            // Build simple syntax
            StringBuilder simpleSyntax = new StringBuilder("while ");
            simpleSyntax.append(condition);
            simpleSyntax.append(": ");

            // Parse body actions
            if (doValue instanceof List) {
                List<Object> doList = (List<Object>) doValue;
                List<String> bodyActionStrings = new ArrayList<>();

                for (Object actionObj : doList) {
                    if (actionObj instanceof String) {
                        bodyActionStrings.add((String) actionObj);
                    } else if (actionObj instanceof Map) {
                        // Recursively parse complex action and convert to string
                        Action bodyAction = parseComplexAction((Map<String, Object>) actionObj);
                        if (bodyAction != null) {
                            bodyActionStrings.add(bodyAction.toDebugString());
                        }
                    }
                }

                simpleSyntax.append(String.join("; ", bodyActionStrings));
            } else if (doValue instanceof String) {
                simpleSyntax.append((String) doValue);
            }

            try {
                return dslParser.parseAction(simpleSyntax.toString());
            } catch (Exception e) {
                log.warn("Failed to parse complex while action: {}", actionMap, e);
                return null;
            }
        } else if (actionMap.containsKey("do")) {
            Map<String, Object> doWhileMap = (Map<String, Object>) actionMap.get("do");
            Object doValue = doWhileMap.get("actions");
            String condition = (String) doWhileMap.get("while");

            // Build simple syntax
            StringBuilder simpleSyntax = new StringBuilder("do: ");

            // Parse body actions
            if (doValue instanceof List) {
                List<Object> doList = (List<Object>) doValue;
                List<String> bodyActionStrings = new ArrayList<>();

                for (Object actionObj : doList) {
                    if (actionObj instanceof String) {
                        bodyActionStrings.add((String) actionObj);
                    } else if (actionObj instanceof Map) {
                        // Recursively parse complex action and convert to string
                        Action bodyAction = parseComplexAction((Map<String, Object>) actionObj);
                        if (bodyAction != null) {
                            bodyActionStrings.add(bodyAction.toDebugString());
                        }
                    }
                }

                simpleSyntax.append(String.join("; ", bodyActionStrings));
            } else if (doValue instanceof String) {
                simpleSyntax.append((String) doValue);
            }

            simpleSyntax.append(" while ").append(condition);

            try {
                return dslParser.parseAction(simpleSyntax.toString());
            } catch (Exception e) {
                log.warn("Failed to parse complex do-while action: {}", actionMap, e);
                return null;
            }
        } else {
            log.warn("Unknown complex action type: {}", actionMap.keySet());
            return null;
        }
    }

    /**
     * Format a value for use in simple syntax
     */
    private String formatValue(Object value) {
        if (value instanceof String) {
            return "\"" + value + "\"";
        } else {
            return String.valueOf(value);
        }
    }

    /**
     * Parse a list of actions, handling both simple string syntax and complex map syntax
     */
    @SuppressWarnings("unchecked")
    private List<Action> parseActionList(Object obj) {
        if (obj instanceof List) {
            List<Object> list = (List<Object>) obj;
            return list.stream()
                    .map(item -> {
                        try {
                            if (item instanceof String) {
                                // Simple syntax: "set x to 5"
                                return dslParser.parseAction((String) item);
                            } else if (item instanceof Map) {
                                Map<String, Object> map = (Map<String, Object>) item;

                                // Check if this is a YAML-parsed simple syntax action
                                // e.g., {forEach item in items: set count to 1}
                                // e.g., {while counter < 10: add 1 to counter}
                                // e.g., {do: add 1 to counter while counter < 10}
                                if (map.size() == 1) {
                                    Map.Entry<String, Object> entry = map.entrySet().iterator().next();
                                    String key = entry.getKey();
                                    Object value = entry.getValue();

                                    // If the key starts with an action keyword, reconstruct the action string
                                    if (key.startsWith("forEach ") || key.startsWith("for ") ||
                                        key.startsWith("while ") || key.equals("do")) {
                                        String actionString = key + ": " + value;
                                        return dslParser.parseAction(actionString);
                                    }
                                }

                                // Complex syntax: {forEach: {variable: x, in: items, do: [...]}}
                                return parseComplexAction(map);
                            } else {
                                // Fallback: try to parse as string
                                return dslParser.parseAction(item.toString());
                            }
                        } catch (Exception e) {
                            log.error("Error parsing action: {}", item, e);
                            throw e;
                        }
                    })
                    .filter(action -> action != null)
                    .collect(Collectors.toList());
        } else if (obj instanceof String) {
            return List.of(dslParser.parseAction((String) obj));
        } else {
            return List.of(dslParser.parseAction(obj.toString()));
        }
    }

    /**
     * Convert object to string list
     */
    @SuppressWarnings("unchecked")
    private List<String> convertToStringList(Object obj) {
        if (obj instanceof List) {
            return ((List<Object>) obj).stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        } else if (obj instanceof String) {
            return List.of((String) obj);
        } else {
            return List.of(obj.toString());
        }
    }

    /**
     * Convert to circuit breaker configuration
     */
    @SuppressWarnings("unchecked")
    private ASTRulesDSL.ASTCircuitBreakerConfig convertToCircuitBreakerConfig(Map<String, Object> circuitBreakerMap) {
        ASTRulesDSL.ASTCircuitBreakerConfig.ASTCircuitBreakerConfigBuilder builder =
                ASTRulesDSL.ASTCircuitBreakerConfig.builder();

        if (circuitBreakerMap.containsKey("enabled")) {
            builder.enabled((Boolean) circuitBreakerMap.get("enabled"));
        }

        if (circuitBreakerMap.containsKey("failure_threshold")) {
            Object threshold = circuitBreakerMap.get("failure_threshold");
            if (threshold instanceof Number) {
                builder.failureThreshold(((Number) threshold).intValue());
            }
        }

        if (circuitBreakerMap.containsKey("timeout_duration")) {
            builder.timeoutDuration((String) circuitBreakerMap.get("timeout_duration"));
        }

        if (circuitBreakerMap.containsKey("recovery_timeout")) {
            builder.recoveryTimeout((String) circuitBreakerMap.get("recovery_timeout"));
        }

        return builder.build();
    }
}
