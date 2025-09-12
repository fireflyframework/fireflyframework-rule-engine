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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.firefly.rules.core.dsl.model.RulesDSL;
import com.firefly.rules.core.validation.NamingConventionValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parser for converting YAML rules definitions to and from RulesDSL objects.
 * Handles the serialization and deserialization of rules definitions.
 * Supports both single rule and multiple rules parsing.
 */
@Component
@Slf4j
public class RulesDSLParser {

    private final ObjectMapper yamlMapper;
    private final NamingConventionValidator namingValidator;

    @Autowired
    public RulesDSLParser(NamingConventionValidator namingValidator) {
        this.namingValidator = namingValidator;
        this.yamlMapper = new ObjectMapper(new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                .enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR));
        
        // Configure the mapper for better handling of our DSL objects
        this.yamlMapper.findAndRegisterModules();
    }

    /**
     * Parse a YAML string into a RulesDSL object
     *
     * @param yamlContent the YAML content as a string
     * @return the parsed RulesDSL object
     * @throws RuleDSLParseException if parsing fails
     */
    public RulesDSL parseRules(String yamlContent) {
        // Validate input
        if (yamlContent == null) {
            throw new RuleDSLParseException("YAML content cannot be null", null);
        }

        if (yamlContent.trim().isEmpty()) {
            throw new RuleDSLParseException("YAML content cannot be empty", null);
        }

        try {
            log.debug("Parsing YAML rules definition: {}", yamlContent);

            // Pre-validate YAML structure
            validateYamlStructure(yamlContent);

            RulesDSL rulesDSL = yamlMapper.readValue(yamlContent, RulesDSL.class);
            validateRules(rulesDSL);
            return rulesDSL;
        } catch (RuleDSLParseException e) {
            // Re-throw our custom exceptions
            throw e;
        } catch (com.fasterxml.jackson.core.JsonParseException e) {
            log.error("JSON/YAML syntax error in rules definition", e);
            throw new RuleDSLParseException("YAML syntax error: " + e.getMessage() +
                " at line " + e.getLocation().getLineNr() +
                ", column " + e.getLocation().getColumnNr(), e);
        } catch (com.fasterxml.jackson.databind.JsonMappingException e) {
            log.error("YAML mapping error in rules definition", e);
            throw new RuleDSLParseException("YAML structure error: " + e.getMessage() +
                " (check field names and data types)", e);
        } catch (Exception e) {
            log.error("Unexpected error parsing YAML rules definition", e);
            throw new RuleDSLParseException("Failed to parse YAML rules definition: " + e.getMessage(), e);
        }
    }

    /**
     * Convert a RulesDSL object to YAML string
     *
     * @param rulesDSL the RulesDSL object to convert
     * @return the YAML representation as a string
     * @throws RuleDSLParseException if serialization fails
     */
    public String toYaml(RulesDSL rulesDSL) {
        try {
            log.debug("Converting RulesDSL to YAML: {}", rulesDSL.getName());
            return yamlMapper.writeValueAsString(rulesDSL);
        } catch (Exception e) {
            log.error("Failed to convert RulesDSL to YAML", e);
            throw new RuleDSLParseException("Failed to convert RulesDSL to YAML: " + e.getMessage(), e);
        }
    }

    /**
     * Validate a parsed RulesDSL object for basic structural integrity
     *
     * @param rulesDSL the rules to validate
     * @throws RuleDSLValidationException if validation fails
     */
    private void validateRules(RulesDSL rulesDSL) {
        if (rulesDSL == null) {
            throw new RuleDSLValidationException("Rules definition cannot be null");
        }

        // Validate required fields with detailed error messages
        validateRequiredFields(rulesDSL);

        // Validate rule logic structure
        validateRuleLogic(rulesDSL);

        // Validate that variables referenced in the rules are defined
        validateVariableReferences(rulesDSL);

        // Validate naming conventions
        validateNamingConventions(rulesDSL);

        // Validate data consistency
        validateDataConsistency(rulesDSL);

        log.debug("Rules validation passed for: {}", rulesDSL.getName());
    }

    /**
     * Validate required fields with detailed error messages
     */
    private void validateRequiredFields(RulesDSL rulesDSL) {
        if (rulesDSL.getName() == null || rulesDSL.getName().trim().isEmpty()) {
            throw new RuleDSLValidationException("Rules name is required and cannot be empty");
        }

        // Validate name format
        String name = rulesDSL.getName().trim();
        if (name.length() > 255) {
            throw new RuleDSLValidationException("Rules name cannot exceed 255 characters");
        }

        if (!name.matches("^[\\p{L}\\p{N}\\p{M}\\p{S}\\s\\-_\"']+$")) {
            throw new RuleDSLValidationException("Rules name contains invalid characters. Only letters, numbers, symbols, spaces, hyphens, underscores, and quotes are allowed");
        }
    }

    /**
     * Validate rule logic structure
     */
    private void validateRuleLogic(RulesDSL rulesDSL) {
        // Check if rules have any form of logic (conditions, when/then, or rules)
        boolean hasConditions = rulesDSL.getConditions() != null && rulesDSL.getConditions().getIfCondition() != null;
        boolean hasWhenThen = rulesDSL.getWhenConditions() != null && !rulesDSL.getWhenConditions().isEmpty();
        boolean hasSubRules = rulesDSL.getRules() != null && !rulesDSL.getRules().isEmpty();

        if (!hasConditions && !hasWhenThen && !hasSubRules) {
            throw new RuleDSLValidationException("Rules must have conditions, when/then logic, or sub-rules. " +
                "Add a 'conditions' block, 'when'/'then' statements, or 'rules' array.");
        }

        // If using when/then syntax, validate completeness
        if (hasWhenThen) {
            if (rulesDSL.getThen() == null) {
                throw new RuleDSLValidationException("When using 'when' conditions, a 'then' block is required");
            }
        }

        // Validate sub-rules if present
        if (hasSubRules) {
            validateSubRules(rulesDSL.getRules());
        }
    }

    /**
     * Validate sub-rules structure
     */
    private void validateSubRules(List<RulesDSL.SubRule> subRules) {
        for (int i = 0; i < subRules.size(); i++) {
            RulesDSL.SubRule subRule = subRules.get(i);

            if (subRule.getName() == null || subRule.getName().trim().isEmpty()) {
                throw new RuleDSLValidationException("Sub-rule at index " + i + " must have a name");
            }

            // Sub-rules must have either conditions (when/conditions) or actions (then/elseActions)
            // Unconditional rules with only 'then' blocks are valid
            boolean hasConditions = subRule.getWhen() != null || subRule.getConditions() != null;
            boolean hasActions = subRule.getThen() != null || subRule.getElseActions() != null;

            if (!hasConditions && !hasActions) {
                throw new RuleDSLValidationException("Sub-rule '" + subRule.getName() + "' must have either conditions ('when'/'conditions') or actions ('then'/'else')");
            }
        }
    }

    /**
     * Validate data consistency across the rule
     */
    private void validateDataConsistency(RulesDSL rulesDSL) {
        // Check for duplicate input variable names
        if (rulesDSL.getInputs() != null) {
            Set<String> inputNames = new HashSet<>();
            for (String input : rulesDSL.getInputs()) {
                if (!inputNames.add(input)) {
                    throw new RuleDSLValidationException("Duplicate input variable: " + input);
                }
            }
        }

        // Check for duplicate variable definitions
        if (rulesDSL.getVariables() != null) {
            Set<String> variableNames = new HashSet<>();
            for (var variable : rulesDSL.getVariables()) {
                if (!variableNames.add(variable.getName())) {
                    throw new RuleDSLValidationException("Duplicate variable definition: " + variable.getName());
                }
            }
        }

        // Check for duplicate constant definitions
        if (rulesDSL.getConstants() != null) {
            Set<String> constantNames = new HashSet<>();
            for (var constant : rulesDSL.getConstants()) {
                if (!constantNames.add(constant.getName())) {
                    throw new RuleDSLValidationException("Duplicate constant definition: " + constant.getName());
                }
            }
        }
    }

    /**
     * Validate that all variable references in the rules are properly defined
     *
     * @param rulesDSL the rules to validate
     */
    private void validateVariableReferences(RulesDSL rulesDSL) {
        log.debug("Performing variable reference validation for rules: {}", rulesDSL.getName());

        // Extract all variable references from the rules
        java.util.Set<String> referencedVariables = extractVariableReferences(rulesDSL);

        // Get defined variables and constants
        java.util.Set<String> definedVariables = new java.util.HashSet<>();

        // Add input variables
        if (rulesDSL.getInputs() != null) {
            definedVariables.addAll(rulesDSL.getInputs());
        }

        // Add constants
        if (rulesDSL.getConstants() != null) {
            rulesDSL.getConstants().forEach(constant -> definedVariables.add(constant.getName()));
        }

        // Check for undefined variables
        java.util.Set<String> undefinedVariables = new java.util.HashSet<>(referencedVariables);
        undefinedVariables.removeAll(definedVariables);

        if (!undefinedVariables.isEmpty()) {
            log.warn("Rules '{}' references undefined variables: {}", rulesDSL.getName(), undefinedVariables);
            // Note: We don't throw an exception here because variables might be resolved from the database
        }

        log.debug("Variable reference validation completed for rules: {}", rulesDSL.getName());
    }

    /**
     * Validate naming conventions for input variables in the rules definition
     *
     * @param rulesDSL the rules to validate
     * @throws RuleDSLValidationException if naming conventions are violated
     */
    private void validateNamingConventions(RulesDSL rulesDSL) {
        log.debug("Performing naming convention validation for rules: {}", rulesDSL.getName());

        // Validate input variable names (should be camelCase)
        if (rulesDSL.getInputs() != null) {
            for (String inputVariable : rulesDSL.getInputs()) {
                if (!namingValidator.isValidInputVariableName(inputVariable)) {
                    String error = String.format(
                        "Input variable '%s' in rule '%s' must follow camelCase naming convention. " +
                        "Expected: camelCase (e.g., creditScore, annualIncome). " +
                        "Found: %s",
                        inputVariable,
                        rulesDSL.getName(),
                        detectNamingPattern(inputVariable));
                    throw new RuleDSLValidationException(error);
                }
            }
        }

        log.debug("Naming convention validation completed for rules: {}", rulesDSL.getName());
    }

    /**
     * Detect the naming pattern of a variable for error messages
     *
     * @param name the variable name
     * @return description of the detected pattern
     */
    private String detectNamingPattern(String name) {
        if (name == null || name.isEmpty()) {
            return "empty or null";
        }

        if (name.matches("^[A-Z][A-Z0-9_]*$")) {
            return "UPPER_CASE_WITH_UNDERSCORES (use for constants only)";
        } else if (name.matches("^[a-z][a-z0-9_]*$")) {
            return "snake_case (use for computed variables only)";
        } else if (name.matches("^[A-Z][a-zA-Z0-9]*$")) {
            return "PascalCase (should be camelCase)";
        } else if (name.contains("_")) {
            return "mixed case with underscores";
        } else if (name.contains("-")) {
            return "kebab-case";
        } else {
            return "unrecognized pattern";
        }
    }

    /**
     * Extract variable references from the rules definition
     */
    private java.util.Set<String> extractVariableReferences(RulesDSL rulesDSL) {
        java.util.Set<String> variables = new java.util.HashSet<>();

        // Extract from simplified when conditions
        if (rulesDSL.getWhenConditions() != null) {
            for (String condition : rulesDSL.getWhenConditions()) {
                variables.addAll(extractVariablesFromString(condition));
            }
        }

        // Extract from simplified then actions
        if (rulesDSL.getThen() != null && rulesDSL.getThen() instanceof Map) {
            variables.addAll(extractVariablesFromMap((Map<String, Object>) rulesDSL.getThen()));
        }

        // Extract from simplified else actions
        if (rulesDSL.getElseAction() != null && rulesDSL.getElseAction() instanceof Map) {
            variables.addAll(extractVariablesFromMap((Map<String, Object>) rulesDSL.getElseAction()));
        }

        return variables;
    }

    /**
     * Extract variable references from a map of actions
     *
     * @param actionMap the action map to analyze
     * @return set of variable names found
     */
    private java.util.Set<String> extractVariablesFromMap(java.util.Map<String, Object> actionMap) {
        java.util.Set<String> variables = new java.util.HashSet<>();

        if (actionMap == null) {
            return variables;
        }

        for (Object value : actionMap.values()) {
            if (value instanceof String) {
                variables.addAll(extractVariablesFromString((String) value));
            }
        }

        return variables;
    }

    /**
     * Extract variable names from a string expression
     */
    private java.util.Set<String> extractVariablesFromString(String expression) {
        java.util.Set<String> variables = new java.util.HashSet<>();

        // Pattern to match variable references (uppercase words)
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\b[A-Z][A-Z_0-9]*\\b");
        java.util.regex.Matcher matcher = pattern.matcher(expression);

        while (matcher.find()) {
            String variable = matcher.group();
            // Exclude common keywords
            if (!isKeyword(variable)) {
                variables.add(variable);
            }
        }

        return variables;
    }

    /**
     * Check if a word is a keyword and not a variable
     */
    private boolean isKeyword(String word) {
        java.util.Set<String> keywords = java.util.Set.of(
                "TRUE", "FALSE", "NULL", "AND", "OR", "NOT", "IF", "THEN", "ELSE",
                "EQUALS", "GREATER_THAN", "LESS_THAN", "AT_LEAST", "BETWEEN", "IN_LIST"
        );
        return keywords.contains(word);
    }

    /**
     * Pre-validate YAML structure before parsing
     *
     * @param yamlContent the YAML content to validate
     * @throws RuleDSLParseException if structure is invalid
     */
    private void validateYamlStructure(String yamlContent) {
        // Check for common YAML syntax issues
        String[] lines = yamlContent.split("\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNumber = i + 1;

            // Check for tabs (YAML doesn't allow tabs for indentation)
            if (line.contains("\t")) {
                throw new RuleDSLParseException("YAML syntax error: Tab characters not allowed for indentation at line " + lineNumber, null);
            }

            // Check for unmatched quotes
            if (hasUnmatchedQuotes(line)) {
                throw new RuleDSLParseException("YAML syntax error: Unmatched quotes at line " + lineNumber, null);
            }

            // Check for invalid characters in key names (skip lines that are array items or comments)
            if (line.contains(":") && !line.trim().startsWith("-") && hasInvalidKeyCharacters(line)) {
                throw new RuleDSLParseException("YAML syntax error: Invalid characters in key name at line " + lineNumber, null);
            }
        }

        // Check for balanced brackets and braces
        validateBalancedBrackets(yamlContent);
    }

    /**
     * Check if a line has unmatched quotes
     */
    private boolean hasUnmatchedQuotes(String line) {
        int doubleQuotes = 0;
        int singleQuotes = 0;
        boolean inDoubleQuote = false;
        boolean inSingleQuote = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"' && !inSingleQuote) {
                if (i == 0 || line.charAt(i - 1) != '\\') {
                    doubleQuotes++;
                    inDoubleQuote = !inDoubleQuote;
                }
            } else if (c == '\'' && !inDoubleQuote) {
                if (i == 0 || line.charAt(i - 1) != '\\') {
                    singleQuotes++;
                    inSingleQuote = !inSingleQuote;
                }
            }
        }

        return (doubleQuotes % 2 != 0) || (singleQuotes % 2 != 0);
    }

    /**
     * Check if a line has invalid characters in key names
     */
    private boolean hasInvalidKeyCharacters(String line) {
        String trimmed = line.trim();
        if (!trimmed.contains(":")) {
            return false;
        }

        // Remove comments before processing
        String lineWithoutComments = trimmed;
        if (trimmed.contains("#")) {
            lineWithoutComments = trimmed.substring(0, trimmed.indexOf("#")).trim();
        }

        if (!lineWithoutComments.contains(":")) {
            return false;
        }

        String key = lineWithoutComments.substring(0, lineWithoutComments.indexOf(":")).trim();

        // Skip validation for empty keys or quoted keys
        if (key.isEmpty() || (key.startsWith("\"") && key.endsWith("\"")) || (key.startsWith("'") && key.endsWith("'"))) {
            return false;
        }

        // Keys should only contain alphanumeric characters, underscores, hyphens, and spaces
        return !key.matches("^[a-zA-Z0-9_\\-\\s]+$");
    }

    /**
     * Validate balanced brackets and braces
     */
    private void validateBalancedBrackets(String yamlContent) {
        int squareBrackets = 0;
        int curlyBraces = 0;
        boolean inQuotes = false;
        char quoteChar = 0;

        for (int i = 0; i < yamlContent.length(); i++) {
            char c = yamlContent.charAt(i);

            if ((c == '"' || c == '\'') && (i == 0 || yamlContent.charAt(i - 1) != '\\')) {
                if (!inQuotes) {
                    inQuotes = true;
                    quoteChar = c;
                } else if (c == quoteChar) {
                    inQuotes = false;
                }
                continue;
            }

            if (!inQuotes) {
                switch (c) {
                    case '[' -> squareBrackets++;
                    case ']' -> squareBrackets--;
                    case '{' -> curlyBraces++;
                    case '}' -> curlyBraces--;
                }
            }
        }

        if (squareBrackets != 0) {
            throw new RuleDSLParseException("YAML syntax error: Unmatched square brackets", null);
        }

        if (curlyBraces != 0) {
            throw new RuleDSLParseException("YAML syntax error: Unmatched curly braces", null);
        }
    }

    /**
     * Exception thrown when YAML parsing fails
     */
    public static class RuleDSLParseException extends RuntimeException {
        public RuleDSLParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when rule validation fails
     */
    public static class RuleDSLValidationException extends RuntimeException {
        public RuleDSLValidationException(String message) {
            super(message);
        }
    }
}
