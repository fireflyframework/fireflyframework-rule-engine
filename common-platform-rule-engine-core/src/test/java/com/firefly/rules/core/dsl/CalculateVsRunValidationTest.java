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

package com.firefly.rules.core.dsl;

import com.firefly.rules.core.dsl.evaluation.ASTRulesEvaluationEngine;
import com.firefly.rules.core.dsl.evaluation.ASTRulesEvaluationResult;
import com.firefly.rules.core.dsl.parser.ASTRulesDSLParser;
import com.firefly.rules.core.dsl.parser.DSLParser;
import com.firefly.rules.core.services.ConstantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify that 'calculate' only works for mathematical operations
 * and 'run' is required for function calls, REST calls, and JSON operations.
 */
@DisplayName("Calculate vs Run Command Validation Tests")
class CalculateVsRunValidationTest {

    private ASTRulesEvaluationEngine evaluationEngine;

    @BeforeEach
    void setUp() {
        DSLParser dslParser = new DSLParser();
        ASTRulesDSLParser parser = new ASTRulesDSLParser(dslParser);
        ConstantService constantService = Mockito.mock(ConstantService.class);

        Mockito.when(constantService.getConstantsByCodes(Mockito.anyList()))
               .thenReturn(Flux.empty());

        evaluationEngine = new ASTRulesEvaluationEngine(parser, constantService, null, null);
    }

    @Test
    @DisplayName("Calculate should work for pure mathematical operations")
    void testCalculateWithMathOperations() {
        String yaml = """
                name: "Math Operations Test"
                description: "Test calculate with pure math"
                
                inputs:
                  - amount
                  - rate
                  - years
                
                when:
                  - "true"
                
                then:
                  - calculate interest as amount * rate
                  - calculate total as amount + interest
                  - calculate compound as amount * (1 + rate) ** years
                  - calculate remainder as amount % 100
                
                output:
                  interest: number
                  total: number
                  compound: number
                  remainder: number
                """;

        Map<String, Object> inputData = Map.of(
            "amount", 1000,
            "rate", 0.05,
            "years", 2
        );

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertTrue(result.isSuccess());
        assertEquals(50.0, result.getOutputData().get("interest"));
        assertEquals(1050.0, result.getOutputData().get("total"));
    }

    @Test
    @DisplayName("Calculate should reject function calls like max()")
    void testCalculateRejectsFunctionCalls() {
        String yaml = """
                name: "Invalid Calculate Test"
                description: "Test calculate with function call"
                
                inputs:
                  - value1
                  - value2
                
                when:
                  - "true"
                
                then:
                  - calculate maximum as max(value1, value2)
                
                output:
                  maximum: number
                """;

        Map<String, Object> inputData = Map.of(
            "value1", 10,
            "value2", 20
        );

        Exception exception = assertThrows(Exception.class, () -> {
            evaluationEngine.evaluateRules(yaml, inputData);
        });

        String message = exception.getMessage();
        assertTrue(message.contains("calculate") || message.contains("run"),
            "Error message should mention calculate/run: " + message);
    }

    @Test
    @DisplayName("Run should work for function calls")
    void testRunWithFunctionCalls() {
        String yaml = """
                name: "Run Function Test"
                description: "Test run with function calls"
                
                inputs:
                  - value1
                  - value2
                  - value3
                
                when:
                  - "true"
                
                then:
                  - run maximum as max(value1, value2, value3)
                  - run minimum as min(value1, value2, value3)
                  - run absolute as abs(value1)
                
                output:
                  maximum: number
                  minimum: number
                  absolute: number
                """;

        Map<String, Object> inputData = Map.of(
            "value1", -10,
            "value2", 20,
            "value3", 15
        );

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertTrue(result.isSuccess());
        assertEquals(20.0, result.getOutputData().get("maximum"));
        assertEquals(-10.0, result.getOutputData().get("minimum"));
        assertEquals(10.0, result.getOutputData().get("absolute"));
    }

    @Test
    @DisplayName("Calculate should reject REST API calls")
    void testCalculateRejectsRestCalls() {
        String yaml = """
                name: "Invalid REST Calculate Test"
                description: "Test calculate with REST call"
                
                inputs:
                  - userId
                
                when:
                  - "true"
                
                then:
                  - calculate user_data as rest_get("https://api.example.com/users/" + userId)
                
                output:
                  user_data: text
                """;

        Map<String, Object> inputData = Map.of("userId", "123");

        Exception exception = assertThrows(Exception.class, () -> {
            evaluationEngine.evaluateRules(yaml, inputData);
        });

        String message = exception.getMessage();
        assertTrue(message.contains("calculate") || message.contains("run"),
            "Error message should mention calculate/run: " + message);
    }

    @Test
    @DisplayName("Calculate should reject JSON operations")
    void testCalculateRejectsJsonOperations() {
        String yaml = """
                name: "Invalid JSON Calculate Test"
                description: "Test calculate with JSON operation"
                
                inputs:
                  - userData
                
                when:
                  - "true"
                
                then:
                  - calculate user_name as json_get(userData, "name")
                
                output:
                  user_name: text
                """;

        Map<String, Object> inputData = Map.of(
            "userData", Map.of("name", "John", "age", 30)
        );

        Exception exception = assertThrows(Exception.class, () -> {
            evaluationEngine.evaluateRules(yaml, inputData);
        });

        String message = exception.getMessage();
        assertTrue(message.contains("calculate") || message.contains("run"),
            "Error message should mention calculate/run: " + message);
    }

    @Test
    @DisplayName("Mixed calculate and run should work correctly")
    void testMixedCalculateAndRun() {
        String yaml = """
                name: "Mixed Calculate and Run Test"
                description: "Test both calculate and run"
                
                inputs:
                  - amount
                  - tax_rate
                  - value1
                  - value2
                
                when:
                  - "true"
                
                then:
                  - calculate tax as amount * tax_rate
                  - calculate total as amount + tax
                  - run maximum as max(value1, value2)
                  - run formatted as format_currency(total)
                
                output:
                  tax: number
                  total: number
                  maximum: number
                  formatted: text
                """;

        Map<String, Object> inputData = Map.of(
            "amount", 100,
            "tax_rate", 0.15,
            "value1", 50,
            "value2", 75
        );

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertTrue(result.isSuccess());
        assertEquals(15.0, result.getOutputData().get("tax"));
        assertEquals(115.0, result.getOutputData().get("total"));
        assertEquals(75.0, result.getOutputData().get("maximum"));
        assertEquals("$115.00", result.getOutputData().get("formatted"));
    }
}

