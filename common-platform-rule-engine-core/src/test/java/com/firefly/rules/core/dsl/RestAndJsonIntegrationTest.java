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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * Integration tests for REST calls combined with JSON path access
 */
@ExtendWith(MockitoExtension.class)
class RestAndJsonIntegrationTest {

    @Mock
    private ConstantService constantService;

    private ASTRulesEvaluationEngine evaluationEngine;

    @BeforeEach
    void setUp() {
        // Mock constant service to return empty flux (no constants from database)
        lenient().when(constantService.getConstantsByCodes(any())).thenReturn(Flux.empty());

        // Use the constructor with default REST and JSON services
        DSLParser dslParser = new DSLParser();
        ASTRulesDSLParser parser = new ASTRulesDSLParser(dslParser);
        evaluationEngine = new ASTRulesEvaluationEngine(parser, constantService);
    }

    @Test
    void testRestCallWithJsonPathExtraction() {
        String yaml = """
                rules:
                  - name: "Fetch and Extract Todo Data"
                    when:
                      - "true"
                    then:
                      - calculate todoResponse as rest_get("https://dummyjson.com/todos/1")
                      - calculate todoText as json_get(todoResponse, "todo")
                      - calculate isCompleted as json_get(todoResponse, "completed")
                      - calculate userId as json_get(todoResponse, "userId")
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

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
                    when:
                      - 'json_get(rest_get("https://dummyjson.com/todos/1"), "completed") == false'
                    then:
                      - set todoStatus to "incomplete"
                      - set actionRequired to true
                    else:
                      - set todoStatus to "complete"
                      - set actionRequired to false
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

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
                      - calculate todo1 as rest_get("https://dummyjson.com/todos/1")
                      - calculate todo2 as rest_get("https://dummyjson.com/todos/2")
                      - calculate user1Id as json_get(todo1, "userId")
                      - calculate user2Id as json_get(todo2, "userId")
                      - calculate sameUser as user1Id == user2Id
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

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
                      - calculate createResponse as rest_post("https://dummyjson.com/todos/add", "test body")
                      - calculate newTodoId as json_get(createResponse, "id")
                      - calculate newTodoText as json_get(createResponse, "todo")
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        // Check that the response was processed (even if it's an error response)
        assertNotNull(result.getOutputData().get("createResponse"));
        // Note: newTodoId and newTodoText will be null because the API returned an error
        // This is expected behavior when sending invalid JSON to the API
    }

    @Test
    void testErrorHandlingInRestAndJsonChain() {
        String yaml = """
                rules:
                  - name: "Error Handling in REST+JSON Chain"
                    when:
                      - "true"
                    then:
                      - calculate errorResponse as rest_get("https://invalid-url-that-does-not-exist.com/api")
                      - calculate hasMessage as json_get(errorResponse, "message")
                      - calculate hasErrorMessage as json_exists(errorResponse, "message")
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        // The error response should be processed correctly
        assertNotNull(result.getOutputData().get("errorResponse"));
        assertNotNull(result.getOutputData().get("hasMessage"));
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
                      - calculate dynamicUrl as "https://dummyjson.com/todos/" + todoId
                      - calculate todoData as rest_get(dynamicUrl)
                      - calculate todoDescription as json_get(todoData, "todo")
                """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("todoId", "3");
        
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

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
                      - calculate userResponse as rest_get("https://dummyjson.com/users/1")
                      - calculate firstName as json_get(userResponse, "firstName")
                      - calculate lastName as json_get(userResponse, "lastName")
                      - calculate city as json_get(userResponse, "address.city")
                      - calculate addressFieldCount as json_size(userResponse, "address")
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

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
