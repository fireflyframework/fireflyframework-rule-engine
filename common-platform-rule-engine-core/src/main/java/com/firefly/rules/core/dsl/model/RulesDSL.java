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

package com.firefly.rules.core.dsl.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * Root model class representing a complete rules definition in YAML DSL format.
 * This class maps to the top-level structure of a YAML rules definition and can contain
 * either a single rule or multiple rules within the same DSL structure.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RulesDSL {

    /**
     * Human-readable name of the rule
     */
    private String name;

    /**
     * Detailed description of what the rule does
     */
    private String description;

    /**
     * DSL version for compatibility tracking
     */
    private String version;

    /**
     * Additional metadata for the rule
     */
    private RuleMetadata metadata;

    /**
     * List of input variables required by this rule
     */
    private List<VariableDefinition> variables;

    /**
     * List of constants used in this rule
     */
    private List<ConstantDefinition> constants;

    /**
     * The main conditional logic of the rule
     */
    private ConditionalBlock conditions;



    /**
     * Output mapping defining what values to return
     */
    private Map<String, Object> output;

    /**
     * Circuit breaker configuration
     */
    @JsonProperty("circuit_breaker")
    private CircuitBreakerConfig circuitBreaker;



    /**
     * Simplified DSL syntax support (inputs, when, then, else)
     */
    private List<String> inputs;

    /**
     * Simplified when conditions (alternative to conditions block)
     */
    @JsonProperty("when")
    private List<String> whenConditions;

    /**
     * Simplified then actions (alternative to conditions.then)
     * Can be either a list of string actions or a complex object
     */
    @JsonProperty("then")
    private Object then;

    /**
     * Simplified else actions (alternative to conditions.else)
     * Can be either a list of string actions or a complex object
     */
    @JsonProperty("else")
    private Object elseAction;

    /**
     * Multiple rule definitions for complex workflows
     */
    private List<SubRule> rules;

    /**
     * Builder helper method to set a single when condition
     */
    public static class RulesDSLBuilder {
        public RulesDSLBuilder when(String condition) {
            this.whenConditions = java.util.Arrays.asList(condition);
            return this;
        }
    }

    /**
     * Metadata associated with the rule
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleMetadata {
        private List<String> tags;
        private Integer priority;
        private String author;
        private String category;
        private String businessOwner;
        private String approver;
        private String riskLevel;
        private List<String> regulatoryRequirements;
    }

    /**
     * Circuit breaker configuration
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CircuitBreakerConfig {
        private Boolean enabled;
        private String message;
        private String condition;
        private Integer threshold;
        private Long timeWindowMs;
    }

    /**
     * Sub-rule definition for multi-rule workflows
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubRule {
        private String name;
        private String description;

        /**
         * When conditions - can be either a single string or list of strings
         */
        private Object when;

        /**
         * Complex conditions block (alternative to when)
         */
        private ConditionalBlock conditions;

        /**
         * Then actions - can be either a single string or list of strings
         */
        private Object then;

        /**
         * Else actions - can be either a single string or list of strings
         */
        private Object elseActions;

        private Boolean required;
        private Integer priority;
        private Long timeoutMs;

        /**
         * Helper method to get when conditions as a list
         */
        public java.util.List<String> getWhenAsList() {
            if (when == null) {
                return java.util.Collections.emptyList();
            }
            if (when instanceof String) {
                return java.util.Arrays.asList((String) when);
            }
            if (when instanceof java.util.List) {
                return (java.util.List<String>) when;
            }
            return java.util.Collections.emptyList();
        }

        /**
         * Helper method to get then actions as a list
         */
        public java.util.List<String> getThenAsList() {
            if (then == null) {
                return java.util.Collections.emptyList();
            }
            if (then instanceof String) {
                return java.util.Arrays.asList((String) then);
            }
            if (then instanceof java.util.List) {
                return (java.util.List<String>) then;
            }
            return java.util.Collections.emptyList();
        }

        /**
         * Helper method to get else actions as a list
         */
        public java.util.List<String> getElseActionsAsList() {
            if (elseActions == null) {
                return java.util.Collections.emptyList();
            }
            if (elseActions instanceof String) {
                return java.util.Arrays.asList((String) elseActions);
            }
            if (elseActions instanceof java.util.List) {
                return (java.util.List<String>) elseActions;
            }
            return java.util.Collections.emptyList();
        }
    }
}
