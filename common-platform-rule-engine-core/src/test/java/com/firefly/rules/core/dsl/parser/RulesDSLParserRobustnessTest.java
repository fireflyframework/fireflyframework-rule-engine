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

package com.firefly.rules.core.dsl.parser;

import com.firefly.rules.core.dsl.model.RulesDSL;
import com.firefly.rules.core.validation.NamingConventionValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for RulesDSLParser robustness and edge cases
 */
class RulesDSLParserRobustnessTest {

    private RulesDSLParser parser;

    @BeforeEach
    void setUp() {
        NamingConventionValidator namingValidator = new NamingConventionValidator();
        parser = new RulesDSLParser(namingValidator);
    }

    @Test
    @DisplayName("Should handle null YAML content gracefully")
    void testNullYamlContent() {
        assertThatThrownBy(() -> parser.parseRules(null))
                .isInstanceOf(RulesDSLParser.RuleDSLParseException.class)
                .hasMessageContaining("YAML content cannot be null");
    }

    @Test
    @DisplayName("Should handle empty YAML content gracefully")
    void testEmptyYamlContent() {
        assertThatThrownBy(() -> parser.parseRules(""))
                .isInstanceOf(RulesDSLParser.RuleDSLParseException.class)
                .hasMessageContaining("YAML content cannot be empty");

        assertThatThrownBy(() -> parser.parseRules("   "))
                .isInstanceOf(RulesDSLParser.RuleDSLParseException.class)
                .hasMessageContaining("YAML content cannot be empty");
    }

    @Test
    @DisplayName("Should detect tab characters in YAML")
    void testTabCharactersInYaml() {
        String yamlWithTabs = """
            name: Test Rule
            \twhen:
            \t  - condition1
            """;

        assertThatThrownBy(() -> parser.parseRules(yamlWithTabs))
                .isInstanceOf(RulesDSLParser.RuleDSLParseException.class)
                .hasMessageContaining("Tab characters not allowed for indentation");
    }

    @Test
    @DisplayName("Should detect unmatched quotes")
    void testUnmatchedQuotes() {
        String yamlWithUnmatchedQuotes = """
            name: Test Rule
            when:
              - condition equals "unmatched quote
            then:
              - set result to "success"
            """;

        assertThatThrownBy(() -> parser.parseRules(yamlWithUnmatchedQuotes))
                .isInstanceOf(RulesDSLParser.RuleDSLParseException.class)
                .hasMessageContaining("Unmatched quotes");
    }

    @Test
    @DisplayName("Should detect unmatched brackets")
    void testUnmatchedBrackets() {
        String yamlWithUnmatchedBrackets = """
            name: Test Rule
            when:
              - condition in_list [value1, value2
            then:
              - set result to "success"
            """;

        assertThatThrownBy(() -> parser.parseRules(yamlWithUnmatchedBrackets))
                .isInstanceOf(RulesDSLParser.RuleDSLParseException.class)
                .hasMessageContaining("Unmatched square brackets");
    }

    @Test
    @DisplayName("Should detect unmatched braces")
    void testUnmatchedBraces() {
        String yamlWithUnmatchedBraces = """
            name: Test Rule
            when:
              - condition: { key: value
            then:
              - set result to "success"
            """;

        assertThatThrownBy(() -> parser.parseRules(yamlWithUnmatchedBraces))
                .isInstanceOf(RulesDSLParser.RuleDSLParseException.class)
                .hasMessageContaining("Unmatched curly braces");
    }

    @Test
    @DisplayName("Should handle missing rule name")
    void testMissingRuleName() {
        String yamlWithoutName = """
            when:
              - condition1
            then:
              - action1
            """;

        assertThatThrownBy(() -> parser.parseRules(yamlWithoutName))
                .isInstanceOf(RulesDSLParser.RuleDSLParseException.class)
                .hasMessageContaining("Rules name is required");
    }

    @Test
    @DisplayName("Should handle empty rule name")
    void testEmptyRuleName() {
        String yamlWithEmptyName = """
            name: ""
            when:
              - condition1
            then:
              - action1
            """;

        assertThatThrownBy(() -> parser.parseRules(yamlWithEmptyName))
                .isInstanceOf(RulesDSLParser.RuleDSLParseException.class)
                .hasMessageContaining("Rules name is required");
    }

    @Test
    @DisplayName("Should handle very long rule names")
    void testVeryLongRuleName() {
        String longName = "A".repeat(300);
        String yamlWithLongName = String.format("""
            name: %s
            when:
              - condition1
            then:
              - action1
            """, longName);

        assertThatThrownBy(() -> parser.parseRules(yamlWithLongName))
                .isInstanceOf(RulesDSLParser.RuleDSLParseException.class)
                .hasMessageContaining("Rules name cannot exceed 255 characters");
    }

    @Test
    @DisplayName("Should handle invalid characters in rule names")
    void testInvalidCharactersInRuleName() {
        String yamlWithInvalidName = """
            name: "Rule with @#$% invalid chars"
            when:
              - condition1
            then:
              - action1
            """;

        assertThatThrownBy(() -> parser.parseRules(yamlWithInvalidName))
                .isInstanceOf(RulesDSLParser.RuleDSLParseException.class)
                .hasMessageContaining("Rules name contains invalid characters");
    }

    @Test
    @DisplayName("Should handle missing rule logic")
    void testMissingRuleLogic() {
        String yamlWithoutLogic = """
            name: Test Rule
            description: A rule without logic
            """;

        assertThatThrownBy(() -> parser.parseRules(yamlWithoutLogic))
                .isInstanceOf(RulesDSLParser.RuleDSLParseException.class)
                .hasMessageContaining("Rules must have conditions, when/then logic, or sub-rules");
    }

    @Test
    @DisplayName("Should handle when without then")
    void testWhenWithoutThen() {
        String yamlWithWhenButNoThen = """
            name: Test Rule
            when:
              - condition1
            """;

        assertThatThrownBy(() -> parser.parseRules(yamlWithWhenButNoThen))
                .isInstanceOf(RulesDSLParser.RuleDSLParseException.class)
                .hasMessageContaining("When using 'when' conditions, a 'then' block is required");
    }

    @Test
    @DisplayName("Should handle duplicate input variables")
    void testDuplicateInputVariables() {
        String yamlWithDuplicateInputs = """
            name: Test Rule
            inputs:
              - variable1
              - variable2
              - variable1
            when:
              - variable1 > 0
            then:
              - set result to "success"
            """;

        assertThatThrownBy(() -> parser.parseRules(yamlWithDuplicateInputs))
                .isInstanceOf(RulesDSLParser.RuleDSLParseException.class)
                .hasMessageContaining("Duplicate input variable: variable1");
    }

    @Test
    @DisplayName("Should handle sub-rules without names")
    void testSubRulesWithoutNames() {
        String yamlWithUnnamedSubRule = """
            name: Test Rule
            rules:
              - when:
                  - condition1
                then:
                  - action1
            """;

        assertThatThrownBy(() -> parser.parseRules(yamlWithUnnamedSubRule))
                .isInstanceOf(RulesDSLParser.RuleDSLParseException.class)
                .hasMessageContaining("Sub-rule at index 0 must have a name");
    }

    @Test
    @DisplayName("Should handle sub-rules without logic")
    void testSubRulesWithoutLogic() {
        String yamlWithSubRuleWithoutLogic = """
            name: Test Rule
            rules:
              - name: Sub Rule 1
                description: A sub-rule without logic
            """;

        assertThatThrownBy(() -> parser.parseRules(yamlWithSubRuleWithoutLogic))
                .isInstanceOf(RulesDSLParser.RuleDSLParseException.class)
                .hasMessageContaining("Sub-rule 'Sub Rule 1' must have either conditions ('when'/'conditions') or actions ('then'/'else')");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "name: Test\nwhen:\n  - condition\nthen:\n  - action",
        "name: 'Test Rule'\nwhen:\n  - condition\nthen:\n  - action",
        "name: \"Test Rule\"\nwhen:\n  - condition\nthen:\n  - action"
    })
    @DisplayName("Should handle various valid YAML formats")
    void testValidYamlFormats(String yaml) {
        assertThatCode(() -> parser.parseRules(yaml))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle complex nested structures")
    void testComplexNestedStructures() {
        String complexYaml = """
            name: Complex Rule
            inputs:
              - input1
              - input2
            when:
              - input1 > 0
              - input2 contains "test"
            then:
              - calculate result as input1 * 2
              - if result > 100:
                  - set status to "high"
                else:
                  - set status to "normal"
            """;

        assertThatCode(() -> parser.parseRules(complexYaml))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should provide detailed error messages for JSON syntax errors")
    void testDetailedJsonSyntaxErrors() {
        String invalidJson = """
            name: Test Rule
            when: [
              condition1,
              condition2,
            ]
            """;

        assertThatThrownBy(() -> parser.parseRules(invalidJson))
                .isInstanceOf(RulesDSLParser.RuleDSLParseException.class)
                .hasMessageContaining("When using 'when' conditions, a 'then' block is required");
    }

    @Test
    @DisplayName("Should handle unicode characters in YAML")
    void testUnicodeCharacters() {
        String yamlWithUnicode = """
            name: "测试规则 🚀"
            when:
              - condition equals "世界"
            then:
              - set result to "成功"
            """;

        assertThatCode(() -> parser.parseRules(yamlWithUnicode))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle very large YAML documents")
    void testLargeYamlDocuments() {
        StringBuilder largeYaml = new StringBuilder();
        largeYaml.append("name: Large Rule\n");
        largeYaml.append("inputs:\n");
        
        // Add many input variables
        for (int i = 0; i < 1000; i++) {
            largeYaml.append("  - input").append(i).append("\n");
        }
        
        largeYaml.append("when:\n");
        largeYaml.append("  - input0 > 0\n");
        largeYaml.append("then:\n");
        largeYaml.append("  - set result to \"success\"\n");

        assertThatCode(() -> parser.parseRules(largeYaml.toString()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle malformed YAML structure")
    void testMalformedYamlStructure() {
        String malformedYaml = """
            name Test Rule
            when
              condition1
            then
              action1
            """;

        assertThatThrownBy(() -> parser.parseRules(malformedYaml))
                .isInstanceOf(RulesDSLParser.RuleDSLParseException.class);
    }
}
