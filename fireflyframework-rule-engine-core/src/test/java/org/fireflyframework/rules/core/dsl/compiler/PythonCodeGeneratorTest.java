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

import org.fireflyframework.rules.core.dsl.model.ASTRulesDSL;
import org.fireflyframework.rules.core.dsl.parser.ASTRulesDSLParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for PythonCodeGenerator.
 *
 * Tests the compilation of various DSL constructs to Python code.
 */
@ExtendWith(MockitoExtension.class)
class PythonCodeGeneratorTest {

    private PythonCodeGenerator pythonCodeGenerator;
    private ASTRulesDSLParser astRulesDSLParser;

    @BeforeEach
    void setUp() {
        pythonCodeGenerator = new PythonCodeGenerator();
        astRulesDSLParser = new ASTRulesDSLParser(new org.fireflyframework.rules.core.dsl.parser.DSLParser());
    }

    @Test
    void testSimpleRuleCompilation() {
        String yamlDsl = """
            name: "simple_credit_check"
            description: "Simple credit score validation"
            version: "1.0.0"

            input:
              creditScore: "number"

            output:
              approved: "boolean"

            when: creditScore >= 650
            then:
              - set approved to true
            else:
              - set approved to false
            """;

        ASTRulesDSL rule = astRulesDSLParser.parseRules(yamlDsl);
        String pythonCode = pythonCodeGenerator.generatePythonFunction(rule);

        assertNotNull(pythonCode);
        assertTrue(pythonCode.contains("def simple_credit_check(context):"));
        assertTrue(pythonCode.contains("from firefly_runtime import *"));
        assertTrue(pythonCode.contains("creditScore"));
        assertTrue(pythonCode.contains("approved"));
        assertTrue(pythonCode.contains("return {"));
    }

    @Test
    void testComplexRuleCompilation() {
        String yamlDsl = """
            name: "loan_approval"
            description: "Complex loan approval logic"
            version: "1.0.0"

            input:
              creditScore: "number"
              income: "number"
              debtToIncomeRatio: "number"

            output:
              approved: "boolean"
              loanAmount: "number"
              interestRate: "number"

            rules:
              - name: "high_credit_score"
                when: creditScore >= 750
                then:
                  - set approved to true
                  - set loanAmount to income * 5
                  - set interestRate to 3.5

              - name: "medium_credit_score"
                when: creditScore >= 650 and creditScore < 750
                then:
                  - set approved to true
                  - set loanAmount to income * 3
                  - set interestRate to 4.5

              - name: "low_credit_score"
                when: creditScore < 650
                then:
                  - set approved to false
                  - set loanAmount to 0
                  - set interestRate to 0
            """;

        ASTRulesDSL rule = astRulesDSLParser.parseRules(yamlDsl);
        String pythonCode = pythonCodeGenerator.generatePythonFunction(rule);

        assertNotNull(pythonCode);
        assertTrue(pythonCode.contains("def loan_approval(context):"));
        assertTrue(pythonCode.contains("high_credit_score"));
        assertTrue(pythonCode.contains("medium_credit_score"));
        assertTrue(pythonCode.contains("low_credit_score"));
        assertTrue(pythonCode.contains("creditScore', 0) >= 750"));
        assertTrue(pythonCode.contains("income', 0) * 5"));
    }

    @Test
    void testFunctionNameSanitization() {
        assertEquals("test_rule", pythonCodeGenerator.sanitizeFunctionName("Test Rule"));
        assertEquals("test_rule_123", pythonCodeGenerator.sanitizeFunctionName("Test-Rule-123"));
        assertEquals("_123_test", pythonCodeGenerator.sanitizeFunctionName("123-Test"));
        assertEquals("test_rule", pythonCodeGenerator.sanitizeFunctionName("test__rule"));
        assertEquals("unnamed_rule", pythonCodeGenerator.sanitizeFunctionName(null));
    }

    @Test
    void testInputOutputVariableExtraction() {
        String yamlDsl = """
            name: "test_rule"
            input:
              var1: "number"
              var2: "string"
            output:
              result1: "boolean"
              result2: "number"
            when: var1 > 0
            then:
              - set result1 to true
            """;

        ASTRulesDSL rule = astRulesDSLParser.parseRules(yamlDsl);

        var inputVars = pythonCodeGenerator.extractInputVariables(rule);
        var outputVars = pythonCodeGenerator.extractOutputVariables(rule);

        assertEquals(2, inputVars.size());
        assertTrue(inputVars.contains("var1"));
        assertTrue(inputVars.contains("var2"));

        assertEquals(2, outputVars.size());
        assertTrue(outputVars.containsKey("result1"));
        assertTrue(outputVars.containsKey("result2"));
    }
}
