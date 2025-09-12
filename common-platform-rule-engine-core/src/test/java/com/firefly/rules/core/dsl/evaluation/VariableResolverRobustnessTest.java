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
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for VariableResolver robustness and edge cases
 */
class VariableResolverRobustnessTest {

    private VariableResolver variableResolver;
    private ArithmeticEvaluator arithmeticEvaluator;
    private EvaluationContext context;

    @BeforeEach
    void setUp() {
        arithmeticEvaluator = new ArithmeticEvaluator();
        variableResolver = new VariableResolver(arithmeticEvaluator);
        context = new EvaluationContext();
        context.setOperationId("test-op-123");
    }

    @Test
    @DisplayName("Should handle null context gracefully")
    void testNullContext() {
        assertThatThrownBy(() -> variableResolver.resolveValue("test", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EvaluationContext cannot be null");
    }

    @Test
    @DisplayName("Should handle null value gracefully")
    void testNullValue() {
        Object result = variableResolver.resolveValue(null, context);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should handle empty string value")
    void testEmptyStringValue() {
        Object result = variableResolver.resolveValue("", context);
        assertThat(result).isEqualTo("");
    }

    @Test
    @DisplayName("Should handle whitespace-only strings")
    void testWhitespaceOnlyStrings() {
        Object result = variableResolver.resolveValue("   ", context);
        assertThat(result).isEqualTo("   ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"\"", "''", "\"hello\"", "'world'", "\"quoted string\"", "'another quoted'"})
    @DisplayName("Should handle various quoted string formats")
    void testQuotedStrings(String quotedString) {
        Object result = variableResolver.resolveValue(quotedString, context);
        
        if (quotedString.equals("\"\"") || quotedString.equals("''")) {
            assertThat(result).isEqualTo("");
        } else {
            String expected = quotedString.substring(1, quotedString.length() - 1);
            assertThat(result).isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("Should handle mismatched quotes gracefully")
    void testMismatchedQuotes() {
        Object result = variableResolver.resolveValue("\"hello'", context);
        // Should return the original string when quotes are mismatched
        assertThat(result).isEqualTo("\"hello'");
    }

    @Test
    @DisplayName("Should handle single character quoted strings")
    void testSingleCharacterQuotedStrings() {
        Object result = variableResolver.resolveValue("\"", context);
        assertThat(result).isEqualTo("\"");
        
        result = variableResolver.resolveValue("'", context);
        assertThat(result).isEqualTo("'");
    }

    @Test
    @DisplayName("Should handle nested quotes in strings")
    void testNestedQuotes() {
        Object result = variableResolver.resolveValue("\"He said 'hello'\"", context);
        assertThat(result).isEqualTo("He said 'hello'");
        
        result = variableResolver.resolveValue("'She said \"goodbye\"'", context);
        assertThat(result).isEqualTo("She said \"goodbye\"");
    }

    @Test
    @DisplayName("Should handle variable resolution priority correctly")
    void testVariableResolutionPriority() {
        // Set up variables with same name in different scopes
        context.setSystemConstant("test_var", "constant_value");
        context.setInputVariable("test_var", "input_value");
        context.setComputedVariable("test_var", "computed_value");

        Object result = variableResolver.resolveValue("test_var", context);
        assertThat(result).isEqualTo("computed_value"); // Computed should have highest priority
    }

    @Test
    @DisplayName("Should handle undefined variables gracefully")
    void testUndefinedVariables() {
        Object result = variableResolver.resolveValue("undefined_variable", context);
        assertThat(result).isEqualTo("undefined_variable"); // Should return as literal string
    }

    @Test
    @DisplayName("Should handle numeric literals correctly")
    void testNumericLiterals() {
        assertThat(variableResolver.resolveValue("42", context)).isEqualTo(42);
        assertThat(variableResolver.resolveValue("-42", context)).isEqualTo(-42);
        assertThat(variableResolver.resolveValue("3.14", context)).isEqualTo(3.14);
        assertThat(variableResolver.resolveValue("-3.14", context)).isEqualTo(-3.14);
        assertThat(variableResolver.resolveValue("0", context)).isEqualTo(0);
        assertThat(variableResolver.resolveValue("0.0", context)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should handle boolean literals correctly")
    void testBooleanLiterals() {
        assertThat(variableResolver.resolveValue("true", context)).isEqualTo(true);
        assertThat(variableResolver.resolveValue("false", context)).isEqualTo(false);
        assertThat(variableResolver.resolveValue("TRUE", context)).isEqualTo(true);
        assertThat(variableResolver.resolveValue("FALSE", context)).isEqualTo(false);
    }

    @Test
    @DisplayName("Should handle arithmetic expressions with edge cases")
    void testArithmeticExpressionEdgeCases() {
        context.setInputVariable("zero", 0);
        context.setInputVariable("one", 1);
        context.setInputVariable("negative", -5);

        // Division by zero should be handled gracefully and return original expression
        Object result = variableResolver.resolveValue("one / zero", context);
        assertThat(result).isEqualTo("one / zero"); // Returns original expression when evaluation fails

        // Large numbers
        context.setInputVariable("large", 1000000);
        result = variableResolver.resolveValue("large * large", context);
        assertThat(result).isEqualTo(1000000000000.0);

        // Negative arithmetic
        result = variableResolver.resolveValue("negative * negative", context);
        assertThat(result).isEqualTo(25.0);
    }

    @Test
    @DisplayName("Should handle complex parenthetical expressions")
    void testComplexParentheticalExpressions() {
        context.setInputVariable("a", 2);
        context.setInputVariable("b", 3);
        context.setInputVariable("c", 4);

        Object result = variableResolver.resolveValue("((a + b) * c) - (a * b)", context);
        assertThat(result).isEqualTo(14.0); // ((2 + 3) * 4) - (2 * 3) = 20 - 6 = 14
    }

    @Test
    @DisplayName("Should handle mismatched parentheses gracefully")
    void testMismatchedParentheses() {
        context.setInputVariable("x", 5);

        // Simple mismatched parentheses should return original expression when evaluation fails
        Object result = variableResolver.resolveValue("(x + 1", context);
        assertThat(result).isEqualTo("(x + 1"); // Returns original expression when evaluation fails

        // Unmatched closing parenthesis gets removed and expression is evaluated
        result = variableResolver.resolveValue("x + 1)", context);
        assertThat(result).isEqualTo(6.0); // Unmatched closing parenthesis removed, expression evaluated
    }

    @Test
    @DisplayName("Should handle empty parentheses")
    void testEmptyParentheses() {
        // Empty parentheses should be handled gracefully and return original expression
        Object result = variableResolver.resolveValue("5 + ()", context);
        assertThat(result).isEqualTo("5 + ()"); // Returns original expression when evaluation fails
    }

    @Test
    @DisplayName("Should handle special characters in variable names")
    void testSpecialCharactersInVariableNames() {
        // Underscores should be allowed
        context.setInputVariable("test_variable", "underscore_value");
        Object result = variableResolver.resolveValue("test_variable", context);
        assertThat(result).isEqualTo("underscore_value");

        // Numbers in variable names
        context.setInputVariable("var123", "numeric_suffix");
        result = variableResolver.resolveValue("var123", context);
        assertThat(result).isEqualTo("numeric_suffix");
    }

    @Test
    @DisplayName("Should handle very long variable names")
    void testVeryLongVariableNames() {
        String longName = "a".repeat(1000);
        context.setInputVariable(longName, "long_name_value");
        
        Object result = variableResolver.resolveValue(longName, context);
        assertThat(result).isEqualTo("long_name_value");
    }

    @Test
    @DisplayName("Should handle unicode characters in strings")
    void testUnicodeCharacters() {
        Object result = variableResolver.resolveValue("\"Hello 世界 🌍\"", context);
        assertThat(result).isEqualTo("Hello 世界 🌍");
    }

    @Test
    @DisplayName("Should handle derived financial variables")
    void testDerivedFinancialVariables() {
        // Set up data for derived variables using the correct variable names
        context.setInputVariable("REQUESTED_LOAN_AMOUNT", 100000);
        context.setInputVariable("ANNUAL_INCOME", 50000);
        context.setInputVariable("EXISTING_DEBT", 24000); // Annual debt (2000 * 12)
        context.setInputVariable("USED_CREDIT", 5000);
        context.setInputVariable("TOTAL_CREDIT_LIMIT", 10000);

        // Test loan_to_income calculation
        Object result = variableResolver.resolveValue("loan_to_income", context);
        assertThat(result).isEqualTo(2.0); // 100000 / 50000

        // Test debt_to_income calculation
        result = variableResolver.resolveValue("debt_to_income", context);
        assertThat(result).isEqualTo(0.48); // 24000 / 50000

        // Test credit_utilization calculation
        result = variableResolver.resolveValue("credit_utilization", context);
        assertThat(result).isEqualTo(0.5); // 5000 / 10000
    }

    @Test
    @DisplayName("Should handle derived variables with missing data")
    void testDerivedVariablesWithMissingData() {
        // Test derived variable when required input is missing
        Object result = variableResolver.resolveValue("loan_to_income", context);
        // When derived variable calculation fails, it returns the original variable name
        assertThat(result).isEqualTo("loan_to_income");
    }
}
