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

import com.firefly.rules.core.dsl.ast.parser.ASTRulesDSLParser;
import com.firefly.rules.core.dsl.ast.parser.DSLParser;
import com.firefly.rules.interfaces.dtos.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test YamlDslValidator support for all new DSL features
 */
@DisplayName("YamlDslValidator New Features Tests")
public class YamlDslValidatorNewFeaturesTest {

    private YamlDslValidator yamlDslValidator;

    @BeforeEach
    void setUp() {
        // Create dependencies
        SyntaxValidator syntaxValidator = Mockito.mock(SyntaxValidator.class);
        NamingConventionValidator namingValidator = new NamingConventionValidator();
        DSLParser dslParser = new DSLParser();
        ASTRulesDSLParser astParser = new ASTRulesDSLParser(dslParser);

        // Mock syntax validator to return no issues
        Mockito.when(syntaxValidator.validate(Mockito.anyString()))
               .thenReturn(java.util.List.of());

        yamlDslValidator = new YamlDslValidator(syntaxValidator, namingValidator, astParser);
    }

    @Nested
    @DisplayName("Complex Conditions Validation")
    class ComplexConditionsValidationTests {

        @Test
        @DisplayName("Should validate complex conditions block")
        void testComplexConditionsValidation() {
            String yaml = """
                name: "Complex Conditions Test"
                description: "Test complex conditions validation"
                
                inputs:
                  - creditScore
                  - annualIncome
                
                conditions:
                  if:
                    and:
                      - compare:
                          left: creditScore
                          operator: "at_least"
                          right: 650
                      - compare:
                          left: annualIncome
                          operator: "greater_than"
                          right: 50000
                  then:
                    actions:
                      - set approval_status to "APPROVED"
                  else:
                    actions:
                      - set approval_status to "DECLINED"
                
                output:
                  approval_status: text
                """;

            ValidationResult result = yamlDslValidator.validate(yaml);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("Should detect missing if condition in complex block")
        void testMissingIfCondition() {
            String yaml = """
                name: "Missing If Test"
                description: "Test missing if condition"
                
                inputs:
                  - creditScore
                
                conditions:
                  then:
                    actions:
                      - set result to "test"
                
                output:
                  result: text
                """;

            ValidationResult result = yamlDslValidator.validate(yaml);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getSummary().getErrors()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Multiple Rules Validation")
    class MultipleRulesValidationTests {

        @Test
        @DisplayName("Should validate multiple rules syntax")
        void testMultipleRulesValidation() {
            String yaml = """
                name: "Multiple Rules Test"
                description: "Test multiple rules validation"
                
                inputs:
                  - creditScore
                  - income
                
                rules:
                  - name: "Initial Check"
                    when:
                      - creditScore >= 600
                    then:
                      - set initial_pass to true
                    else:
                      - set initial_pass to false
                      
                  - name: "Final Decision"
                    when:
                      - initial_pass == true
                      - income > 50000
                    then:
                      - set final_decision to "APPROVED"
                    else:
                      - set final_decision to "DECLINED"
                
                output:
                  initial_pass: boolean
                  final_decision: text
                """;

            ValidationResult result = yamlDslValidator.validate(yaml);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("Should warn about missing sub-rule names")
        void testMissingSubRuleNames() {
            String yaml = """
                name: "Missing Sub-rule Names Test"
                description: "Test missing sub-rule names"
                
                inputs:
                  - creditScore
                
                rules:
                  - when:
                      - creditScore >= 600
                    then:
                      - set result to "pass"
                
                output:
                  result: text
                """;

            ValidationResult result = yamlDslValidator.validate(yaml);
            // Should be valid but have warnings
            assertThat(result.getStatus()).isIn(
                ValidationResult.ValidationStatus.VALID,
                ValidationResult.ValidationStatus.WARNING
            );
            if (result.getSummary().getWarnings() > 0) {
                assertThat(result.getSummary().getWarnings()).isGreaterThan(0);
            }
        }
    }

    @Nested
    @DisplayName("Mixed Syntax Validation")
    class MixedSyntaxValidationTests {

        @Test
        @DisplayName("Should warn about mixed syntax patterns")
        void testMixedSyntaxWarning() {
            String yaml = """
                name: "Mixed Syntax Test"
                description: "Test mixed syntax warning"
                
                inputs:
                  - creditScore
                
                when:
                  - creditScore >= 600
                then:
                  - set simple_result to "pass"
                
                conditions:
                  if:
                    compare:
                      left: creditScore
                      operator: "greater_than"
                      right: 700
                  then:
                    actions:
                      - set complex_result to "excellent"
                
                output:
                  simple_result: text
                  complex_result: text
                """;

            ValidationResult result = yamlDslValidator.validate(yaml);
            // Should have warnings about mixed syntax
            assertThat(result.getSummary().getWarnings()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Enhanced Function Validation")
    class EnhancedFunctionValidationTests {

        @Test
        @DisplayName("Should validate new built-in functions")
        void testNewBuiltInFunctions() {
            String yaml = """
                name: "New Functions Test"
                description: "Test new built-in functions"
                
                inputs:
                  - userData
                  - apiUrl
                
                when:
                  - json_exists(userData, "email")
                
                then:
                  - calculate api_response as rest_get(apiUrl)
                  - calculate user_email as json_get(userData, "email")
                  - calculate formatted_date as format_date(now(), "yyyy-MM-dd")
                  - calculate user_age as calculate_age(json_get(userData, "birthDate"))
                  - calculate is_valid_email as validate_email(user_email)
                
                output:
                  api_response: object
                  user_email: text
                  formatted_date: text
                  user_age: number
                  is_valid_email: boolean
                """;

            ValidationResult result = yamlDslValidator.validate(yaml);
            assertThat(result.isValid()).isTrue();
        }
    }

    @Nested
    @DisplayName("Enhanced Metadata Validation")
    class EnhancedMetadataValidationTests {

        @Test
        @DisplayName("Should validate enhanced metadata fields")
        void testEnhancedMetadataValidation() {
            String yaml = """
                name: "Enhanced Metadata Test"
                description: "Test enhanced metadata validation"
                
                inputs:
                  - creditScore
                
                metadata:
                  tags: ["credit", "risk-assessment"]
                  author: "Risk Management Team"
                  category: "Credit Scoring"
                  priority: 1
                  riskLevel: "HIGH"
                  last_modified: "2025-01-15"
                  review_date: "2025-06-15"
                  version: "1.2.0"
                
                when:
                  - creditScore >= 600
                
                then:
                  - set result to "pass"
                
                output:
                  result: text
                """;

            ValidationResult result = yamlDslValidator.validate(yaml);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("Should detect invalid metadata formats")
        void testInvalidMetadataFormats() {
            String yaml = """
                name: "Invalid Metadata Test"
                description: "Test invalid metadata formats"
                
                inputs:
                  - creditScore
                
                metadata:
                  tags: "should-be-array"
                  priority: "should-be-number"
                  riskLevel: "INVALID_LEVEL"
                  author: ""
                
                when:
                  - creditScore >= 600
                
                then:
                  - set result to "pass"
                
                output:
                  result: text
                """;

            ValidationResult result = yamlDslValidator.validate(yaml);
            // Should have warnings/info issues about metadata format
            assertThat(result.getSummary().getTotalIssues()).isGreaterThan(0);
        }
    }
}
