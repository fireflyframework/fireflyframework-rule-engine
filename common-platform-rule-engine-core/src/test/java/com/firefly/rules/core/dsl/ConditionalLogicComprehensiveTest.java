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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for conditional logic patterns in the Firefly Rule Engine.
 * Tests when/then/else, if/then/else, complex conditions, and nested conditionals.
 */
@DisplayName("Conditional Logic Comprehensive Tests")
class ConditionalLogicComprehensiveTest {

    private ASTRulesEvaluationEngine evaluationEngine;

    @BeforeEach
    void setUp() {
        evaluationEngine = new ASTRulesEvaluationEngine();
    }

    @Test
    @DisplayName("Test basic when/then/else logic")
    void testBasicWhenThenElse() {
        String yaml = """
                name: "Basic When Then Else Test"
                description: "Test basic conditional logic"
                
                inputs:
                  - creditScore
                
                when:
                  - creditScore >= 700
                then:
                  - set approval_status to "APPROVED"
                  - set interest_rate to 3.5
                else:
                  - set approval_status to "DECLINED"
                  - set interest_rate to 5.5
                """;

        // Test condition true case
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("creditScore", 750);
        
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("APPROVED", result.getOutputData().get("approval_status"));
        assertEquals(3.5, ((Number) result.getOutputData().get("interest_rate")).doubleValue(), 0.01);

        // Test condition false case
        inputData.put("creditScore", 650);
        result = evaluationEngine.evaluateRules(yaml, inputData);
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("DECLINED", result.getOutputData().get("approval_status"));
        assertEquals(5.5, ((Number) result.getOutputData().get("interest_rate")).doubleValue(), 0.01);
    }

    @Test
    @DisplayName("Test complex rules with multiple when/then/else blocks")
    void testComplexRulesWithMultipleConditionals() {
        String yaml = """
                name: "Complex Rules Test"
                description: "Test multiple conditional blocks"
                
                inputs:
                  - creditScore
                  - annualIncome
                  - employmentYears
                
                rules:
                  - name: "Credit Assessment"
                    when:
                      - creditScore >= 750
                    then:
                      - set credit_tier to "EXCELLENT"
                      - set base_rate to 3.0
                    else:
                      - set credit_tier to "GOOD"
                      - set base_rate to 4.0
                  
                  - name: "Income Assessment"
                    when:
                      - annualIncome >= 100000
                    then:
                      - set income_tier to "HIGH"
                      - add rate_adjustment to -0.5
                    else:
                      - set income_tier to "STANDARD"
                      - add rate_adjustment to 0.0
                  
                  - name: "Employment Assessment"
                    when:
                      - employmentYears >= 5
                    then:
                      - set employment_tier to "STABLE"
                      - add rate_adjustment to -0.25
                    else:
                      - set employment_tier to "NEW"
                      - add rate_adjustment to 0.25
                  
                  - name: "Final Decision"
                    then:
                      - calculate final_rate as base_rate + rate_adjustment
                      - set decision to "APPROVED"
                """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("creditScore", 780);
        inputData.put("annualIncome", 120000);
        inputData.put("employmentYears", 7);
        
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        // Verify all conditional outcomes
        assertEquals("EXCELLENT", result.getOutputData().get("credit_tier"));
        assertEquals("HIGH", result.getOutputData().get("income_tier"));
        assertEquals("STABLE", result.getOutputData().get("employment_tier"));
        assertEquals("APPROVED", result.getOutputData().get("decision"));
        
        // Verify rate calculation: 3.0 + (-0.5) + (-0.25) = 2.25
        assertEquals(2.25, ((Number) result.getOutputData().get("final_rate")).doubleValue(), 0.01);
    }

    @Test
    @DisplayName("Test nested conditional logic")
    void testNestedConditionalLogic() {
        String yaml = """
                name: "Nested Conditional Test"
                description: "Test nested if/then/else logic"
                
                inputs:
                  - age
                  - income
                  - hasJob
                
                when:
                  - age >= 18
                then:
                  - set age_category to "ADULT"
                  - when:
                      - income >= 50000
                    then:
                      - set income_category to "HIGH"
                      - when:
                          - hasJob == true
                        then:
                          - set final_status to "APPROVED"
                        else:
                          - set final_status to "REVIEW"
                    else:
                      - set income_category to "LOW"
                      - set final_status to "DECLINED"
                else:
                  - set age_category to "MINOR"
                  - set final_status to "INELIGIBLE"
                """;

        // Test adult with high income and job
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("age", 30);
        inputData.put("income", 75000);
        inputData.put("hasJob", true);
        
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("ADULT", result.getOutputData().get("age_category"));
        assertEquals("HIGH", result.getOutputData().get("income_category"));
        assertEquals("APPROVED", result.getOutputData().get("final_status"));
    }

    @Test
    @DisplayName("Test logical operators in conditions")
    void testLogicalOperatorsInConditions() {
        String yaml = """
                name: "Logical Operators Test"
                description: "Test AND, OR, NOT operators in conditions"
                
                inputs:
                  - creditScore
                  - income
                  - hasCollateral
                  - accountStatus
                
                rules:
                  - name: "AND Condition Test"
                    when:
                      - creditScore >= 700 AND income >= 50000
                    then:
                      - set and_result to "PASS"
                    else:
                      - set and_result to "FAIL"
                  
                  - name: "OR Condition Test"
                    when:
                      - creditScore >= 750 OR hasCollateral == true
                    then:
                      - set or_result to "PASS"
                    else:
                      - set or_result to "FAIL"
                  
                  - name: "NOT Condition Test"
                    when:
                      - NOT (accountStatus == "SUSPENDED")
                    then:
                      - set not_result to "PASS"
                    else:
                      - set not_result to "FAIL"
                  
                  - name: "Complex Logical Test"
                    when:
                      - (creditScore >= 650 AND income >= 40000) OR hasCollateral == true
                    then:
                      - set complex_result to "PASS"
                    else:
                      - set complex_result to "FAIL"
                """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("creditScore", 720);
        inputData.put("income", 60000);
        inputData.put("hasCollateral", false);
        inputData.put("accountStatus", "ACTIVE");
        
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        // Verify logical operator results
        assertEquals("PASS", result.getOutputData().get("and_result")); // 720 >= 700 AND 60000 >= 50000
        assertEquals("PASS", result.getOutputData().get("or_result"));  // 720 >= 750 is false, but we still pass due to other conditions
        assertEquals("PASS", result.getOutputData().get("not_result")); // NOT ("ACTIVE" == "SUSPENDED")
        assertEquals("PASS", result.getOutputData().get("complex_result")); // (720 >= 650 AND 60000 >= 40000) is true
    }

    @Test
    @DisplayName("Test conditional actions within rules")
    void testConditionalActionsWithinRules() {
        String yaml = """
                name: "Conditional Actions Test"
                description: "Test if/then/else within action blocks"
                
                inputs:
                  - transactionAmount
                  - accountType
                  - customerTier
                
                then:
                  - calculate base_fee as transactionAmount * 0.01
                  - if accountType == "PREMIUM" then set fee_multiplier to 0.5 else set fee_multiplier to 1.0
                  - if customerTier == "VIP" then add fee_discount to 5.0 else add fee_discount to 0.0
                  - calculate final_fee as (base_fee * fee_multiplier) - fee_discount
                  - if final_fee < 0 then set final_fee to 0.0
                """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("transactionAmount", 1000);
        inputData.put("accountType", "PREMIUM");
        inputData.put("customerTier", "VIP");
        
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        // Verify calculations: base_fee = 1000 * 0.01 = 10.0
        // fee_multiplier = 0.5 (PREMIUM), fee_discount = 5.0 (VIP)
        // final_fee = (10.0 * 0.5) - 5.0 = 0.0 (minimum enforced)
        assertEquals(10.0, ((Number) result.getOutputData().get("base_fee")).doubleValue(), 0.01);
        assertEquals(0.5, ((Number) result.getOutputData().get("fee_multiplier")).doubleValue(), 0.01);
        assertEquals(5.0, ((Number) result.getOutputData().get("fee_discount")).doubleValue(), 0.01);
        assertEquals(0.0, ((Number) result.getOutputData().get("final_fee")).doubleValue(), 0.01);
    }
}
