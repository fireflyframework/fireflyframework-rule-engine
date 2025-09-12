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

import com.firefly.rules.core.dsl.model.Condition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ArithmeticEvaluator
 */
class ArithmeticEvaluatorTest {

    private ArithmeticEvaluator arithmeticEvaluator;
    private EvaluationContext context;

    @BeforeEach
    void setUp() {
        arithmeticEvaluator = new ArithmeticEvaluator();
        context = new EvaluationContext();
    }

    @Test
    void testBasicAddition() {
        // Given
        Condition.ArithmeticOperation operation = Condition.ArithmeticOperation.builder()
                .operation("add")
                .operands(Arrays.asList(5, 3))
                .build();

        // When
        Object result = arithmeticEvaluator.evaluate(operation, context);

        // Then
        assertThat(result.toString()).isEqualTo("8.0");
    }

    @Test
    void testBasicSubtraction() {
        // Given
        Condition.ArithmeticOperation operation = Condition.ArithmeticOperation.builder()
                .operation("subtract")
                .operands(Arrays.asList(10, 3))
                .build();

        // When
        Object result = arithmeticEvaluator.evaluate(operation, context);

        // Then
        assertThat(result.toString()).isEqualTo("7.0");
    }

    @Test
    void testBasicMultiplication() {
        // Given
        Condition.ArithmeticOperation operation = Condition.ArithmeticOperation.builder()
                .operation("multiply")
                .operands(Arrays.asList(4, 5))
                .build();

        // When
        Object result = arithmeticEvaluator.evaluate(operation, context);

        // Then
        assertThat(result.toString()).isEqualTo("20.00");
    }

    @Test
    void testBasicDivision() {
        // Given
        Condition.ArithmeticOperation operation = Condition.ArithmeticOperation.builder()
                .operation("divide")
                .operands(Arrays.asList(20, 4))
                .build();

        // When
        Object result = arithmeticEvaluator.evaluate(operation, context);

        // Then
        assertThat(result.toString()).isEqualTo("5.0000000000");
    }

    @Test
    void testModuloOperation() {
        // Given
        Condition.ArithmeticOperation operation = Condition.ArithmeticOperation.builder()
                .operation("modulo")
                .operands(Arrays.asList(10, 3))
                .build();

        // When
        Object result = arithmeticEvaluator.evaluate(operation, context);

        // Then
        assertThat(result.toString()).isEqualTo("1.0");
    }

    @Test
    void testPowerOperation() {
        // Given
        Condition.ArithmeticOperation operation = Condition.ArithmeticOperation.builder()
                .operation("power")
                .operands(Arrays.asList(2, 3))
                .build();

        // When
        Object result = arithmeticEvaluator.evaluate(operation, context);

        // Then
        assertThat(result.toString()).isEqualTo("8.0");
    }

    @Test
    void testWithDecimalNumbers() {
        // Given
        Condition.ArithmeticOperation operation = Condition.ArithmeticOperation.builder()
                .operation("add")
                .operands(Arrays.asList(10.5, 2.5))
                .build();

        // When
        Object result = arithmeticEvaluator.evaluate(operation, context);

        // Then
        assertThat(result.toString()).isEqualTo("13.0");
    }

    @Test
    void testWithMultipleOperands() {
        // Given
        Condition.ArithmeticOperation operation = Condition.ArithmeticOperation.builder()
                .operation("add")
                .operands(Arrays.asList(1, 2, 3, 4))
                .build();

        // When
        Object result = arithmeticEvaluator.evaluate(operation, context);

        // Then
        assertThat(result.toString()).isEqualTo("10.0");
    }

    @Test
    void testWithVariableReferences() {
        // Given
        context.setInputVariable("x", 5);
        context.setInputVariable("y", 3);
        
        Condition.ArithmeticOperation operation = Condition.ArithmeticOperation.builder()
                .operation("multiply")
                .operands(Arrays.asList("x", "y"))
                .build();

        // When
        Object result = arithmeticEvaluator.evaluate(operation, context);

        // Then
        assertThat(result.toString()).isEqualTo("15.00");
    }

    @Test
    void testDivisionByZero() {
        // Given
        Condition.ArithmeticOperation operation = Condition.ArithmeticOperation.builder()
                .operation("divide")
                .operands(Arrays.asList(10, 0))
                .build();

        // When & Then
        assertThatThrownBy(() -> arithmeticEvaluator.evaluate(operation, context))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Division by zero");
    }

    @Test
    void testInvalidOperation() {
        // Given
        Condition.ArithmeticOperation operation = Condition.ArithmeticOperation.builder()
                .operation("invalid_op")
                .operands(Arrays.asList(5, 3))
                .build();

        // When & Then
        assertThatThrownBy(() -> arithmeticEvaluator.evaluate(operation, context))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Unknown arithmetic operation");
    }

    @Test
    void testNullOperation() {
        // When & Then
        assertThatThrownBy(() -> arithmeticEvaluator.evaluate(null, context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid arithmetic operation");
    }

    @Test
    void testInsufficientOperands() {
        // Given - use subtract which requires at least 2 operands
        Condition.ArithmeticOperation operation = Condition.ArithmeticOperation.builder()
                .operation("subtract")
                .operands(Arrays.asList(5)) // Only one operand
                .build();

        // When & Then
        assertThatThrownBy(() -> arithmeticEvaluator.evaluate(operation, context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Subtract operation requires at least 2 operands");
    }

    @Test
    void testWithStringNumbers() {
        // Given
        Condition.ArithmeticOperation operation = Condition.ArithmeticOperation.builder()
                .operation("add")
                .operands(Arrays.asList("5", "3"))
                .build();

        // When
        Object result = arithmeticEvaluator.evaluate(operation, context);

        // Then
        assertThat(result.toString()).isEqualTo("8");
    }

    @Test
    void testWithMixedTypes() {
        // Given
        Condition.ArithmeticOperation operation = Condition.ArithmeticOperation.builder()
                .operation("multiply")
                .operands(Arrays.asList(2.5, 4))
                .build();

        // When
        Object result = arithmeticEvaluator.evaluate(operation, context);

        // Then
        assertThat(result.toString()).isEqualTo("10.00");
    }
}
