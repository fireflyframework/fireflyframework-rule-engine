
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
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Unit tests for RulesEvaluationEngine
 */
class RulesEvaluationEngineTest {

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
    }

    @Test
    void testEvaluateSimplifiedDSLWithApproval() {
        // Given
        RulesDSL rulesDSL = RulesDSL.builder()
                .inputs(java.util.Arrays.asList("customer_age", "order_total"))
                .when("customer_age >= 18 && order_total > 100")
                .then(Map.of("action", "approve", "discount", 10))
                .elseAction(Map.of("action", "reject", "reason", "Requirements not met"))
                .build();

        // Mock constants
        ConstantDTO ageVar = ConstantDTO.builder()
                .id(UUID.randomUUID())
                .code("customer_age")
                .name("Customer Age")
                .valueType(ValueType.NUMBER)
                .build();
        ageVar.setCurrentValue(25);

        ConstantDTO totalVar = ConstantDTO.builder()
                .id(UUID.randomUUID())
                .code("order_total")
                .name("Order Total")
                .valueType(ValueType.NUMBER)
                .build();
        totalVar.setCurrentValue(150.0);

        // Add constants to the test service
        constantService.addConstant("customer_age", ageVar);
        constantService.addConstant("order_total", totalVar);



        // When
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("customer_age", 25);
        inputData.put("order_total", 150.0);

        Mono<RulesEvaluationResult> result = rulesEvaluationEngine.evaluateRulesReactive(rulesDSL, inputData)
                .subscribeOn(reactor.core.scheduler.Schedulers.immediate());

        // Then
        RulesEvaluationResult evalResult = result.block();



        // Original assertions
        assert evalResult.isSuccess();
        assert evalResult.getConditionResult();
        assert evalResult.getOutputData().get("action").equals("approve");
        assert evalResult.getOutputData().get("discount").equals(10);
    }

    @Test
    void testEvaluateSimplifiedDSLWithRejection() {
        // Given
        RulesDSL rulesDSL = RulesDSL.builder()
                .inputs(java.util.Arrays.asList("customer_age", "order_total"))
                .when("customer_age >= 18 && order_total > 100")
                .then(Map.of("action", "approve"))
                .elseAction(Map.of("action", "reject", "reason", "Requirements not met"))
                .build();

        // Mock constants - customer too young
        ConstantDTO ageVar = ConstantDTO.builder()
                .id(UUID.randomUUID())
                .code("customer_age")
                .name("Customer Age")
                .valueType(ValueType.NUMBER)
                .build();
        ageVar.setCurrentValue(16);

        ConstantDTO totalVar = ConstantDTO.builder()
                .id(UUID.randomUUID())
                .code("order_total")
                .name("Order Total")
                .valueType(ValueType.NUMBER)
                .build();
        totalVar.setCurrentValue(150.0);

        constantService.addConstant("customer_age", ageVar);
        constantService.addConstant("order_total", totalVar);

        // When
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("customer_age", 16); // Below 18, should trigger rejection
        inputData.put("order_total", 50.0); // Below 100, should trigger rejection

        Mono<RulesEvaluationResult> result = rulesEvaluationEngine.evaluateRulesReactive(rulesDSL, inputData);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(evalResult -> {
                    return evalResult.isSuccess() &&
                           !evalResult.getConditionResult() &&
                           evalResult.getOutputData().get("action").equals("reject") &&
                           evalResult.getOutputData().get("reason").equals("Requirements not met");
                })
                .verifyComplete();
    }

    @Test
    void testEvaluateWithInputData() {
        // Given
        RulesDSL rulesDSL = RulesDSL.builder()
                .inputs(java.util.Arrays.asList("customer_age"))
                .when("customer_age >= 18 && dynamic_amount > 500")
                .then(Map.of("action", "approve"))
                .elseAction(Map.of("action", "reject"))
                .build();

        ConstantDTO ageVar = ConstantDTO.builder()
                .id(UUID.randomUUID())
                .code("customer_age")
                .name("Customer Age")
                .valueType(ValueType.NUMBER)
                .build();
        ageVar.setCurrentValue(25);

        constantService.addConstant("customer_age", ageVar);

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("customer_age", 25);
        inputData.put("dynamic_amount", 600.0);

        // When
        Mono<RulesEvaluationResult> result = rulesEvaluationEngine.evaluateRulesReactive(rulesDSL, inputData);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(evalResult -> {
                    return evalResult.isSuccess() &&
                           evalResult.getConditionResult() &&
                           evalResult.getOutputData().get("action").equals("approve");
                })
                .verifyComplete();
    }

    @Test
    void testEvaluateWithVariableNotFound() {
        // Given
        RulesDSL rulesDSL = RulesDSL.builder()
                .inputs(java.util.Arrays.asList("unknown_variable"))
                .when("unknown_variable > 0")
                .then(Map.of("action", "approve"))
                .build();

        // No variables added - unknown_variable will not be found

        // When
        Mono<RulesEvaluationResult> result = rulesEvaluationEngine.evaluateRulesReactive(rulesDSL, new HashMap<>());

        // Then
        StepVerifier.create(result)
                .expectNextMatches(evalResult -> {
                    return evalResult.isSuccess() &&
                           !evalResult.getConditionResult() &&
                           evalResult.getOutputData().isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void testEvaluateWithStringComparison() {
        // Given
        RulesDSL ruleDSL = RulesDSL.builder()
                .inputs(java.util.Arrays.asList("customer_tier"))
                .when("customer_tier == \"PREMIUM\"")
                .then(Map.of("discount", 15, "priority", "high"))
                .elseAction(Map.of("discount", 5, "priority", "normal"))
                .build();

        ConstantDTO tierVar = ConstantDTO.builder()
                .id(UUID.randomUUID())
                .code("customer_tier")
                .name("Customer Tier")
                .valueType(ValueType.STRING)
                .build();
        tierVar.setCurrentValue("PREMIUM");

        constantService.addConstant("customer_tier", tierVar);
        // "PREMIUM" is not added as a variable - it's a literal value

        // When
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("customer_tier", "PREMIUM");

        Mono<RulesEvaluationResult> result = rulesEvaluationEngine.evaluateRulesReactive(ruleDSL, inputData);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(evalResult -> {
                    return evalResult.isSuccess() &&
                           evalResult.getConditionResult() &&
                           evalResult.getOutputData().get("discount").equals(15) &&
                           evalResult.getOutputData().get("priority").equals("high");
                })
                .verifyComplete();
    }

    @Test
    void testEvaluateWithBooleanVariable() {
        // Given
        RulesDSL ruleDSL = RulesDSL.builder()
                .inputs(java.util.Arrays.asList("is_vip"))
                .when("is_vip")
                .then(Map.of("service", "premium", "fast_track", true))
                .elseAction(Map.of("service", "standard", "fast_track", false))
                .build();

        ConstantDTO vipVar = ConstantDTO.builder()
                .id(UUID.randomUUID())
                .code("is_vip")
                .name("Is VIP")
                .valueType(ValueType.BOOLEAN)
                .build();
        vipVar.setCurrentValue(true);

        constantService.addConstant("is_vip", vipVar);

        // When
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("is_vip", true);

        Mono<RulesEvaluationResult> result = rulesEvaluationEngine.evaluateRulesReactive(ruleDSL, inputData);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(evalResult -> {
                    return evalResult.isSuccess() &&
                           evalResult.getConditionResult() &&
                           evalResult.getOutputData().get("service").equals("premium") &&
                           evalResult.getOutputData().get("fast_track").equals(true);
                })
                .verifyComplete();
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

        // Other methods not needed for tests
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
        public Mono<ConstantDTO> updateConstant(java.util.UUID id, ConstantDTO constantDTO) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> deleteConstant(java.util.UUID id) {
            return Mono.empty();
        }

        @Override
        public Mono<ConstantDTO> getConstantById(java.util.UUID constantId) {
            return Mono.empty();
        }
    }
}
