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

/**
 * Represents a block of actions to execute in the rule DSL.
 * Actions can include setting variables, calling functions, or nested conditions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionBlock {

    /**
     * List of actions to execute
     */
    private List<Action> actions;

    /**
     * Nested conditional blocks (for complex logic)
     */
    private ConditionalBlock conditions;

    /**
     * Represents a single action
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Action {

        // Set variable action
        private SetAction set;

        // Function call action
        private CallAction call;

        // Calculate action
        private CalculateAction calculate;

        // Circuit breaker action
        @JsonProperty("circuit_breaker")
        private CircuitBreakerAction circuitBreaker;

        /**
         * Set variable action
         */
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class SetAction {
            private String variable;
            private Object value;
        }

        /**
         * Function call action
         */
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CallAction {
            private String function;
            private List<Object> parameters;
        }

        /**
         * Calculate action for expression evaluation
         */
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CalculateAction {
            private String variable;
            private String expression;
        }

        /**
         * Circuit breaker action
         */
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CircuitBreakerAction {
            private Boolean trigger;
            private String message;
        }
    }
}
