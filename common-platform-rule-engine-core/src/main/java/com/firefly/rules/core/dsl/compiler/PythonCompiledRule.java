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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Represents a compiled Python rule with all necessary metadata and code.
 * This is the result of compiling a DSL rule to executable Python code.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PythonCompiledRule {

    /**
     * Name of the original rule
     */
    private String ruleName;

    /**
     * Description of the rule
     */
    private String description;

    /**
     * Version of the rule
     */
    private String version;

    /**
     * Generated Python code
     */
    private String pythonCode;

    /**
     * List of input variable names expected by the rule
     */
    private List<String> inputVariables;

    /**
     * Map of output variable names to their types
     */
    private Map<String, String> outputVariables;

    /**
     * Timestamp when the rule was compiled
     */
    private LocalDateTime compiledAt;

    /**
     * Hash of the original DSL for cache invalidation
     */
    private String sourceHash;

    /**
     * Python function name (sanitized version of rule name)
     */
    private String functionName;

    /**
     * Required Python runtime dependencies
     */
    private List<String> dependencies;

    /**
     * Compilation metadata
     */
    private CompilationMetadata metadata;

    public PythonCompiledRule(String ruleName, String description, String version,
                             String pythonCode, List<String> inputVariables,
                             Map<String, String> outputVariables) {
        this.ruleName = ruleName;
        this.description = description;
        this.version = version;
        this.pythonCode = pythonCode;
        this.inputVariables = inputVariables;
        this.outputVariables = outputVariables;
        this.compiledAt = LocalDateTime.now();
        this.functionName = sanitizeFunctionName(ruleName);
        this.dependencies = List.of("firefly_runtime");
    }

    /**
     * Get the main function name for this compiled rule
     */
    public String getMainFunctionName() {
        return functionName != null ? functionName : sanitizeFunctionName(ruleName);
    }

    /**
     * Check if the compiled rule is valid and executable
     */
    public boolean isValid() {
        return pythonCode != null && !pythonCode.trim().isEmpty() &&
               ruleName != null && !ruleName.trim().isEmpty();
    }

    /**
     * Get the size of the generated Python code in bytes
     */
    public int getCodeSize() {
        return pythonCode != null ? pythonCode.getBytes().length : 0;
    }

    /**
     * Sanitize rule name to create a valid Python function name
     */
    private String sanitizeFunctionName(String name) {
        if (name == null) return "unnamed_rule";

        String sanitized = name.toLowerCase()
            .replaceAll("[^a-zA-Z0-9_]", "_")
            .replaceAll("_{2,}", "_")
            .replaceAll("^_|_$", "");

        // Add underscore prefix if starts with number
        if (sanitized.matches("^[0-9].*")) {
            sanitized = "_" + sanitized;
        }

        return sanitized;
    }

    /**
     * Compilation metadata for tracking and debugging
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CompilationMetadata {
        private String compilerVersion;
        private long compilationTimeMs;
        private int astNodeCount;
        private List<String> warnings;
        private Map<String, Object> optimizations;
    }
}
