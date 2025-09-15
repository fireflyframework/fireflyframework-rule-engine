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

package com.firefly.rules.core.dsl.visitor;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consolidated context object that holds the state during AST-based rule evaluation.
 * Contains variables, constants, and execution state with full feature support.
 * This replaces both the legacy and AST-specific EvaluationContext classes.
 */
@Data
@NoArgsConstructor
public class EvaluationContext {

    /**
     * Name of the rule being evaluated
     */
    private String ruleName;

    /**
     * Operation ID for tracing and logging
     */
    private String operationId;

    /**
     * Start time of evaluation (for performance tracking)
     */
    private long startTime;

    /**
     * Input variables provided by the controller (runtime values like annualIncome, creditScore)
     */
    private Map<String, Object> inputVariables = new ConcurrentHashMap<>();

    /**
     * System constants loaded from database (system-wide values like MINIMUM_CREDIT_SCORE, MAX_LOAN_AMOUNT)
     */
    private Map<String, Object> systemConstants = new ConcurrentHashMap<>();

    /**
     * Computed variables created during rule evaluation (like loan_to_income_ratio, final_score)
     */
    private Map<String, Object> computedVariables = new ConcurrentHashMap<>();

    /**
     * Whether the circuit breaker has been triggered
     */
    private boolean circuitBreakerTriggered = false;

    /**
     * Message associated with circuit breaker trigger
     */
    private String circuitBreakerMessage;

    /**
     * Constructor with operation ID
     */
    public EvaluationContext(String operationId) {
        this.operationId = operationId;
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Constructor with operation ID and initial input variables
     */
    public EvaluationContext(String operationId, Map<String, Object> initialVariables) {
        this.operationId = operationId;
        this.startTime = System.currentTimeMillis();
        if (initialVariables != null) {
            this.inputVariables.putAll(initialVariables);
        }
    }

    /**
     * Get a value by name, checking computed variables, input variables, and system constants in order
     * This is the primary method for variable resolution in AST evaluation
     *
     * @param name the variable/constant name
     * @return the value, or null if not found
     */
    public Object getValue(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }

        String trimmedName = name.trim();

        // Check computed variables first (they can override input variables)
        if (computedVariables.containsKey(trimmedName)) {
            return computedVariables.get(trimmedName);
        }

        // Check input variables (runtime values provided by controller)
        if (inputVariables.containsKey(trimmedName)) {
            return inputVariables.get(trimmedName);
        }

        // Check system constants (predefined values from database)
        return systemConstants.get(trimmedName);
    }

    /**
     * Get a variable value (alias for getValue for AST compatibility)
     */
    public Object getVariable(String name) {
        return getValue(name);
    }

    /**
     * Set an input variable value (provided by controller)
     *
     * @param name the variable name
     * @param value the variable value
     */
    public void setInputVariable(String name, Object value) {
        validateVariableName(name, "input variable");
        inputVariables.put(name, value);
    }

    /**
     * Set a variable value (alias for setInputVariable for AST compatibility)
     */
    public void setVariable(String name, Object value) {
        setInputVariable(name, value);
    }

    /**
     * Set a computed variable value (created during rule evaluation)
     *
     * @param name the variable name
     * @param value the variable value
     */
    public void setComputedVariable(String name, Object value) {
        validateVariableName(name, "computed variable");
        computedVariables.put(name, value);
    }

    /**
     * Get a constant value
     */
    public Object getConstant(String name) {
        return systemConstants.get(name);
    }

    /**
     * Set a system constant value (loaded from database)
     *
     * @param name the constant name
     * @param value the constant value
     */
    public void setSystemConstant(String name, Object value) {
        validateVariableName(name, "system constant");
        systemConstants.put(name, value);
    }

    /**
     * Set a constant value (alias for setSystemConstant for AST compatibility)
     */
    public void setConstant(String name, Object value) {
        setSystemConstant(name, value);
    }

    /**
     * Check if a value exists (variable or constant)
     *
     * @param name the variable/constant name
     * @return true if the value exists
     */
    public boolean hasValue(String name) {
        return computedVariables.containsKey(name) ||
               inputVariables.containsKey(name) ||
               systemConstants.containsKey(name);
    }

    /**
     * Check if a variable exists (alias for hasValue for AST compatibility)
     */
    public boolean hasVariable(String name) {
        return hasValue(name);
    }

    /**
     * Get all input variables
     *
     * @return map of input variables
     */
    public Map<String, Object> getInputVariables() {
        return new ConcurrentHashMap<>(inputVariables);
    }

    /**
     * Get all computed variables
     *
     * @return map of computed variables
     */
    public Map<String, Object> getComputedVariables() {
        return new ConcurrentHashMap<>(computedVariables);
    }

    /**
     * Get all system constants
     *
     * @return map of system constants
     */
    public Map<String, Object> getSystemConstants() {
        return new ConcurrentHashMap<>(systemConstants);
    }

    /**
     * Get all constants (alias for getSystemConstants)
     *
     * @return map of constants
     */
    public Map<String, Object> getConstants() {
        return getSystemConstants();
    }

    /**
     * Get all variables (including computed ones) for AST compatibility
     */
    public Map<String, Object> getAllVariables() {
        Map<String, Object> all = new ConcurrentHashMap<>();
        all.putAll(systemConstants);
        all.putAll(inputVariables);
        all.putAll(computedVariables);
        return all;
    }

    /**
     * Trigger the circuit breaker
     *
     * @param message the circuit breaker message
     */
    public void triggerCircuitBreaker(String message) {
        this.circuitBreakerTriggered = true;
        this.circuitBreakerMessage = message;
    }

    /**
     * Get a summary of all variables and their sources for logging
     *
     * @return a formatted string showing all variables and their types
     */
    public String getVariableSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Variable Summary:\n");

        if (!inputVariables.isEmpty()) {
            summary.append("  Input Variables: ").append(inputVariables).append("\n");
        }

        if (!systemConstants.isEmpty()) {
            summary.append("  System Constants: ").append(systemConstants).append("\n");
        }

        if (!computedVariables.isEmpty()) {
            summary.append("  Computed Variables: ").append(computedVariables).append("\n");
        }

        return summary.toString();
    }

    /**
     * Get the source type of a variable (for debugging)
     *
     * @param name the variable name
     * @return the source type: "COMPUTED", "INPUT", "CONSTANT", or "NOT_FOUND"
     */
    public String getVariableSource(String name) {
        if (computedVariables.containsKey(name)) {
            return "COMPUTED";
        } else if (inputVariables.containsKey(name)) {
            return "INPUT";
        } else if (systemConstants.containsKey(name)) {
            return "CONSTANT";
        } else {
            return "NOT_FOUND";
        }
    }
    /**
     * Remove a computed variable (for testing purposes)
     *
     * @param name the variable name to remove
     */
    public void removeComputedVariable(String name) {
        computedVariables.remove(name);
    }

    /**
     * Remove an input variable (for testing purposes)
     *
     * @param name the variable name to remove
     */
    public void removeInputVariable(String name) {
        inputVariables.remove(name);
    }

    /**
     * Remove a system constant (for testing purposes)
     *
     * @param name the constant name to remove
     */
    public void removeSystemConstant(String name) {
        systemConstants.remove(name);
    }

    /**
     * Get the operation ID, generating one if not set
     *
     * @return the operation ID
     */
    public String getOperationId() {
        if (operationId == null) {
            operationId = "op-" + UUID.randomUUID().toString().substring(0, 8);
        }
        return operationId;
    }

    /**
     * Get start time for performance tracking
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * Get variable type for a given name
     *
     * @param name the variable name
     * @return the variable type or null if not found
     */
    public String getVariableType(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }

        String trimmedName = name.trim();

        if (computedVariables.containsKey(trimmedName)) {
            return "computed";
        }
        if (inputVariables.containsKey(trimmedName)) {
            return "input";
        }
        if (systemConstants.containsKey(trimmedName)) {
            return "constant";
        }
        return null;
    }

    /**
     * Check if a variable exists in any scope
     *
     * @param name the variable name
     * @return true if the variable exists
     */
    public boolean variableExists(String name) {
        return getVariableType(name) != null;
    }

    /**
     * Create a copy of this context
     */
    public EvaluationContext copy() {
        EvaluationContext copy = new EvaluationContext(operationId);
        copy.inputVariables.putAll(this.inputVariables);
        copy.systemConstants.putAll(this.systemConstants);
        copy.computedVariables.putAll(this.computedVariables);
        copy.ruleName = this.ruleName;
        copy.circuitBreakerTriggered = this.circuitBreakerTriggered;
        copy.circuitBreakerMessage = this.circuitBreakerMessage;
        return copy;
    }

    /**
     * Validate variable name
     *
     * @param name the variable name to validate
     * @param type the type of variable for error messages
     */
    private void validateVariableName(String name, String type) {
        if (name == null) {
            throw new IllegalArgumentException(type + " name cannot be null");
        }
        if (name.trim().isEmpty()) {
            throw new IllegalArgumentException(type + " name cannot be empty");
        }
    }
}
