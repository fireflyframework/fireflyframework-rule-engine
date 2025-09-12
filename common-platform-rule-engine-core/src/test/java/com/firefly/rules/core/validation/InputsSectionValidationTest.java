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

package com.firefly.rules.core.validation;

import com.firefly.rules.core.dsl.evaluation.*;
import com.firefly.rules.core.dsl.parser.RulesDSLParser;
import com.firefly.rules.core.services.ConstantService;
import com.firefly.rules.interfaces.dtos.crud.ConstantDTO;
import com.firefly.rules.interfaces.enums.ValueType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test to validate the proper usage of the inputs section in YAML DSL
 * This test demonstrates what should and should not be included in the inputs section
 */
class InputsSectionValidationTest {

    private TestConstantService constantService;
    private RulesEvaluationEngine rulesEvaluationEngine;
    private RulesDSLParser rulesDSLParser;

    @BeforeEach
    void setUp() {
        constantService = new TestConstantService();
        NamingConventionValidator namingValidator = new NamingConventionValidator();
        rulesDSLParser = new RulesDSLParser(namingValidator);

        // Create all required dependencies for RulesEvaluationEngine
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

        // Setup database constants
        setupDatabaseConstants();
    }

    private void setupDatabaseConstants() {
        // Database constants (UPPER_CASE) - these should NOT be in inputs section
        ConstantDTO minCreditScore = ConstantDTO.builder()
                .id(UUID.randomUUID())
                .code("MIN_CREDIT_SCORE")
                .name("Minimum Credit Score")
                .valueType(ValueType.NUMBER)
                .build();
        minCreditScore.setCurrentValue(650);
        constantService.addConstant("MIN_CREDIT_SCORE", minCreditScore);

        ConstantDTO maxLoanAmount = ConstantDTO.builder()
                .id(UUID.randomUUID())
                .code("MAX_LOAN_AMOUNT")
                .name("Maximum Loan Amount")
                .valueType(ValueType.NUMBER)
                .build();
        maxLoanAmount.setCurrentValue(500000);
        constantService.addConstant("MAX_LOAN_AMOUNT", maxLoanAmount);
    }

    @Test
    void testCorrectInputsSection_OnlyRuntimeVariables_ShouldSucceed() {
        // Given - Rule with ONLY runtime variables in inputs section
        String ruleDefinition = """
            name: "Correct Inputs Usage"
            description: "Demonstrates proper inputs section usage"
            
            inputs:
              - creditScore        # ✅ camelCase: runtime value from API
              - annualIncome       # ✅ camelCase: runtime value from API
              - requestedAmount    # ✅ camelCase: runtime value from API
              - existingDebt       # ✅ camelCase: runtime value from API
            
            when:
              - creditScore at_least MIN_CREDIT_SCORE      # MIN_CREDIT_SCORE: database constant (NOT in inputs)
              - requestedAmount less_than MAX_LOAN_AMOUNT  # MAX_LOAN_AMOUNT: database constant (NOT in inputs)
              - annualIncome at_least 40000
            
            then:
              - calculate debt_to_income as existingDebt / annualIncome    # debt_to_income: computed (NOT in inputs)
              - calculate loan_to_income as requestedAmount / annualIncome # loan_to_income: computed (NOT in inputs)
              - set approval_status to "APPROVED"                         # approval_status: computed (NOT in inputs)
            
            output:
              debt_to_income: debt_to_income
              loan_to_income: loan_to_income
              approval_status: approval_status
            """;

        // Input data matches the inputs section exactly
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("creditScore", 720);        // camelCase - matches inputs section
        inputData.put("annualIncome", 75000);     // camelCase - matches inputs section
        inputData.put("requestedAmount", 250000); // camelCase - matches inputs section
        inputData.put("existingDebt", 25000);     // camelCase - matches inputs section

        // When
        RulesEvaluationResult result = rulesEvaluationEngine.evaluateRules(ruleDefinition, inputData);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputData()).containsKey("debt_to_income");
        assertThat(result.getOutputData()).containsKey("loan_to_income");
        assertThat(result.getOutputData()).containsKey("approval_status");
        
        // Verify computed values
        assertThat(result.getOutputData().get("debt_to_income")).isEqualTo(25000.0 / 75000.0);
        assertThat(result.getOutputData().get("loan_to_income")).isEqualTo(250000.0 / 75000.0);
        assertThat(result.getOutputData().get("approval_status")).isEqualTo("APPROVED");
    }

    @Test
    void testIncorrectInputsSection_DatabaseConstantsInInputs_ShouldFail() {
        // Given - Rule incorrectly includes database constants in inputs section
        String ruleDefinition = """
            name: "Incorrect Inputs Usage - Database Constants"
            
            inputs:
              - creditScore
              - MIN_CREDIT_SCORE     # ❌ Database constant should NOT be in inputs
              - MAX_LOAN_AMOUNT      # ❌ Database constant should NOT be in inputs
            
            when:
              - creditScore at_least MIN_CREDIT_SCORE
            
            then:
              - set result to "approved"
            """;

        // When & Then - Should fail during parsing due to naming convention violation
        assertThatThrownBy(() -> rulesDSLParser.parseRules(ruleDefinition))
                .isInstanceOf(RulesDSLParser.RuleDSLParseException.class)
                .hasMessageContaining("MIN_CREDIT_SCORE")
                .hasMessageContaining("camelCase");
    }

    @Test
    void testIncorrectInputsSection_ComputedVariablesInInputs_ShouldFail() {
        // Given - Rule incorrectly includes computed variables in inputs section
        String ruleDefinition = """
            name: "Incorrect Inputs Usage - Computed Variables"
            
            inputs:
              - creditScore
              - debt_to_income       # ❌ Computed variable should NOT be in inputs
              - final_score          # ❌ Computed variable should NOT be in inputs
            
            when:
              - creditScore at_least 650
            
            then:
              - calculate debt_to_income as 0.3
              - set final_score to 85
            """;

        // When & Then - Should fail during parsing due to naming convention violation
        assertThatThrownBy(() -> rulesDSLParser.parseRules(ruleDefinition))
                .isInstanceOf(RulesDSLParser.RuleDSLParseException.class)
                .hasMessageContaining("debt_to_income")
                .hasMessageContaining("camelCase");
    }

    /**
     * Test implementation of ConstantService for testing
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
}
