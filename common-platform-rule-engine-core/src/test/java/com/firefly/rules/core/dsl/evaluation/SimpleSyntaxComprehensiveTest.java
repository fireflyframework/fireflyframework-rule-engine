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

import com.firefly.rules.core.dsl.model.RulesDSL;
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
import static org.assertj.core.api.Assertions.within;

/**
 * Comprehensive test for all documented simple syntax patterns to ensure
 * documentation and implementation are fully aligned.
 */
class SimpleSyntaxComprehensiveTest {

    private RulesDSLParser parser;
    private RulesEvaluationEngine evaluator;
    private ConstantService constantService;

    @BeforeEach
    void setUp() {
        // Create a test implementation of ConstantService
        constantService = new TestConstantService();

        NamingConventionValidator namingValidator = new NamingConventionValidator();
        parser = new RulesDSLParser(namingValidator);
        ArithmeticEvaluator arithmeticEvaluator = new ArithmeticEvaluator();
        VariableResolver variableResolver = new VariableResolver(arithmeticEvaluator);
        ConditionEvaluator conditionEvaluator = new ConditionEvaluator(variableResolver, arithmeticEvaluator);
        ActionExecutor actionExecutor = new ActionExecutor(variableResolver, namingValidator, conditionEvaluator);

        evaluator = new RulesEvaluationEngine(
            parser,
            conditionEvaluator,
            actionExecutor,
            variableResolver,
            constantService
        );
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

        // Other methods not needed for testing
        @Override
        public Mono<com.firefly.common.core.queries.PaginationResponse<ConstantDTO>> filterConstants(
                com.firefly.common.core.filters.FilterRequest<ConstantDTO> filterRequest) {
            return Mono.empty();
        }

        @Override
        public Mono<ConstantDTO> createConstant(ConstantDTO constantDTO) {
            return Mono.empty();
        }

        @Override
        public Mono<ConstantDTO> updateConstant(UUID constantId, ConstantDTO constantDTO) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> deleteConstant(UUID constantId) {
            return Mono.empty();
        }

        @Override
        public Mono<ConstantDTO> getConstantById(UUID constantId) {
            return Mono.empty();
        }
    }

    @Test
    void testVariableAssignmentSyntax() {
        // Test: set variable to value
        String ruleDefinition = """
            name: "Variable Assignment Test"
            description: "Test set variable to value syntax"

            inputs:
              - creditScore

            rules:
              - name: "Set Variables"
                then:
                  - set is_eligible to true
                  - set risk_score to 75
                  - set approval_reason to "Meets all criteria"
                  - set customer_tier to "PREMIUM"

            output:
              is_eligible: boolean
              risk_score: number
              approval_reason: text
              customer_tier: text
            """;

        // When
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("creditScore", 750);

        var result = evaluator.evaluateRules(ruleDefinition, inputData);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputData().get("is_eligible")).isEqualTo(true);
        assertThat(result.getOutputData().get("risk_score")).isEqualTo(75);
        assertThat(result.getOutputData().get("approval_reason")).isEqualTo("Meets all criteria");
        assertThat(result.getOutputData().get("customer_tier")).isEqualTo("PREMIUM");
    }

    @Test
    void testCalculateExpressionSyntax() {
        // Test: calculate variable as expression
        String ruleDefinition = """
            name: "Calculate Expression Test"
            description: "Test calculate variable as expression syntax"

            inputs:
              - totalDebt
              - annualIncome
              - creditScore
              - incomeScore

            rules:
              - name: "Calculate Expressions"
                then:
                  - calculate debt_ratio as totalDebt / annualIncome
                  - calculate weighted_score as (creditScore * 0.4) + (incomeScore * 0.6)

            output:
              debt_ratio: number
              weighted_score: number
            """;

        // When
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("totalDebt", 50000);
        inputData.put("annualIncome", 100000);
        inputData.put("creditScore", 750);
        inputData.put("incomeScore", 80);

        var result = evaluator.evaluateRules(ruleDefinition, inputData);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputData().get("debt_ratio")).isEqualTo(0.5);
        assertThat(result.getOutputData().get("weighted_score")).isEqualTo(348.0); // (750 * 0.4) + (80 * 0.6)
    }

    @Test
    void testArithmeticOperationsSyntax() {
        // Test: add/subtract/multiply/divide operations
        String ruleDefinition = """
            name: "Arithmetic Operations Test"
            description: "Test all arithmetic operation syntax patterns"

            inputs:
              - baseScore
              - penaltyPoints
              - riskFactor
              - monthlyPayment

            rules:
              - name: "Arithmetic Operations"
                then:
                  - set base_score to baseScore
                  - set penalty_points to penaltyPoints
                  - set risk_factor to riskFactor
                  - set monthly_payment to monthlyPayment
                  - add 10 to base_score
                  - subtract penalty_points from base_score
                  - multiply risk_factor by 1.5
                  - divide monthly_payment by 12

            output:
              base_score: number
              penalty_points: number
              risk_factor: number
              monthly_payment: number
            """;

        // When
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("baseScore", 100);
        inputData.put("penaltyPoints", 5);
        inputData.put("riskFactor", 2.0);
        inputData.put("monthlyPayment", 1200);

        var result = evaluator.evaluateRules(ruleDefinition, inputData);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputData().get("base_score")).isEqualTo(105.0); // 100 + 10 - 5
        assertThat(result.getOutputData().get("risk_factor")).isEqualTo(3.0); // 2.0 * 1.5
        assertThat(result.getOutputData().get("monthly_payment")).isEqualTo(100.0); // 1200 / 12
    }

    @Test
    void testConditionalActionsSyntax() {
        // Test: if condition then action
        String ruleDefinition = """
            name: "Conditional Actions Test"
            description: "Test if condition then action syntax"

            inputs:
              - creditScore
              - annualIncome
              - accountAge
              - hasGuarantor

            rules:
              - name: "Conditional Actions"
                then:
                  - set tier to "STANDARD"
                  - set risk_score to 50
                  - set loyalty_bonus to 0
                  - if creditScore greater_than 750 then set tier to "PRIME"
                  - if annualIncome less_than 50000 then add 5 to risk_score
                  - if accountAge at_least 24 then set loyalty_bonus to 100
                  - if hasGuarantor equals true then subtract 10 from risk_score

            output:
              tier: text
              risk_score: number
              loyalty_bonus: number
            """;

        // When
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("creditScore", 780);
        inputData.put("annualIncome", 45000);
        inputData.put("accountAge", 30);
        inputData.put("hasGuarantor", true);

        var result = evaluator.evaluateRules(ruleDefinition, inputData);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputData().get("tier")).isEqualTo("PRIME"); // creditScore > 750
        assertThat(result.getOutputData().get("risk_score")).isEqualTo(45.0); // 50 + 5 - 10
        assertThat(result.getOutputData().get("loyalty_bonus")).isEqualTo(100); // accountAge >= 24
    }

    @Test
    void testFunctionCallsWithSyntax() {
        // Test: call function_name with [parameters]
        String ruleDefinition = """
            name: "Function Calls Test"
            description: "Test call function_name with [parameters] syntax"

            inputs:
              - monthlyDebt
              - annualIncome
              - principal
              - rate
              - time

            rules:
              - name: "Function Calls"
                then:
                  - call calculate_debt_ratio with [monthlyDebt, annualIncome / 12, "debt_to_income"]
                  - call calculate with ["principal * rate * time", "simple_interest"]
                  - call log with ["Processing application", "INFO"]

            output:
              debt_to_income: number
              simple_interest: number
            """;

        // When
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("monthlyDebt", 2000);
        inputData.put("annualIncome", 60000);
        inputData.put("principal", 10000);
        inputData.put("rate", 0.05);
        inputData.put("time", 2);

        var result = evaluator.evaluateRules(ruleDefinition, inputData);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputData().get("debt_to_income")).isEqualTo(0.4); // 2000 / (60000/12)
        assertThat(result.getOutputData().get("simple_interest")).isEqualTo(1000.0); // 10000 * 0.05 * 2
    }

    @Test
    void testComplexDocumentedExample() {
        // Test the exact example from documentation
        String ruleDefinition = """
            name: "Documentation Example Test"
            description: "Test the exact patterns shown in documentation"

            inputs:
              - creditScore
              - monthlyDebt
              - annualIncome
              - baseRisk

            rules:
              - name: "Documentation Examples"
                then:
                  - calculate approval_score as creditScore * 0.8
                  - if baseRisk greater_than 50 then set compliance_flag to "MONITOR"
                  - call calculate_debt_ratio with [monthlyDebt, annualIncome / 12, "debt_to_income"]

            output:
              approval_score: number
              compliance_flag: text
              debt_to_income: number
            """;

        // When
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("creditScore", 750);
        inputData.put("monthlyDebt", 3000);
        inputData.put("annualIncome", 72000);
        inputData.put("baseRisk", 60);

        var result = evaluator.evaluateRules(ruleDefinition, inputData);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputData().get("approval_score")).isEqualTo(600.0); // 750 * 0.8
        assertThat(result.getOutputData().get("compliance_flag")).isEqualTo("MONITOR"); // baseRisk > 50
        assertThat(result.getOutputData().get("debt_to_income")).isEqualTo(0.5); // 3000 / (72000/12)
    }

    @Test
    void testPowerOperationSyntax() {
        // Test power operations using ^ operator
        String ruleDefinition = """
            name: "Power Operations Test"
            description: "Test power operations with ^ operator"

            inputs:
              - baseValue
              - principal
              - rate
              - years

            rules:
              - name: "Power Operations"
                then:
                  - calculate square_value as baseValue ^ 2
                  - calculate cube_value as baseValue ^ 3
                  - calculate compound_interest as principal * (1 + rate) ^ years

            output:
              square_value: "Square of base value"
              cube_value: "Cube of base value"
              compound_interest: "Compound interest calculation"
            """;

        // When
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("baseValue", 5);
        inputData.put("principal", 1000);
        inputData.put("rate", 0.05);
        inputData.put("years", 3);

        var result = evaluator.evaluateRules(ruleDefinition, inputData);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputData().get("square_value")).isEqualTo(25.0); // 5^2
        assertThat(result.getOutputData().get("cube_value")).isEqualTo(125.0); // 5^3
        // 1000 * (1.05)^3 = 1000 * 1.157625 = 1157.625 (with floating point precision)
        assertThat((Double) result.getOutputData().get("compound_interest")).isCloseTo(1157.625, within(0.001));
    }
}
