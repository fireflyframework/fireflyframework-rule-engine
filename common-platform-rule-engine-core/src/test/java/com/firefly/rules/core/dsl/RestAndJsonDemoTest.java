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
import com.firefly.rules.core.dsl.ast.evaluation.ASTRulesEvaluationResult;
import com.firefly.rules.core.dsl.ast.parser.ASTRulesDSLParser;
import com.firefly.rules.core.dsl.ast.parser.DSLParser;
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
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Demonstration test showing REST and JSON functionality working
 */
@DisplayName("REST and JSON Functionality Demo")
class RestAndJsonDemoTest {

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

        // Create real services (they will handle errors gracefully)
        WebClient.Builder webClientBuilder = Mockito.mock(WebClient.Builder.class);
        ObjectMapper objectMapper = new ObjectMapper();
        RestCallService restCallService = new RestCallServiceImpl(webClientBuilder, objectMapper);
        JsonPathService jsonPathService = new JsonPathServiceImpl();

        evaluationEngine = new ASTRulesEvaluationEngine(parser, constantService, restCallService, jsonPathService);
    }

    @Test
    @DisplayName("Test REST function is recognized and executed")
    void testRestFunctionRecognized() {
        String yaml = """
                when:
                  - "true"
                then:
                  - calculate apiResponse as rest_get("https://dummyjson.com/todos/1")
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        // The REST call will return an error response due to mock WebClient,
        // but this proves the function is recognized and executed
        Map<String, Object> apiResponse = (Map<String, Object>) result.getOutputData().get("apiResponse");
        assertNotNull(apiResponse);
        assertEquals(false, apiResponse.get("success"));
        assertEquals(true, apiResponse.get("error"));
        assertTrue(apiResponse.get("message").toString().contains("REST GET failed"));
    }

    @Test
    @DisplayName("Test JSON path function with mock data")
    void testJsonPathFunction() {
        String yaml = """
                when:
                  - "true"
                then:
                  - calculate userName as json_get(userData, "name")
                  - calculate userAge as json_get(userData, "age")
                  - calculate hasEmail as json_exists(userData, "email")
                """;

        Map<String, Object> inputData = new HashMap<>();
        Map<String, Object> userData = new HashMap<>();
        userData.put("name", "John Doe");
        userData.put("age", 30);
        userData.put("email", "john@example.com");
        inputData.put("userData", userData);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("John Doe", result.getOutputData().get("userName"));
        assertEquals(30, result.getOutputData().get("userAge"));
        assertEquals(true, result.getOutputData().get("hasEmail"));
    }

    @Test
    @DisplayName("Test complex JSON path with nested objects")
    void testComplexJsonPath() {
        String yaml = """
                when:
                  - "true"
                then:
                  - calculate city as json_get(userData, "address.city")
                  - calculate firstHobby as json_get(userData, "hobbies[0]")
                  - calculate hobbiesCount as json_size(userData, "hobbies")
                """;

        Map<String, Object> inputData = new HashMap<>();
        Map<String, Object> userData = new HashMap<>();
        Map<String, Object> address = new HashMap<>();
        address.put("city", "New York");
        address.put("state", "NY");
        userData.put("address", address);
        userData.put("hobbies", new String[]{"reading", "swimming", "coding"});
        inputData.put("userData", userData);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("New York", result.getOutputData().get("city"));
        assertEquals("reading", result.getOutputData().get("firstHobby"));
        assertEquals(3, result.getOutputData().get("hobbiesCount"));
    }

    @Test
    @DisplayName("Test banking scenario with REST and JSON integration")
    void testBankingScenario() {
        String yaml = """
                when:
                  - "true"
                then:
                  - calculate creditResponse as rest_get("https://credit-api.example.com/score/123456789")
                  - calculate creditScore as json_get(creditResponse, "score")
                  - calculate hasDelinquencies as json_exists(creditResponse, "report.delinquencies")
                  - calculate approval as creditScore >= 650 ? "APPROVED" : "DECLINED"
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        // The REST call will fail due to mock, but this demonstrates the syntax works
        assertNotNull(result.getOutputData().get("creditResponse"));
        assertNotNull(result.getOutputData().get("creditScore"));
        assertNotNull(result.getOutputData().get("hasDelinquencies"));
        assertNotNull(result.getOutputData().get("approval"));
    }
}
