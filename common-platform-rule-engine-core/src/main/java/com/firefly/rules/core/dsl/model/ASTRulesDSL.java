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

package com.firefly.rules.core.dsl.ast.model;

import com.firefly.rules.core.dsl.ast.action.Action;
import com.firefly.rules.core.dsl.ast.condition.Condition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Pure AST-based rules DSL model that replaces the legacy model classes.
 * This represents a complete rule definition using AST nodes.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ASTRulesDSL {
    
    private String name;
    private String description;
    private String version;

    // Metadata support
    private Map<String, Object> metadata;

    // Input/Output definitions
    private Map<String, String> input;
    private Map<String, String> output;
    
    // Constants
    private List<ASTConstantDefinition> constants;
    
    // Simple syntax support (when/then/else)
    private List<Condition> whenConditions;
    private List<Action> thenActions;
    private List<Action> elseActions;
    
    // Complex syntax support (multiple rules)
    private List<ASTSubRule> rules;
    
    // Complex conditions block
    private ASTConditionalBlock conditions;

    // Circuit breaker configuration
    private ASTCircuitBreakerConfig circuitBreaker;
    
    /**
     * AST-based sub-rule definition
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ASTSubRule {
        private String name;
        private String description;
        
        // Simple syntax
        private List<Condition> whenConditions;
        private List<Action> thenActions;
        private List<Action> elseActions;
        
        // Complex syntax
        private ASTConditionalBlock conditions;
    }
    
    /**
     * AST-based conditional block
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ASTConditionalBlock {
        private Condition ifCondition;
        private ASTActionBlock thenBlock;
        private ASTActionBlock elseBlock;
    }
    
    /**
     * AST-based action block
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ASTActionBlock {
        private List<Action> actions;
        private ASTConditionalBlock nestedConditions; // Support for nested conditions!
    }
    
    /**
     * AST-based constant definition
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ASTConstantDefinition {
        private String name;
        private String code;
        private String type;
        private Object defaultValue;
    }
    
    /**
     * Check if this rule uses simple syntax (when/then/else)
     */
    public boolean isSimpleSyntax() {
        return whenConditions != null && !whenConditions.isEmpty();
    }
    
    /**
     * Check if this rule uses multiple rules syntax
     */
    public boolean isMultipleRulesSyntax() {
        return rules != null && !rules.isEmpty();
    }
    
    /**
     * Check if this rule uses complex conditions syntax
     */
    public boolean isComplexConditionsSyntax() {
        return conditions != null;
    }
    
    /**
     * Get all conditions from this rule (regardless of syntax)
     */
    public List<Condition> getAllConditions() {
        if (isSimpleSyntax()) {
            return whenConditions;
        } else if (isComplexConditionsSyntax()) {
            return List.of(conditions.getIfCondition());
        } else if (isMultipleRulesSyntax()) {
            return rules.stream()
                    .flatMap(rule -> {
                        if (rule.getWhenConditions() != null) {
                            return rule.getWhenConditions().stream();
                        } else if (rule.getConditions() != null) {
                            return List.of(rule.getConditions().getIfCondition()).stream();
                        }
                        return List.<Condition>of().stream();
                    })
                    .toList();
        }
        return List.of();
    }
    
    /**
     * Get all actions from this rule (regardless of syntax)
     */
    public List<Action> getAllActions() {
        if (isSimpleSyntax()) {
            var actions = new java.util.ArrayList<Action>();
            if (thenActions != null) actions.addAll(thenActions);
            if (elseActions != null) actions.addAll(elseActions);
            return actions;
        } else if (isComplexConditionsSyntax()) {
            var actions = new java.util.ArrayList<Action>();
            if (conditions.getThenBlock() != null && conditions.getThenBlock().getActions() != null) {
                actions.addAll(conditions.getThenBlock().getActions());
            }
            if (conditions.getElseBlock() != null && conditions.getElseBlock().getActions() != null) {
                actions.addAll(conditions.getElseBlock().getActions());
            }
            return actions;
        } else if (isMultipleRulesSyntax()) {
            return rules.stream()
                    .flatMap(rule -> {
                        var actions = new java.util.ArrayList<Action>();
                        if (rule.getThenActions() != null) actions.addAll(rule.getThenActions());
                        if (rule.getElseActions() != null) actions.addAll(rule.getElseActions());
                        if (rule.getConditions() != null) {
                            if (rule.getConditions().getThenBlock() != null && 
                                rule.getConditions().getThenBlock().getActions() != null) {
                                actions.addAll(rule.getConditions().getThenBlock().getActions());
                            }
                            if (rule.getConditions().getElseBlock() != null && 
                                rule.getConditions().getElseBlock().getActions() != null) {
                                actions.addAll(rule.getConditions().getElseBlock().getActions());
                            }
                        }
                        return actions.stream();
                    })
                    .toList();
        }
        return List.of();
    }

    /**
     * AST-based circuit breaker configuration
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ASTCircuitBreakerConfig {
        private boolean enabled;
        private int failureThreshold;
        private String timeoutDuration;
        private String recoveryTimeout;
    }
}
