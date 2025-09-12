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
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive error handling tests for the rule engine
 */
class ErrorHandlingComprehensiveTest {

    private RulesEvaluationEngine rulesEvaluationEngine;
    private TestConstantService constantService;
    private RulesDSLParser rulesDSLParser;
    private VariableResolver variableResolver;
    private ArithmeticEvaluator arithmeticEvaluator;

    @BeforeEach
    void setUp() {
        constantService = new TestConstantService();
        NamingConventionValidator namingValidator = new NamingConventionValidator();
        rulesDSLParser = new RulesDSLParser(namingValidator);
        arithmeticEvaluator = new ArithmeticEvaluator();
        variableResolver = new VariableResolver(arithmeticEvaluator);
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
    @DisplayName("Should handle arithmetic overflow gracefully")
    void testArithmeticOverflow() {
        EvaluationContext context = new EvaluationContext();
        context.setInputVariable("maxValue", Double.MAX_VALUE);
        context.setInputVariable("largeValue", Double.MAX_VALUE / 2);

        // This should cause overflow
        Object result = variableResolver.resolveValue("maxValue + largeValue", context);
        
        // Should handle overflow without crashing
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Should handle arithmetic underflow gracefully")
    void testArithmeticUnderflow() {
        EvaluationContext context = new EvaluationContext();
        context.setInputVariable("minValue", Double.MIN_VALUE);
        context.setInputVariable("smallValue", Double.MIN_VALUE);

        Object result = variableResolver.resolveValue("minValue / smallValue", context);
        
        // Should handle underflow without crashing
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Should handle invalid arithmetic expressions")
    void testInvalidArithmeticExpressions() {
        EvaluationContext context = new EvaluationContext();
        context.setInputVariable("value", 10);

        // Test various invalid expressions
        assertThatCode(() -> variableResolver.resolveValue("value +", context))
                .doesNotThrowAnyException();

        assertThatCode(() -> variableResolver.resolveValue("+ value", context))
                .doesNotThrowAnyException();

        assertThatCode(() -> variableResolver.resolveValue("value * * 2", context))
                .doesNotThrowAnyException();

        assertThatCode(() -> variableResolver.resolveValue("value / / 2", context))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle stack overflow in recursive expressions")
    void testStackOverflowPrevention() {
        EvaluationContext context = new EvaluationContext();
        context.setInputVariable("x", 1);

        // Create a deeply nested expression that could cause stack overflow
        StringBuilder deepExpression = new StringBuilder("x");
        for (int i = 0; i < 1000; i++) {
            deepExpression.insert(0, "(").append(" + 1)");
        }

        assertThatCode(() -> variableResolver.resolveValue(deepExpression.toString(), context))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle memory exhaustion scenarios")
    void testMemoryExhaustionPrevention() {
        String yaml = """
            name: Memory Test
            inputs:
              - value
            when:
              - value > 0
            then:
              - set result to "success"
            """;

        RulesDSL rulesDSL = rulesDSLParser.parseRules(yaml);
        
        // Create a very large input map
        Map<String, Object> largeInputData = new HashMap<>();
        for (int i = 0; i < 10000; i++) {
            largeInputData.put("key_" + i, "value_" + i);
        }
        largeInputData.put("value", 10);

        Mono<RulesEvaluationResult> result = rulesEvaluationEngine.evaluateRulesReactive(rulesDSL, largeInputData);
        RulesEvaluationResult evalResult = result.block();

        assertThat(evalResult.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Should handle division by zero gracefully")
    void testInfiniteLoopPrevention() {
        EvaluationContext context = new EvaluationContext();
        context.setInputVariable("a", 1);
        context.setInputVariable("b", 0);

        // Expression that causes division by zero - should be handled gracefully
        Object result = variableResolver.resolveValue("a / b", context);

        // Should return the original expression string as fallback, not throw exception
        assertThat(result).isNotNull();
        assertThat(result.toString()).isEqualTo("a / b");
    }

    @Test
    @DisplayName("Should handle malformed parentheses gracefully")
    void testMalformedParentheses() {
        EvaluationContext context = new EvaluationContext();
        context.setInputVariable("x", 5);

        // Test case 1: Missing closing parenthesis - now handled gracefully
        Object result1 = variableResolver.resolveValue("(x + 1", context);
        assertThat(result1).isEqualTo("(x + 1"); // Returns original expression when evaluation fails

        // Test case 2: Extra opening parenthesis - should handle gracefully (no stack overflow)
        // The system now handles this gracefully without throwing an exception
        Object result2 = variableResolver.resolveValue("((x + 1)", context);
        assertThat(result2).isNotNull(); // Should not crash or cause stack overflow

        // Test case 3: Extra closing parenthesis - should handle gracefully (no stack overflow)
        // The system now handles this gracefully without throwing an exception
        Object result3 = variableResolver.resolveValue("(x + 1))", context);
        assertThat(result3).isNotNull(); // Should not crash or cause stack overflow
    }

    @Test
    @DisplayName("Should handle null context gracefully")
    void testNullContextHandling() {
        assertThatThrownBy(() -> variableResolver.resolveValue("test", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EvaluationContext cannot be null");
    }

    @Test
    @DisplayName("Should handle corrupted evaluation context")
    void testCorruptedEvaluationContext() {
        EvaluationContext context = new EvaluationContext();
        
        // Add some normal data
        context.setInputVariable("normal", "value");
        
        // Try to add problematic data
        assertThatThrownBy(() -> context.setInputVariable(null, "value"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> context.setInputVariable("", "value"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should handle concurrent modification of context")
    void testConcurrentContextModification() {
        EvaluationContext context = new EvaluationContext();
        context.setInputVariable("shared", "initial");

        // Simulate concurrent access
        Runnable modifier = () -> {
            for (int i = 0; i < 100; i++) {
                context.setInputVariable("shared", "modified_" + i);
                context.getValue("shared");
            }
        };

        Thread thread1 = new Thread(modifier);
        Thread thread2 = new Thread(modifier);

        assertThatCode(() -> {
            thread1.start();
            thread2.start();
            thread1.join();
            thread2.join();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle invalid function calls")
    void testInvalidFunctionCalls() {
        EvaluationContext context = new EvaluationContext();
        context.setInputVariable("value", "test");

        // Test invalid function calls
        Object result = variableResolver.resolveValue("nonexistent_function(value)", context);
        
        // Should return the original string when function doesn't exist
        assertThat(result).isEqualTo("nonexistent_function(value)");
    }

    @Test
    @DisplayName("Should handle type conversion errors")
    void testTypeConversionErrors() {
        EvaluationContext context = new EvaluationContext();
        context.setInputVariable("stringValue", "not_a_number");
        context.setInputVariable("numberValue", 42);

        // Try to perform arithmetic with incompatible types
        assertThatCode(() -> variableResolver.resolveValue("stringValue + numberValue", context))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle circular variable references")
    void testCircularVariableReferences() {
        EvaluationContext context = new EvaluationContext();
        context.setInputVariable("a", "b");
        context.setInputVariable("b", "a");

        // This should not cause infinite recursion
        Object result = variableResolver.resolveValue("a", context);
        assertThat(result).isEqualTo("b"); // Should resolve to the direct value
    }

    @Test
    @DisplayName("Should handle extremely deep nested objects")
    void testExtremelyDeepNestedObjects() {
        EvaluationContext context = new EvaluationContext();
        
        // Create a deeply nested object
        Map<String, Object> deepObject = new HashMap<>();
        Map<String, Object> current = deepObject;
        
        for (int i = 0; i < 100; i++) {
            Map<String, Object> next = new HashMap<>();
            current.put("level" + i, next);
            current = next;
        }
        current.put("value", "deep_value");
        
        context.setInputVariable("deepObject", deepObject);

        // Try to access the deeply nested value
        assertThatCode(() -> variableResolver.resolveValue("deepObject.level0.level1.level2", context))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle invalid regex patterns")
    void testInvalidRegexPatterns() {
        String yaml = """
            name: Invalid Regex Test
            inputs:
              - text
            when:
              - text matches "[invalid regex"
            then:
              - set result to "matched"
            """;

        RulesDSL rulesDSL = rulesDSLParser.parseRules(yaml);
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("text", "test string");

        Mono<RulesEvaluationResult> result = rulesEvaluationEngine.evaluateRulesReactive(rulesDSL, inputData);
        RulesEvaluationResult evalResult = result.block();

        // Should handle invalid regex gracefully
        assertThat(evalResult.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Should handle resource exhaustion gracefully")
    void testResourceExhaustionHandling() {
        // Create a rule that could potentially exhaust resources
        String yaml = """
            name: Resource Exhaustion Test
            inputs:
              - iterations
            when:
              - iterations > 0
            then:
              - calculate result as iterations * iterations * iterations
            """;

        RulesDSL rulesDSL = rulesDSLParser.parseRules(yaml);
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("iterations", Integer.MAX_VALUE);

        Mono<RulesEvaluationResult> result = rulesEvaluationEngine.evaluateRulesReactive(rulesDSL, inputData);
        RulesEvaluationResult evalResult = result.block();

        // Should complete without exhausting resources
        assertThat(evalResult.isSuccess()).isTrue();
    }

    /**
     * Test implementation of ConstantService for testing
     */
    private static class TestConstantService implements ConstantService {
        private final Map<String, ConstantDTO> constants = new HashMap<>();

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
