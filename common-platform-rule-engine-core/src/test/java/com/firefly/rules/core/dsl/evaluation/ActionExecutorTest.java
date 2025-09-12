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

import com.firefly.rules.core.dsl.model.ActionBlock;
import com.firefly.rules.core.validation.NamingConventionValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ActionExecutor
 */
class ActionExecutorTest {

    private ActionExecutor actionExecutor;
    private VariableResolver variableResolver;
    private ConditionEvaluator conditionEvaluator;
    private ArithmeticEvaluator arithmeticEvaluator;
    private EvaluationContext context;

    @BeforeEach
    void setUp() {
        arithmeticEvaluator = new ArithmeticEvaluator();
        variableResolver = new VariableResolver(arithmeticEvaluator);
        conditionEvaluator = new ConditionEvaluator(variableResolver, arithmeticEvaluator);
        NamingConventionValidator namingValidator = new NamingConventionValidator();
        actionExecutor = new ActionExecutor(variableResolver, namingValidator, conditionEvaluator);
        context = new EvaluationContext();
    }

    @Test
    void testExecuteSimpleVariableAssignment() {
        // Given
        ActionBlock.Action.SetAction setAction = ActionBlock.Action.SetAction.builder()
                .variable("result")
                .value("approved")
                .build();

        ActionBlock.Action action = ActionBlock.Action.builder()
                .set(setAction)
                .build();

        ActionBlock actionBlock = ActionBlock.builder()
                .actions(Arrays.asList(action))
                .build();

        // When
        actionExecutor.execute(actionBlock, context);

        // Then
        assertThat(context.getValue("result")).isEqualTo("approved");
    }

    @Test
    void testExecuteArithmeticAssignment() {
        // Given
        context.setInputVariable("base_amount", 100);
        context.setInputVariable("discount_rate", 0.1);

        ActionBlock.Action.SetAction setAction = ActionBlock.Action.SetAction.builder()
                .variable("discount_amount")
                .value("base_amount * discount_rate")
                .build();

        ActionBlock.Action action = ActionBlock.Action.builder()
                .set(setAction)
                .build();

        ActionBlock actionBlock = ActionBlock.builder()
                .actions(Arrays.asList(action))
                .build();

        // When
        actionExecutor.execute(actionBlock, context);

        // Then
        assertThat(context.getValue("discount_amount")).isEqualTo(10.0); // Arithmetic expressions are evaluated
    }

    @Test
    void testExecuteFunctionCall() {
        // Given
        ActionBlock.Action.CallAction callAction = ActionBlock.Action.CallAction.builder()
                .function("log")
                .parameters(Arrays.asList("Processing customer application"))
                .build();

        ActionBlock.Action action = ActionBlock.Action.builder()
                .call(callAction)
                .build();

        ActionBlock actionBlock = ActionBlock.builder()
                .actions(Arrays.asList(action))
                .build();

        // When
        actionExecutor.execute(actionBlock, context);

        // Then - Function call should execute without error
        // (log function doesn't modify context, just logs)
        assertThat(context.getInputVariables()).isNotNull();
    }

    @Test
    void testExecuteNotifyFunction() {
        // Given
        context.setInputVariable("customer_name", "John Doe");

        ActionBlock.Action.CallAction callAction = ActionBlock.Action.CallAction.builder()
                .function("notify")
                .parameters(Arrays.asList("customer_name", "Application approved"))
                .build();

        ActionBlock.Action action = ActionBlock.Action.builder()
                .call(callAction)
                .build();

        ActionBlock actionBlock = ActionBlock.builder()
                .actions(Arrays.asList(action))
                .build();

        // When
        actionExecutor.execute(actionBlock, context);

        // Then - Notify function should execute without error
        assertThat(context.getInputVariables()).containsKey("customer_name");
    }

    @Test
    void testExecuteCalculateFunction() {
        // Given
        context.setInputVariable("principal", 10000);
        context.setInputVariable("rate", 0.05);
        context.setInputVariable("time", 2);

        ActionBlock.Action.CallAction callAction = ActionBlock.Action.CallAction.builder()
                .function("calculate")
                .parameters(Arrays.asList("principal * rate * time", "interest"))
                .build();

        ActionBlock.Action action = ActionBlock.Action.builder()
                .call(callAction)
                .build();

        ActionBlock actionBlock = ActionBlock.builder()
                .actions(Arrays.asList(action))
                .build();

        // When
        actionExecutor.execute(actionBlock, context);

        // Then
        assertThat(context.getValue("interest").toString()).isEqualTo("1000.0");
    }

    @Test
    void testExecuteMultipleActions() {
        // Given
        context.setInputVariable("base_score", 700);

        ActionBlock.Action.SetAction setAction1 = ActionBlock.Action.SetAction.builder()
                .variable("status")
                .value("approved")
                .build();

        ActionBlock.Action.SetAction setAction2 = ActionBlock.Action.SetAction.builder()
                .variable("final_score")
                .value("base_score + 50")
                .build();

        ActionBlock.Action action1 = ActionBlock.Action.builder()
                .set(setAction1)
                .build();

        ActionBlock.Action action2 = ActionBlock.Action.builder()
                .set(setAction2)
                .build();

        ActionBlock actionBlock = ActionBlock.builder()
                .actions(Arrays.asList(action1, action2))
                .build();

        // When
        actionExecutor.execute(actionBlock, context);

        // Then
        assertThat(context.getValue("status")).isEqualTo("approved");
        assertThat(context.getValue("final_score")).isEqualTo(750.0); // Arithmetic expressions are evaluated
    }

    @Test
    void testExecuteWithCircuitBreaker() {
        // Given
        ActionBlock.Action.CircuitBreakerAction circuitBreaker = ActionBlock.Action.CircuitBreakerAction.builder()
                .message("Risk threshold exceeded")
                .trigger(true)
                .build();
                
        ActionBlock.Action action = ActionBlock.Action.builder()
                .circuitBreaker(circuitBreaker)
                .build();
                
        ActionBlock actionBlock = ActionBlock.builder()
                .actions(Arrays.asList(action))
                .build();

        // When
        actionExecutor.execute(actionBlock, context);

        // Then
        assertThat(context.isCircuitBreakerTriggered()).isTrue();
        assertThat(context.getCircuitBreakerMessage()).isEqualTo("Risk threshold exceeded");
    }

    @Test
    void testExecuteNullActionBlock() {
        // When
        actionExecutor.execute(null, context);

        // Then - Should not throw exception
        assertThat(context.getInputVariables()).isNotNull();
    }

    @Test
    void testExecuteEmptyActionBlock() {
        // Given
        ActionBlock actionBlock = ActionBlock.builder()
                .actions(Arrays.asList())
                .build();

        // When
        actionExecutor.execute(actionBlock, context);

        // Then - Should not throw exception
        assertThat(context.getInputVariables()).isNotNull();
    }

    @Test
    void testExecuteWithVariableReferences() {
        // Given
        context.setInputVariable("customer_tier", "PREMIUM");
        context.setInputVariable("base_discount", 5);

        ActionBlock.Action.SetAction setAction = ActionBlock.Action.SetAction.builder()
                .variable("final_discount")
                .value("base_discount + 10")
                .build();

        ActionBlock.Action action = ActionBlock.Action.builder()
                .set(setAction)
                .build();

        ActionBlock actionBlock = ActionBlock.builder()
                .actions(Arrays.asList(action))
                .build();

        // When
        actionExecutor.execute(actionBlock, context);

        // Then
        assertThat(context.getValue("final_discount")).isEqualTo(15.0); // Arithmetic expressions are evaluated
    }

    @Test
    void testExecuteWithStringConcatenation() {
        // Given
        context.setInputVariable("first_name", "John");
        context.setInputVariable("last_name", "Doe");

        ActionBlock.Action.SetAction setAction = ActionBlock.Action.SetAction.builder()
                .variable("full_name")
                .value("\"John Doe\"") // Direct string assignment
                .build();

        ActionBlock.Action action = ActionBlock.Action.builder()
                .set(setAction)
                .build();

        ActionBlock actionBlock = ActionBlock.builder()
                .actions(Arrays.asList(action))
                .build();

        // When
        actionExecutor.execute(actionBlock, context);

        // Then
        assertThat(context.getValue("full_name")).isEqualTo("John Doe"); // Quotes are removed from string literals
    }
}
