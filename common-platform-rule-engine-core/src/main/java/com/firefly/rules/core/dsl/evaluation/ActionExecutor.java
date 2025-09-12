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

import com.firefly.rules.core.dsl.model.ActionBlock;
import com.firefly.rules.core.utils.JsonLogger;
import com.firefly.rules.core.validation.NamingConventionValidator;
import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes actions defined in the rule DSL.
 * Handles variable assignments, function calls, and circuit breaker triggers.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ActionExecutor {

    private final VariableResolver variableResolver;
    private final NamingConventionValidator namingValidator;
    private final ConditionEvaluator conditionEvaluator;

    /**
     * Execute an action block
     *
     * @param actionBlock the action block to execute
     * @param context the evaluation context
     */
    public void execute(ActionBlock actionBlock, EvaluationContext context) {
        if (actionBlock == null) {
            return;
        }

        String operationId = context.getOperationId();
        JsonLogger.logActionExecution(log, operationId, "action_block", "Executing action block", true);

        // Execute individual actions
        if (actionBlock.getActions() != null) {
            for (ActionBlock.Action action : actionBlock.getActions()) {
                executeAction(action, context);

                // Stop execution if circuit breaker is triggered
                if (context.isCircuitBreakerTriggered()) {
                    JsonLogger.logCircuitBreaker(log, operationId, context.getCircuitBreakerMessage(), "action_block");
                    break;
                }
            }
        }

        // Execute nested conditions (if not circuit breaker triggered)
        if (!context.isCircuitBreakerTriggered() && actionBlock.getConditions() != null) {
            executeNestedConditions(actionBlock.getConditions(), context);
        }
    }

    /**
     * Execute a simple action string (for simplified DSL syntax)
     *
     * @param actionString the action string to execute
     * @param context the evaluation context
     */
    public void executeAction(String actionString, EvaluationContext context) {
        if (actionString == null || actionString.trim().isEmpty()) {
            return;
        }

        String operationId = context.getOperationId();
        JsonLogger.logActionExecution(log, operationId, "string_action", actionString, true);

        try {
            parseAndExecuteSimpleAction(actionString.trim(), context);
        } catch (Exception e) {
            JsonLogger.error(log, operationId, "Error executing action: " + actionString, e);
        }
    }

    /**
     * Execute a single action
     *
     * @param action the action to execute
     * @param context the evaluation context
     */
    private void executeAction(ActionBlock.Action action, EvaluationContext context) {
        if (action == null) {
            return;
        }

        // Handle set variable action
        if (action.getSet() != null) {
            executeSetAction(action.getSet(), context);
        }

        // Handle function call action
        if (action.getCall() != null) {
            executeCallAction(action.getCall(), context);
        }

        // Handle calculate action
        if (action.getCalculate() != null) {
            executeCalculateAction(action.getCalculate(), context);
        }

        // Handle circuit breaker action
        if (action.getCircuitBreaker() != null) {
            executeCircuitBreakerAction(action.getCircuitBreaker(), context);
        }
    }

    /**
     * Execute a set variable action
     *
     * @param setAction the set action
     * @param context the evaluation context
     */
    private void executeSetAction(ActionBlock.Action.SetAction setAction, EvaluationContext context) {
        String variableName = setAction.getVariable();
        Object value = variableResolver.resolveValue(setAction.getValue(), context);

        String operationId = context.getOperationId();
        JsonLogger.logVariableAssignment(log, operationId, variableName, value, "computed");
        setComputedVariableWithValidation(context, variableName, value);
    }

    /**
     * Execute a calculate action
     *
     * @param calculateAction the calculate action
     * @param context the evaluation context
     */
    private void executeCalculateAction(ActionBlock.Action.CalculateAction calculateAction, EvaluationContext context) {
        String variableName = calculateAction.getVariable();
        String expression = calculateAction.getExpression();

        try {
            // Delegate expression evaluation to VariableResolver
            Object result = variableResolver.resolveValue(expression, context);

            String operationId = context.getOperationId();
            if (result != null) {
                setComputedVariableWithValidation(context, variableName, result);
                JsonLogger.logVariableAssignment(log, operationId, variableName, result, "calculated");
            } else {
                JsonLogger.warn(log, operationId, "Expression " + expression + " evaluated to null");
            }
        } catch (Exception e) {
            String operationId = context.getOperationId();
            JsonLogger.error(log, operationId, "Failed to calculate expression: " + expression, e);
        }
    }

    /**
     * Execute a function call action
     *
     * @param callAction the call action
     * @param context the evaluation context
     */
    private void executeCallAction(ActionBlock.Action.CallAction callAction, EvaluationContext context) {
        String functionName = callAction.getFunction();

        log.debug("Executing function call: {}", functionName);

        // Enhanced function registry implementation
        switch (functionName.toLowerCase()) {
            case "log":
            case "audit":
                executeLogFunction(callAction, context);
                break;
            case "notify":
                executeNotifyFunction(callAction, context);
                break;
            case "calculate":
                executeCalculateFunction(callAction, context);
                break;

            // Mathematical functions
            case "apply_discount":
                executeApplyDiscountFunction(callAction, context);
                break;
            case "calculate_interest":
                executeCalculateInterestFunction(callAction, context);
                break;

            // Data operations
            case "encrypt":
                executeEncryptFunction(callAction, context);
                break;
            case "decrypt":
                executeDecryptFunction(callAction, context);
                break;
            case "mask_data":
                executeMaskDataFunction(callAction, context);
                break;
            case "validate_data":
                executeValidateDataFunction(callAction, context);
                break;

            // Financial calculation functions
            case "calculate_loan_payment":
                executeCalculateLoanPaymentFunction(callAction, context);
                break;
            case "calculate_compound_interest":
                executeCalculateCompoundInterestFunction(callAction, context);
                break;
            case "calculate_credit_score":
                executeCalculateCreditScoreFunction(callAction, context);
                break;
            case "calculate_debt_ratio":
                executeCalculateDebtRatioFunction(callAction, context);
                break;
            case "calculate_ltv":
                executeCalculateLTVFunction(callAction, context);
                break;
            case "calculate_payment_schedule":
                executeCalculatePaymentScheduleFunction(callAction, context);
                break;
            case "calculate_amortization":
                executeCalculateAmortizationFunction(callAction, context);
                break;
            case "calculate_apr":
                executeCalculateAPRFunction(callAction, context);
                break;
            case "calculate_risk_score":
                executeCalculateRiskScoreFunction(callAction, context);
                break;
            case "format_currency":
                executeFormatCurrencyFunction(callAction, context);
                break;
            case "format_percentage":
                executeFormatPercentageFunction(callAction, context);
                break;
            case "generate_account_number":
                executeGenerateAccountNumberFunction(callAction, context);
                break;
            case "generate_transaction_id":
                executeGenerateTransactionIdFunction(callAction, context);
                break;
            case "audit_log":
                executeAuditLogFunction(callAction, context);
                break;
            case "send_notification":
                executeSendNotificationFunction(callAction, context);
                break;

            default:
                String operationId = context.getOperationId();
                JsonLogger.warn(log, operationId, "Unknown function: " + functionName);
                break;
        }
    }

    /**
     * Execute log/audit function
     */
    private void executeLogFunction(ActionBlock.Action.CallAction callAction, EvaluationContext context) {
        List<Object> params = callAction.getParameters();
        String message = params != null && !params.isEmpty() ? params.get(0).toString() : "Rule execution log";
        String level = params != null && params.size() > 1 ? params.get(1).toString() : "INFO";

        String operationId = context.getOperationId();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("message", message != null ? message : "null");
        parameters.put("level", level != null ? level : "INFO");
        JsonLogger.logFunctionCall(log, operationId, "log", parameters, true);
    }

    /**
     * Execute notify function
     */
    private void executeNotifyFunction(ActionBlock.Action.CallAction callAction, EvaluationContext context) {
        List<Object> params = callAction.getParameters();
        String operationId = context.getOperationId();

        if (params == null || params.size() < 2) {
            JsonLogger.warn(log, operationId, "Notify function requires at least 2 parameters: recipient and message");
            return;
        }

        String recipient = params.get(0).toString();
        String message = params.get(1).toString();

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("recipient", recipient != null ? recipient : "null");
        parameters.put("message", message != null ? message : "null");
        JsonLogger.logFunctionCall(log, operationId, "notify", parameters, true);
        // In a real implementation, this would send actual notifications
    }

    /**
     * Execute calculate function
     */
    private void executeCalculateFunction(ActionBlock.Action.CallAction callAction, EvaluationContext context) {
        List<Object> params = callAction.getParameters();
        if (params == null || params.size() < 2) {
            log.warn("Calculate function requires at least 2 parameters: expression and result variable");
            return;
        }

        String expression = params.get(0).toString();
        String resultVar = params.get(1).toString();

        try {
            // Delegate all expression evaluation to VariableResolver
            Object result = variableResolver.resolveValue(expression, context);

            String operationId = context.getOperationId();
            if (result != null) {
                setComputedVariableWithValidation(context, resultVar, result);
                JsonLogger.logVariableAssignment(log, operationId, resultVar, result, "calculated");
            } else {
                JsonLogger.warn(log, operationId, "Expression " + expression + " evaluated to null");
            }
        } catch (Exception e) {
            String operationId = context.getOperationId();
            JsonLogger.error(log, operationId, "Failed to calculate expression: " + expression, e);
        }
    }



    /**
     * Execute a circuit breaker action
     *
     * @param circuitBreakerAction the circuit breaker action
     * @param context the evaluation context
     */
    private void executeCircuitBreakerAction(ActionBlock.Action.CircuitBreakerAction circuitBreakerAction, 
                                           EvaluationContext context) {
        if (circuitBreakerAction.getTrigger() != null && circuitBreakerAction.getTrigger()) {
            String message = circuitBreakerAction.getMessage();
            if (message == null) {
                message = "Circuit breaker triggered";
            }
            
            String operationId = context.getOperationId();
            JsonLogger.logCircuitBreaker(log, operationId, message, "circuit_breaker_action");
            context.triggerCircuitBreaker(message);
        }
    }

    /**
     * Execute nested conditional logic within an action block
     *
     * @param conditionalBlock the nested conditional block
     * @param context the evaluation context
     */
    private void executeNestedConditions(com.firefly.rules.core.dsl.model.ConditionalBlock conditionalBlock, 
                                       EvaluationContext context) {
        if (conditionalBlock.getIfCondition() == null) {
            log.warn("Nested conditional block missing 'if' condition");
            return;
        }

        boolean conditionResult = conditionEvaluator.evaluate(conditionalBlock.getIfCondition(), context);
        
        log.debug("Nested condition result: {}", conditionResult);

        ActionBlock actionBlock = conditionResult ? 
                conditionalBlock.getThenBlock() : 
                conditionalBlock.getElseBlock();

        if (actionBlock != null) {
            execute(actionBlock, context);
        }
    }

    /**
     * Parse and execute simple action strings like "set credit_tier to PRIME"
     */
    private void parseAndExecuteSimpleAction(String actionString, EvaluationContext context) {
        // Handle "set variable to value" pattern
        if (actionString.startsWith("set ") && actionString.contains(" to ")) {
            String[] parts = actionString.substring(4).split(" to ", 2);
            if (parts.length == 2) {
                String variableName = parts[0].trim();
                Object value = variableResolver.resolveValue(parts[1].trim(), context);
                String operationId = context.getOperationId();
                JsonLogger.logVariableAssignment(log, operationId, variableName, value, "set_action");
                setComputedVariableWithValidation(context, variableName, value);
                return;
            }
        }

        // Handle "add value to variable" pattern
        if (actionString.startsWith("add ") && actionString.contains(" to ")) {
            String[] parts = actionString.substring(4).split(" to ", 2);
            if (parts.length == 2) {
                Object addValue = variableResolver.resolveValue(parts[0].trim(), context);
                String variableName = parts[1].trim();
                Object currentValue = context.getValue(variableName);

                if (addValue instanceof Number && currentValue instanceof Number) {
                    double result = ((Number) currentValue).doubleValue() + ((Number) addValue).doubleValue();
                    context.setComputedVariable(variableName, result);
                    String operationId = context.getOperationId();
                    Map<String, Object> data = new HashMap<>();
                    data.put("addValue", addValue);
                    data.put("variableName", variableName);
                    data.put("result", result);
                    JsonLogger.debug(log, operationId, "Added value to variable", data);
                }
                return;
            }
        }

        // Handle "subtract value from variable" pattern
        if (actionString.startsWith("subtract ") && actionString.contains(" from ")) {
            String[] parts = actionString.substring(9).split(" from ", 2);
            if (parts.length == 2) {
                Object subtractValue = variableResolver.resolveValue(parts[0].trim(), context);
                String variableName = parts[1].trim();
                Object currentValue = context.getValue(variableName);

                if (subtractValue instanceof Number && currentValue instanceof Number) {
                    double result = ((Number) currentValue).doubleValue() - ((Number) subtractValue).doubleValue();
                    context.setComputedVariable(variableName, result);
                    String operationId = context.getOperationId();
                    Map<String, Object> data = new HashMap<>();
                    data.put("subtractValue", subtractValue);
                    data.put("variableName", variableName);
                    data.put("result", result);
                    JsonLogger.debug(log, operationId, "Subtracted value from variable", data);
                }
                return;
            }
        }

        // Handle "multiply variable by value" pattern
        if (actionString.startsWith("multiply ") && actionString.contains(" by ")) {
            String[] parts = actionString.substring(9).split(" by ", 2);
            if (parts.length == 2) {
                String variableName = parts[0].trim();
                Object multiplyValue = variableResolver.resolveValue(parts[1].trim(), context);
                Object currentValue = context.getValue(variableName);

                if (multiplyValue instanceof Number && currentValue instanceof Number) {
                    double result = ((Number) currentValue).doubleValue() * ((Number) multiplyValue).doubleValue();
                    context.setComputedVariable(variableName, result);
                    String operationId = context.getOperationId();
                    Map<String, Object> data = new HashMap<>();
                    data.put("variableName", variableName);
                    data.put("multiplyValue", multiplyValue);
                    data.put("result", result);
                    JsonLogger.debug(log, operationId, "Multiplied variable by value", data);
                }
                return;
            }
        }

        // Handle "divide variable by value" pattern
        if (actionString.startsWith("divide ") && actionString.contains(" by ")) {
            String[] parts = actionString.substring(7).split(" by ", 2);
            if (parts.length == 2) {
                String variableName = parts[0].trim();
                Object divideValue = variableResolver.resolveValue(parts[1].trim(), context);
                Object currentValue = context.getValue(variableName);

                if (divideValue instanceof Number && currentValue instanceof Number) {
                    double divisor = ((Number) divideValue).doubleValue();
                    if (divisor != 0) {
                        double result = ((Number) currentValue).doubleValue() / divisor;
                        context.setComputedVariable(variableName, result);
                        String operationId = context.getOperationId();
                        Map<String, Object> data = new HashMap<>();
                        data.put("variableName", variableName);
                        data.put("divideValue", divideValue);
                        data.put("result", result);
                        JsonLogger.debug(log, operationId, "Divided variable by value", data);
                    } else {
                        String operationId = context.getOperationId();
                        JsonLogger.warn(log, operationId, "Division by zero attempted for variable: " + variableName);
                    }
                }
                return;
            }
        }

        // Handle "append value to variable" pattern
        if (actionString.startsWith("append ") && actionString.contains(" to ")) {
            String[] parts = actionString.substring(7).split(" to ", 2);
            if (parts.length == 2) {
                Object appendValue = variableResolver.resolveValue(parts[0].trim(), context);
                String variableName = parts[1].trim();
                Object currentValue = context.getValue(variableName);

                if (currentValue instanceof java.util.List) {
                    @SuppressWarnings("unchecked")
                    java.util.List<Object> list = (java.util.List<Object>) currentValue;
                    list.add(appendValue);
                    log.debug("Appended {} to list variable '{}'", appendValue, variableName);
                } else {
                    // Create a new list if the variable doesn't exist or isn't a list
                    java.util.List<Object> newList = new java.util.ArrayList<>();
                    if (currentValue != null) {
                        newList.add(currentValue);
                    }
                    newList.add(appendValue);
                    context.setComputedVariable(variableName, newList);
                    log.debug("Created new list for variable '{}' and appended {}", variableName, appendValue);
                }
                return;
            }
        }

        // Handle "calculate variable as expression" pattern
        if (actionString.startsWith("calculate ") && actionString.contains(" as ")) {
            String[] parts = actionString.substring(10).split(" as ", 2);
            if (parts.length == 2) {
                String variableName = parts[0].trim();
                Object value = variableResolver.resolveValue(parts[1].trim(), context);
                String operationId = context.getOperationId();
                JsonLogger.logVariableAssignment(log, operationId, variableName, value, "calculated");
                context.setComputedVariable(variableName, value);
                return;
            }
        }

        // Handle conditional actions like "if condition then action"
        if (actionString.startsWith("if ") && actionString.contains(" then ")) {
            String[] parts = actionString.substring(3).split(" then ", 2);
            if (parts.length == 2) {
                boolean conditionResult = conditionEvaluator.evaluateExpression(parts[0].trim(), context);
                if (conditionResult) {
                    parseAndExecuteSimpleAction(parts[1].trim(), context);
                }
                return;
            }
        }

        // Handle "call function_name with [parameters]" pattern
        if (actionString.startsWith("call ") && actionString.contains(" with ")) {
            String[] parts = actionString.substring(5).split(" with ", 2);
            if (parts.length == 2) {
                String functionName = parts[0].trim();
                String parametersString = parts[1].trim();

                // Parse parameters - expecting array format like [param1, param2, ...]
                if (parametersString.startsWith("[") && parametersString.endsWith("]")) {
                    String paramContent = parametersString.substring(1, parametersString.length() - 1).trim();
                    List<Object> parameters = new ArrayList<>();

                    if (!paramContent.isEmpty()) {
                        // Split by comma, but handle quoted strings and nested expressions
                        String[] paramParts = parseParameterList(paramContent);
                        for (String param : paramParts) {
                            Object resolvedParam = variableResolver.resolveValue(param.trim(), context);
                            parameters.add(resolvedParam);
                        }
                    }

                    // Create and execute the call action
                    ActionBlock.Action.CallAction callAction = ActionBlock.Action.CallAction.builder()
                            .function(functionName)
                            .parameters(parameters)
                            .build();

                    executeCallAction(callAction, context);
                    return;
                }
            }
        }

        String operationId = context.getOperationId();
        JsonLogger.warn(log, operationId, "Unknown action pattern: " + actionString);
    }

    /**
     * Parse parameter list from string format like "param1, param2, param3"
     * Handles quoted strings and basic comma separation
     */
    private String[] parseParameterList(String paramContent) {
        List<String> params = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = '"';

        for (int i = 0; i < paramContent.length(); i++) {
            char c = paramContent.charAt(i);

            if (!inQuotes && (c == '"' || c == '\'')) {
                inQuotes = true;
                quoteChar = c;
                current.append(c);
            } else if (inQuotes && c == quoteChar) {
                inQuotes = false;
                current.append(c);
            } else if (!inQuotes && c == ',') {
                params.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }

        // Add the last parameter
        if (current.length() > 0) {
            params.add(current.toString().trim());
        }

        return params.toArray(new String[0]);
    }

    // ===== ENHANCED ACTION FUNCTIONS =====

    /**
     * Apply discount based on customer tier or conditions
     */
    private void executeApplyDiscountFunction(ActionBlock.Action.CallAction callAction, EvaluationContext context) {
        List<Object> params = callAction.getParameters();
        if (params == null || params.size() < 3) {
            log.warn("apply_discount function requires at least 3 parameters: amount, discount_rate, result_variable");
            return;
        }

        try {
            double amount = ((Number) variableResolver.resolveValue(params.get(0), context)).doubleValue();
            double discountRate = ((Number) variableResolver.resolveValue(params.get(1), context)).doubleValue();
            String resultVariable = params.get(2).toString();

            double discountedAmount = amount * (1.0 - discountRate);
            context.setComputedVariable(resultVariable, discountedAmount);

            // Also store discount amount
            context.setComputedVariable(resultVariable + "_discount", amount * discountRate);

            String operationId = context.getOperationId();
            Map<String, Object> data = new HashMap<>();
            data.put("discountRate", discountRate * 100);
            data.put("amount", amount);
            data.put("discountedAmount", discountedAmount);
            data.put("resultVariable", resultVariable);
            JsonLogger.debug(log, operationId, "Applied discount", data);
        } catch (Exception e) {
            String operationId = context.getOperationId();
            JsonLogger.error(log, operationId, "Failed to apply discount", e);
        }
    }

    /**
     * Calculate interest for loans or investments
     */
    private void executeCalculateInterestFunction(ActionBlock.Action.CallAction callAction, EvaluationContext context) {
        List<Object> params = callAction.getParameters();
        if (params == null || params.size() < 5) {
            log.warn("calculate_interest function requires 5 parameters: principal, rate, time, compound_frequency, result_variable");
            return;
        }

        try {
            double principal = ((Number) variableResolver.resolveValue(params.get(0), context)).doubleValue();
            double rate = ((Number) variableResolver.resolveValue(params.get(1), context)).doubleValue();
            double time = ((Number) variableResolver.resolveValue(params.get(2), context)).doubleValue();
            double compoundFrequency = ((Number) variableResolver.resolveValue(params.get(3), context)).doubleValue();
            String resultVariable = params.get(4).toString();

            double finalAmount = principal * Math.pow(1 + rate / compoundFrequency, compoundFrequency * time);
            double interest = finalAmount - principal;

            context.setComputedVariable(resultVariable, finalAmount);
            context.setComputedVariable(resultVariable + "_interest", interest);

            String operationId = context.getOperationId();
            Map<String, Object> data = new HashMap<>();
            data.put("principal", principal);
            data.put("rate", rate);
            data.put("time", time);
            data.put("finalAmount", finalAmount);
            JsonLogger.debug(log, operationId, "Calculated compound interest", data);
        } catch (Exception e) {
            String operationId = context.getOperationId();
            JsonLogger.error(log, operationId, "Failed to calculate interest", e);
        }
    }



    /**
     * Encrypt sensitive data
     */
    private void executeEncryptFunction(ActionBlock.Action.CallAction callAction, EvaluationContext context) {
        List<Object> params = callAction.getParameters();
        String operationId = context.getOperationId();
        if (params == null || params.size() < 2) {
            JsonLogger.warn(log, operationId, "encrypt function requires at least 2 parameters: data, result_variable");
            return;
        }

        try {
            String data = variableResolver.resolveValue(params.get(0), context).toString();
            String resultVariable = params.get(1).toString();
            String algorithm = params.size() > 2 ? params.get(2).toString() : "AES";

            String encryptedData = encryptData(data, algorithm);
            context.setComputedVariable(resultVariable, encryptedData);

            Map<String, Object> logData = new HashMap<>();
            logData.put("algorithm", algorithm);
            JsonLogger.debug(log, operationId, "Encrypted data", logData);
        } catch (Exception e) {
            JsonLogger.error(log, operationId, "Failed to encrypt data", e);
        }
    }

    /**
     * Decrypt sensitive data
     */
    private void executeDecryptFunction(ActionBlock.Action.CallAction callAction, EvaluationContext context) {
        List<Object> params = callAction.getParameters();
        String operationId = context.getOperationId();
        if (params == null || params.size() < 2) {
            JsonLogger.warn(log, operationId, "decrypt function requires at least 2 parameters: encrypted_data, result_variable");
            return;
        }

        try {
            String encryptedData = variableResolver.resolveValue(params.get(0), context).toString();
            String resultVariable = params.get(1).toString();
            String algorithm = params.size() > 2 ? params.get(2).toString() : "AES";

            String decryptedData = decryptData(encryptedData, algorithm);
            context.setComputedVariable(resultVariable, decryptedData);

            Map<String, Object> logData = new HashMap<>();
            logData.put("algorithm", algorithm);
            JsonLogger.debug(log, operationId, "Decrypted data", logData);
        } catch (Exception e) {
            JsonLogger.error(log, operationId, "Failed to decrypt data", e);
        }
    }

    /**
     * Mask sensitive data for logging/display
     */
    private void executeMaskDataFunction(ActionBlock.Action.CallAction callAction, EvaluationContext context) {
        List<Object> params = callAction.getParameters();
        String operationId = context.getOperationId();
        if (params == null || params.size() < 3) {
            JsonLogger.warn(log, operationId, "mask_data function requires 3 parameters: data, mask_type, result_variable");
            return;
        }

        try {
            String data = variableResolver.resolveValue(params.get(0), context).toString();
            String maskType = params.get(1).toString();
            String resultVariable = params.get(2).toString();

            String maskedData = maskData(data, maskType);
            context.setComputedVariable(resultVariable, maskedData);

            Map<String, Object> logData = new HashMap<>();
            logData.put("maskType", maskType);
            JsonLogger.debug(log, operationId, "Masked data", logData);
        } catch (Exception e) {
            JsonLogger.error(log, operationId, "Failed to mask data", e);
        }
    }

    /**
     * Validate data against business rules
     */
    private void executeValidateDataFunction(ActionBlock.Action.CallAction callAction, EvaluationContext context) {
        List<Object> params = callAction.getParameters();
        String operationId = context.getOperationId();
        if (params == null || params.size() < 3) {
            JsonLogger.warn(log, operationId, "validate_data function requires 3 parameters: data, validation_rules, result_variable");
            return;
        }

        try {
            Object data = variableResolver.resolveValue(params.get(0), context);
            Object validationRules = params.get(1);
            String resultVariable = params.get(2).toString();

            boolean isValid = validateData(data, validationRules, context);
            context.setComputedVariable(resultVariable, isValid);

            Map<String, Object> logData = new HashMap<>();
            logData.put("isValid", isValid);
            JsonLogger.debug(log, operationId, "Data validation result", logData);
        } catch (Exception e) {
            JsonLogger.error(log, operationId, "Failed to validate data", e);
        }
    }













    // ===== HELPER METHODS FOR ENHANCED FUNCTIONS =====

    /**
     * Encrypt data using specified algorithm
     */
    private String encryptData(String data, String algorithm) {
        // In a real implementation, this would use proper encryption libraries
        // For demonstration, we'll use a simple Base64 encoding with a prefix
        try {
            String encoded = java.util.Base64.getEncoder().encodeToString(data.getBytes("UTF-8"));
            return algorithm.toUpperCase() + ":" + encoded;
        } catch (Exception e) {
            log.error("Failed to encrypt data", e);
            return data; // Return original data if encryption fails
        }
    }

    /**
     * Decrypt data using specified algorithm
     */
    private String decryptData(String encryptedData, String algorithm) {
        // In a real implementation, this would use proper decryption libraries
        try {
            String prefix = algorithm.toUpperCase() + ":";
            if (encryptedData.startsWith(prefix)) {
                String encoded = encryptedData.substring(prefix.length());
                byte[] decoded = java.util.Base64.getDecoder().decode(encoded);
                return new String(decoded, "UTF-8");
            }
            return encryptedData; // Return as-is if not properly encrypted
        } catch (Exception e) {
            log.error("Failed to decrypt data", e);
            return encryptedData;
        }
    }

    /**
     * Mask sensitive data for display/logging
     */
    private String maskData(String data, String maskType) {
        if (data == null || data.isEmpty()) {
            return data;
        }

        switch (maskType.toLowerCase()) {
            case "ssn":
                // Mask SSN: XXX-XX-1234
                if (data.length() >= 4) {
                    return "XXX-XX-" + data.substring(data.length() - 4);
                }
                break;
            case "credit_card":
                // Mask credit card: XXXX-XXXX-XXXX-1234
                if (data.length() >= 4) {
                    return "XXXX-XXXX-XXXX-" + data.substring(data.length() - 4);
                }
                break;
            case "email":
                // Mask email: j***@example.com
                int atIndex = data.indexOf('@');
                if (atIndex > 1) {
                    return data.charAt(0) + "***" + data.substring(atIndex);
                }
                break;
            case "phone":
                // Mask phone: (XXX) XXX-1234
                if (data.length() >= 4) {
                    return "(XXX) XXX-" + data.substring(data.length() - 4);
                }
                break;
            default:
                // Generic masking: show first and last character
                if (data.length() > 2) {
                    return data.charAt(0) + "***" + data.charAt(data.length() - 1);
                }
        }

        return "***"; // Fallback masking
    }

    /**
     * Validate data against business rules
     */
    private boolean validateData(Object data, Object validationRules, EvaluationContext context) {
        // Simplified validation logic
        // In a real implementation, this would use a comprehensive validation framework

        if (validationRules instanceof Map) {
            Map<?, ?> rules = (Map<?, ?>) validationRules;

            for (Map.Entry<?, ?> rule : rules.entrySet()) {
                String fieldName = rule.getKey().toString();
                Object ruleValue = rule.getValue();

                // Get field value from data
                Object fieldValue = null;
                if (data instanceof Map) {
                    fieldValue = ((Map<?, ?>) data).get(fieldName);
                }

                // Apply validation rule
                if (!applyValidationRule(fieldValue, ruleValue)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Apply individual validation rule
     */
    private boolean applyValidationRule(Object fieldValue, Object ruleValue) {
        if (ruleValue instanceof Map) {
            Map<?, ?> rule = (Map<?, ?>) ruleValue;

            // Required field check
            if (Boolean.TRUE.equals(rule.get("required")) && fieldValue == null) {
                return false;
            }

            // Type check
            String expectedType = (String) rule.get("type");
            if (expectedType != null && fieldValue != null) {
                if (!isValidType(fieldValue, expectedType)) {
                    return false;
                }
            }

            // Range check for numbers
            if (fieldValue instanceof Number) {
                Number min = (Number) rule.get("min");
                Number max = (Number) rule.get("max");
                double value = ((Number) fieldValue).doubleValue();

                if (min != null && value < min.doubleValue()) return false;
                if (max != null && value > max.doubleValue()) return false;
            }

            // Length check for strings
            if (fieldValue instanceof String) {
                Integer minLength = (Integer) rule.get("minLength");
                Integer maxLength = (Integer) rule.get("maxLength");
                int length = ((String) fieldValue).length();

                if (minLength != null && length < minLength) return false;
                if (maxLength != null && length > maxLength) return false;
            }
        }

        return true;
    }

    /**
     * Check if value matches expected type
     */
    private boolean isValidType(Object value, String expectedType) {
        switch (expectedType.toLowerCase()) {
            case "string":
                return value instanceof String;
            case "number":
                return value instanceof Number;
            case "boolean":
                return value instanceof Boolean;
            case "email":
                return value instanceof String && ((String) value).contains("@");
            case "phone":
                return value instanceof String && ((String) value).matches("\\d{10,15}");
            default:
                return true; // Unknown type, assume valid
        }
    }

    /**
     * Set a computed variable with naming convention validation
     *
     * @param context the evaluation context
     * @param variableName the variable name
     * @param value the variable value
     * @throws RuntimeException if naming convention is violated
     */
    private void setComputedVariableWithValidation(EvaluationContext context, String variableName, Object value) {
        // Validate naming convention for computed variables (should be snake_case)
        String validationError = namingValidator.validateComputedVariableName(variableName);
        if (validationError != null) {
            String operationId = context.getOperationId();
            RuntimeException exception = new RuntimeException("Computed variable naming convention violation: " + validationError);
            JsonLogger.error(log, operationId, "Computed variable naming convention violation", exception);
            throw exception;
        }

        // Set the variable if validation passes
        context.setComputedVariable(variableName, value);
    }

    // ===== FINANCIAL CALCULATION FUNCTIONS =====

    /**
     * Calculate loan payment using standard amortization formula
     */
    private void executeCalculateLoanPaymentFunction(ActionBlock.Action.CallAction callAction, EvaluationContext context) {
        List<Object> params = callAction.getParameters();
        String operationId = context.getOperationId();

        if (params == null || params.size() < 4) {
            JsonLogger.warn(log, operationId, "calculate_loan_payment requires 4 parameters: principal, rate, term, result_variable");
            return;
        }

        try {
            Object principal = variableResolver.resolveValue(params.get(0), context);
            Object rate = variableResolver.resolveValue(params.get(1), context);
            Object term = variableResolver.resolveValue(params.get(2), context);
            String resultVar = params.get(3).toString();

            double p = ((Number) principal).doubleValue();
            double r = ((Number) rate).doubleValue() / 100 / 12; // Monthly rate
            int n = ((Number) term).intValue() * 12; // Total payments

            double payment = p * (r * Math.pow(1 + r, n)) / (Math.pow(1 + r, n) - 1);

            setComputedVariableWithValidation(context, resultVar, payment);

            Map<String, Object> logParams = new HashMap<>();
            logParams.put("principal", p);
            logParams.put("rate", ((Number) rate).doubleValue());
            logParams.put("term", ((Number) term).intValue());
            logParams.put("payment", payment);
            JsonLogger.logFunctionCall(log, operationId, "calculate_loan_payment", logParams, true);
        } catch (Exception e) {
            JsonLogger.error(log, operationId, "Error calculating loan payment", e);
        }
    }

    /**
     * Calculate compound interest
     */
    private void executeCalculateCompoundInterestFunction(ActionBlock.Action.CallAction callAction, EvaluationContext context) {
        List<Object> params = callAction.getParameters();
        String operationId = context.getOperationId();

        if (params == null || params.size() < 5) {
            JsonLogger.warn(log, operationId, "calculate_compound_interest requires 5 parameters: principal, rate, time, compound_frequency, result_variable");
            return;
        }

        try {
            Object principal = variableResolver.resolveValue(params.get(0), context);
            Object rate = variableResolver.resolveValue(params.get(1), context);
            Object time = variableResolver.resolveValue(params.get(2), context);
            Object frequency = variableResolver.resolveValue(params.get(3), context);
            String resultVar = params.get(4).toString();

            double p = ((Number) principal).doubleValue();
            double r = ((Number) rate).doubleValue() / 100;
            double t = ((Number) time).doubleValue();
            int n = ((Number) frequency).intValue();

            double amount = p * Math.pow(1 + r / n, n * t);

            setComputedVariableWithValidation(context, resultVar, amount);

            Map<String, Object> logParams = new HashMap<>();
            logParams.put("principal", p);
            logParams.put("rate", ((Number) rate).doubleValue());
            logParams.put("time", t);
            logParams.put("frequency", n);
            logParams.put("amount", amount);
            JsonLogger.logFunctionCall(log, operationId, "calculate_compound_interest", logParams, true);
        } catch (Exception e) {
            JsonLogger.error(log, operationId, "Error calculating compound interest", e);
        }
    }

    /**
     * Calculate basic credit score based on factors
     */
    private void executeCalculateCreditScoreFunction(ActionBlock.Action.CallAction callAction, EvaluationContext context) {
        List<Object> params = callAction.getParameters();
        String operationId = context.getOperationId();

        if (params == null || params.size() < 6) {
            JsonLogger.warn(log, operationId, "calculate_credit_score requires 6 parameters: payment_history, credit_utilization, credit_history_length, credit_mix, new_credit, result_variable");
            return;
        }

        try {
            Object paymentHistory = variableResolver.resolveValue(params.get(0), context);
            Object creditUtilization = variableResolver.resolveValue(params.get(1), context);
            Object creditHistoryLength = variableResolver.resolveValue(params.get(2), context);
            Object creditMix = variableResolver.resolveValue(params.get(3), context);
            Object newCredit = variableResolver.resolveValue(params.get(4), context);
            String resultVar = params.get(5).toString();

            // Simplified credit score calculation (weights based on FICO model)
            double score = 300 + // Base score
                    (((Number) paymentHistory).doubleValue() * 0.35 * 550) + // 35% weight
                    (((Number) creditUtilization).doubleValue() * 0.30 * 550) + // 30% weight
                    (((Number) creditHistoryLength).doubleValue() * 0.15 * 550) + // 15% weight
                    (((Number) creditMix).doubleValue() * 0.10 * 550) + // 10% weight
                    (((Number) newCredit).doubleValue() * 0.10 * 550); // 10% weight

            // Cap at 850
            score = Math.min(score, 850);

            setComputedVariableWithValidation(context, resultVar, (int) score);

            Map<String, Object> logParams = new HashMap<>();
            logParams.put("payment_history", ((Number) paymentHistory).doubleValue());
            logParams.put("credit_utilization", ((Number) creditUtilization).doubleValue());
            logParams.put("credit_history_length", ((Number) creditHistoryLength).doubleValue());
            logParams.put("credit_mix", ((Number) creditMix).doubleValue());
            logParams.put("new_credit", ((Number) newCredit).doubleValue());
            logParams.put("score", (int) score);
            JsonLogger.logFunctionCall(log, operationId, "calculate_credit_score", logParams, true);
        } catch (Exception e) {
            JsonLogger.error(log, operationId, "Error calculating credit score", e);
        }
    }

    /**
     * Calculate debt-to-income ratio
     */
    private void executeCalculateDebtRatioFunction(ActionBlock.Action.CallAction callAction, EvaluationContext context) {
        List<Object> params = callAction.getParameters();
        String operationId = context.getOperationId();

        if (params == null || params.size() < 3) {
            JsonLogger.warn(log, operationId, "calculate_debt_ratio requires 3 parameters: total_debt, total_income, result_variable");
            return;
        }

        try {
            Object totalDebt = variableResolver.resolveValue(params.get(0), context);
            Object totalIncome = variableResolver.resolveValue(params.get(1), context);
            String resultVar = params.get(2).toString();

            double debt = ((Number) totalDebt).doubleValue();
            double income = ((Number) totalIncome).doubleValue();

            if (income <= 0) {
                JsonLogger.warn(log, operationId, "Income must be greater than 0 for debt ratio calculation");
                return;
            }

            double ratio = debt / income;

            setComputedVariableWithValidation(context, resultVar, ratio);

            Map<String, Object> logParams = new HashMap<>();
            logParams.put("total_debt", debt);
            logParams.put("total_income", income);
            logParams.put("ratio", ratio);
            JsonLogger.logFunctionCall(log, operationId, "calculate_debt_ratio", logParams, true);
        } catch (Exception e) {
            JsonLogger.error(log, operationId, "Error calculating debt ratio", e);
        }
    }

    /**
     * Calculate loan-to-value ratio
     */
    private void executeCalculateLTVFunction(ActionBlock.Action.CallAction callAction, EvaluationContext context) {
        List<Object> params = callAction.getParameters();
        String operationId = context.getOperationId();

        if (params == null || params.size() < 3) {
            JsonLogger.warn(log, operationId, "calculate_ltv requires 3 parameters: loan_amount, property_value, result_variable");
            return;
        }

        try {
            Object loanAmount = variableResolver.resolveValue(params.get(0), context);
            Object propertyValue = variableResolver.resolveValue(params.get(1), context);
            String resultVar = params.get(2).toString();

            double loan = ((Number) loanAmount).doubleValue();
            double value = ((Number) propertyValue).doubleValue();

            if (value <= 0) {
                JsonLogger.warn(log, operationId, "Property value must be greater than 0 for LTV calculation");
                return;
            }

            double ltv = loan / value;

            setComputedVariableWithValidation(context, resultVar, ltv);

            Map<String, Object> logParams = new HashMap<>();
            logParams.put("loan_amount", loan);
            logParams.put("property_value", value);
            logParams.put("ltv", ltv);
            JsonLogger.logFunctionCall(log, operationId, "calculate_ltv", logParams, true);
        } catch (Exception e) {
            JsonLogger.error(log, operationId, "Error calculating LTV", e);
        }
    }

    /**
     * Calculate payment schedule for a loan
     */
    private void executeCalculatePaymentScheduleFunction(ActionBlock.Action.CallAction callAction, EvaluationContext context) {
        List<Object> params = callAction.getParameters();
        String operationId = context.getOperationId();

        if (params == null || params.size() < 4) {
            JsonLogger.warn(log, operationId, "calculate_payment_schedule requires 4 parameters: principal, rate, term, result_variable");
            return;
        }

        try {
            Object principal = variableResolver.resolveValue(params.get(0), context);
            Object rate = variableResolver.resolveValue(params.get(1), context);
            Object term = variableResolver.resolveValue(params.get(2), context);
            String resultVar = params.get(3).toString();

            double p = ((Number) principal).doubleValue();
            double r = ((Number) rate).doubleValue() / 100 / 12;
            int n = ((Number) term).intValue() * 12;

            double monthlyPayment = p * (r * Math.pow(1 + r, n)) / (Math.pow(1 + r, n) - 1);
            double totalPayments = monthlyPayment * n;
            double totalInterest = totalPayments - p;

            Map<String, Object> schedule = new HashMap<>();
            schedule.put("monthly_payment", monthlyPayment);
            schedule.put("total_payments", totalPayments);
            schedule.put("total_interest", totalInterest);
            schedule.put("number_of_payments", n);

            setComputedVariableWithValidation(context, resultVar, schedule);

            Map<String, Object> logParams = new HashMap<>();
            logParams.put("principal", p);
            logParams.put("rate", ((Number) rate).doubleValue());
            logParams.put("term", ((Number) term).intValue());
            logParams.put("schedule", schedule);
            JsonLogger.logFunctionCall(log, operationId, "calculate_payment_schedule", logParams, true);
        } catch (Exception e) {
            JsonLogger.error(log, operationId, "Error calculating payment schedule", e);
        }
    }

    /**
     * Calculate amortization table
     */
    private void executeCalculateAmortizationFunction(ActionBlock.Action.CallAction callAction, EvaluationContext context) {
        List<Object> params = callAction.getParameters();
        String operationId = context.getOperationId();

        if (params == null || params.size() < 4) {
            JsonLogger.warn(log, operationId, "calculate_amortization requires 4 parameters: principal, rate, term, result_variable");
            return;
        }

        try {
            Object principal = variableResolver.resolveValue(params.get(0), context);
            Object rate = variableResolver.resolveValue(params.get(1), context);
            Object term = variableResolver.resolveValue(params.get(2), context);
            String resultVar = params.get(3).toString();

            double p = ((Number) principal).doubleValue();
            double r = ((Number) rate).doubleValue() / 100 / 12;
            int n = ((Number) term).intValue() * 12;

            double monthlyPayment = p * (r * Math.pow(1 + r, n)) / (Math.pow(1 + r, n) - 1);

            // Calculate first few payments for demonstration
            List<Map<String, Object>> amortizationTable = new ArrayList<>();
            double remainingBalance = p;

            for (int i = 1; i <= Math.min(n, 12); i++) { // Show first 12 payments
                double interestPayment = remainingBalance * r;
                double principalPayment = monthlyPayment - interestPayment;
                remainingBalance -= principalPayment;

                Map<String, Object> payment = new HashMap<>();
                payment.put("payment_number", i);
                payment.put("monthly_payment", monthlyPayment);
                payment.put("principal_payment", principalPayment);
                payment.put("interest_payment", interestPayment);
                payment.put("remaining_balance", remainingBalance);

                amortizationTable.add(payment);
            }

            setComputedVariableWithValidation(context, resultVar, amortizationTable);

            Map<String, Object> logParams = new HashMap<>();
            logParams.put("principal", p);
            logParams.put("rate", ((Number) rate).doubleValue());
            logParams.put("term", ((Number) term).intValue());
            logParams.put("payments_calculated", amortizationTable.size());
            JsonLogger.logFunctionCall(log, operationId, "calculate_amortization", logParams, true);
        } catch (Exception e) {
            JsonLogger.error(log, operationId, "Error calculating amortization", e);
        }
    }

    /**
     * Calculate APR (Annual Percentage Rate)
     */
    private void executeCalculateAPRFunction(ActionBlock.Action.CallAction callAction, EvaluationContext context) {
        List<Object> params = callAction.getParameters();
        String operationId = context.getOperationId();

        if (params == null || params.size() < 4) {
            JsonLogger.warn(log, operationId, "calculate_apr requires 4 parameters: loan_amount, total_cost, term, result_variable");
            return;
        }

        try {
            Object loanAmount = variableResolver.resolveValue(params.get(0), context);
            Object totalCost = variableResolver.resolveValue(params.get(1), context);
            Object term = variableResolver.resolveValue(params.get(2), context);
            String resultVar = params.get(3).toString();

            double loan = ((Number) loanAmount).doubleValue();
            double cost = ((Number) totalCost).doubleValue();
            double years = ((Number) term).doubleValue();

            // Simplified APR calculation
            double totalInterest = cost - loan;
            double apr = (totalInterest / loan / years) * 100;

            setComputedVariableWithValidation(context, resultVar, apr);

            Map<String, Object> logParams = new HashMap<>();
            logParams.put("loan_amount", loan);
            logParams.put("total_cost", cost);
            logParams.put("term", years);
            logParams.put("apr", apr);
            JsonLogger.logFunctionCall(log, operationId, "calculate_apr", logParams, true);
        } catch (Exception e) {
            JsonLogger.error(log, operationId, "Error calculating APR", e);
        }
    }

    /**
     * Calculate risk score based on multiple factors
     */
    private void executeCalculateRiskScoreFunction(ActionBlock.Action.CallAction callAction, EvaluationContext context) {
        List<Object> params = callAction.getParameters();
        String operationId = context.getOperationId();

        if (params == null || params.size() < 6) {
            JsonLogger.warn(log, operationId, "calculate_risk_score requires 6 parameters: credit_score, debt_ratio, income, employment_years, loan_amount, result_variable");
            return;
        }

        try {
            Object creditScore = variableResolver.resolveValue(params.get(0), context);
            Object debtRatio = variableResolver.resolveValue(params.get(1), context);
            Object income = variableResolver.resolveValue(params.get(2), context);
            Object employmentYears = variableResolver.resolveValue(params.get(3), context);
            Object loanAmount = variableResolver.resolveValue(params.get(4), context);
            String resultVar = params.get(5).toString();

            // Simplified risk scoring algorithm
            int score = ((Number) creditScore).intValue();
            double ratio = ((Number) debtRatio).doubleValue();
            double inc = ((Number) income).doubleValue();
            double emp = ((Number) employmentYears).doubleValue();
            double loan = ((Number) loanAmount).doubleValue();

            // Risk score calculation (lower is better)
            double riskScore = 100; // Base risk

            // Credit score factor (higher credit score = lower risk)
            if (score >= 750) riskScore -= 30;
            else if (score >= 700) riskScore -= 20;
            else if (score >= 650) riskScore -= 10;
            else if (score < 600) riskScore += 20;

            // Debt ratio factor
            if (ratio > 0.4) riskScore += 25;
            else if (ratio > 0.3) riskScore += 15;
            else if (ratio < 0.2) riskScore -= 10;

            // Employment stability
            if (emp >= 5) riskScore -= 15;
            else if (emp >= 2) riskScore -= 5;
            else if (emp < 1) riskScore += 15;

            // Loan amount relative to income
            double loanToIncomeRatio = loan / inc;
            if (loanToIncomeRatio > 5) riskScore += 20;
            else if (loanToIncomeRatio > 3) riskScore += 10;
            else if (loanToIncomeRatio < 2) riskScore -= 5;

            // Ensure risk score is within reasonable bounds
            riskScore = Math.max(0, Math.min(100, riskScore));

            setComputedVariableWithValidation(context, resultVar, riskScore);

            Map<String, Object> logParams = new HashMap<>();
            logParams.put("credit_score", score);
            logParams.put("debt_ratio", ratio);
            logParams.put("income", inc);
            logParams.put("employment_years", emp);
            logParams.put("loan_amount", loan);
            logParams.put("risk_score", riskScore);
            JsonLogger.logFunctionCall(log, operationId, "calculate_risk_score", logParams, true);
        } catch (Exception e) {
            JsonLogger.error(log, operationId, "Error calculating risk score", e);
        }
    }

    // ===== UTILITY FUNCTIONS =====

    /**
     * Format currency value
     */
    private void executeFormatCurrencyFunction(ActionBlock.Action.CallAction callAction, EvaluationContext context) {
        List<Object> params = callAction.getParameters();
        String operationId = context.getOperationId();

        if (params == null || params.size() < 2) {
            JsonLogger.warn(log, operationId, "format_currency requires 2 parameters: amount, result_variable");
            return;
        }

        try {
            Object amount = variableResolver.resolveValue(params.get(0), context);
            String resultVar = params.get(1).toString();

            double value = ((Number) amount).doubleValue();
            String formatted = String.format("$%.2f", value);

            setComputedVariableWithValidation(context, resultVar, formatted);

            Map<String, Object> logParams = new HashMap<>();
            logParams.put("amount", value);
            logParams.put("formatted", formatted);
            JsonLogger.logFunctionCall(log, operationId, "format_currency", logParams, true);
        } catch (Exception e) {
            JsonLogger.error(log, operationId, "Error formatting currency", e);
        }
    }

    /**
     * Format percentage value
     */
    private void executeFormatPercentageFunction(ActionBlock.Action.CallAction callAction, EvaluationContext context) {
        List<Object> params = callAction.getParameters();
        String operationId = context.getOperationId();

        if (params == null || params.size() < 2) {
            JsonLogger.warn(log, operationId, "format_percentage requires 2 parameters: value, result_variable");
            return;
        }

        try {
            Object value = variableResolver.resolveValue(params.get(0), context);
            String resultVar = params.get(1).toString();

            double val = ((Number) value).doubleValue();
            String formatted = String.format("%.2f%%", val * 100);

            setComputedVariableWithValidation(context, resultVar, formatted);

            Map<String, Object> logParams = new HashMap<>();
            logParams.put("value", val);
            logParams.put("formatted", formatted);
            JsonLogger.logFunctionCall(log, operationId, "format_percentage", logParams, true);
        } catch (Exception e) {
            JsonLogger.error(log, operationId, "Error formatting percentage", e);
        }
    }

    /**
     * Generate account number
     */
    private void executeGenerateAccountNumberFunction(ActionBlock.Action.CallAction callAction, EvaluationContext context) {
        List<Object> params = callAction.getParameters();
        String operationId = context.getOperationId();

        if (params == null || params.isEmpty()) {
            JsonLogger.warn(log, operationId, "generate_account_number requires 1 parameter: result_variable");
            return;
        }

        try {
            String resultVar = params.get(0).toString();

            // Generate a simple account number (in real implementation, this would be more sophisticated)
            long timestamp = System.currentTimeMillis();
            int random = (int) (Math.random() * 10000);
            String accountNumber = String.format("%d%04d", timestamp % 1000000000L, random);

            setComputedVariableWithValidation(context, resultVar, accountNumber);

            Map<String, Object> logParams = new HashMap<>();
            logParams.put("account_number", accountNumber);
            JsonLogger.logFunctionCall(log, operationId, "generate_account_number", logParams, true);
        } catch (Exception e) {
            JsonLogger.error(log, operationId, "Error generating account number", e);
        }
    }

    /**
     * Generate transaction ID
     */
    private void executeGenerateTransactionIdFunction(ActionBlock.Action.CallAction callAction, EvaluationContext context) {
        List<Object> params = callAction.getParameters();
        String operationId = context.getOperationId();

        if (params == null || params.isEmpty()) {
            JsonLogger.warn(log, operationId, "generate_transaction_id requires 1 parameter: result_variable");
            return;
        }

        try {
            String resultVar = params.get(0).toString();

            // Generate a transaction ID
            String transactionId = "TXN-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 10000);

            setComputedVariableWithValidation(context, resultVar, transactionId);

            Map<String, Object> logParams = new HashMap<>();
            logParams.put("transaction_id", transactionId);
            JsonLogger.logFunctionCall(log, operationId, "generate_transaction_id", logParams, true);
        } catch (Exception e) {
            JsonLogger.error(log, operationId, "Error generating transaction ID", e);
        }
    }

    /**
     * Enhanced audit logging
     */
    private void executeAuditLogFunction(ActionBlock.Action.CallAction callAction, EvaluationContext context) {
        List<Object> params = callAction.getParameters();
        String operationId = context.getOperationId();

        if (params == null || params.size() < 2) {
            JsonLogger.warn(log, operationId, "audit_log requires 2 parameters: event_type, details");
            return;
        }

        try {
            String eventType = params.get(0).toString();
            Object details = variableResolver.resolveValue(params.get(1), context);

            Map<String, Object> auditEntry = new HashMap<>();
            auditEntry.put("timestamp", System.currentTimeMillis());
            auditEntry.put("event_type", eventType);
            auditEntry.put("details", details);
            auditEntry.put("operation_id", operationId);

            Map<String, Object> logParams = new HashMap<>();
            logParams.put("event_type", eventType);
            logParams.put("details", details);
            JsonLogger.logFunctionCall(log, operationId, "audit_log", logParams, true);

            // In a real implementation, this would write to an audit log system
            log.info("AUDIT: {}", auditEntry);
        } catch (Exception e) {
            JsonLogger.error(log, operationId, "Error creating audit log", e);
        }
    }

    /**
     * Send notification
     */
    private void executeSendNotificationFunction(ActionBlock.Action.CallAction callAction, EvaluationContext context) {
        List<Object> params = callAction.getParameters();
        String operationId = context.getOperationId();

        if (params == null || params.size() < 3) {
            JsonLogger.warn(log, operationId, "send_notification requires 3 parameters: recipient, message, channel");
            return;
        }

        try {
            String recipient = params.get(0).toString();
            Object message = variableResolver.resolveValue(params.get(1), context);
            String channel = params.get(2).toString();

            Map<String, Object> notification = new HashMap<>();
            notification.put("recipient", recipient);
            notification.put("message", message);
            notification.put("channel", channel);
            notification.put("timestamp", System.currentTimeMillis());

            Map<String, Object> logParams = new HashMap<>();
            logParams.put("recipient", recipient);
            logParams.put("message", message);
            logParams.put("channel", channel);
            JsonLogger.logFunctionCall(log, operationId, "send_notification", logParams, true);

            // In a real implementation, this would integrate with notification services
            log.info("NOTIFICATION: {}", notification);
        } catch (Exception e) {
            JsonLogger.error(log, operationId, "Error sending notification", e);
        }
    }
}