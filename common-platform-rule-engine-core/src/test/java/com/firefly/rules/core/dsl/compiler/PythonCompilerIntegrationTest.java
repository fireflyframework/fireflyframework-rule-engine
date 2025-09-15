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

package com.firefly.rules.core.dsl.compiler;

import com.firefly.rules.core.dsl.model.ASTRulesDSL;
import com.firefly.rules.core.dsl.parser.ASTRulesDSLParser;
import com.firefly.rules.core.dsl.parser.DSLParser;
import com.firefly.rules.core.services.ConstantService;
import com.firefly.rules.core.validation.YamlDslValidator;
import com.firefly.rules.interfaces.dtos.crud.ConstantDTO;
import com.firefly.rules.interfaces.dtos.validation.ValidationResult;
import com.firefly.rules.interfaces.enums.ValueType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Integration test for the complete Python compilation pipeline.
 * Tests the end-to-end process from YAML DSL to executable Python code.
 */
@ExtendWith(MockitoExtension.class)
class PythonCompilerIntegrationTest {

    private PythonCodeGenerator pythonCodeGenerator;
    private PythonCompilationService pythonCompilationService;
    private ASTRulesDSLParser astRulesDSLParser;

    @Mock
    private YamlDslValidator yamlDslValidator;

    @Mock
    private ConstantService constantService;

    @BeforeEach
    void setUp() {
        pythonCodeGenerator = new PythonCodeGenerator();
        // Inject the mock ConstantService into PythonCodeGenerator
        pythonCodeGenerator.setConstantService(constantService);

        astRulesDSLParser = new ASTRulesDSLParser(new DSLParser());
        pythonCompilationService = new PythonCompilationService(
            astRulesDSLParser, yamlDslValidator, pythonCodeGenerator);

        // Mock validator to always return valid
        ValidationResult validResult = ValidationResult.builder()
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
        when(yamlDslValidator.validate(any(String.class))).thenReturn(validResult);
    }

    @Test
    void testSimpleRuleCompilation() {
        String yamlDsl = """
                name: "Simple Test Rule"
                description: "A simple test rule"
                version: "1.0"
                
                inputs:
                  score: "number"
                  
                outputs:
                  result: "string"
                
                when:
                  - score > 80
                then:
                  - set result to "passed"
                else:
                  - set result to "failed"
                """;

        PythonCompiledRule compiledRule = pythonCompilationService.compileRule(yamlDsl, "simple_test");

        assertNotNull(compiledRule);
        assertEquals("simple_test", compiledRule.getRuleName());
        assertEquals("A simple test rule", compiledRule.getDescription());
        assertEquals("1.0", compiledRule.getVersion());
        assertNotNull(compiledRule.getPythonCode());
        assertNotNull(compiledRule.getFunctionName());
        assertNotNull(compiledRule.getCompiledAt());

        String pythonCode = compiledRule.getPythonCode();

        // Debug: Print the generated Python code
        System.out.println("=== GENERATED PYTHON CODE ===");
        System.out.println(pythonCode);
        System.out.println("=== END PYTHON CODE ===");

        // Verify the generated Python code contains expected elements
        assertTrue(pythonCode.contains("from firefly_runtime import *"));
        assertTrue(pythonCode.contains("def simple_test(context):"));
        assertTrue(pythonCode.contains("context.get('score', 0) > 80"));
        assertTrue(pythonCode.contains("context['result'] = \"passed\""));
        assertTrue(pythonCode.contains("context['result'] = \"failed\""));
        assertTrue(pythonCode.contains("return {"));
        assertTrue(pythonCode.contains("'result': context.get('result')"));
    }

    @Test
    void testComplexRuleWithCalculations() {
        String yamlDsl = """
                name: "Credit Scoring Rule"
                description: "Calculate credit score and approval"
                version: "2.0"
                
                inputs:
                  income: "number"
                  debt: "number"
                  age: "number"
                  
                outputs:
                  creditScore: "number"
                  approved: "boolean"
                  reason: "string"
                
                rules:
                  - when:
                      - income > 50000
                      - debt < (income * 0.3)
                      - age >= 21
                    then:
                      - calculate creditScore as (income / 1000) + (100 - (debt / income * 100))
                      - set approved to true
                      - set reason to "Approved based on income and debt ratio"
                    else:
                      - set creditScore to 300
                      - set approved to false
                      - set reason to "Denied - insufficient criteria"
                """;

        PythonCompiledRule compiledRule = pythonCompilationService.compileRule(yamlDsl, "credit_scoring");

        assertNotNull(compiledRule);
        assertEquals("credit_scoring", compiledRule.getRuleName());
        assertEquals("Calculate credit score and approval", compiledRule.getDescription());
        assertEquals("2.0", compiledRule.getVersion());

        String pythonCode = compiledRule.getPythonCode();

        // Verify complex calculations are properly generated
        assertTrue(pythonCode.contains("context.get('income', 0) > 50000"));
        assertTrue(pythonCode.contains("context.get('debt', 0) < (context.get('income', 0) * 0.3)"));
        assertTrue(pythonCode.contains("context.get('age', 0) >= 21"));
        assertTrue(pythonCode.contains("(context.get('income', 0) / 1000)"));
        assertTrue(pythonCode.contains("context['approved'] = True"));
        assertTrue(pythonCode.contains("context['approved'] = False"));
        
        // Verify input and output variables are extracted
        assertTrue(compiledRule.getInputVariables().contains("income"));
        assertTrue(compiledRule.getInputVariables().contains("debt"));
        assertTrue(compiledRule.getInputVariables().contains("age"));
        
        assertTrue(compiledRule.getOutputVariables().containsKey("creditScore"));
        assertTrue(compiledRule.getOutputVariables().containsKey("approved"));
        assertTrue(compiledRule.getOutputVariables().containsKey("reason"));
    }

    @Test
    void testFunctionNameSanitization() {
        String yamlDsl = """
                name: "Test Rule with Special-Characters & Spaces!"
                
                when:
                  - true
                then:
                  - set result to "ok"
                """;

        PythonCompiledRule compiledRule = pythonCompilationService.compileRule(yamlDsl, "test-rule-123");

        assertNotNull(compiledRule.getFunctionName());
        // Function name should be sanitized for Python
        assertTrue(compiledRule.getFunctionName().matches("[a-zA-Z_][a-zA-Z0-9_]*"));
        assertFalse(compiledRule.getFunctionName().contains("-"));
        assertFalse(compiledRule.getFunctionName().contains(" "));
        assertFalse(compiledRule.getFunctionName().contains("!"));
    }

    @Test
    void testCacheHitAndMiss() {
        String yamlDsl = """
                name: "Cache Test Rule"
                
                when:
                  - true
                then:
                  - set result to "cached"
                """;

        // First compilation - cache miss
        PythonCompiledRule firstCompilation = pythonCompilationService.compileRule(yamlDsl, "cache_test", true);
        assertNotNull(firstCompilation);

        // Second compilation - should hit cache
        PythonCompiledRule secondCompilation = pythonCompilationService.compileRule(yamlDsl, "cache_test", true);
        assertNotNull(secondCompilation);

        // Should be the same instance from cache
        assertEquals(firstCompilation.getSourceHash(), secondCompilation.getSourceHash());
        assertEquals(firstCompilation.getPythonCode(), secondCompilation.getPythonCode());
    }

    @Test
    void testBatchCompilation() {
        String rule1 = """
                name: "Rule 1"
                when:
                  - value > 10
                then:
                  - set result to "high"
                """;

        String rule2 = """
                name: "Rule 2"
                when:
                  - value <= 10
                then:
                  - set result to "low"
                """;

        Map<String, String> rules = Map.of(
            "rule1", rule1,
            "rule2", rule2
        );

        Map<String, PythonCompiledRule> compiledRules = pythonCompilationService.compileRules(rules, false);

        assertEquals(2, compiledRules.size());
        assertTrue(compiledRules.containsKey("rule1"));
        assertTrue(compiledRules.containsKey("rule2"));

        PythonCompiledRule compiledRule1 = compiledRules.get("rule1");
        PythonCompiledRule compiledRule2 = compiledRules.get("rule2");

        assertNotNull(compiledRule1);
        assertNotNull(compiledRule2);
        assertEquals("Rule 1", compiledRule1.getDescription());
        assertEquals("Rule 2", compiledRule2.getDescription());
    }

    @Test
    @DisplayName("Should compile complex B2B Credit Scoring rule with constants")
    void testComplexB2BCreditScoringCompilation() {
        String yamlDsl = getB2BCreditScoringRule();

        PythonCompiledRule result = pythonCompilationService.compileRule(yamlDsl, "b2b_credit_scoring");

        assertNotNull(result);
        assertEquals("b2b_credit_scoring", result.getRuleName());

        // Verify the Python code contains expected elements
        String pythonCode = result.getPythonCode();

        // Check copyright and license headers
        assertTrue(pythonCode.contains("Copyright 2025 Firefly Software Solutions Inc"));
        assertTrue(pythonCode.contains("Licensed under the Apache License, Version 2.0"));
        assertTrue(pythonCode.contains("Made with ❤️"));

        // Check constants initialization
        assertTrue(pythonCode.contains("Initialize constants from database"));
        assertTrue(pythonCode.contains("MIN_BUSINESS_CREDIT_SCORE"));
        assertTrue(pythonCode.contains("EXCELLENT_CREDIT_THRESHOLD"));
        assertTrue(pythonCode.contains("MAX_DEBT_TO_INCOME_RATIO"));

        // Check interactive main section
        assertTrue(pythonCode.contains("if __name__ == \"__main__\":"));
        assertTrue(pythonCode.contains("print_firefly_header"));
        assertTrue(pythonCode.contains("constants_need_config"));

        // Check function definition
        assertTrue(pythonCode.contains("def b2b_credit_scoring(context):"));

        // Check complex rule logic
        assertTrue(pythonCode.contains("data_validation_complete"));

        // Print the generated code for inspection
        System.out.println("=== GENERATED B2B CREDIT SCORING PYTHON CODE ===");
        System.out.println(pythonCode);
        System.out.println("=== END GENERATED CODE ===");
    }

    private String getB2BCreditScoringRule() {
        return """
            name: "B2B Credit Scoring Platform"
            description: "Comprehensive business credit assessment using multiple data sources"
            version: "1.0.0"

            # Input variables from loan application API and external data sources
            inputs:
              # Business identification and basic info
              businessId: text
              businessName: text
              taxId: text
              businessType: text
              industryCode: text
              yearsInBusiness: number
              numberOfEmployees: number

              # Loan application details
              requestedAmount: number
              loanPurpose: text
              requestedTerm: number
              hasCollateral: boolean
              collateralValue: number

              # Financial information from application
              annualRevenue: number
              monthlyRevenue: number
              monthlyExpenses: number
              existingDebt: number
              monthlyDebtPayments: number

              # Business owner information
              ownerCreditScore: number
              ownerYearsExperience: number
              ownershipPercentage: number

              # Credit bureau data
              businessCreditScore: number
              paymentHistoryScore: number
              creditUtilization: number
              publicRecordsCount: number
              tradelineCount: number

            # System constants for business rules
            constants:
              # Credit score thresholds
              - code: MIN_BUSINESS_CREDIT_SCORE
                defaultValue: 650
              - code: EXCELLENT_CREDIT_THRESHOLD
                defaultValue: 750

              # Financial ratio limits
              - code: MAX_DEBT_TO_INCOME_RATIO
                defaultValue: 0.4
              - code: MIN_DEBT_SERVICE_COVERAGE
                defaultValue: 1.25
              - code: MAX_LOAN_TO_VALUE_RATIO
                defaultValue: 0.8

              # Business criteria
              - code: MIN_YEARS_IN_BUSINESS
                defaultValue: 2
              - code: MIN_ANNUAL_REVENUE
                defaultValue: 100000

            # Multi-stage evaluation using sequential rules
            rules:
              # Stage 1: Data Validation and Preparation
              - name: "Data Validation and Preparation"
                when:
                  - businessId is_not_empty
                  - requestedAmount is_positive
                  - annualRevenue is_positive
                  - businessCreditScore is_credit_score
                  - ownerCreditScore is_credit_score
                then:
                  # Validate all required financial data is present and valid
                  - set monthly_revenue_valid to (monthlyRevenue is_positive)
                  - set monthly_expenses_valid to (monthlyExpenses is_positive)
                  - set existing_debt_valid to (existingDebt is_not_null)
                  - set has_complete_financial_data to (monthly_revenue_valid AND monthly_expenses_valid AND existing_debt_valid)

                  # Calculate data quality indicators
                  - calculate debt_to_income_ratio as monthlyDebtPayments / monthlyRevenue
                  - set data_validation_complete to has_complete_financial_data

                  - if data_validation_complete then set validation_status to "PASSED"
                  - if NOT data_validation_complete then set validation_status to "FAILED"
                else:
                  - set data_validation_complete to false
                  - set validation_status to "FAILED"

            # Define all output variables that will be returned
            output:
              # Primary decision outputs
              validation_status: text
              data_validation_complete: boolean
              debt_to_income_ratio: number
              has_complete_financial_data: boolean
            """;
    }

    @Test
    @DisplayName("Should handle constants from database vs default values correctly")
    void testConstantsFromDatabaseVsDefaultValues() {
        // Setup mock constants from database
        ConstantDTO existingConstant = ConstantDTO.builder()
            .code("EXISTING_CONSTANT")
            .currentValue(999)  // This should override the default value of 100
            .valueType(ValueType.NUMBER)
            .build();

        // Mock the ConstantService to return only the existing constant
        when(constantService.getConstantsByCodes(anyList()))
            .thenReturn(Flux.just(existingConstant));

        String yamlDsl = getConstantsTestRule();

        PythonCompiledRule result = pythonCompilationService.compileRule(yamlDsl, "constants_test");

        assertNotNull(result);
        assertEquals("constants_test", result.getRuleName());

        String pythonCode = result.getPythonCode();

        // Check that constants are properly initialized with correct comments
        assertTrue(pythonCode.contains("Initialize constants from database"));

        // Constant that exists in database should override default value
        assertTrue(pythonCode.contains("constants['EXISTING_CONSTANT'] = 999"));
        assertTrue(pythonCode.contains("# From database"));

        // Constant with default value but not in database should use default
        assertTrue(pythonCode.contains("constants['DEFAULT_ONLY_CONSTANT'] = 500"));
        assertTrue(pythonCode.contains("# Default value"));

        // Constant without default and not in database should be None with warning
        assertTrue(pythonCode.contains("constants['MISSING_CONSTANT'] = None"));
        assertTrue(pythonCode.contains("# WARNING: Constant not found in database and no default value provided"));

        // Print the generated code for inspection
        System.out.println("=== CONSTANTS TEST PYTHON CODE ===");
        System.out.println(pythonCode);
        System.out.println("=== END CONSTANTS TEST CODE ===");
    }

    private String getConstantsTestRule() {
        return """
            name: "Constants Test Rule"
            description: "Test rule to demonstrate constants handling from database vs defaults"
            version: "1.0.0"

            inputs:
              score: number
              amount: number

            constants:
              # This constant exists in database and should override default value
              - code: EXISTING_CONSTANT
                defaultValue: 100

              # This constant has default but doesn't exist in database
              - code: DEFAULT_ONLY_CONSTANT
                defaultValue: 500

              # This constant has no default and doesn't exist in database
              - code: MISSING_CONSTANT

            rules:
              - name: "Test Constants Usage"
                when:
                  - score is_positive
                then:
                  - if score greater_than EXISTING_CONSTANT then set result to "high"
                  - if amount greater_than DEFAULT_ONLY_CONSTANT then set approved to true
                  - if MISSING_CONSTANT is_not_null then set has_config to true
                  - if MISSING_CONSTANT is_null then set has_config to false

            output:
              result: text
              approved: boolean
              has_config: boolean
            """;
    }
}
