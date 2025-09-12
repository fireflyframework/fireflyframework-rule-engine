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

import com.firefly.common.core.queries.PaginationResponse;
import com.firefly.rules.core.dsl.parser.RulesDSLParser;
import com.firefly.rules.core.services.ConstantService;
import com.firefly.rules.core.validation.NamingConventionValidator;
import com.firefly.rules.interfaces.dtos.crud.ConstantDTO;
import com.firefly.rules.interfaces.enums.ValueType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests to verify that all example rules can be parsed and executed correctly
 */
class ExampleRulesIntegrationTest {

    private RulesEvaluationEngine rulesEvaluationEngine;
    private RulesDSLParser rulesDSLParser;

    @BeforeEach
    void setUp() {
        // Create a test implementation of ConstantService
        ConstantService constantService = new TestConstantService();

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
    }

    @Test
    void testCreditScoringBasicEligibilityRule() {
        // Given
        String ruleDefinition = """
            name: "Basic Credit Eligibility"
            description: "Check minimum requirements for credit approval"
            
            inputs:
              - creditScore
              - annualIncome
              - employmentYears

            when:
              - creditScore at_least MIN_CREDIT_SCORE
              - annualIncome at_least MIN_ANNUAL_INCOME
              - employmentYears at_least 1
            
            then:
              - set eligible to true
              - set base_score to 60
              - set eligibility_reason to "Meets minimum requirements"
            
            else:
              - set eligible to false
              - set base_score to 0
              - set eligibility_reason to "Does not meet minimum requirements"
            
            output:
              eligible: boolean
              base_score: number
              eligibility_reason: text
            """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("creditScore", 700);
        inputData.put("annualIncome", 50000);
        inputData.put("employmentYears", 2);

        // When
        RulesEvaluationResult result = rulesEvaluationEngine.evaluateRules(ruleDefinition, inputData);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getConditionResult()).isTrue();
        assertThat(result.getOutputData()).containsEntry("eligible", true);
        assertThat(result.getOutputData()).containsEntry("base_score", 60);
        assertThat(result.getOutputData()).containsEntry("eligibility_reason", "Meets minimum requirements");
    }

    @Test
    void testCreditScoringBasicEligibilityRuleFailure() {
        // Given - same rule as above
        String ruleDefinition = """
            name: "Basic Credit Eligibility"
            description: "Check minimum requirements for credit approval"
            
            inputs:
              - creditScore
              - annualIncome
              - employmentYears

            when:
              - creditScore at_least MIN_CREDIT_SCORE
              - annualIncome at_least MIN_ANNUAL_INCOME
              - employmentYears at_least 1
            
            then:
              - set eligible to true
              - set base_score to 60
              - set eligibility_reason to "Meets minimum requirements"
            
            else:
              - set eligible to false
              - set base_score to 0
              - set eligibility_reason to "Does not meet minimum requirements"
            
            output:
              eligible: boolean
              base_score: number
              eligibility_reason: text
            """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("creditScore", 600); // Below threshold
        inputData.put("annualIncome", 50000);
        inputData.put("employmentYears", 2);

        // When
        RulesEvaluationResult result = rulesEvaluationEngine.evaluateRules(ruleDefinition, inputData);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getConditionResult()).isFalse();
        assertThat(result.getOutputData()).containsEntry("eligible", false);
        assertThat(result.getOutputData()).containsEntry("base_score", 0);
        assertThat(result.getOutputData()).containsEntry("eligibility_reason", "Does not meet minimum requirements");
    }

    @Test
    void testIncomeAssessmentRule() {
        // Given
        String ruleDefinition = """
            name: "Income to Loan Ratio Assessment"
            description: "Evaluate loan amount relative to income"

            inputs:
              - annualIncome
              - requestedLoanAmount
              - existingDebt

            rules:
              - name: "Calculate Ratios"
                then:
                  - set income_score to 0
                  - calculate loan_to_income as requestedLoanAmount / annualIncome
                  - calculate total_debt as existingDebt + requestedLoanAmount
                  - calculate debt_to_income as total_debt / annualIncome

              - name: "Assess Risk"
                when: loan_to_income less_than 0.3
                then:
                  - set income_risk to "LOW"
                  - add 25 to income_score

              - name: "Medium Risk"
                when: loan_to_income between 0.3 and 0.5
                then:
                  - set income_risk to "MEDIUM"
                  - add 15 to income_score

              - name: "High Risk"
                when: loan_to_income greater_than 0.5
                then:
                  - set income_risk to "HIGH"
                  - add 5 to income_score

            output:
              income_risk: text
              income_score: number
              loan_to_income_ratio: loan_to_income
              debt_to_income_ratio: debt_to_income
            """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("annualIncome", 100000);
        inputData.put("requestedLoanAmount", 25000); // 0.25 ratio - LOW risk
        inputData.put("existingDebt", 10000);

        // When
        RulesEvaluationResult result = rulesEvaluationEngine.evaluateRules(ruleDefinition, inputData);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputData()).containsEntry("income_risk", "LOW");
        assertThat(result.getOutputData()).containsKey("income_score");
        assertThat(result.getOutputData()).containsKey("loan_to_income");
        assertThat(result.getOutputData()).containsKey("debt_to_income");
    }

    @Test
    void testPaymentHistoryAnalysisRule() {
        // Given
        String ruleDefinition = """
            name: "Payment History Scoring"
            description: "Score based on payment history patterns"

            inputs:
              - paymentHistory
              - latePayments12m
              - defaultsCount

            rules:
              - name: "Base History Score"
                then:
                  - set history_score to 0

              - name: "Excellent History"
                when:
                  - latePayments12m equals 0
                  - defaultsCount equals 0
                then:
                  - add 30 to history_score
                  - set payment_grade to "EXCELLENT"

              - name: "Good History"
                when:
                  - latePayments12m between 1 and 2
                  - defaultsCount equals 0
                then:
                  - add 20 to history_score
                  - set payment_grade to "GOOD"

            output:
              payment_grade: text
              history_score: number
            """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("paymentHistory", "GOOD");
        inputData.put("latePayments12m", 0);
        inputData.put("defaultsCount", 0);

        // When
        RulesEvaluationResult result = rulesEvaluationEngine.evaluateRules(ruleDefinition, inputData);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputData()).containsEntry("payment_grade", "EXCELLENT");
        assertThat(result.getOutputData()).containsKey("history_score");
    }

    @Test
    void testAMLRiskAssessmentRule() {
        // Given
        String ruleDefinition = """
            name: "AML Risk Assessment"
            description: "Evaluate transaction patterns for potential money laundering activities"

            inputs:
              - transactionAmount
              - customerRiskProfile
              - transactionFrequency24h
              - accountAgeDays
              - geographicRiskScore
              - transactionType

            when:
              - transactionAmount greater_than 10000
              - customerRiskProfile in_list ["HIGH", "UNKNOWN"]
              - transactionFrequency24h greater_than 5

            then:
              - set aml_risk_score to 85
              - set requires_manual_review to true
              - set compliance_flag to "AML_REVIEW_REQUIRED"
              - set review_priority to "HIGH"

            else:
              - calculate base_risk as geographicRiskScore * 0.3
              - add transactionAmount / 1000 to base_risk
              - set aml_risk_score to base_risk
              - set requires_manual_review to false

            output:
              aml_risk_score: number
              requires_manual_review: boolean
              compliance_flag: text
              review_priority: text
            """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("transactionAmount", 15000);
        inputData.put("customerRiskProfile", "HIGH");
        inputData.put("transactionFrequency24h", 6);
        inputData.put("accountAgeDays", 30);
        inputData.put("geographicRiskScore", 7);
        inputData.put("transactionType", "WIRE_TRANSFER");

        // When
        RulesEvaluationResult result = rulesEvaluationEngine.evaluateRules(ruleDefinition, inputData);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getConditionResult()).isTrue();
        assertThat(result.getOutputData()).containsEntry("aml_risk_score", 85);
        assertThat(result.getOutputData()).containsEntry("requires_manual_review", true);
        assertThat(result.getOutputData()).containsEntry("compliance_flag", "AML_REVIEW_REQUIRED");
        assertThat(result.getOutputData()).containsEntry("review_priority", "HIGH");
    }

    @Test
    void testCardFraudDetectionRule() {
        // Given
        String ruleDefinition = """
            name: "Card Fraud Detection"
            description: "Real-time fraud detection for credit/debit card transactions"

            inputs:
              - transactionAmount
              - merchantCategory
              - transactionTime
              - customerLocation
              - merchantLocation
              - cardPresent
              - recentFailedAttempts

            rules:
              - name: "High-Risk Merchant Category"
                when:
                  - merchantCategory in_list ["GAMBLING", "ADULT_ENTERTAINMENT", "CRYPTOCURRENCY"]
                then:
                  - set merchant_risk_score to 40
                  - set risk_factors to ["HIGH_RISK_MERCHANT"]

              - name: "Card Not Present"
                when:
                  - cardPresent equals false
                then:
                  - add 20 to merchant_risk_score
                  - append "CNP_TRANSACTION" to risk_factors

              - name: "Recent Failed Attempts"
                when:
                  - recentFailedAttempts greater_than 2
                then:
                  - add 25 to merchant_risk_score
                  - append "MULTIPLE_FAILURES" to risk_factors

              - name: "Final Risk Assessment"
                then:
                  - calculate final_fraud_score as min(merchant_risk_score, 100)
                  - if final_fraud_score greater_than 70 then set decision to "DECLINE"
                  - if final_fraud_score between 40 and 70 then set decision to "REVIEW"
                  - if final_fraud_score less_than 40 then set decision to "APPROVE"

            output:
              fraud_score: final_fraud_score
              decision: text
              risk_factors: risk_factors
            """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("transactionAmount", 500);
        inputData.put("merchantCategory", "GAMBLING");
        inputData.put("transactionTime", "14:30");
        inputData.put("customerLocation", "40.7128,-74.0060");
        inputData.put("merchantLocation", "40.7589,-73.9851");
        inputData.put("cardPresent", false);
        inputData.put("recentFailedAttempts", 3);

        // When
        RulesEvaluationResult result = rulesEvaluationEngine.evaluateRules(ruleDefinition, inputData);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputData()).containsKey("final_fraud_score");
        assertThat(result.getOutputData()).containsKey("decision");
        assertThat(result.getOutputData()).containsKey("risk_factors");
    }

    @Test
    void testComplexArithmeticExpression() {
        // Given
        String ruleDefinition = """
            name: "Final Credit Decision"
            description: "Combine all scores for final decision"

            inputs:
              - baseScore
              - incomeScore
              - historyScore
              - creditScore

            rules:
              - name: "Calculate Final Score"
                then:
                  - calculate weighted_score as (baseScore * 0.2) + (incomeScore * 0.3) + (historyScore * 0.2) + (creditScore * 0.3)
                  - set final_score to round(weighted_score)

              - name: "Excellent Credit"
                when: final_score at_least 90
                then:
                  - set decision to "APPROVED"
                  - set interest_rate to 4.5
                  - set credit_limit to 50000
                  - set risk_category to "PRIME"

            output:
              decision: text
              final_score: number
              interest_rate: number
              credit_limit: number
              risk_category: text
            """;

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("baseScore", 90);
        inputData.put("incomeScore", 95);
        inputData.put("historyScore", 90);
        inputData.put("creditScore", 95);

        // When
        RulesEvaluationResult result = rulesEvaluationEngine.evaluateRules(ruleDefinition, inputData);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputData()).containsKey("final_score");
        assertThat(result.getOutputData()).containsKey("decision");
    }

    /**
     * Test implementation of ConstantService for testing
     */
    private static class TestConstantService implements ConstantService {
        private final Map<String, ConstantDTO> constants = new HashMap<>();

        public TestConstantService() {
            // Setup database constants
            setupConstants();
        }

        private void setupConstants() {
            // MIN_CREDIT_SCORE constant
            ConstantDTO minCreditScore = ConstantDTO.builder()
                    .id(UUID.randomUUID())
                    .code("MIN_CREDIT_SCORE")
                    .name("Minimum Credit Score")
                    .valueType(ValueType.NUMBER)
                    .build();
            minCreditScore.setCurrentValue(650);
            constants.put("MIN_CREDIT_SCORE", minCreditScore);

            // MIN_ANNUAL_INCOME constant
            ConstantDTO minAnnualIncome = ConstantDTO.builder()
                    .id(UUID.randomUUID())
                    .code("MIN_ANNUAL_INCOME")
                    .name("Minimum Annual Income")
                    .valueType(ValueType.NUMBER)
                    .build();
            minAnnualIncome.setCurrentValue(40000);
            constants.put("MIN_ANNUAL_INCOME", minAnnualIncome);
        }
        @Override
        public Mono<PaginationResponse<ConstantDTO>> filterConstants(
                com.firefly.common.core.filters.FilterRequest<ConstantDTO> filterRequest) {
            return Mono.empty();
        }

        @Override
        public Mono<ConstantDTO> createConstant(ConstantDTO constantDTO) {
            return Mono.empty();
        }

        @Override
        public Mono<ConstantDTO> updateConstant(java.util.UUID constantId, ConstantDTO constantDTO) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> deleteConstant(java.util.UUID constantId) {
            return Mono.empty();
        }

        @Override
        public Mono<ConstantDTO> getConstantById(java.util.UUID constantId) {
            return Mono.empty();
        }

        @Override
        public Mono<ConstantDTO> getConstantByCode(String code) {
            ConstantDTO constant = constants.get(code);
            return constant != null ? Mono.just(constant) : Mono.empty();
        }
    }
}
