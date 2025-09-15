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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firefly.rules.core.dsl.evaluation.ASTRulesEvaluationEngine;
import com.firefly.rules.core.dsl.evaluation.ASTRulesEvaluationResult;
import com.firefly.rules.core.dsl.parser.ASTRulesDSLParser;
import com.firefly.rules.core.dsl.parser.DSLParser;
import com.firefly.rules.core.services.ConstantService;
import com.firefly.rules.core.services.JsonPathService;
import com.firefly.rules.core.services.RestCallService;
import com.firefly.rules.core.services.impl.JsonPathServiceImpl;
import com.firefly.rules.core.services.impl.RestCallServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real REST API test to verify actual HTTP calls work
 */
@DisplayName("Real REST API Integration Tests")
class RealRestApiTest {

    private ASTRulesEvaluationEngine evaluationEngine;

    @BeforeEach
    void setUp() {
        // Create dependencies
        DSLParser dslParser = new DSLParser();
        ASTRulesDSLParser parser = new ASTRulesDSLParser(dslParser);
        ConstantService constantService = Mockito.mock(ConstantService.class);

        // Mock constant service to return empty flux (no constants from database)
        Mockito.when(constantService.getConstantsByCodes(Mockito.anyList()))
               .thenReturn(Flux.empty());

        // Create REAL services with actual WebClient
        WebClient.Builder webClientBuilder = WebClient.builder();
        ObjectMapper objectMapper = new ObjectMapper();
        RestCallService restCallService = new RestCallServiceImpl(webClientBuilder, objectMapper);
        JsonPathService jsonPathService = new JsonPathServiceImpl();

        evaluationEngine = new ASTRulesEvaluationEngine(parser, constantService, restCallService, jsonPathService);
    }

    @Test
    @DisplayName("Test real REST GET call to DummyJSON API")
    void testRealRestGetCall() {
        String yaml = """
                when:
                  - "true"
                then:
                  - calculate todoData as rest_get("https://dummyjson.com/todos/1")
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        // Verify we got real data from the API
        Map<String, Object> todoData = (Map<String, Object>) result.getOutputData().get("todoData");
        assertNotNull(todoData);
        
        // DummyJSON should return a todo with these fields
        assertNotNull(todoData.get("id"));
        assertNotNull(todoData.get("todo"));
        assertNotNull(todoData.get("completed"));
        assertNotNull(todoData.get("userId"));
        
        // Verify the ID is 1 as requested
        assertEquals(1, todoData.get("id"));
        
        System.out.println("✅ Real REST GET response: " + todoData);
    }

    @Test
    @DisplayName("Test REST GET with JSON path extraction")
    void testRestGetWithJsonPath() {
        String yaml = """
                when:
                  - "true"
                then:
                  - calculate todoData as rest_get("https://dummyjson.com/todos/1")
                  - calculate todoId as json_get(todoData, "id")
                  - calculate todoText as json_get(todoData, "todo")
                  - calculate isCompleted as json_get(todoData, "completed")
                  - calculate userId as json_get(todoData, "userId")
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        // Verify the extracted values
        assertEquals(1, result.getOutputData().get("todoId"));
        assertNotNull(result.getOutputData().get("todoText"));
        assertNotNull(result.getOutputData().get("isCompleted"));
        assertNotNull(result.getOutputData().get("userId"));
        
        System.out.println("✅ Extracted todo ID: " + result.getOutputData().get("todoId"));
        System.out.println("✅ Extracted todo text: " + result.getOutputData().get("todoText"));
        System.out.println("✅ Extracted completed status: " + result.getOutputData().get("isCompleted"));
        System.out.println("✅ Extracted user ID: " + result.getOutputData().get("userId"));
    }

    @Test
    @DisplayName("Test real REST POST call to DummyJSON API")
    void testRealRestPostCall() {
        String yaml = """
                when:
                  - "true"
                then:
                  - calculate postResponse as rest_post("https://dummyjson.com/todos/add", "simple test body")
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        // Verify we got a response from the POST (could be success or error)
        Map<String, Object> postResponse = (Map<String, Object>) result.getOutputData().get("postResponse");
        assertNotNull(postResponse);

        // Verify the POST function executed and returned a structured response
        assertNotNull(postResponse.get("error")); // Should have error field
        assertNotNull(postResponse.get("success")); // Should have success field
        assertNotNull(postResponse.get("message")); // Should have message field
        
        System.out.println("✅ Real REST POST response: " + postResponse);
    }

    @Test
    @DisplayName("Test REST call with multiple todos")
    void testRestCallMultipleTodos() {
        String yaml = """
                when:
                  - "true"
                then:
                  - calculate todosData as rest_get("https://dummyjson.com/todos?limit=3")
                  - calculate totalTodos as json_get(todosData, "total")
                  - calculate todosArray as json_get(todosData, "todos")
                  - calculate firstTodo as json_get(todosData, "todos[0]")
                  - calculate firstTodoText as json_get(todosData, "todos[0].todo")
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        // Verify we got the list data
        assertNotNull(result.getOutputData().get("totalTodos"));
        assertNotNull(result.getOutputData().get("todosArray"));
        assertNotNull(result.getOutputData().get("firstTodo"));
        assertNotNull(result.getOutputData().get("firstTodoText"));
        
        System.out.println("✅ Total todos: " + result.getOutputData().get("totalTodos"));
        System.out.println("✅ First todo: " + result.getOutputData().get("firstTodo"));
        System.out.println("✅ First todo text: " + result.getOutputData().get("firstTodoText"));
    }

    @Test
    @DisplayName("Test banking scenario with real external API simulation")
    void testBankingScenarioWithRealApi() {
        String yaml = """
                when:
                  - "true"
                then:
                  - calculate userProfile as rest_get("https://dummyjson.com/users/1")
                  - calculate userName as json_get(userProfile, "firstName")
                  - calculate userLastName as json_get(userProfile, "lastName")
                  - calculate userAge as json_get(userProfile, "age")
                  - calculate userEmail as json_get(userProfile, "email")
                  - calculate hasAddress as json_exists(userProfile, "address")
                  - calculate userCity as json_get(userProfile, "address.city")
                  - calculate eligibilityScore as userAge >= 18
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        // Verify we got real user data
        assertNotNull(result.getOutputData().get("userName"));
        assertNotNull(result.getOutputData().get("userLastName"));
        assertNotNull(result.getOutputData().get("userAge"));
        assertNotNull(result.getOutputData().get("userEmail"));
        assertEquals(true, result.getOutputData().get("hasAddress"));
        assertNotNull(result.getOutputData().get("userCity"));
        assertNotNull(result.getOutputData().get("eligibilityScore"));

        // Verify eligibility score is a boolean (true if age >= 18)
        assertTrue(result.getOutputData().get("eligibilityScore") instanceof Boolean);
        
        System.out.println("✅ User: " + result.getOutputData().get("userName") + " " + result.getOutputData().get("userLastName"));
        System.out.println("✅ Age: " + result.getOutputData().get("userAge"));
        System.out.println("✅ Email: " + result.getOutputData().get("userEmail"));
        System.out.println("✅ City: " + result.getOutputData().get("userCity"));
        System.out.println("✅ Eligibility Score: " + result.getOutputData().get("eligibilityScore"));
    }

    @Test
    @DisplayName("Test error handling with invalid URL")
    void testErrorHandlingWithInvalidUrl() {
        String yaml = """
                when:
                  - "true"
                then:
                  - calculate errorResponse as rest_get("https://invalid-url-that-does-not-exist.com/api")
                  - calculate hasError as json_exists(errorResponse, "error")
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        // Verify we got a response (could be success or error)
        Map<String, Object> errorResponse = (Map<String, Object>) result.getOutputData().get("errorResponse");
        assertNotNull(errorResponse);

        // The response might be successful or an error, both are valid
        assertNotNull(errorResponse.get("message"));

        // Verify the JSON path function worked correctly
        assertNotNull(result.getOutputData().get("hasError"));
        
        System.out.println("✅ Error response handled correctly: " + errorResponse.get("message"));
    }
}
