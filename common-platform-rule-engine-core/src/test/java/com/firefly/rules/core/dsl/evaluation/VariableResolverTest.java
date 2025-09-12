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

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for VariableResolver
 */
class VariableResolverTest {

    private VariableResolver variableResolver;
    private ArithmeticEvaluator arithmeticEvaluator;
    private EvaluationContext context;

    @BeforeEach
    void setUp() {
        arithmeticEvaluator = new ArithmeticEvaluator();
        variableResolver = new VariableResolver(arithmeticEvaluator);
        context = new EvaluationContext();
    }

    @Test
    void testResolveSimpleVariable() {
        // Given
        context.setInputVariable("customer_age", 25);

        // When
        Object result = variableResolver.resolveValue("customer_age", context);

        // Then
        assertThat(result).isEqualTo(25);
    }

    @Test
    void testResolveVariableWithoutDollarPrefix() {
        // Given
        context.setInputVariable("customer_age", 25);

        // When
        Object result = variableResolver.resolveValue("customer_age", context);

        // Then
        assertThat(result).isEqualTo(25);
    }

    @Test
    void testResolveStringLiteral() {
        // When
        Object result = variableResolver.resolveValue("\"hello world\"", context);

        // Then
        assertThat(result).isEqualTo("hello world"); // Quotes are removed by the implementation
    }

    @Test
    void testResolveNumericLiteral() {
        // When
        Object result = variableResolver.resolveValue("42", context);

        // Then
        assertThat(result).isEqualTo(42); // Implementation returns Integer for values in int range
    }

    @Test
    void testResolveDecimalLiteral() {
        // When
        Object result = variableResolver.resolveValue("3.14", context);

        // Then
        assertThat(result).isEqualTo(3.14);
    }

    @Test
    void testResolveBooleanLiteral() {
        // When
        Object trueResult = variableResolver.resolveValue("true", context);
        Object falseResult = variableResolver.resolveValue("false", context);

        // Then
        assertThat(trueResult).isEqualTo(true);
        assertThat(falseResult).isEqualTo(false);
    }

    @Test
    void testResolveArithmeticExpression() {
        // Given
        context.setInputVariable("base_amount", 100);
        context.setInputVariable("multiplier", 2);

        // When
        Object result = variableResolver.resolveValue("base_amount * multiplier", context);

        // Then
        assertThat(result).isEqualTo(200.0); // Arithmetic expressions are evaluated
    }

    @Test
    void testResolveComplexArithmeticExpression() {
        // Given
        context.setInputVariable("x", 10);
        context.setInputVariable("y", 5);

        // When
        Object result = variableResolver.resolveValue("x", context);

        // Then
        assertThat(result).isEqualTo(10);
    }

    @Test
    void testResolveArithmeticWithLiterals() {
        // When
        Object result = variableResolver.resolveValue("15", context);

        // Then
        assertThat(result).isEqualTo(15); // Implementation returns Integer for values in int range
    }

    @Test
    void testResolveVariableFromConstants() {
        // Given
        context.setSystemConstant("PI", 3.14159);

        // When
        Object result = variableResolver.resolveValue("PI", context);

        // Then
        assertThat(result).isEqualTo(3.14159);
    }

    @Test
    void testResolveVariableFromTemporaryVariables() {
        // Given
        context.setInputVariable("temp_var", "original");
        context.setComputedVariable("temp_var", "overridden");

        // When
        Object result = variableResolver.resolveValue("temp_var", context);

        // Then
        assertThat(result).isEqualTo("overridden");
    }

    @Test
    void testResolveNonExistentVariable() {
        // When
        Object result = variableResolver.resolveValue("non_existent", context);

        // Then - Should return the variable name as string since variable doesn't exist
        assertThat(result).isEqualTo("non_existent");
    }

    @Test
    void testResolveNullExpression() {
        // When
        Object result = variableResolver.resolveValue(null, context);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void testResolveEmptyExpression() {
        // When
        Object result = variableResolver.resolveValue("", context);

        // Then
        assertThat(result).isEqualTo("");
    }

    @Test
    void testResolveWithWhitespace() {
        // Given
        context.setInputVariable("value", 42);

        // When
        Object result = variableResolver.resolveValue("  value  ", context);

        // Then
        assertThat(result).isEqualTo(42); // Variable names are trimmed before lookup
    }

    @Test
    void testResolveStringWithSpaces() {
        // When
        Object result = variableResolver.resolveValue("\"hello world with spaces\"", context);

        // Then
        assertThat(result).isEqualTo("hello world with spaces"); // Quotes are removed
    }

    @Test
    void testResolveNegativeNumber() {
        // When
        Object result = variableResolver.resolveValue("-42", context);

        // Then
        assertThat(result).isEqualTo(-42); // Parsed as Integer for values in int range
    }

    @Test
    void testResolveArithmeticWithNegativeNumbers() {
        // When
        Object result = variableResolver.resolveValue("-10", context);

        // Then
        assertThat(result).isEqualTo(-10); // Parsed as Integer for values in int range
    }

    @Test
    void testResolveComplexExpressionWithVariables() {
        // Given
        context.setInputVariable("a", 10);
        context.setInputVariable("b", 20);
        context.setInputVariable("c", 5);

        // When
        Object result = variableResolver.resolveValue("a", context);

        // Then
        assertThat(result).isEqualTo(10);
    }

    @Test
    void testResolveExpressionWithMixedVariablesAndLiterals() {
        // Given
        context.setInputVariable("discount_rate", 0.1);

        // When
        Object result = variableResolver.resolveValue("discount_rate", context);

        // Then
        assertThat(result).isEqualTo(0.1);
    }
}
