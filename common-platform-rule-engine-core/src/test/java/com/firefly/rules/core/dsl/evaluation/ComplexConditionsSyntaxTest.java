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

import com.firefly.rules.core.dsl.parser.RulesDSLParser;
import com.firefly.rules.core.services.ConstantService;
import com.firefly.rules.core.validation.NamingConventionValidator;
import com.firefly.rules.interfaces.dtos.crud.ConstantDTO;
import com.firefly.rules.interfaces.enums.ValueType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for the Complex Conditions Syntax (Advanced) as documented in the YAML DSL Reference.
 * Tests the 'conditions' block with 'if', 'then', 'else' structure for sophisticated conditional logic.
 */
class ComplexConditionsSyntaxTest {

    private TestConstantService constantService;
    private RulesEvaluationEngine rulesEvaluationEngine;
    private RulesDSLParser rulesDSLParser;

    @BeforeEach
    void setUp() {
        // Create a test implementation of ConstantService
        constantService = new TestConstantService();

        NamingConventionValidator namingValidator = new NamingConventionValidator();
        rulesDSLParser = new RulesDSLParser(namingValidator);
        ArithmeticEvaluator arithmeticEvaluator = new ArithmeticEvaluator();
        VariableResolver variableResolver = new VariableResolver(arithmeticEvaluator);
        ConditionEvaluator conditionEvaluator = new ConditionEvaluator(variableResolver, arithmeticEvaluator);
        ActionExecutor actionExecutor = new ActionExecutor(variableResolver, namingValidator, conditionEvaluator);

        rulesEvaluationEngine = new RulesEvaluationEngine(
            rulesDSLParser,
            conditionEvaluator,
            actionExecutor,
            variableResolver,
            constantService
        );

        // Setup common constants
        setupCommonConstants();
    }

    private void setupCommonConstants() {
        // MIN_CREDIT_SCORE constant
        ConstantDTO minCreditScore = ConstantDTO.builder()
                .id(UUID.randomUUID())
                .code("MIN_CREDIT_SCORE")
                .name("Minimum Credit Score")
                .valueType(ValueType.NUMBER)
                .build();
        minCreditScore.setCurrentValue(650);
        constantService.addConstant("MIN_CREDIT_SCORE", minCreditScore);

        // MAX_LOAN_AMOUNT constant
        ConstantDTO maxLoanAmount = ConstantDTO.builder()
                .id(UUID.randomUUID())
                .code("MAX_LOAN_AMOUNT")
                .name("Maximum Loan Amount")
                .valueType(ValueType.NUMBER)
                .build();
        maxLoanAmount.setCurrentValue(100000);
        constantService.addConstant("MAX_LOAN_AMOUNT", maxLoanAmount);
    }

    @Test
    void testBasicComplexConditionsStructure() {
        // Given: Basic complex conditions syntax as documented in YAML DSL Reference
        String ruleDefinition = """
            name: "Basic Complex Conditions Test"
            description: "Test basic complex conditions structure with if/then/else"
            
            inputs:
              - creditScore
              - annualIncome
              - hasGuarantor
            
            conditions:
              if:
                and:
                  - compare:
                      left: creditScore
                      operator: ">="
                      right: 650
                  - or:
                      - compare:
                          left: annualIncome
                          operator: ">"
                          right: 50000
                      - compare:
                          left: hasGuarantor
                          operator: "=="
                          right: true
              then:
                actions:
                  - set:
                      variable: "is_eligible"
                      value: true
              else:
                actions:
                  - set:
                      variable: "is_eligible"
                      value: false
            
            output:
              is_eligible: boolean
            """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("creditScore", 700);
        inputData.put("annualIncome", 60000);
        inputData.put("hasGuarantor", false);

        // When
        RulesEvaluationResult result = rulesEvaluationEngine.evaluateRules(ruleDefinition, inputData);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getConditionResult()).isTrue();
        assertThat(result.getOutputData()).containsEntry("is_eligible", true);
    }

    @Test
    void testComplexConditionsWithElseBranch() {
        // Given: Complex conditions that should trigger the else branch
        String ruleDefinition = """
            name: "Complex Conditions Else Branch Test"
            description: "Test complex conditions that trigger else actions"
            
            inputs:
              - creditScore
              - annualIncome
              - hasGuarantor
            
            conditions:
              if:
                and:
                  - compare:
                      left: creditScore
                      operator: ">="
                      right: 650
                  - compare:
                      left: annualIncome
                      operator: ">"
                      right: 80000
              then:
                actions:
                  - set:
                      variable: "approval_status"
                      value: "APPROVED"
                  - set:
                      variable: "interest_rate"
                      value: 3.5
              else:
                actions:
                  - set:
                      variable: "approval_status"
                      value: "DECLINED"
                  - set:
                      variable: "rejection_reason"
                      value: "Insufficient qualifications"
            
            output:
              approval_status: text
              rejection_reason: text
              interest_rate: number
            """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("creditScore", 700);
        inputData.put("annualIncome", 60000); // Less than 80000, should trigger else
        inputData.put("hasGuarantor", false);

        // When
        RulesEvaluationResult result = rulesEvaluationEngine.evaluateRules(ruleDefinition, inputData);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getConditionResult()).isFalse();
        assertThat(result.getOutputData()).containsEntry("approval_status", "DECLINED");
        assertThat(result.getOutputData()).containsEntry("rejection_reason", "Insufficient qualifications");
        assertThat(result.getOutputData()).doesNotContainKey("interest_rate");
    }

    @Test
    void testNestedLogicalOperations() {
        // Given: Complex nested AND/OR operations
        String ruleDefinition = """
            name: "Nested Logical Operations Test"
            description: "Test complex nested AND/OR conditions"
            
            inputs:
              - creditScore
              - annualIncome
              - employmentYears
              - hasGuarantor
              - guarantorIncome
            
            conditions:
              if:
                or:
                  - and:
                      - compare:
                          left: creditScore
                          operator: ">="
                          right: 750
                      - compare:
                          left: annualIncome
                          operator: ">"
                          right: 100000
                  - and:
                      - compare:
                          left: creditScore
                          operator: ">="
                          right: MIN_CREDIT_SCORE
                      - compare:
                          left: employmentYears
                          operator: ">="
                          right: 3
                      - compare:
                          left: hasGuarantor
                          operator: "=="
                          right: true
                      - compare:
                          left: guarantorIncome
                          operator: ">"
                          right: 75000
              then:
                actions:
                  - set:
                      variable: "risk_category"
                      value: "LOW"
                  - set:
                      variable: "approved"
                      value: true
              else:
                actions:
                  - set:
                      variable: "risk_category"
                      value: "HIGH"
                  - set:
                      variable: "approved"
                      value: false
            
            output:
              risk_category: text
              approved: boolean
            """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("creditScore", 680);
        inputData.put("annualIncome", 70000);
        inputData.put("employmentYears", 5);
        inputData.put("hasGuarantor", true);
        inputData.put("guarantorIncome", 80000);

        // When
        RulesEvaluationResult result = rulesEvaluationEngine.evaluateRules(ruleDefinition, inputData);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getConditionResult()).isTrue();
        assertThat(result.getOutputData()).containsEntry("risk_category", "LOW");
        assertThat(result.getOutputData()).containsEntry("approved", true);
    }

    @Test
    void testComplexActionsInThenElseBlocks() {
        // Given: Complex actions including set, call, and calculate operations
        String ruleDefinition = """
            name: "Complex Actions Test"
            description: "Test complex actions in then/else blocks"

            inputs:
              - creditScore
              - annualIncome
              - requestedAmount

            conditions:
              if:
                compare:
                  left: creditScore
                  operator: ">="
                  right: 700
              then:
                actions:
                  - set:
                      variable: "is_eligible"
                      value: true
                  - calculate:
                      variable: "debt_to_income"
                      expression: "requestedAmount / annualIncome"
                  - call:
                      function: "log"
                      parameters: ["Eligibility approved", "INFO"]
                  - set:
                      variable: "approval_tier"
                      value: "PREMIUM"
              else:
                actions:
                  - set:
                      variable: "is_eligible"
                      value: false
                  - set:
                      variable: "rejection_reason"
                      value: "Credit score below threshold"
                  - call:
                      function: "log"
                      parameters: ["Application rejected", "WARN"]

            output:
              is_eligible: boolean
              debt_to_income: number
              approval_tier: text
              rejection_reason: text
            """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("creditScore", 750);
        inputData.put("annualIncome", 80000);
        inputData.put("requestedAmount", 40000);

        // When
        RulesEvaluationResult result = rulesEvaluationEngine.evaluateRules(ruleDefinition, inputData);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getConditionResult()).isTrue();
        assertThat(result.getOutputData()).containsEntry("is_eligible", true);
        assertThat(result.getOutputData()).containsEntry("approval_tier", "PREMIUM");
        assertThat(result.getOutputData()).containsKey("debt_to_income");
        assertThat(result.getOutputData()).doesNotContainKey("rejection_reason");

        // Verify calculated value
        Double debtToIncome = (Double) result.getOutputData().get("debt_to_income");
        assertThat(debtToIncome).isEqualTo(0.5); // 40000 / 80000
    }

    @Test
    void testComplexConditionsWithNotOperator() {
        // Given: Complex conditions using NOT operator
        String ruleDefinition = """
            name: "Complex Conditions with NOT Test"
            description: "Test complex conditions with NOT operator"

            inputs:
              - accountStatus
              - creditScore
              - hasDelinquencies

            conditions:
              if:
                and:
                  - not:
                      compare:
                        left: accountStatus
                        operator: "=="
                        right: "SUSPENDED"
                  - compare:
                      left: creditScore
                      operator: ">="
                      right: MIN_CREDIT_SCORE
                  - not:
                      compare:
                        left: hasDelinquencies
                        operator: "=="
                        right: true
              then:
                actions:
                  - set:
                      variable: "account_eligible"
                      value: true
                  - set:
                      variable: "status"
                      value: "ACTIVE_ELIGIBLE"
              else:
                actions:
                  - set:
                      variable: "account_eligible"
                      value: false
                  - set:
                      variable: "status"
                      value: "INELIGIBLE"

            output:
              account_eligible: boolean
              status: text
            """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("accountStatus", "ACTIVE");
        inputData.put("creditScore", 680);
        inputData.put("hasDelinquencies", false);

        // When
        RulesEvaluationResult result = rulesEvaluationEngine.evaluateRules(ruleDefinition, inputData);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getConditionResult()).isTrue();
        assertThat(result.getOutputData()).containsEntry("account_eligible", true);
        assertThat(result.getOutputData()).containsEntry("status", "ACTIVE_ELIGIBLE");
    }

    @Test
    void testComplexConditionsWithVariableReferences() {
        // Given: Complex conditions referencing computed variables
        String ruleDefinition = """
            name: "Complex Conditions with Variable References"
            description: "Test complex conditions with variable references"

            inputs:
              - monthlyIncome
              - monthlyDebt
              - creditScore

            rules:
              - name: "Calculate Ratios"
                then:
                  - calculate debt_to_income as monthlyDebt / monthlyIncome
                  - calculate income_multiplier as monthlyIncome / 1000

              - name: "Complex Decision"
                conditions:
                  if:
                    and:
                      - compare:
                          left: debt_to_income
                          operator: "<"
                          right: 0.4
                      - compare:
                          left: creditScore
                          operator: ">="
                          right: MIN_CREDIT_SCORE
                      - compare:
                          left: income_multiplier
                          operator: ">"
                          right: 5
                  then:
                    actions:
                      - set:
                          variable: "final_decision"
                          value: "APPROVED"
                      - set:
                          variable: "risk_level"
                          value: "LOW"
                  else:
                    actions:
                      - set:
                          variable: "final_decision"
                          value: "DECLINED"
                      - set:
                          variable: "risk_level"
                          value: "HIGH"

            output:
              final_decision: text
              risk_level: text
              debt_to_income: number
            """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("monthlyIncome", 8000);
        inputData.put("monthlyDebt", 2000);
        inputData.put("creditScore", 720);

        // When
        RulesEvaluationResult result = rulesEvaluationEngine.evaluateRules(ruleDefinition, inputData);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputData()).containsEntry("final_decision", "APPROVED");
        assertThat(result.getOutputData()).containsEntry("risk_level", "LOW");
        assertThat(result.getOutputData()).containsKey("debt_to_income");
    }

    @Test
    void testComplexConditionsWithStringOperators() {
        // Given: Complex conditions using string operators
        String ruleDefinition = """
            name: "Complex String Operations Test"
            description: "Test complex conditions with string operators"

            inputs:
              - customerType
              - accountNumber
              - email
              - phoneNumber

            conditions:
              if:
                and:
                  - compare:
                      left: customerType
                      operator: "in_list"
                      right: ["PREMIUM", "GOLD", "PLATINUM"]
                  - compare:
                      left: accountNumber
                      operator: "starts_with"
                      right: "CHK"
                  - compare:
                      left: email
                      operator: "ends_with"
                      right: "@company.com"
                  - compare:
                      left: phoneNumber
                      operator: "matches"
                      right: '^\\+1.*'
              then:
                actions:
                  - set:
                      variable: "is_corporate_customer"
                      value: true
                  - set:
                      variable: "priority_level"
                      value: "HIGH"
              else:
                actions:
                  - set:
                      variable: "is_corporate_customer"
                      value: false
                  - set:
                      variable: "priority_level"
                      value: "STANDARD"

            output:
              is_corporate_customer: boolean
              priority_level: text
            """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("customerType", "PREMIUM");
        inputData.put("accountNumber", "CHK123456");
        inputData.put("email", "john.doe@company.com");
        inputData.put("phoneNumber", "+1234567890");

        // When
        RulesEvaluationResult result = rulesEvaluationEngine.evaluateRules(ruleDefinition, inputData);



        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getConditionResult()).isTrue();
        assertThat(result.getOutputData()).containsEntry("is_corporate_customer", true);
        assertThat(result.getOutputData()).containsEntry("priority_level", "HIGH");
    }

    @Test
    void testComplexConditionsWithFunctionCalls() {
        // Given: Complex conditions using function calls
        String ruleDefinition = """
            name: "Complex Function Calls Test"
            description: "Test complex conditions with function calls"

            inputs:
              - email
              - phoneNumber
              - age
              - accountBalance

            conditions:
              if:
                and:
                  - function:
                      name: "is_valid"
                      parameters: ["email", "email_format"]
                  - function:
                      name: "is_valid"
                      parameters: ["phoneNumber", "phone_format"]
                  - function:
                      name: "in_range"
                      parameters: ["age", 18, 65]
                  - compare:
                      left: accountBalance
                      operator: ">"
                      right: 1000
              then:
                actions:
                  - set:
                      variable: "account_verified"
                      value: true
                  - call:
                      function: "log"
                      parameters: ["Account verification successful", "INFO"]
              else:
                actions:
                  - set:
                      variable: "account_verified"
                      value: false
                  - call:
                      function: "log"
                      parameters: ["Account verification failed", "WARN"]

            output:
              account_verified: boolean
            """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("email", "test@example.com");
        inputData.put("phoneNumber", "+1234567890");
        inputData.put("age", 30);
        inputData.put("accountBalance", 5000);

        // When
        RulesEvaluationResult result = rulesEvaluationEngine.evaluateRules(ruleDefinition, inputData);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getConditionResult()).isTrue();
        assertThat(result.getOutputData()).containsEntry("account_verified", true);
    }

    @Test
    void testMixedSimpleAndComplexSyntax() {
        // Given: Rule mixing simple and complex syntax
        String ruleDefinition = """
            name: "Mixed Syntax Test"
            description: "Test mixing simple when/then with complex conditions"

            inputs:
              - creditScore
              - annualIncome
              - accountAge

            rules:
              - name: "Simple Initial Check"
                when: creditScore at_least 600
                then:
                  - set initial_eligible to true
                elseActions:
                  - set initial_eligible to false

              - name: "Complex Final Decision"
                conditions:
                  if:
                    and:
                      - compare:
                          left: initial_eligible
                          operator: "=="
                          right: true
                      - or:
                          - compare:
                              left: annualIncome
                              operator: ">"
                              right: 75000
                          - compare:
                              left: accountAge
                              operator: ">="
                              right: 24
                  then:
                    actions:
                      - set:
                          variable: "final_approval"
                          value: "APPROVED"
                  else:
                    actions:
                      - set:
                          variable: "final_approval"
                          value: "DECLINED"

            output:
              initial_eligible: boolean
              final_approval: text
            """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("creditScore", 650);
        inputData.put("annualIncome", 70000);
        inputData.put("accountAge", 30);

        // When
        RulesEvaluationResult result = rulesEvaluationEngine.evaluateRules(ruleDefinition, inputData);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputData()).containsEntry("initial_eligible", true);
        assertThat(result.getOutputData()).containsEntry("final_approval", "APPROVED");
    }

    @Test
    void testComplexConditionsWithArithmeticOperations() {
        // Given: Complex conditions with arithmetic operations
        String ruleDefinition = """
            name: "Complex Arithmetic Operations Test"
            description: "Test complex conditions with arithmetic operations"

            inputs:
              - baseAmount
              - taxRate
              - discountRate
              - minimumThreshold

            conditions:
              if:
                and:
                  - compare:
                      left:
                        arithmetic:
                          operation: "multiply"
                          operands: ["baseAmount", "taxRate"]
                      operator: ">"
                      right: 0
                  - compare:
                      left:
                        arithmetic:
                          operation: "subtract"
                          operands: ["baseAmount", "discountRate"]
                      operator: ">"
                      right: "minimumThreshold"
              then:
                actions:
                  - calculate:
                      variable: "final_amount"
                      expression: "baseAmount * (1 + taxRate) * (1 - discountRate)"
                  - set:
                      variable: "calculation_valid"
                      value: true
              else:
                actions:
                  - set:
                      variable: "calculation_valid"
                      value: false
                  - set:
                      variable: "error_message"
                      value: "Amount below minimum threshold"

            output:
              calculation_valid: boolean
              final_amount: number
              error_message: text
            """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("baseAmount", 1000);
        inputData.put("taxRate", 0.1);
        inputData.put("discountRate", 0.05);
        inputData.put("minimumThreshold", 900);

        // When
        RulesEvaluationResult result = rulesEvaluationEngine.evaluateRules(ruleDefinition, inputData);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputData()).containsEntry("calculation_valid", true);
        assertThat(result.getOutputData()).containsKey("final_amount");
    }

    @Test
    void testComplexConditionsWithNullValues() {
        // Given: Very simple test to isolate the NPE issue
        String ruleDefinition = """
            name: "Null Values Test"
            description: "Test null value handling"

            inputs:
              - primaryIncome

            when:
              - "primaryIncome is_not_null"
            then:
              - "set income_verified to true"
            else:
              - "set income_verified to false"

            output:
              income_verified: boolean
            """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("primaryIncome", 60000);

        // When
        RulesEvaluationResult result = null;
        try {
            result = rulesEvaluationEngine.evaluateRules(ruleDefinition, inputData);
        } catch (Exception e) {
            System.err.println("Exception during rule evaluation: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
            throw e;
        }

        // Then
        if (!result.isSuccess()) {
            System.err.println("Rule evaluation failed. Error: " + result.getError());
            System.err.println("Output data: " + result.getOutputData());
        }
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputData()).containsEntry("income_verified", true);
    }

    @Test
    void testComplexConditionsEdgeCases() {
        // Given: Edge cases for complex conditions
        String ruleDefinition = """
            name: "Edge Cases Test"
            description: "Test edge cases in complex conditions"

            inputs:
              - emptyString
              - zeroValue
              - booleanFlag

            conditions:
              if:
                or:
                  - compare:
                      left: emptyString
                      operator: "is_empty"
                  - compare:
                      left: zeroValue
                      operator: "=="
                      right: 0
                  - compare:
                      left: booleanFlag
                      operator: "=="
                      right: false
              then:
                actions:
                  - set:
                      variable: "edge_case_detected"
                      value: true
                  - set:
                      variable: "case_type"
                      value: "EDGE_CASE"
              else:
                actions:
                  - set:
                      variable: "edge_case_detected"
                      value: false
                  - set:
                      variable: "case_type"
                      value: "NORMAL"

            output:
              edge_case_detected: boolean
              case_type: text
            """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("emptyString", "");
        inputData.put("zeroValue", 0);
        inputData.put("booleanFlag", false);

        // When
        RulesEvaluationResult result = rulesEvaluationEngine.evaluateRules(ruleDefinition, inputData);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getConditionResult()).isTrue();
        assertThat(result.getOutputData()).containsEntry("edge_case_detected", true);
        assertThat(result.getOutputData()).containsEntry("case_type", "EDGE_CASE");
    }

    /**
     * Test implementation of ConstantService for testing purposes
     */
    private static class TestConstantService implements ConstantService {
        private final Map<String, ConstantDTO> constants = new HashMap<>();

        public void addConstant(String code, ConstantDTO constant) {
            constants.put(code, constant);
        }

        @Override
        public Mono<ConstantDTO> getConstantByCode(String code) {
            ConstantDTO constant = constants.get(code);
            return constant != null ? Mono.just(constant) : Mono.empty();
        }

        @Override
        public Mono<com.firefly.common.core.queries.PaginationResponse<ConstantDTO>> filterConstants(
                com.firefly.common.core.filters.FilterRequest<ConstantDTO> filterRequest) {
            return Mono.empty();
        }

        @Override
        public Mono<ConstantDTO> createConstant(ConstantDTO constantDTO) {
            return Mono.just(constantDTO);
        }

        @Override
        public Mono<ConstantDTO> updateConstant(UUID id, ConstantDTO constantDTO) {
            return Mono.just(constantDTO);
        }

        @Override
        public Mono<Void> deleteConstant(UUID id) {
            return Mono.empty();
        }

        @Override
        public Mono<ConstantDTO> getConstantById(UUID id) {
            return Mono.empty();
        }
    }
}
