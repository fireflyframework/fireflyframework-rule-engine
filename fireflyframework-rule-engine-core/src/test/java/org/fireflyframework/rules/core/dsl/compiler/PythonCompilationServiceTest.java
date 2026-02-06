/*
 * Copyright 2024-2026 Firefly Software Solutions Inc
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

package org.fireflyframework.rules.core.dsl.compiler;

import org.fireflyframework.rules.core.dsl.parser.ASTRulesDSLParser;
import org.fireflyframework.rules.core.dsl.model.ASTRulesDSL;
import org.fireflyframework.rules.core.validation.YamlDslValidator;
import org.fireflyframework.rules.interfaces.dtos.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Test class for PythonCompilationService.
 */
@ExtendWith(MockitoExtension.class)
class PythonCompilationServiceTest {

    @Mock
    private ASTRulesDSLParser astRulesDSLParser;

    @Mock
    private YamlDslValidator yamlDslValidator;

    @Mock
    private PythonCodeGenerator pythonCodeGenerator;

    private PythonCompilationService pythonCompilationService;

    @BeforeEach
    void setUp() {
        pythonCompilationService = new PythonCompilationService(
            astRulesDSLParser, yamlDslValidator, pythonCodeGenerator);
    }

    @Test
    void testSuccessfulCompilation() {
        String yamlDsl = """
            name: "test_rule"
            when: creditScore >= 650
            then:
              - set approved = true
            """;

        // Mock successful validation
        ValidationResult validationResult = ValidationResult.builder()
            .status(ValidationResult.ValidationStatus.VALID)
            .summary(ValidationResult.ValidationSummary.builder()
                .totalIssues(0)
                .criticalErrors(0)
                .errors(0)
                .warnings(0)
                .suggestions(0)
                .qualityScore(100.0)
                .build())
            .build();
        when(yamlDslValidator.validate(any())).thenReturn(validationResult);

        // Mock AST parsing
        ASTRulesDSL mockRule = ASTRulesDSL.builder()
            .name("test_rule")
            .description("Test rule")
            .build();
        when(astRulesDSLParser.parseRules(any())).thenReturn(mockRule);

        // Mock Python code generation
        String expectedPythonCode = "def test_rule(context):\n    return {}";
        when(pythonCodeGenerator.generatePythonFunction(any(), any())).thenReturn(expectedPythonCode);

        PythonCompiledRule result = pythonCompilationService.compileRule(yamlDsl, "test_rule");

        assertNotNull(result);
        assertEquals("test_rule", result.getRuleName());
        assertEquals(expectedPythonCode, result.getPythonCode());
    }

    @Test
    void testCompilationWithValidationError() {
        String yamlDsl = "invalid yaml";

        // Mock validation failure
        ValidationResult validationResult = ValidationResult.builder()
            .status(ValidationResult.ValidationStatus.ERROR)
            .summary(ValidationResult.ValidationSummary.builder()
                .totalIssues(1)
                .criticalErrors(0)
                .errors(1)
                .warnings(0)
                .suggestions(0)
                .qualityScore(0.0)
                .build())
            .build();
        when(yamlDslValidator.validate(any())).thenReturn(validationResult);

        assertThrows(PythonCompilationService.PythonCompilationException.class, () -> {
            pythonCompilationService.compileRule(yamlDsl, "test_rule");
        });
    }

    @Test
    void testCacheOperations() {
        String yamlDsl = """
            name: "cached_rule"
            when: true
            then:
              - set result = true
            """;

        // Mock successful validation and generation
        ValidationResult validationResult = ValidationResult.builder()
            .status(ValidationResult.ValidationStatus.VALID)
            .summary(ValidationResult.ValidationSummary.builder()
                .totalIssues(0)
                .criticalErrors(0)
                .errors(0)
                .warnings(0)
                .suggestions(0)
                .qualityScore(100.0)
                .build())
            .build();
        when(yamlDslValidator.validate(any())).thenReturn(validationResult);

        // Mock AST parsing
        ASTRulesDSL mockRule = ASTRulesDSL.builder()
            .name("cached_rule")
            .description("Cached rule")
            .build();
        when(astRulesDSLParser.parseRules(any())).thenReturn(mockRule);

        when(pythonCodeGenerator.generatePythonFunction(any(), any())).thenReturn("def cached_rule(context): return {}");

        // First compilation should cache the result
        PythonCompiledRule result1 = pythonCompilationService.compileRule(yamlDsl, "cached_rule", true);

        // Check if rule is cached
        assertTrue(pythonCompilationService.isRuleCached(yamlDsl, "cached_rule"));

        // Get cached rule
        PythonCompiledRule cachedRule = pythonCompilationService.getCachedRule(yamlDsl, "cached_rule");
        assertNotNull(cachedRule);
        assertEquals(result1.getRuleName(), cachedRule.getRuleName());

        // Remove from cache
        assertTrue(pythonCompilationService.removeCachedRule(yamlDsl, "cached_rule"));
        assertFalse(pythonCompilationService.isRuleCached(yamlDsl, "cached_rule"));
    }

    @Test
    void testBatchCompilation() {
        Map<String, String> rules = Map.of(
            "rule1", "name: rule1\nwhen: true\nthen:\n  - set result = 1",
            "rule2", "name: rule2\nwhen: false\nthen:\n  - set result = 2"
        );

        // Mock successful validation and generation
        ValidationResult validationResult = ValidationResult.builder()
            .status(ValidationResult.ValidationStatus.VALID)
            .summary(ValidationResult.ValidationSummary.builder()
                .totalIssues(0)
                .criticalErrors(0)
                .errors(0)
                .warnings(0)
                .suggestions(0)
                .qualityScore(100.0)
                .build())
            .build();
        when(yamlDslValidator.validate(any())).thenReturn(validationResult);

        // Mock AST parsing
        ASTRulesDSL mockRule1 = ASTRulesDSL.builder()
            .name("rule1")
            .description("Rule 1")
            .build();
        ASTRulesDSL mockRule2 = ASTRulesDSL.builder()
            .name("rule2")
            .description("Rule 2")
            .build();
        when(astRulesDSLParser.parseRules(any()))
            .thenReturn(mockRule1)
            .thenReturn(mockRule2);

        when(pythonCodeGenerator.generatePythonFunction(any(), any()))
            .thenReturn("def rule1(context): return {}")
            .thenReturn("def rule2(context): return {}");

        Map<String, PythonCompiledRule> results = pythonCompilationService.compileRules(rules, true);

        assertEquals(2, results.size());
        assertTrue(results.containsKey("rule1"));
        assertTrue(results.containsKey("rule2"));
    }

    @Test
    void testCompilationStatistics() {
        Map<String, Object> stats = pythonCompilationService.getCompilationStats();

        assertNotNull(stats);
        assertTrue(stats.containsKey("totalCompilations"));
        assertTrue(stats.containsKey("successfulCompilations"));
        assertTrue(stats.containsKey("failedCompilations"));
        assertTrue(stats.containsKey("cacheHits"));
        assertTrue(stats.containsKey("cacheSize"));
        assertTrue(stats.containsKey("successRate"));
        assertTrue(stats.containsKey("cacheHitRate"));
    }

    @Test
    void testClearCache() {
        // This should not throw any exception
        assertDoesNotThrow(() -> pythonCompilationService.clearCache());
    }
}
