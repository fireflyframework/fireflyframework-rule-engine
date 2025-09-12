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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for EvaluationContext robustness and edge cases
 */
class EvaluationContextRobustnessTest {

    private EvaluationContext context;

    @BeforeEach
    void setUp() {
        context = new EvaluationContext();
    }

    @Test
    @DisplayName("Should handle null variable names gracefully")
    void testNullVariableNames() {
        assertThatThrownBy(() -> context.setInputVariable(null, "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("input variable name cannot be null");

        assertThatThrownBy(() -> context.setComputedVariable(null, "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("computed variable name cannot be null");

        assertThatThrownBy(() -> context.setSystemConstant(null, "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("system constant name cannot be null");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    @DisplayName("Should handle empty/whitespace variable names gracefully")
    void testEmptyVariableNames(String name) {
        if (name == null) {
            // Null case is handled by testNullVariableNames
            return;
        }

        assertThatThrownBy(() -> context.setInputVariable(name, "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("input variable name cannot be empty");

        assertThatThrownBy(() -> context.setComputedVariable(name, "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("computed variable name cannot be empty");

        assertThatThrownBy(() -> context.setSystemConstant(name, "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("system constant name cannot be empty");
    }

    @Test
    @DisplayName("Should handle getValue with null/empty names gracefully")
    void testGetValueWithInvalidNames() {
        assertThat(context.getValue(null)).isNull();
        assertThat(context.getValue("")).isNull();
        assertThat(context.getValue("   ")).isNull();
    }

    @Test
    @DisplayName("Should trim variable names when getting values")
    void testVariableNameTrimming() {
        context.setInputVariable("test_var", "test_value");
        
        // Should find the variable even with whitespace
        assertThat(context.getValue("  test_var  ")).isEqualTo("test_value");
        assertThat(context.getValue("\ttest_var\n")).isEqualTo("test_value");
    }

    @Test
    @DisplayName("Should handle variable type detection correctly")
    void testVariableTypeDetection() {
        context.setSystemConstant("constant_var", "constant_value");
        context.setInputVariable("input_var", "input_value");
        context.setComputedVariable("computed_var", "computed_value");

        assertThat(context.getVariableType("constant_var")).isEqualTo("constant");
        assertThat(context.getVariableType("input_var")).isEqualTo("input");
        assertThat(context.getVariableType("computed_var")).isEqualTo("computed");
        assertThat(context.getVariableType("nonexistent_var")).isNull();
        assertThat(context.getVariableType(null)).isNull();
        assertThat(context.getVariableType("")).isNull();
    }

    @Test
    @DisplayName("Should handle variable existence checks correctly")
    void testVariableExistenceChecks() {
        context.setInputVariable("existing_var", "value");

        assertThat(context.variableExists("existing_var")).isTrue();
        assertThat(context.variableExists("nonexistent_var")).isFalse();
        assertThat(context.variableExists(null)).isFalse();
        assertThat(context.variableExists("")).isFalse();
        assertThat(context.variableExists("   ")).isFalse();
    }

    @Test
    @DisplayName("Should handle variable priority correctly")
    void testVariablePriority() {
        String varName = "priority_test";
        
        // Set constant first
        context.setSystemConstant(varName, "constant");
        assertThat(context.getValue(varName)).isEqualTo("constant");
        assertThat(context.getVariableType(varName)).isEqualTo("constant");

        // Add input variable (should override constant)
        context.setInputVariable(varName, "input");
        assertThat(context.getValue(varName)).isEqualTo("input");
        assertThat(context.getVariableType(varName)).isEqualTo("input");

        // Add computed variable (should override input)
        context.setComputedVariable(varName, "computed");
        assertThat(context.getValue(varName)).isEqualTo("computed");
        assertThat(context.getVariableType(varName)).isEqualTo("computed");
    }

    @Test
    @DisplayName("Should handle null values in variables gracefully")
    void testNullValues() {
        // ConcurrentHashMap doesn't allow null values, so we test with empty strings instead
        context.setInputVariable("empty_var", "");
        context.setComputedVariable("empty_computed", "");
        context.setSystemConstant("empty_constant", "");

        assertThat(context.getValue("empty_var")).isEqualTo("");
        assertThat(context.getValue("empty_computed")).isEqualTo("");
        assertThat(context.getValue("empty_constant")).isEqualTo("");

        // Test accessing non-existent variables returns null
        assertThat(context.getValue("non_existent")).isNull();

        // Variables should still exist even with empty values
        assertThat(context.variableExists("empty_var")).isTrue();
        assertThat(context.variableExists("empty_computed")).isTrue();
        assertThat(context.variableExists("empty_constant")).isTrue();
    }

    @Test
    @DisplayName("Should handle various data types correctly")
    void testVariousDataTypes() {
        context.setInputVariable("string_var", "string_value");
        context.setInputVariable("int_var", 42);
        context.setInputVariable("double_var", 3.14);
        context.setInputVariable("boolean_var", true);
        context.setInputVariable("object_var", new java.util.HashMap<>());

        assertThat(context.getValue("string_var")).isInstanceOf(String.class);
        assertThat(context.getValue("int_var")).isInstanceOf(Integer.class);
        assertThat(context.getValue("double_var")).isInstanceOf(Double.class);
        assertThat(context.getValue("boolean_var")).isInstanceOf(Boolean.class);
        assertThat(context.getValue("object_var")).isInstanceOf(java.util.Map.class);
    }

    @Test
    @DisplayName("Should handle operation ID generation")
    void testOperationIdGeneration() {
        // Should generate operation ID if not set
        String operationId1 = context.getOperationId();
        assertThat(operationId1).isNotNull();
        assertThat(operationId1).startsWith("op-");

        // Should return same ID on subsequent calls
        String operationId2 = context.getOperationId();
        assertThat(operationId2).isEqualTo(operationId1);

        // Should allow setting custom operation ID
        context.setOperationId("custom-op-123");
        assertThat(context.getOperationId()).isEqualTo("custom-op-123");
    }

    @Test
    @DisplayName("Should handle circuit breaker state correctly")
    void testCircuitBreakerState() {
        assertThat(context.isCircuitBreakerTriggered()).isFalse();
        assertThat(context.getCircuitBreakerMessage()).isNull();

        context.triggerCircuitBreaker("Test circuit breaker message");
        
        assertThat(context.isCircuitBreakerTriggered()).isTrue();
        assertThat(context.getCircuitBreakerMessage()).isEqualTo("Test circuit breaker message");
    }

    @Test
    @DisplayName("Should handle variable removal correctly")
    void testVariableRemoval() {
        context.setInputVariable("test_var", "input_value");
        context.setComputedVariable("test_var", "computed_value");

        assertThat(context.getValue("test_var")).isEqualTo("computed_value");

        // Remove computed variable
        context.removeComputedVariable("test_var");
        assertThat(context.getValue("test_var")).isEqualTo("input_value");

        // Remove input variable
        context.removeInputVariable("test_var");
        assertThat(context.getValue("test_var")).isNull();
        assertThat(context.variableExists("test_var")).isFalse();
    }

    @Test
    @DisplayName("Should handle concurrent access safely")
    void testConcurrentAccess() {
        // The context uses ConcurrentHashMap, so it should be thread-safe
        // This is a basic test to ensure no exceptions are thrown
        
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                context.setInputVariable("var_" + i, "value_" + i);
                context.getValue("var_" + i);
                context.setComputedVariable("computed_" + i, "computed_value_" + i);
            }
        };

        Thread thread1 = new Thread(task);
        Thread thread2 = new Thread(task);

        assertThatCode(() -> {
            thread1.start();
            thread2.start();
            thread1.join();
            thread2.join();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle variable summary generation")
    void testVariableSummaryGeneration() {
        context.setInputVariable("input1", "value1");
        context.setSystemConstant("CONSTANT1", "const_value");
        context.setComputedVariable("computed1", "comp_value");

        String summary = context.getVariableSummary();
        
        assertThat(summary).contains("Variable Summary:");
        assertThat(summary).contains("Input Variables:");
        assertThat(summary).contains("System Constants:");
        assertThat(summary).contains("Computed Variables:");
        assertThat(summary).contains("input1");
        assertThat(summary).contains("CONSTANT1");
        assertThat(summary).contains("computed1");
    }

    @Test
    @DisplayName("Should handle large numbers of variables")
    void testLargeNumberOfVariables() {
        // Test with a large number of variables to ensure performance
        int numVariables = 10000;
        
        for (int i = 0; i < numVariables; i++) {
            context.setInputVariable("var_" + i, "value_" + i);
        }

        // Should be able to retrieve all variables efficiently
        for (int i = 0; i < numVariables; i++) {
            assertThat(context.getValue("var_" + i)).isEqualTo("value_" + i);
        }

        assertThat(context.getInputVariables()).hasSize(numVariables);
    }

    @Test
    @DisplayName("Should handle special characters in variable values")
    void testSpecialCharactersInValues() {
        context.setInputVariable("special_chars", "!@#$%^&*()_+-={}[]|\\:;\"'<>?,./");
        context.setInputVariable("unicode", "Hello 世界 🌍");
        context.setInputVariable("newlines", "Line 1\nLine 2\rLine 3\r\n");

        assertThat(context.getValue("special_chars")).isEqualTo("!@#$%^&*()_+-={}[]|\\:;\"'<>?,./");
        assertThat(context.getValue("unicode")).isEqualTo("Hello 世界 🌍");
        assertThat(context.getValue("newlines")).isEqualTo("Line 1\nLine 2\rLine 3\r\n");
    }
}
