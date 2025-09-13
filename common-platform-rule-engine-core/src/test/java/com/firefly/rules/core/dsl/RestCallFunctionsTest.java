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

import com.firefly.rules.core.dsl.ast.evaluation.ASTRulesEvaluationEngine;
import com.firefly.rules.core.dsl.ast.parser.ASTRulesDSLParser;
import com.firefly.rules.core.services.ConstantService;
import com.firefly.rules.core.services.JsonPathService;
import com.firefly.rules.core.services.RestCallService;
import com.firefly.rules.core.services.impl.JsonPathServiceImpl;
import com.firefly.rules.core.services.impl.RestCallServiceImpl;
import com.firefly.rules.interfaces.dtos.evaluation.RulesEvaluationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Test REST call functions in the DSL
 */
@ExtendWith(MockitoExtension.class)
class RestCallFunctionsTest {

    @Mock
    private ConstantService constantService;

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    private ASTRulesEvaluationEngine evaluationEngine;
    private RestCallService restCallService;
    private JsonPathService jsonPathService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Setup mocks
        when(webClientBuilder.defaultHeader(any(), any())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        when(constantService.findByCode(any())).thenReturn(Mono.empty());

        // Create services
        objectMapper = new ObjectMapper();
        restCallService = new RestCallServiceImpl(webClientBuilder, objectMapper);
        jsonPathService = new JsonPathServiceImpl();

        // Create evaluation engine
        ASTRulesDSLParser parser = new ASTRulesDSLParser();
        evaluationEngine = new ASTRulesEvaluationEngine(parser, constantService, restCallService, jsonPathService);
    }

    @Test
    void testRestGetFunctionInSimpleSyntax() {
        String yaml = """
                rules:
                  - name: "Test REST GET"
                    when:
                      - "true"
                    then:
                      - call rest_get with ["https://dummyjson.com/todos/1"]
                """;

        Map<String, Object> inputData = new HashMap<>();
        RulesEvaluationResult result = evaluationEngine.evaluate(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        // Note: This test will make an actual HTTP call to dummyjson.com
        // In a real test environment, you would mock the WebClient
    }

    @Test
    void testRestGetFunctionInComplexSyntax() {
        String yaml = """
                rules:
                  - name: "Test REST GET Complex"
                    conditions:
                      - if: "true"
                        then:
                          actions:
                            - calculate: "rest_get('https://dummyjson.com/todos/1')"
                              as: "todoData"
                """;

        Map<String, Object> inputData = new HashMap<>();
        RulesEvaluationResult result = evaluationEngine.evaluate(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        // The result should contain the calculated todoData
    }

    @Test
    void testRestPostFunction() {
        String yaml = """
                rules:
                  - name: "Test REST POST"
                    when:
                      - "true"
                    then:
                      - call rest_post with ["https://dummyjson.com/todos/add", {"todo": "Test todo", "completed": false, "userId": 1}]
                """;

        Map<String, Object> inputData = new HashMap<>();
        RulesEvaluationResult result = evaluationEngine.evaluate(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    @Test
    void testRestCallWithHeaders() {
        String yaml = """
                rules:
                  - name: "Test REST with Headers"
                    when:
                      - "true"
                    then:
                      - call rest_get with ["https://dummyjson.com/todos/1", {"Content-Type": "application/json"}]
                """;

        Map<String, Object> inputData = new HashMap<>();
        RulesEvaluationResult result = evaluationEngine.evaluate(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    @Test
    void testRestCallWithTimeout() {
        String yaml = """
                rules:
                  - name: "Test REST with Timeout"
                    when:
                      - "true"
                    then:
                      - call rest_get with ["https://dummyjson.com/todos/1", {}, 10000]
                """;

        Map<String, Object> inputData = new HashMap<>();
        RulesEvaluationResult result = evaluationEngine.evaluate(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    @Test
    void testGenericRestCallFunction() {
        String yaml = """
                rules:
                  - name: "Test Generic REST Call"
                    when:
                      - "true"
                    then:
                      - call rest_call with ["GET", "https://dummyjson.com/todos/1"]
                """;

        Map<String, Object> inputData = new HashMap<>();
        RulesEvaluationResult result = evaluationEngine.evaluate(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    @Test
    void testRestCallErrorHandling() {
        String yaml = """
                rules:
                  - name: "Test REST Error Handling"
                    when:
                      - "true"
                    then:
                      - call rest_get with ["https://invalid-url-that-does-not-exist.com/api"]
                """;

        Map<String, Object> inputData = new HashMap<>();
        RulesEvaluationResult result = evaluationEngine.evaluate(yaml, inputData);

        assertNotNull(result);
        // The rule should still succeed, but the REST call should return an error response
        assertTrue(result.isSuccess());
    }

    @Test
    void testRestCallWithVariables() {
        String yaml = """
                rules:
                  - name: "Test REST with Variables"
                    when:
                      - "true"
                    then:
                      - calculate: "'https://dummyjson.com/todos/' + todoId"
                        as: "apiUrl"
                      - call rest_get with [apiUrl]
                """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("todoId", "1");
        
        RulesEvaluationResult result = evaluationEngine.evaluate(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    @Test
    void testRestCallInCondition() {
        String yaml = """
                rules:
                  - name: "Test REST in Condition"
                    conditions:
                      - if: "json_get(rest_get('https://dummyjson.com/todos/1'), 'completed') == false"
                        then:
                          actions:
                            - set: "todoIncomplete"
                              to: true
                """;

        Map<String, Object> inputData = new HashMap<>();
        RulesEvaluationResult result = evaluationEngine.evaluate(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
    }
}
