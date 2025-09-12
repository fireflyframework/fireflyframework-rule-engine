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

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

/**
 * Represents a condition in the rule DSL.
 * Conditions can be comparisons, logical operations, or function calls.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Condition {

    // Comparison condition
    private ComparisonCondition compare;

    // Logical conditions
    private List<Condition> and;
    private List<Condition> or;
    private Condition not;

    // Arithmetic operation
    private ArithmeticOperation arithmetic;

    // Function call
    private FunctionCall function;

    // Direct value (for simple true/false conditions)
    private Object value;

    /**
     * Represents a comparison operation (e.g., left > right)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComparisonCondition {
        private Object left;
        private String operator; // >, <, >=, <=, ==, !=, contains, starts_with, ends_with, in, not_in
        private Object right;
    }

    /**
     * Represents an arithmetic operation
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ArithmeticOperation {
        private String operation; // add, subtract, multiply, divide, modulo, power
        private List<Object> operands;
    }

    /**
     * Represents a function call
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionCall {
        private String name;
        private List<Object> parameters;
    }
}
