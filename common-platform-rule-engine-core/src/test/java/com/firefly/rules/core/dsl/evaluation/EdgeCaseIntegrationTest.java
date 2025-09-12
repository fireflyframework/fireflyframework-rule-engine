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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive edge case and boundary condition tests for the entire rule engine system
 */
class EdgeCaseIntegrationTest {

    private RulesEvaluationEngine rulesEvaluationEngine;
    private TestConstantService constantService;
    private RulesDSLParser rulesDSLParser;

    @BeforeEach
    void setUp() {
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
    @DisplayName("Should handle extreme numeric values")
    void testExtremeNumericValues() {
        String yaml = """
            name: Extreme Numbers Test
            inputs:
              - largeNumber
              - smallNumber
              - negativeNumber
            when:
              - largeNumber > 0
            then:
              - calculate result as largeNumber + smallNumber + negativeNumber
            """;

        RulesDSL rulesDSL = rulesDSLParser.parseRules(yaml);
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("largeNumber", Double.MAX_VALUE);
        inputData.put("smallNumber", Double.MIN_VALUE);
        inputData.put("negativeNumber", -Double.MAX_VALUE);

        Mono<RulesEvaluationResult> result = rulesEvaluationEngine.evaluateRulesReactive(rulesDSL, inputData);
        RulesEvaluationResult evalResult = result.block();

        assertThat(evalResult.isSuccess()).isTrue();
        assertThat(evalResult.getConditionResult()).isTrue();
    }

    @Test
    @DisplayName("Should handle division by zero gracefully")
    void testDivisionByZero() {
        String yaml = """
            name: Division by Zero Test
            inputs:
              - numerator
              - denominator
            when:
              - numerator > 0
            then:
              - calculate result as numerator / denominator
            """;

        RulesDSL rulesDSL = rulesDSLParser.parseRules(yaml);
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("numerator", 10);
        inputData.put("denominator", 0);

        Mono<RulesEvaluationResult> result = rulesEvaluationEngine.evaluateRulesReactive(rulesDSL, inputData);
        RulesEvaluationResult evalResult = result.block();

        // Should handle the error gracefully
        assertThat(evalResult.isSuccess()).isTrue();
        // The calculation should fail but not crash the entire evaluation
    }

    @Test
    @DisplayName("Should handle null and undefined variables")
    void testNullAndUndefinedVariables() {
        String yaml = """
            name: Null Variables Test
            inputs:
              - definedVar
              - nullVar
            when:
              - definedVar is_not_null
              - undefinedVar is_null
            then:
              - set result to "handled_nulls"
            else:
              - set result to "null_check_failed"
            """;

        RulesDSL rulesDSL = rulesDSLParser.parseRules(yaml);
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("definedVar", "value");
        inputData.put("emptyVar", "");
        // undefinedVar is intentionally not provided

        Mono<RulesEvaluationResult> result = rulesEvaluationEngine.evaluateRulesReactive(rulesDSL, inputData);
        RulesEvaluationResult evalResult = result.block();

        assertThat(evalResult.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Should handle very long strings")
    void testVeryLongStrings() {
        String longString = "A".repeat(10000);
        String yaml = """
            name: Long String Test
            inputs:
              - longString
            when:
              - longString contains "A"
            then:
              - set result to "found_A"
            """;

        RulesDSL rulesDSL = rulesDSLParser.parseRules(yaml);
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("longString", longString);

        Mono<RulesEvaluationResult> result = rulesEvaluationEngine.evaluateRulesReactive(rulesDSL, inputData);
        RulesEvaluationResult evalResult = result.block();

        assertThat(evalResult.isSuccess()).isTrue();
        assertThat(evalResult.getConditionResult()).isTrue();
        assertThat(evalResult.getOutputData().get("result")).isEqualTo("found_A");
    }

    @Test
    @DisplayName("Should handle complex nested data structures")
    void testComplexNestedDataStructures() {
        String yaml = """
            name: Nested Data Test
            inputs:
              - customer
            when:
              - customer.profile.age >= 18
            then:
              - set eligible to true
            """;

        RulesDSL rulesDSL = rulesDSLParser.parseRules(yaml);
        
        Map<String, Object> profile = new HashMap<>();
        profile.put("age", 25);
        profile.put("name", "John Doe");
        
        Map<String, Object> customer = new HashMap<>();
        customer.put("profile", profile);
        customer.put("id", "12345");
        
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("customer", customer);

        Mono<RulesEvaluationResult> result = rulesEvaluationEngine.evaluateRulesReactive(rulesDSL, inputData);
        RulesEvaluationResult evalResult = result.block();

        assertThat(evalResult.isSuccess()).isTrue();
        assertThat(evalResult.getConditionResult()).isTrue();
        assertThat(evalResult.getOutputData().get("eligible")).isEqualTo(true);
    }

    @Test
    @DisplayName("Should handle circular references safely")
    void testCircularReferences() {
        String yaml = """
            name: Circular Reference Test
            inputs:
              - value
            when:
              - value > 0
            then:
              - calculate result as value + result
            """;

        RulesDSL rulesDSL = rulesDSLParser.parseRules(yaml);
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("value", 10);

        Mono<RulesEvaluationResult> result = rulesEvaluationEngine.evaluateRulesReactive(rulesDSL, inputData);
        RulesEvaluationResult evalResult = result.block();

        // Should handle circular reference gracefully
        assertThat(evalResult.isSuccess()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Hello 世界", "🚀🌍💫", "Ñoño", "Москва", "العربية", "हिन्दी"
    })
    @DisplayName("Should handle unicode and special characters")
    void testUnicodeAndSpecialCharacters(String unicodeString) {
        String yaml = """
            name: Unicode Test
            inputs:
              - unicodeValue
            when:
              - unicodeValue is_not_null
            then:
              - set result to unicodeValue
            """;

        RulesDSL rulesDSL = rulesDSLParser.parseRules(yaml);
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("unicodeValue", unicodeString);

        Mono<RulesEvaluationResult> result = rulesEvaluationEngine.evaluateRulesReactive(rulesDSL, inputData);
        RulesEvaluationResult evalResult = result.block();

        assertThat(evalResult.isSuccess()).isTrue();
        assertThat(evalResult.getConditionResult()).isTrue();
        assertThat(evalResult.getOutputData().get("result")).isEqualTo(unicodeString);
    }

    @Test
    @DisplayName("Should handle deeply nested arithmetic expressions")
    void testDeeplyNestedArithmetic() {
        String yaml = """
            name: Deep Arithmetic Test
            inputs:
              - a
              - b
              - c
              - d
            when:
              - a > 0
            then:
              - calculate result as ((a + b) * (c - d)) / ((a * b) + (c / d))
            """;

        RulesDSL rulesDSL = rulesDSLParser.parseRules(yaml);
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("a", 10.0);
        inputData.put("b", 5.0);
        inputData.put("c", 20.0);
        inputData.put("d", 2.0);

        Mono<RulesEvaluationResult> result = rulesEvaluationEngine.evaluateRulesReactive(rulesDSL, inputData);
        RulesEvaluationResult evalResult = result.block();

        assertThat(evalResult.isSuccess()).isTrue();
        assertThat(evalResult.getConditionResult()).isTrue();
        assertThat(evalResult.getOutputData()).containsKey("result");
    }

    @Test
    @DisplayName("Should handle mixed data types in comparisons")
    void testMixedDataTypeComparisons() {
        String yaml = """
            name: Mixed Types Test
            inputs:
              - stringNumber
              - actualNumber
              - booleanValue
            when:
              - stringNumber equals "42"
              - actualNumber equals 42
              - booleanValue equals true
            then:
              - set result to "types_matched"
            """;

        RulesDSL rulesDSL = rulesDSLParser.parseRules(yaml);
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("stringNumber", "42");
        inputData.put("actualNumber", 42);
        inputData.put("booleanValue", true);

        Mono<RulesEvaluationResult> result = rulesEvaluationEngine.evaluateRulesReactive(rulesDSL, inputData);
        RulesEvaluationResult evalResult = result.block();

        assertThat(evalResult.isSuccess()).isTrue();
        assertThat(evalResult.getConditionResult()).isTrue();
        assertThat(evalResult.getOutputData().get("result")).isEqualTo("types_matched");
    }

    @Test
    @DisplayName("Should handle empty collections and arrays")
    void testEmptyCollections() {
        String yaml = """
            name: Empty Collections Test
            inputs:
              - emptyList
              - emptyMap
            when:
              - emptyList is_not_null
            then:
              - set result to "empty_collections_handled"
            """;

        RulesDSL rulesDSL = rulesDSLParser.parseRules(yaml);
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("emptyList", List.of());
        inputData.put("emptyMap", Map.of());

        Mono<RulesEvaluationResult> result = rulesEvaluationEngine.evaluateRulesReactive(rulesDSL, inputData);
        RulesEvaluationResult evalResult = result.block();

        assertThat(evalResult.isSuccess()).isTrue();
        assertThat(evalResult.getConditionResult()).isTrue();
        assertThat(evalResult.getOutputData().get("result")).isEqualTo("empty_collections_handled");
    }

    @Test
    @DisplayName("Should handle concurrent rule evaluations")
    void testConcurrentEvaluations() {
        String yaml = """
            name: Concurrent Test
            inputs:
              - value
            when:
              - value > 0
            then:
              - calculate result as value * 2
            """;

        RulesDSL rulesDSL = rulesDSLParser.parseRules(yaml);

        // Run multiple evaluations concurrently
        List<Mono<RulesEvaluationResult>> evaluations = List.of(
            createEvaluation(rulesDSL, 1),
            createEvaluation(rulesDSL, 2),
            createEvaluation(rulesDSL, 3),
            createEvaluation(rulesDSL, 4),
            createEvaluation(rulesDSL, 5)
        );

        List<RulesEvaluationResult> results = Mono.zip(evaluations, objects -> {
            RulesEvaluationResult[] resultArray = new RulesEvaluationResult[objects.length];
            for (int i = 0; i < objects.length; i++) {
                resultArray[i] = (RulesEvaluationResult) objects[i];
            }
            return List.of(resultArray);
        }).block();

        assertThat(results).hasSize(5);
        for (RulesEvaluationResult result : results) {
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getConditionResult()).isTrue();
        }
    }

    private Mono<RulesEvaluationResult> createEvaluation(RulesDSL rulesDSL, int value) {
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("value", value);
        return rulesEvaluationEngine.evaluateRulesReactive(rulesDSL, inputData);
    }

    @Test
    @DisplayName("Should handle malformed input data gracefully")
    void testMalformedInputData() {
        String yaml = """
            name: Malformed Input Test
            inputs:
              - validInput
              - invalidInput
            when:
              - validInput > 0
            then:
              - set result to "success"
            """;

        RulesDSL rulesDSL = rulesDSLParser.parseRules(yaml);
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("validInput", 10);
        inputData.put("invalidInput", new Object() {
            @Override
            public String toString() {
                throw new RuntimeException("Malformed object");
            }
        });

        Mono<RulesEvaluationResult> result = rulesEvaluationEngine.evaluateRulesReactive(rulesDSL, inputData);
        RulesEvaluationResult evalResult = result.block();

        // Should handle malformed data gracefully
        assertThat(evalResult.isSuccess()).isTrue();
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
            if (constantDTO.getId() == null) {
                constantDTO.setId(UUID.randomUUID());
            }
            constants.put(constantDTO.getCode(), constantDTO);
            return Mono.just(constantDTO);
        }

        @Override
        public Mono<ConstantDTO> updateConstant(UUID constantId, ConstantDTO constantDTO) {
            constantDTO.setId(constantId);
            constants.put(constantDTO.getCode(), constantDTO);
            return Mono.just(constantDTO);
        }

        @Override
        public Mono<Void> deleteConstant(UUID constantId) {
            constants.entrySet().removeIf(entry -> entry.getValue().getId().equals(constantId));
            return Mono.empty();
        }

        @Override
        public Mono<ConstantDTO> getConstantById(UUID constantId) {
            return constants.values().stream()
                    .filter(c -> c.getId().equals(constantId))
                    .findFirst()
                    .map(Mono::just)
                    .orElse(Mono.empty());
        }
    }
}
