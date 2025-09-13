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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Verification test for REST functions to ensure documentation accuracy
 */
@DisplayName("REST Functions Verification Tests")
class RestFunctionsVerificationTest {

    private ASTRulesEvaluationEngine evaluationEngine;
    private WebClient.Builder webClientBuilder;
    private WebClient webClient;
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    private WebClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        // Create dependencies
        DSLParser dslParser = new DSLParser();
        ASTRulesDSLParser parser = new ASTRulesDSLParser(dslParser);
        ConstantService constantService = Mockito.mock(ConstantService.class);

        // Mock constant service to return empty flux (no constants from database)
        when(constantService.getConstantsByCodes(Mockito.anyList()))
               .thenReturn(Flux.empty());

        // Create comprehensive mocks for WebClient
        webClientBuilder = Mockito.mock(WebClient.Builder.class);
        webClient = Mockito.mock(WebClient.class);
        requestBodyUriSpec = Mockito.mock(WebClient.RequestBodyUriSpec.class);
        requestHeadersSpec = Mockito.mock(WebClient.RequestHeadersSpec.class);
        responseSpec = Mockito.mock(WebClient.ResponseSpec.class);

        // Setup mock chain
        lenient().when(webClientBuilder.defaultHeader(any(), any())).thenReturn(webClientBuilder);
        lenient().when(webClientBuilder.build()).thenReturn(webClient);
        lenient().when(webClient.method(any())).thenReturn(requestBodyUriSpec);
        lenient().when(requestBodyUriSpec.uri(any(String.class))).thenReturn(requestHeadersSpec);
        lenient().when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        lenient().when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("{\"id\":1,\"todo\":\"Test\",\"completed\":false}"));

        // Create services
        ObjectMapper objectMapper = new ObjectMapper();
        RestCallService restCallService = new RestCallServiceImpl(webClientBuilder, objectMapper);
        JsonPathService jsonPathService = new JsonPathServiceImpl();

        evaluationEngine = new ASTRulesEvaluationEngine(parser, constantService, restCallService, jsonPathService);
    }

    @Test
    @DisplayName("Test rest_get function is recognized and executed")
    void testRestGetFunction() {
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
        
        // The REST call should return a response (either success or error)
        assertNotNull(result.getOutputData().get("apiResponse"));
    }

    @Test
    @DisplayName("Test rest_post function is recognized and executed")
    void testRestPostFunction() {
        String yaml = """
                when:
                  - "true"
                then:
                  - calculate apiResponse as rest_post("https://dummyjson.com/todos/add", "test body")
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.getOutputData().get("apiResponse"));
    }

    @Test
    @DisplayName("Test rest_put function is recognized and executed")
    void testRestPutFunction() {
        String yaml = """
                when:
                  - "true"
                then:
                  - calculate apiResponse as rest_put("https://dummyjson.com/todos/1", "updated body")
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.getOutputData().get("apiResponse"));
    }

    @Test
    @DisplayName("Test rest_delete function is recognized and executed")
    void testRestDeleteFunction() {
        String yaml = """
                when:
                  - "true"
                then:
                  - calculate apiResponse as rest_delete("https://dummyjson.com/todos/1")
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.getOutputData().get("apiResponse"));
    }

    @Test
    @DisplayName("Test rest_patch function is recognized and executed")
    void testRestPatchFunction() {
        String yaml = """
                when:
                  - "true"
                then:
                  - calculate apiResponse as rest_patch("https://dummyjson.com/todos/1", "patch body")
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.getOutputData().get("apiResponse"));
    }

    @Test
    @DisplayName("Test rest_call generic function is recognized and executed")
    void testRestCallFunction() {
        String yaml = """
                when:
                  - "true"
                then:
                  - calculate apiResponse as rest_call("GET", "https://dummyjson.com/todos/1")
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.getOutputData().get("apiResponse"));
    }

    @Test
    @DisplayName("Test REST and JSON integration")
    void testRestAndJsonIntegration() {
        String yaml = """
                when:
                  - "true"
                then:
                  - calculate apiResponse as rest_get("https://dummyjson.com/todos/1")
                  - calculate todoId as json_get(apiResponse, "id")
                  - calculate todoText as json_get(apiResponse, "todo")
                  - calculate isCompleted as json_get(apiResponse, "completed")
                """;

        Map<String, Object> inputData = new HashMap<>();
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.getOutputData().get("apiResponse"));
        assertNotNull(result.getOutputData().get("todoId"));
        assertNotNull(result.getOutputData().get("todoText"));
        assertNotNull(result.getOutputData().get("isCompleted"));
    }

    @Test
    @DisplayName("Test banking scenario with REST and JSON")
    void testBankingScenarioWithRestAndJson() {
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
        assertNotNull(result.getOutputData().get("creditResponse"));
        assertNotNull(result.getOutputData().get("creditScore"));
        assertNotNull(result.getOutputData().get("hasDelinquencies"));
        assertNotNull(result.getOutputData().get("approval"));
    }
}
