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

/**
 * Unit tests for ConditionEvaluator
 */
class ConditionEvaluatorTest {

    private ConditionEvaluator conditionEvaluator;
    private VariableResolver variableResolver;
    private ArithmeticEvaluator arithmeticEvaluator;
    private EvaluationContext context;

    @BeforeEach
    void setUp() {
        arithmeticEvaluator = new ArithmeticEvaluator();
        variableResolver = new VariableResolver(arithmeticEvaluator);
        conditionEvaluator = new ConditionEvaluator(variableResolver, arithmeticEvaluator);
        context = new EvaluationContext();
    }

    @Test
    void testEvaluateSimpleComparison() {
        // Given
        context.setInputVariable("age", 25);

        Condition condition = Condition.builder()
                .compare(Condition.ComparisonCondition.builder()
                        .left("age")
                        .operator(">=")
                        .right(18)
                        .build())
                .build();

        // When
        boolean result = conditionEvaluator.evaluate(condition, context);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void testEvaluateStringComparison() {
        // Given
        context.setInputVariable("status", "ACTIVE");

        Condition condition = Condition.builder()
                .compare(Condition.ComparisonCondition.builder()
                        .left("status")
                        .operator("==")
                        .right("ACTIVE")
                        .build())
                .build();

        // When
        boolean result = conditionEvaluator.evaluate(condition, context);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void testEvaluateAndCondition() {
        // Given
        context.setInputVariable("age", 25);
        context.setInputVariable("income", 50000);
        
        Condition ageCondition = Condition.builder()
                .compare(Condition.ComparisonCondition.builder()
                        .left("age")
                        .operator(">=")
                        .right(18)
                        .build())
                .build();
                
        Condition incomeCondition = Condition.builder()
                .compare(Condition.ComparisonCondition.builder()
                        .left("income")
                        .operator(">")
                        .right(30000)
                        .build())
                .build();
        
        Condition andCondition = Condition.builder()
                .and(Arrays.asList(ageCondition, incomeCondition))
                .build();

        // When
        boolean result = conditionEvaluator.evaluate(andCondition, context);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void testEvaluateOrCondition() {
        // Given
        context.setInputVariable("age", 16);
        context.setInputVariable("has_guardian", true);
        
        Condition ageCondition = Condition.builder()
                .compare(Condition.ComparisonCondition.builder()
                        .left("age")
                        .operator(">=")
                        .right(18)
                        .build())
                .build();
                
        Condition guardianCondition = Condition.builder()
                .value(true) // Direct boolean value
                .build();
        
        Condition orCondition = Condition.builder()
                .or(Arrays.asList(ageCondition, guardianCondition))
                .build();

        // When
        boolean result = conditionEvaluator.evaluate(orCondition, context);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void testEvaluateNotCondition() {
        // Given
        context.setInputVariable("is_blocked", false);
        
        Condition blockedCondition = Condition.builder()
                .compare(Condition.ComparisonCondition.builder()
                        .left("is_blocked")
                        .operator("==")
                        .right(true)
                        .build())
                .build();
        
        Condition notCondition = Condition.builder()
                .not(blockedCondition)
                .build();

        // When
        boolean result = conditionEvaluator.evaluate(notCondition, context);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void testEvaluateArithmeticCondition() {
        // Given
        context.setInputVariable("base_amount", 100);
        context.setInputVariable("multiplier", 2);
        
        Condition.ArithmeticOperation arithmetic = Condition.ArithmeticOperation.builder()
                .operation("multiply")
                .operands(Arrays.asList("base_amount", "multiplier"))
                .build();
        
        Condition condition = Condition.builder()
                .arithmetic(arithmetic)
                .build();

        // When
        boolean result = conditionEvaluator.evaluate(condition, context);

        // Then - arithmetic operations return their computed value, which is truthy if non-zero
        assertThat(result).isTrue();
    }

    @Test
    void testEvaluateFunctionCall() {
        // Given
        context.setInputVariable("email", "test@example.com");
        
        Condition.FunctionCall functionCall = Condition.FunctionCall.builder()
                .name("is_valid")
                .parameters(Arrays.asList("email"))
                .build();
        
        Condition condition = Condition.builder()
                .function(functionCall)
                .build();

        // When
        boolean result = conditionEvaluator.evaluate(condition, context);

        // Then
        assertThat(result).isTrue(); // is_valid function should return true for valid email
    }

    @Test
    void testEvaluateDirectValue() {
        // Given
        Condition trueCondition = Condition.builder()
                .value(true)
                .build();
                
        Condition falseCondition = Condition.builder()
                .value(false)
                .build();

        // When
        boolean trueResult = conditionEvaluator.evaluate(trueCondition, context);
        boolean falseResult = conditionEvaluator.evaluate(falseCondition, context);

        // Then
        assertThat(trueResult).isTrue();
        assertThat(falseResult).isFalse();
    }

    @Test
    void testEvaluateComplexCondition() {
        // Given
        context.setInputVariable("age", 25);
        context.setInputVariable("income", 60000);
        context.setInputVariable("credit_score", 750);
        
        // (age >= 18 AND income > 50000) OR credit_score > 700
        Condition ageCondition = Condition.builder()
                .compare(Condition.ComparisonCondition.builder()
                        .left("age")
                        .operator(">=")
                        .right(18)
                        .build())
                .build();
                
        Condition incomeCondition = Condition.builder()
                .compare(Condition.ComparisonCondition.builder()
                        .left("income")
                        .operator(">")
                        .right(50000)
                        .build())
                .build();
                
        Condition creditCondition = Condition.builder()
                .compare(Condition.ComparisonCondition.builder()
                        .left("credit_score")
                        .operator(">")
                        .right(700)
                        .build())
                .build();
        
        Condition andCondition = Condition.builder()
                .and(Arrays.asList(ageCondition, incomeCondition))
                .build();
        
        Condition complexCondition = Condition.builder()
                .or(Arrays.asList(andCondition, creditCondition))
                .build();

        // When
        boolean result = conditionEvaluator.evaluate(complexCondition, context);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void testEvaluateWithVariableReferences() {
        // Given
        context.setInputVariable("threshold", 100);
        context.setInputVariable("current_value", 150);
        
        Condition condition = Condition.builder()
                .compare(Condition.ComparisonCondition.builder()
                        .left("current_value")
                        .operator(">")
                        .right("threshold")
                        .build())
                .build();

        // When
        boolean result = conditionEvaluator.evaluate(condition, context);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void testEvaluateInequality() {
        // Given
        context.setInputVariable("status", "PENDING");
        
        Condition condition = Condition.builder()
                .compare(Condition.ComparisonCondition.builder()
                        .left("status")
                        .operator("!=")
                        .right("COMPLETED")
                        .build())
                .build();

        // When
        boolean result = conditionEvaluator.evaluate(condition, context);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void testEvaluateLessThanCondition() {
        // Given
        context.setInputVariable("score", 75);
        
        Condition condition = Condition.builder()
                .compare(Condition.ComparisonCondition.builder()
                        .left("score")
                        .operator("<")
                        .right(80)
                        .build())
                .build();

        // When
        boolean result = conditionEvaluator.evaluate(condition, context);

        // Then
        assertThat(result).isTrue();
    }
}
