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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration tests for REST calls combined with JSON path access
 */
@ExtendWith(MockitoExtension.class)
class RestAndJsonIntegrationTest {

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
    void testRestCallWithJsonPathExtraction() {
        String yaml = """
                rules:
                  - name: "Fetch and Extract Todo Data"
                    when:
                      - "true"
                    then:
                      - calculate: "rest_get('https://dummyjson.com/todos/1')"
                        as: "todoResponse"
                      - calculate: "json_get(todoResponse, 'todo')"
                        as: "todoText"
                      - calculate: "json_get(todoResponse, 'completed')"
                        as: "isCompleted"
                      - calculate: "json_get(todoResponse, 'userId')"
                        as: "userId"
                """;

        Map<String, Object> inputData = new HashMap<>();
        RulesEvaluationResult result = evaluationEngine.evaluate(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        // Check that the extracted values are present
        assertNotNull(result.getOutputData().get("todoResponse"));
        assertNotNull(result.getOutputData().get("todoText"));
        assertNotNull(result.getOutputData().get("isCompleted"));
        assertNotNull(result.getOutputData().get("userId"));
    }

    @Test
    void testConditionalLogicBasedOnRestResponse() {
        String yaml = """
                rules:
                  - name: "Conditional Logic on REST Response"
                    conditions:
                      - if: "json_get(rest_get('https://dummyjson.com/todos/1'), 'completed') == false"
                        then:
                          actions:
                            - set: "todoStatus"
                              to: "incomplete"
                            - set: "actionRequired"
                              to: true
                        else:
                          actions:
                            - set: "todoStatus"
                              to: "complete"
                            - set: "actionRequired"
                              to: false
                """;

        Map<String, Object> inputData = new HashMap<>();
        RulesEvaluationResult result = evaluationEngine.evaluate(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        // The result should contain the conditional outputs
        assertNotNull(result.getOutputData().get("todoStatus"));
        assertNotNull(result.getOutputData().get("actionRequired"));
    }

    @Test
    void testMultipleRestCallsWithJsonProcessing() {
        String yaml = """
                rules:
                  - name: "Multiple REST Calls with JSON Processing"
                    when:
                      - "true"
                    then:
                      - calculate: "rest_get('https://dummyjson.com/todos/1')"
                        as: "todo1"
                      - calculate: "rest_get('https://dummyjson.com/todos/2')"
                        as: "todo2"
                      - calculate: "json_get(todo1, 'userId')"
                        as: "user1Id"
                      - calculate: "json_get(todo2, 'userId')"
                        as: "user2Id"
                      - calculate: "user1Id == user2Id"
                        as: "sameUser"
                """;

        Map<String, Object> inputData = new HashMap<>();
        RulesEvaluationResult result = evaluationEngine.evaluate(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        // Check that all calculated values are present
        assertNotNull(result.getOutputData().get("todo1"));
        assertNotNull(result.getOutputData().get("todo2"));
        assertNotNull(result.getOutputData().get("user1Id"));
        assertNotNull(result.getOutputData().get("user2Id"));
        assertNotNull(result.getOutputData().get("sameUser"));
    }

    @Test
    void testRestPostWithJsonResponseProcessing() {
        String yaml = """
                rules:
                  - name: "POST and Process Response"
                    when:
                      - "true"
                    then:
                      - calculate: "rest_post('https://dummyjson.com/todos/add', {'todo': 'New task', 'completed': false, 'userId': 1})"
                        as: "createResponse"
                      - calculate: "json_get(createResponse, 'id')"
                        as: "newTodoId"
                      - calculate: "json_get(createResponse, 'todo')"
                        as: "newTodoText"
                """;

        Map<String, Object> inputData = new HashMap<>();
        RulesEvaluationResult result = evaluationEngine.evaluate(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        // Check that the response was processed
        assertNotNull(result.getOutputData().get("createResponse"));
        assertNotNull(result.getOutputData().get("newTodoId"));
        assertNotNull(result.getOutputData().get("newTodoText"));
    }

    @Test
    void testErrorHandlingInRestAndJsonChain() {
        String yaml = """
                rules:
                  - name: "Error Handling in REST+JSON Chain"
                    when:
                      - "true"
                    then:
                      - calculate: "rest_get('https://invalid-url-that-does-not-exist.com/api')"
                        as: "errorResponse"
                      - calculate: "json_get(errorResponse, 'error')"
                        as: "hasError"
                      - calculate: "json_exists(errorResponse, 'message')"
                        as: "hasErrorMessage"
                """;

        Map<String, Object> inputData = new HashMap<>();
        RulesEvaluationResult result = evaluationEngine.evaluate(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        // The error response should be processed correctly
        assertNotNull(result.getOutputData().get("errorResponse"));
        assertNotNull(result.getOutputData().get("hasError"));
        assertNotNull(result.getOutputData().get("hasErrorMessage"));
    }

    @Test
    void testDynamicUrlConstructionWithJsonData() {
        String yaml = """
                rules:
                  - name: "Dynamic URL Construction"
                    when:
                      - "true"
                    then:
                      - calculate: "'https://dummyjson.com/todos/' + todoId"
                        as: "dynamicUrl"
                      - calculate: "rest_get(dynamicUrl)"
                        as: "todoData"
                      - calculate: "json_get(todoData, 'todo')"
                        as: "todoDescription"
                """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("todoId", "3");
        
        RulesEvaluationResult result = evaluationEngine.evaluate(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        // Check that the dynamic URL was constructed and used
        assertEquals("https://dummyjson.com/todos/3", result.getOutputData().get("dynamicUrl"));
        assertNotNull(result.getOutputData().get("todoData"));
        assertNotNull(result.getOutputData().get("todoDescription"));
    }

    @Test
    void testComplexJsonPathOnRestResponse() {
        String yaml = """
                rules:
                  - name: "Complex JSON Path on REST Response"
                    when:
                      - "true"
                    then:
                      - calculate: "rest_get('https://dummyjson.com/users/1')"
                        as: "userResponse"
                      - calculate: "json_get(userResponse, 'firstName')"
                        as: "firstName"
                      - calculate: "json_get(userResponse, 'lastName')"
                        as: "lastName"
                      - calculate: "json_get(userResponse, 'address.city')"
                        as: "city"
                      - calculate: "json_size(userResponse, 'address')"
                        as: "addressFieldCount"
                """;

        Map<String, Object> inputData = new HashMap<>();
        RulesEvaluationResult result = evaluationEngine.evaluate(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        // Check that nested JSON paths were processed
        assertNotNull(result.getOutputData().get("userResponse"));
        assertNotNull(result.getOutputData().get("firstName"));
        assertNotNull(result.getOutputData().get("lastName"));
        assertNotNull(result.getOutputData().get("city"));
        assertNotNull(result.getOutputData().get("addressFieldCount"));
    }
}
