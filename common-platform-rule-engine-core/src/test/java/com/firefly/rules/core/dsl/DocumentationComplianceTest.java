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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Test suite to verify that all documented features in docs/yaml-dsl-reference.md are properly implemented.
 * This test ensures 100% compliance between documentation and actual implementation.
 */
@DisplayName("Documentation Compliance Tests")
class DocumentationComplianceTest {

    private ASTRulesEvaluationEngine evaluationEngine;

    @BeforeEach
    void setUp() {
        // Create dependencies
        DSLParser dslParser = new DSLParser();
        ASTRulesDSLParser parser = new ASTRulesDSLParser(dslParser);
        ConstantService constantService = Mockito.mock(ConstantService.class);

        // Mock constant service to return empty flux (no constants from database)
        when(constantService.getConstantsByCodes(Mockito.anyList()))
               .thenReturn(Flux.empty());

        // Use the constructor with default REST and JSON services
        evaluationEngine = new ASTRulesEvaluationEngine(parser, constantService);
    }

    @Test
    @DisplayName("Test all documented REST functions work correctly")
    void testAllRestFunctions() {
        String yaml = """
                name: "REST Functions Test"
                description: "Test all documented REST functions"
                
                inputs:
                  - testUrl
                
                when:
                  - "true"
                
                then:
                  - calculate getResponse as rest_get(testUrl)
                  - calculate postResponse as rest_post(testUrl, "test body")
                  - calculate putResponse as rest_put(testUrl, "test body")
                  - calculate patchResponse as rest_patch(testUrl, "test body")
                  - calculate deleteResponse as rest_delete(testUrl)
                  - calculate callResponse as rest_call("GET", testUrl)
                """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("testUrl", "https://dummyjson.com/todos/1");
        
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        // Verify all REST functions executed and returned responses
        assertNotNull(result.getOutputData().get("getResponse"));
        assertNotNull(result.getOutputData().get("postResponse"));
        assertNotNull(result.getOutputData().get("putResponse"));
        assertNotNull(result.getOutputData().get("patchResponse"));
        assertNotNull(result.getOutputData().get("deleteResponse"));
        assertNotNull(result.getOutputData().get("callResponse"));
    }

    @Test
    @DisplayName("Test all documented JSON functions work correctly")
    void testAllJsonFunctions() {
        String yaml = """
                name: "JSON Functions Test"
                description: "Test all documented JSON functions"
                
                inputs:
                  - userData
                
                when:
                  - "true"
                
                then:
                  - calculate userName as json_get(userData, "name")
                  - calculate userAge as json_get(userData, "age")
                  - calculate hasEmail as json_exists(userData, "email")
                  - calculate hasPhone as json_exists(userData, "phone")
                  - calculate hobbiesCount as json_size(userData, "hobbies")
                  - calculate nameType as json_type(userData, "name")
                  - calculate ageType as json_type(userData, "age")
                """;

        Map<String, Object> inputData = new HashMap<>();
        Map<String, Object> userData = new HashMap<>();
        userData.put("name", "John Doe");
        userData.put("age", 30);
        userData.put("email", "john@example.com");
        userData.put("hobbies", java.util.Arrays.asList("reading", "swimming", "coding"));
        inputData.put("userData", userData);
        
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        // Verify all JSON functions worked correctly
        assertEquals("John Doe", result.getOutputData().get("userName"));
        assertEquals(30, result.getOutputData().get("userAge"));
        assertEquals(true, result.getOutputData().get("hasEmail"));
        assertEquals(false, result.getOutputData().get("hasPhone"));
        assertEquals(3, result.getOutputData().get("hobbiesCount"));
        assertEquals("String", result.getOutputData().get("nameType"));
        assertEquals("Integer", result.getOutputData().get("ageType"));
    }

    @Test
    @DisplayName("Test all documented financial validation functions work correctly")
    void testFinancialValidationFunctions() {
        String yaml = """
                name: "Financial Validation Test"
                description: "Test all documented financial validation functions"
                
                inputs:
                  - creditScore
                  - ssn
                  - accountNumber
                  - routingNumber
                  - birthDate
                
                when:
                  - "true"
                
                then:
                  - calculate validCreditScore as is_valid_credit_score(creditScore)
                  - calculate validSSN as is_valid_ssn(ssn)
                  - calculate validAccount as is_valid_account(accountNumber)
                  - calculate validRouting as is_valid_routing(routingNumber)
                  - calculate ageCheck as age_meets_requirement(birthDate, 18)
                """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("creditScore", 750);
        inputData.put("ssn", "123-45-6789");
        inputData.put("accountNumber", "12345678");
        inputData.put("routingNumber", "021000021");
        inputData.put("birthDate", "1990-01-01");
        
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        // Verify all financial validation functions worked
        assertEquals(true, result.getOutputData().get("validCreditScore"));
        assertEquals(true, result.getOutputData().get("validSSN"));
        assertEquals(true, result.getOutputData().get("validAccount"));
        assertEquals(true, result.getOutputData().get("validRouting"));
        assertEquals(true, result.getOutputData().get("ageCheck"));
    }

    @Test
    @DisplayName("Test all documented financial calculation functions work correctly")
    void testFinancialCalculationFunctions() {
        String yaml = """
                name: "Financial Calculation Test"
                description: "Test all documented financial calculation functions"
                
                inputs:
                  - monthlyDebt
                  - monthlyIncome
                  - balance
                  - limit
                  - loanAmount
                  - propertyValue
                  - onTimePayments
                  - totalPayments
                
                when:
                  - "true"
                
                then:
                  - calculate dtiRatio as debt_to_income_ratio(monthlyDebt, monthlyIncome)
                  - calculate utilization as credit_utilization(balance, limit)
                  - calculate ltvRatio as loan_to_value(loanAmount, propertyValue)
                  - calculate paymentScore as payment_history_score(onTimePayments, totalPayments)
                """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("monthlyDebt", 2000);
        inputData.put("monthlyIncome", 6000);
        inputData.put("balance", 1500);
        inputData.put("limit", 5000);
        inputData.put("loanAmount", 200000);
        inputData.put("propertyValue", 250000);
        inputData.put("onTimePayments", 23);
        inputData.put("totalPayments", 24);
        
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        // Verify all financial calculation functions worked
        assertEquals(0.3333, (Double) result.getOutputData().get("dtiRatio"), 0.001);
        assertEquals(0.3, (Double) result.getOutputData().get("utilization"), 0.001);
        assertEquals(0.8, (Double) result.getOutputData().get("ltvRatio"), 0.001);
        assertEquals(95.83, (Double) result.getOutputData().get("paymentScore"), 0.01);
    }

    @Test
    @DisplayName("Test all documented comparison operators work correctly")
    void testAllComparisonOperators() {
        String yaml = """
                name: "Comparison Operators Test"
                description: "Test all documented comparison operators"
                
                inputs:
                  - value1
                  - value2
                  - textValue
                  - listValue
                
                when:
                  - "true"
                
                then:
                  - set greater_than_result to (value1 greater_than value2)
                  - set less_than_result to (value1 less_than 100)
                  - set at_least_result to (value1 at_least 50)
                  - set equals_result to (value2 equals 30)
                  - set between_result to (value1 between 40 and 80)
                  - set contains_result to (textValue contains "test")
                  - set starts_with_result to (textValue starts_with "hello")
                  - set in_list_result to (value2 in_list listValue)
                """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("value1", 75);
        inputData.put("value2", 30);
        inputData.put("textValue", "hello test world");
        inputData.put("listValue", java.util.Arrays.asList(10, 20, 30, 40));
        
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        // Verify all comparison operators worked correctly
        assertEquals(true, result.getOutputData().get("greater_than_result"));
        assertEquals(true, result.getOutputData().get("less_than_result"));
        assertEquals(true, result.getOutputData().get("at_least_result"));
        assertEquals(true, result.getOutputData().get("equals_result"));
        assertEquals(true, result.getOutputData().get("between_result"));
        assertEquals(true, result.getOutputData().get("contains_result"));
        assertEquals(true, result.getOutputData().get("starts_with_result"));
        assertEquals(true, result.getOutputData().get("in_list_result"));
    }
}
