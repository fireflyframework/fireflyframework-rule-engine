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
import com.firefly.rules.interfaces.dtos.crud.ConstantDTO;
import com.firefly.rules.interfaces.enums.ValueType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Comprehensive test for the B2B Credit Scoring Engine example from the documentation.
 * This test validates the complete multi-stage credit evaluation workflow including:
 * - Data validation and preparation
 * - Financial analysis and ratio calculations
 * - Business profile and industry risk assessment
 * - Credit assessment and scoring
 * - Final decision and risk classification
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("B2B Credit Scoring Engine Test")
class B2BCreditScoringEngineTest {

    @Mock
    private ConstantService constantService;

    private ASTRulesEvaluationEngine evaluationEngine;

    @BeforeEach
    void setUp() {
        DSLParser dslParser = new DSLParser();
        ASTRulesDSLParser parser = new ASTRulesDSLParser(dslParser);
        evaluationEngine = new ASTRulesEvaluationEngine(parser, constantService, null, null);

        // Mock all the constants used in the credit scoring rule
        setupMockConstants();
    }

    private void setupMockConstants() {
        ConstantDTO minBusinessCreditScore = ConstantDTO.builder()
                .code("MIN_BUSINESS_CREDIT_SCORE")
                .valueType(ValueType.NUMBER)
                .currentValue(new BigDecimal("650"))
                .build();

        ConstantDTO excellentCreditThreshold = ConstantDTO.builder()
                .code("EXCELLENT_CREDIT_THRESHOLD")
                .valueType(ValueType.NUMBER)
                .currentValue(new BigDecimal("750"))
                .build();

        ConstantDTO maxDebtToIncomeRatio = ConstantDTO.builder()
                .code("MAX_DEBT_TO_INCOME_RATIO")
                .valueType(ValueType.NUMBER)
                .currentValue(new BigDecimal("0.4"))
                .build();

        ConstantDTO minDebtServiceCoverage = ConstantDTO.builder()
                .code("MIN_DEBT_SERVICE_COVERAGE")
                .valueType(ValueType.NUMBER)
                .currentValue(new BigDecimal("1.25"))
                .build();

        ConstantDTO maxLoanToValueRatio = ConstantDTO.builder()
                .code("MAX_LOAN_TO_VALUE_RATIO")
                .valueType(ValueType.NUMBER)
                .currentValue(new BigDecimal("0.8"))
                .build();

        ConstantDTO minYearsInBusiness = ConstantDTO.builder()
                .code("MIN_YEARS_IN_BUSINESS")
                .valueType(ValueType.NUMBER)
                .currentValue(new BigDecimal("2"))
                .build();

        ConstantDTO minAnnualRevenue = ConstantDTO.builder()
                .code("MIN_ANNUAL_REVENUE")
                .valueType(ValueType.NUMBER)
                .currentValue(new BigDecimal("100000"))
                .build();

        ConstantDTO maxLoanAmountUnsecured = ConstantDTO.builder()
                .code("MAX_LOAN_AMOUNT_UNSECURED")
                .valueType(ValueType.NUMBER)
                .currentValue(new BigDecimal("250000"))
                .build();

        ConstantDTO highRiskIndustryMultiplier = ConstantDTO.builder()
                .code("HIGH_RISK_INDUSTRY_MULTIPLIER")
                .valueType(ValueType.NUMBER)
                .currentValue(new BigDecimal("1.5"))
                .build();

        ConstantDTO lowRiskIndustryMultiplier = ConstantDTO.builder()
                .code("LOW_RISK_INDUSTRY_MULTIPLIER")
                .valueType(ValueType.NUMBER)
                .currentValue(new BigDecimal("0.8"))
                .build();

        ConstantDTO creditScoreWeight = ConstantDTO.builder()
                .code("CREDIT_SCORE_WEIGHT")
                .valueType(ValueType.NUMBER)
                .currentValue(new BigDecimal("0.3"))
                .build();

        ConstantDTO financialStabilityWeight = ConstantDTO.builder()
                .code("FINANCIAL_STABILITY_WEIGHT")
                .valueType(ValueType.NUMBER)
                .currentValue(new BigDecimal("0.25"))
                .build();

        ConstantDTO businessProfileWeight = ConstantDTO.builder()
                .code("BUSINESS_PROFILE_WEIGHT")
                .valueType(ValueType.NUMBER)
                .currentValue(new BigDecimal("0.25"))
                .build();

        ConstantDTO cashFlowWeight = ConstantDTO.builder()
                .code("CASH_FLOW_WEIGHT")
                .valueType(ValueType.NUMBER)
                .currentValue(new BigDecimal("0.2"))
                .build();

        when(constantService.getConstantsByCodes(anyList()))
                .thenReturn(Flux.just(
                        minBusinessCreditScore, excellentCreditThreshold, maxDebtToIncomeRatio,
                        minDebtServiceCoverage, maxLoanToValueRatio, minYearsInBusiness,
                        minAnnualRevenue, maxLoanAmountUnsecured, highRiskIndustryMultiplier,
                        lowRiskIndustryMultiplier, creditScoreWeight, financialStabilityWeight,
                        businessProfileWeight, cashFlowWeight
                ));
    }

    @Test
    @DisplayName("Should approve qualified business loan application")
    void testQualifiedBusinessLoanApproval() {
        // Create input data for a qualified business (from documentation example)
        Map<String, Object> inputData = createQualifiedBusinessInputData();

        // Get the complete B2B credit scoring rule from documentation
        String creditScoringRule = getB2BCreditScoringRule();

        // Execute the rule
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(creditScoringRule, inputData);

        // Verify the rule executed successfully
        assertTrue(result.isSuccess(), "Rule evaluation should succeed");
        assertNotNull(result.getOutputData(), "Output data should not be null");

        // Verify primary decision outputs
        assertEquals("APPROVED", result.getOutputData().get("approval_status"));
        assertEquals("LOW", result.getOutputData().get("risk_level"));
        assertNotNull(result.getOutputData().get("final_credit_score"));
        assertNotNull(result.getOutputData().get("interest_rate"));
        assertEquals(150000, result.getOutputData().get("approved_amount"));

        // Verify financial analysis results
        assertNotNull(result.getOutputData().get("debt_to_income_ratio"));
        assertNotNull(result.getOutputData().get("debt_service_coverage"));
        assertNotNull(result.getOutputData().get("profit_margin"));

        // Verify decision factors
        assertEquals(true, result.getOutputData().get("meets_credit_requirements"));
        assertEquals(true, result.getOutputData().get("meets_financial_requirements"));
        assertEquals(true, result.getOutputData().get("meets_business_requirements"));

        // Verify data validation passed
        assertEquals("PASSED", result.getOutputData().get("validation_status"));

        System.out.println("=== B2B Credit Scoring Test Results ===");
        System.out.println("Approval Status: " + result.getOutputData().get("approval_status"));
        System.out.println("Final Credit Score: " + result.getOutputData().get("final_credit_score"));
        System.out.println("Risk Level: " + result.getOutputData().get("risk_level"));
        System.out.println("Interest Rate: " + result.getOutputData().get("interest_rate"));
        System.out.println("Approved Amount: " + result.getOutputData().get("approved_amount"));
        System.out.println("Debt-to-Income Ratio: " + result.getOutputData().get("debt_to_income_ratio"));
        System.out.println("Debt Service Coverage: " + result.getOutputData().get("debt_service_coverage"));
        System.out.println("Recommendation: " + result.getOutputData().get("recommendation_summary"));
    }

    @Test
    @DisplayName("Should decline unqualified business loan application")
    void testUnqualifiedBusinessLoanDecline() {
        // Create input data for an unqualified business
        Map<String, Object> inputData = createUnqualifiedBusinessInputData();

        // Get the complete B2B credit scoring rule
        String creditScoringRule = getB2BCreditScoringRule();

        // Execute the rule
        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(creditScoringRule, inputData);

        // Verify the rule executed successfully
        assertTrue(result.isSuccess(), "Rule evaluation should succeed");
        assertNotNull(result.getOutputData(), "Output data should not be null");

        // Verify primary decision outputs
        assertEquals("DECLINED", result.getOutputData().get("approval_status"));
        assertEquals("HIGH", result.getOutputData().get("risk_level"));
        assertNotNull(result.getOutputData().get("rejection_reason"));

        System.out.println("=== B2B Credit Scoring Decline Test Results ===");
        System.out.println("Approval Status: " + result.getOutputData().get("approval_status"));
        System.out.println("Risk Level: " + result.getOutputData().get("risk_level"));
        System.out.println("Rejection Reason: " + result.getOutputData().get("rejection_reason"));
    }

    private Map<String, Object> createQualifiedBusinessInputData() {
        Map<String, Object> inputData = new HashMap<>();
        
        // Business identification and basic info
        inputData.put("businessId", "BUS123456");
        inputData.put("businessName", "Tech Solutions LLC");
        inputData.put("taxId", "12-3456789");
        inputData.put("businessType", "LLC");
        inputData.put("industryCode", "541511"); // Professional services (low risk)
        inputData.put("yearsInBusiness", 5);
        inputData.put("numberOfEmployees", 25);
        
        // Loan application details
        inputData.put("requestedAmount", 150000);
        inputData.put("loanPurpose", "equipment");
        inputData.put("requestedTerm", 60);
        inputData.put("hasCollateral", true);
        inputData.put("collateralValue", 200000);
        
        // Financial information
        inputData.put("annualRevenue", 1200000);
        inputData.put("monthlyRevenue", 100000);
        inputData.put("monthlyExpenses", 75000);
        inputData.put("existingDebt", 50000);
        inputData.put("monthlyDebtPayments", 2500);
        
        // Business owner information
        inputData.put("ownerCreditScore", 720);
        inputData.put("ownerYearsExperience", 10);
        inputData.put("ownershipPercentage", 75);
        
        // Credit bureau data
        inputData.put("businessCreditScore", 680);
        inputData.put("paymentHistoryScore", 85);
        inputData.put("creditUtilization", 0.3);
        inputData.put("publicRecordsCount", 0);
        inputData.put("tradelineCount", 8);
        
        // Banking and transaction data
        inputData.put("avgMonthlyDeposits", 105000);
        inputData.put("accountAgeMonths", 36);
        inputData.put("nsfCount12Months", 1);
        inputData.put("cashFlowVolatility", 15);
        inputData.put("averageAccountBalance", 25000);
        
        // Tax and financial verification data
        inputData.put("verifiedAnnualRevenue", 1180000);
        inputData.put("taxComplianceScore", 95);
        inputData.put("businessExpenses", 950000);
        inputData.put("netIncome", 230000);
        inputData.put("taxFilingHistory", 5);
        
        return inputData;
    }

    private Map<String, Object> createUnqualifiedBusinessInputData() {
        Map<String, Object> inputData = new HashMap<>();
        
        // Business with poor credit and financial metrics
        inputData.put("businessId", "BUS789012");
        inputData.put("businessName", "Struggling Restaurant LLC");
        inputData.put("taxId", "98-7654321");
        inputData.put("businessType", "LLC");
        inputData.put("industryCode", "722511"); // Food service (high risk)
        inputData.put("yearsInBusiness", 1); // Below minimum
        inputData.put("numberOfEmployees", 5);
        
        // Loan application details
        inputData.put("requestedAmount", 75000);
        inputData.put("loanPurpose", "working capital");
        inputData.put("requestedTerm", 36);
        inputData.put("hasCollateral", false);
        inputData.put("collateralValue", 0);
        
        // Poor financial information
        inputData.put("annualRevenue", 80000); // Below minimum
        inputData.put("monthlyRevenue", 6500);
        inputData.put("monthlyExpenses", 7000); // Expenses exceed revenue
        inputData.put("existingDebt", 25000);
        inputData.put("monthlyDebtPayments", 1200);
        
        // Business owner information
        inputData.put("ownerCreditScore", 580); // Poor credit
        inputData.put("ownerYearsExperience", 2);
        inputData.put("ownershipPercentage", 100);
        
        // Poor credit bureau data
        inputData.put("businessCreditScore", 520); // Below minimum
        inputData.put("paymentHistoryScore", 45);
        inputData.put("creditUtilization", 0.9); // High utilization
        inputData.put("publicRecordsCount", 2); // Has public records
        inputData.put("tradelineCount", 3);
        
        // Poor banking data
        inputData.put("avgMonthlyDeposits", 6000);
        inputData.put("accountAgeMonths", 12);
        inputData.put("nsfCount12Months", 8); // Many NSF incidents
        inputData.put("cashFlowVolatility", 75); // High volatility
        inputData.put("averageAccountBalance", 1500);
        
        // Tax and financial verification data
        inputData.put("verifiedAnnualRevenue", 75000);
        inputData.put("taxComplianceScore", 60); // Poor compliance
        inputData.put("businessExpenses", 85000);
        inputData.put("netIncome", -10000); // Negative income
        inputData.put("taxFilingHistory", 1);
        
        return inputData;
    }

    private String getB2BCreditScoringRule() {
        return """
            name: "B2B Credit Scoring Platform"
            description: "Comprehensive business credit assessment using multiple data sources"
            version: "1.0.0"
            
            metadata:
              tags: ["b2b", "credit-scoring", "business-loans"]
              author: "Credit Risk Engineering Team"
              category: "Business Credit Assessment"
            """;
    }
}
