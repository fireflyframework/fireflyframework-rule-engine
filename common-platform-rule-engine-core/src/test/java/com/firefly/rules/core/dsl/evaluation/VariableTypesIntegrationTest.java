package com.firefly.rules.core.dsl.evaluation;

import com.firefly.rules.core.dsl.model.ConstantDefinition;
import com.firefly.rules.core.dsl.model.RulesDSL;
import com.firefly.rules.core.dsl.model.VariableDefinition;
import com.firefly.rules.core.dsl.parser.RulesDSLParser;
import com.firefly.rules.core.services.ConstantService;
import com.firefly.rules.core.validation.NamingConventionValidator;
import com.firefly.rules.interfaces.dtos.crud.ConstantDTO;
import com.firefly.rules.interfaces.enums.ValueType;
import com.firefly.common.core.filters.FilterRequest;
import com.firefly.common.core.queries.PaginationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test to verify proper handling of CONSTANTS, INPUT VARIABLES, and COMPUTED VALUES
 */
class VariableTypesIntegrationTest {

    private RulesEvaluationEngine rulesEvaluationEngine;
    private TestConstantService constantService;
    private RulesDSLParser rulesDSLParser;
    private VariableResolver variableResolver;
    private ArithmeticEvaluator arithmeticEvaluator;
    private ConditionEvaluator conditionEvaluator;
    private ActionExecutor actionExecutor;

    @BeforeEach
    void setUp() {
        constantService = new TestConstantService();
        NamingConventionValidator namingValidator = new NamingConventionValidator();
        rulesDSLParser = new RulesDSLParser(namingValidator);
        arithmeticEvaluator = new ArithmeticEvaluator();
        variableResolver = new VariableResolver(arithmeticEvaluator);
        conditionEvaluator = new ConditionEvaluator(variableResolver, arithmeticEvaluator);
        actionExecutor = new ActionExecutor(variableResolver, namingValidator, conditionEvaluator);
        rulesEvaluationEngine = new RulesEvaluationEngine(
            rulesDSLParser,
            conditionEvaluator,
            actionExecutor,
            variableResolver,
            constantService
        );
    }

    @Test
    void testVariableTypesHandling() {
        // Setup system constants in the database
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

        // Create rule with inline constants, input variables, and computed values
        RulesDSL ruleDSL = RulesDSL.builder()
                .name("Comprehensive Variable Types Test")
                .description("Test all three types of variables")
                // Define inline constants
                .constants(Arrays.asList(
                        ConstantDefinition.builder()
                                .name("RISK_MULTIPLIER")
                                .value(1.5)
                                .build(),
                        ConstantDefinition.builder()
                                .name("BASE_RATE")
                                .value(0.05)
                                .build()
                ))
                // Define input variables with defaults
                .variables(Arrays.asList(
                        VariableDefinition.builder()
                                .name("customer_credit_score")
                                .type("number")
                                .defaultValue(600)
                                .build(),
                        VariableDefinition.builder()
                                .name("requested_amount")
                                .type("number")
                                .required(true)
                                .build(),
                        VariableDefinition.builder()
                                .name("customer_income")
                                .type("number")
                                .required(true)
                                .build()
                ))
                .inputs(Arrays.asList("customer_credit_score", "requested_amount", "customer_income"))
                .when("customer_credit_score >= MIN_CREDIT_SCORE && requested_amount <= MAX_LOAN_AMOUNT")
                .then(Arrays.asList(
                        "set debt_to_income_ratio to requested_amount / customer_income",
                        "set risk_score to debt_to_income_ratio * RISK_MULTIPLIER",
                        "set final_rate to BASE_RATE + (risk_score * 0.01)",
                        "set decision to \"approved\""
                ))
                .elseAction(Arrays.asList(
                        "set decision to \"rejected\"",
                        "set reason to \"Credit score or loan amount requirements not met\""
                ))
                .build();

        // Provide input data (INPUT VARIABLES)
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("customer_credit_score", 720);  // Override default
        inputData.put("requested_amount", 300000);
        inputData.put("customer_income", 100000);

        // Execute rule
        RulesEvaluationResult result = rulesEvaluationEngine.evaluateRulesReactive(ruleDSL, inputData).block();

        // Verify result
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getConditionResult()).isTrue();

        // Verify output contains computed values
        Map<String, Object> outputData = result.getOutputData();
        assertThat(outputData).isNotNull();
        assertThat(outputData.get("decision")).isEqualTo("approved");
        assertThat(outputData.get("debt_to_income_ratio")).isEqualTo(3.0);  // 300000 / 100000
        assertThat(outputData.get("risk_score")).isEqualTo(4.5);  // 3.0 * 1.5
        // Arithmetic expression is now correctly evaluated: 0.05 + (4.5 * 0.01) = 0.095
        assertThat(outputData.get("final_rate")).isEqualTo(0.095);
    }

    @Test
    void testVariableResolutionPriority() {
        // Test that computed variables override input variables, which override constants
        EvaluationContext context = new EvaluationContext();
        
        // Set all three types with same name
        context.setSystemConstant("test_value", "constant");
        context.setInputVariable("test_value", "input");
        context.setComputedVariable("test_value", "computed");

        // getValue should return computed value (highest priority)
        assertThat(context.getValue("test_value")).isEqualTo("computed");

        // Remove computed, should return input
        context.removeComputedVariable("test_value");
        assertThat(context.getValue("test_value")).isEqualTo("input");

        // Remove input, should return constant
        context.removeInputVariable("test_value");
        assertThat(context.getValue("test_value")).isEqualTo("constant");
    }

    @Test
    void testVariableTypesInArithmeticExpressions() {
        EvaluationContext context = new EvaluationContext();

        // Set up different types of variables
        context.setSystemConstant("CONSTANT_VALUE", 10);
        context.setInputVariable("INPUT_VALUE", 20);
        context.setComputedVariable("COMPUTED_VALUE", 30);

        VariableResolver resolver = new VariableResolver(new ArithmeticEvaluator());

        // Test arithmetic with all three types
        Object result = resolver.resolveValue("CONSTANT_VALUE + INPUT_VALUE + COMPUTED_VALUE", context);
        assertThat(result).isEqualTo(60.0);  // 10 + 20 + 30
    }

    @Test
    void testEvaluationContextIntegrity() {
        EvaluationContext context = new EvaluationContext();

        // Test operation ID generation
        String operationId1 = context.getOperationId();
        String operationId2 = context.getOperationId();
        assertThat(operationId1).isEqualTo(operationId2); // Should be same instance
        assertThat(operationId1).startsWith("op-");
        assertThat(operationId1).hasSize(11); // "op-" + 8 chars

        // Test rule name and timing
        context.setRuleName("Test Rule");
        context.setStartTime(System.currentTimeMillis());
        assertThat(context.getRuleName()).isEqualTo("Test Rule");
        assertThat(context.getStartTime()).isGreaterThan(0);

        // Test circuit breaker functionality
        assertThat(context.isCircuitBreakerTriggered()).isFalse();
        context.triggerCircuitBreaker("Test circuit breaker");
        assertThat(context.isCircuitBreakerTriggered()).isTrue();
        assertThat(context.getCircuitBreakerMessage()).isEqualTo("Test circuit breaker");
    }

    @Test
    void testEvaluationContextVariableSourceTracking() {
        EvaluationContext context = new EvaluationContext();

        // Test variable source tracking
        context.setSystemConstant("TEST_CONSTANT", "constant_value");
        context.setInputVariable("TEST_INPUT", "input_value");
        context.setComputedVariable("TEST_COMPUTED", "computed_value");

        assertThat(context.getVariableSource("TEST_CONSTANT")).isEqualTo("CONSTANT");
        assertThat(context.getVariableSource("TEST_INPUT")).isEqualTo("INPUT");
        assertThat(context.getVariableSource("TEST_COMPUTED")).isEqualTo("COMPUTED");
        assertThat(context.getVariableSource("NON_EXISTENT")).isEqualTo("NOT_FOUND");

        // Test hasValue method
        assertThat(context.hasValue("TEST_CONSTANT")).isTrue();
        assertThat(context.hasValue("TEST_INPUT")).isTrue();
        assertThat(context.hasValue("TEST_COMPUTED")).isTrue();
        assertThat(context.hasValue("NON_EXISTENT")).isFalse();
    }

    @Test
    void testEvaluationContextCollectionMethods() {
        EvaluationContext context = new EvaluationContext();

        // Add test data
        context.setSystemConstant("CONST1", "value1");
        context.setSystemConstant("CONST2", "value2");
        context.setInputVariable("INPUT1", "value3");
        context.setInputVariable("INPUT2", "value4");
        context.setComputedVariable("COMP1", "value5");
        context.setComputedVariable("COMP2", "value6");

        // Test collection getters return defensive copies
        Map<String, Object> constants = context.getSystemConstants();
        Map<String, Object> inputs = context.getInputVariables();
        Map<String, Object> computed = context.getComputedVariables();

        assertThat(constants).hasSize(2);
        assertThat(inputs).hasSize(2);
        assertThat(computed).hasSize(2);

        // Verify defensive copies (modifications don't affect original)
        constants.put("NEW_CONST", "new_value");
        assertThat(context.getSystemConstants()).hasSize(2); // Original unchanged

        inputs.put("NEW_INPUT", "new_value");
        assertThat(context.getInputVariables()).hasSize(2); // Original unchanged

        computed.put("NEW_COMP", "new_value");
        assertThat(context.getComputedVariables()).hasSize(2); // Original unchanged
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
        public Mono<PaginationResponse<ConstantDTO>> filterConstants(FilterRequest<ConstantDTO> filterRequest) {
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
