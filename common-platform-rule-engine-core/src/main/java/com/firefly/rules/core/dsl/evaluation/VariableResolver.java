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

import com.firefly.rules.core.dsl.model.Condition;
import com.firefly.rules.core.utils.JsonLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves variable references in rule expressions.
 * Handles variable_name syntax and nested object access.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VariableResolver {

    private final ArithmeticEvaluator arithmeticEvaluator;

    /**
     * Resolve a value that may contain variable references
     *
     * @param value the value to resolve (may be a variable reference, literal, or complex expression)
     * @param context the evaluation context
     * @return the resolved value
     */
    public Object resolveValue(Object value, EvaluationContext context) {
        if (value == null) {
            return null;
        }

        // Validate context
        if (context == null) {
            throw new IllegalArgumentException("EvaluationContext cannot be null");
        }

        // Handle string variable references
        if (value instanceof String) {
            String stringValue = (String) value;

            // Handle empty strings
            if (stringValue.isEmpty()) {
                return stringValue;
            }

            // Try to resolve as variable/constant name
            Object resolvedValue = context.getValue(stringValue);
            if (resolvedValue != null) {
                return resolvedValue;
            }

            // Try to resolve as derived financial variable
            Object derivedValue = resolveDerivedVariable(stringValue, context);
            if (derivedValue != null) {
                return derivedValue;
            }

            // Handle nested property access (e.g., customer.profile.age)
            if (stringValue.contains(".")) {
                Object nestedValue = resolveNestedProperty(stringValue, context);
                if (nestedValue != null) {
                    return nestedValue;
                }
            }

            // Handle quoted string literals with validation
            if (isQuotedString(stringValue)) {
                return extractQuotedStringValue(stringValue, context);
            }

            // Try to parse as number
            try {
                if (stringValue.contains(".")) {
                    return Double.parseDouble(stringValue);
                } else {
                    long longValue = Long.parseLong(stringValue);
                    // Return Integer if it fits in int range, otherwise Long
                    if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                        return (int) longValue;
                    } else {
                        return longValue;
                    }
                }
            } catch (NumberFormatException e) {
                // Not a number, return as string literal
            }

            // Handle boolean literals
            if ("true".equalsIgnoreCase(stringValue)) {
                return true;
            }
            if ("false".equalsIgnoreCase(stringValue)) {
                return false;
            }

            // Handle function calls like min(a, b), max(a, b), round(x)
            // Only treat as function call if it has a function name before the first parenthesis
            if (stringValue.contains("(") && stringValue.endsWith(")")) {
                int firstParen = stringValue.indexOf('(');
                String beforeParen = stringValue.substring(0, firstParen).trim();
                // Only treat as function call if there's a valid function name (not empty, no operators)
                if (!beforeParen.isEmpty() && !beforeParen.contains("+") && !beforeParen.contains("-")
                    && !beforeParen.contains("*") && !beforeParen.contains("/")) {
                    return resolveFunctionCall(stringValue, context);
                }
            }

            // Handle arithmetic expressions (containing operators)
            // Only treat as arithmetic if it looks like a valid arithmetic expression
            // and doesn't look like a regex pattern or other special string
            if (isArithmeticExpression(stringValue)) {
                try {
                    return evaluateArithmeticExpression(stringValue, context);
                } catch (IllegalArgumentException e) {
                    // Re-throw critical validation errors but handle mismatched parentheses gracefully
                    if (e.getMessage() != null && (e.getMessage().contains("circular references") ||
                                                   e.getMessage().contains("too complex"))) {
                        throw e;
                    }
                    String operationId = context.getOperationId();
                    Map<String, Object> data = new HashMap<>();
                    data.put("expression", stringValue);
                    data.put("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                    JsonLogger.warn(log, operationId, "Failed to evaluate arithmetic expression", data);
                    return stringValue;
                } catch (Exception e) {
                    String operationId = context.getOperationId();
                    Map<String, Object> data = new HashMap<>();
                    data.put("expression", stringValue);
                    data.put("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                    JsonLogger.warn(log, operationId, "Failed to evaluate arithmetic expression", data);
                    return stringValue;
                }
            }

            return stringValue;
        }

        // Handle arithmetic operations
        if (value instanceof Condition.ArithmeticOperation) {
            return arithmeticEvaluator.evaluate((Condition.ArithmeticOperation) value, context);
        }

        // Handle maps (for complex expressions)
        if (value instanceof Map) {
            Map<?, ?> mapValue = (Map<?, ?>) value;
            
            // Check if it's a comparison
            if (mapValue.containsKey("compare")) {
                // This would be handled by ConditionEvaluator, but we can return the map as-is
                return mapValue;
            }
            
            // Check if it's an arithmetic operation
            if (mapValue.containsKey("arithmetic")) {
                Object arithmeticDef = mapValue.get("arithmetic");
                if (arithmeticDef instanceof Map) {
                    return resolveArithmeticFromMap((Map<?, ?>) arithmeticDef, context);
                }
            }
        }

        // Return literal values as-is
        return value;
    }

    /**
     * Resolve derived financial variables by calculating them from input data
     */
    private Object resolveDerivedVariable(String variableName, EvaluationContext context) {
        switch (variableName.toLowerCase()) {
            case "loan_to_income":
                return calculateLoanToIncome(context);
            case "debt_to_income":
                return calculateDebtToIncome(context);
            case "credit_utilization":
                return calculateCreditUtilization(context);
            case "loan_to_value":
                return calculateLoanToValue(context);
            case "payment_to_income":
                return calculatePaymentToIncome(context);
            case "total_debt_service":
                return calculateTotalDebtService(context);
            default:
                return null; // Not a derived variable
        }
    }

    /**
     * Calculate loan-to-income ratio
     */
    private Double calculateLoanToIncome(EvaluationContext context) {
        Object loanAmount = getVariableValue(context, "REQUESTED_LOAN_AMOUNT", "loan_amount", "LOAN_AMOUNT");
        Object income = getVariableValue(context, "ANNUAL_INCOME", "income", "INCOME");

        if (loanAmount instanceof Number && income instanceof Number) {
            double loan = ((Number) loanAmount).doubleValue();
            double inc = ((Number) income).doubleValue();
            if (inc != 0) {
                return loan / inc;
            }
        }
        return null;
    }

    /**
     * Calculate debt-to-income ratio
     */
    private Double calculateDebtToIncome(EvaluationContext context) {
        Object debt = getVariableValue(context, "EXISTING_DEBT", "debt", "TOTAL_DEBT", "debt_amount");
        Object income = getVariableValue(context, "ANNUAL_INCOME", "income", "INCOME");

        if (debt instanceof Number && income instanceof Number) {
            double debtAmount = ((Number) debt).doubleValue();
            double inc = ((Number) income).doubleValue();
            if (inc != 0) {
                return debtAmount / inc;
            }
        }
        return null;
    }

    /**
     * Calculate credit utilization ratio
     */
    private Double calculateCreditUtilization(EvaluationContext context) {
        Object usedCredit = getVariableValue(context, "USED_CREDIT", "credit_used", "CREDIT_BALANCE");
        Object totalCredit = getVariableValue(context, "TOTAL_CREDIT_LIMIT", "credit_limit", "AVAILABLE_CREDIT");

        if (usedCredit instanceof Number && totalCredit instanceof Number) {
            double used = ((Number) usedCredit).doubleValue();
            double total = ((Number) totalCredit).doubleValue();
            if (total != 0) {
                return used / total;
            }
        }
        return null;
    }

    /**
     * Calculate loan-to-value ratio
     */
    private Double calculateLoanToValue(EvaluationContext context) {
        Object loanAmount = getVariableValue(context, "REQUESTED_LOAN_AMOUNT", "loan_amount", "LOAN_AMOUNT");
        Object propertyValue = getVariableValue(context, "PROPERTY_VALUE", "property_value", "COLLATERAL_VALUE");

        if (loanAmount instanceof Number && propertyValue instanceof Number) {
            double loan = ((Number) loanAmount).doubleValue();
            double value = ((Number) propertyValue).doubleValue();
            if (value != 0) {
                return loan / value;
            }
        }
        return null;
    }

    /**
     * Calculate payment-to-income ratio
     */
    private Double calculatePaymentToIncome(EvaluationContext context) {
        Object monthlyPayment = getVariableValue(context, "MONTHLY_PAYMENT", "payment", "LOAN_PAYMENT");
        Object monthlyIncome = getVariableValue(context, "MONTHLY_INCOME", "monthly_income");

        // If monthly income not available, calculate from annual
        if (monthlyIncome == null) {
            Object annualIncome = getVariableValue(context, "ANNUAL_INCOME", "income", "INCOME");
            if (annualIncome instanceof Number) {
                monthlyIncome = ((Number) annualIncome).doubleValue() / 12.0;
            }
        }

        if (monthlyPayment instanceof Number && monthlyIncome instanceof Number) {
            double payment = ((Number) monthlyPayment).doubleValue();
            double income = ((Number) monthlyIncome).doubleValue();
            if (income != 0) {
                return payment / income;
            }
        }
        return null;
    }

    /**
     * Calculate total debt service ratio
     */
    private Double calculateTotalDebtService(EvaluationContext context) {
        Object totalDebtPayments = getVariableValue(context, "TOTAL_DEBT_PAYMENTS", "debt_payments", "MONTHLY_DEBT_PAYMENTS");
        Object monthlyIncome = getVariableValue(context, "MONTHLY_INCOME", "monthly_income");

        // If monthly income not available, calculate from annual
        if (monthlyIncome == null) {
            Object annualIncome = getVariableValue(context, "ANNUAL_INCOME", "income", "INCOME");
            if (annualIncome instanceof Number) {
                monthlyIncome = ((Number) annualIncome).doubleValue() / 12.0;
            }
        }

        if (totalDebtPayments instanceof Number && monthlyIncome instanceof Number) {
            double payments = ((Number) totalDebtPayments).doubleValue();
            double income = ((Number) monthlyIncome).doubleValue();
            if (income != 0) {
                return payments / income;
            }
        }
        return null;
    }

    /**
     * Helper method to get variable value by trying multiple possible names
     */
    private Object getVariableValue(EvaluationContext context, String... possibleNames) {
        for (String name : possibleNames) {
            Object value = context.getValue(name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }



    /**
     * Resolve nested property access (e.g., customer.age)
     *
     * @param propertyPath the property path
     * @param context the evaluation context
     * @return the property value
     */
    private Object resolveNestedProperty(String propertyPath, EvaluationContext context) {
        String[] parts = propertyPath.split("\\.");
        Object current = context.getValue(parts[0]);
        
        if (current == null) {
            String operationId = context.getOperationId();
            JsonLogger.warn(log, operationId, "Root variable not found: " + parts[0]);
            return null;
        }
        
        // Navigate through the property path
        for (int i = 1; i < parts.length; i++) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(parts[i]);
            } else {
                // For non-map objects, we could use reflection here
                // For now, just log a warning and return null
                String operationId = context.getOperationId();
                Map<String, Object> data = new HashMap<>();
                data.put("property", parts[i]);
                data.put("objectClass", current.getClass().getSimpleName());
                JsonLogger.warn(log, operationId, "Cannot access property on non-map object", data);
                return null;
            }

            if (current == null) {
                String operationId = context.getOperationId();
                String currentPath = String.join(".", java.util.Arrays.copyOfRange(parts, 0, i + 1));
                JsonLogger.warn(log, operationId, "Property not found: " + currentPath);
                return null;
            }
        }
        
        return current;
    }

    /**
     * Resolve arithmetic operation from a map definition
     *
     * @param arithmeticMap the arithmetic operation map
     * @param context the evaluation context
     * @return the computed result
     */
    private Object resolveArithmeticFromMap(Map<?, ?> arithmeticMap, EvaluationContext context) {
        String operation = (String) arithmeticMap.get("operation");
        Object operands = arithmeticMap.get("operands");
        
        if (operation == null || operands == null) {
            throw new IllegalArgumentException("Arithmetic operation must have 'operation' and 'operands' fields");
        }
        
        Condition.ArithmeticOperation arithmeticOp = Condition.ArithmeticOperation.builder()
                .operation(operation)
                .operands((java.util.List<Object>) operands)
                .build();

        return arithmeticEvaluator.evaluate(arithmeticOp, context);
    }

    /**
     * Resolve function calls like min(a, b), max(a, b), round(x)
     *
     * @param functionCall the function call string
     * @param context the evaluation context
     * @return the function result
     */
    private Object resolveFunctionCall(String functionCall, EvaluationContext context) {
        int openParen = functionCall.indexOf('(');
        if (openParen == -1) {
            return functionCall; // Not a function call
        }

        String functionName = functionCall.substring(0, openParen).trim();
        String parametersStr = functionCall.substring(openParen + 1, functionCall.length() - 1).trim();

        // Parse parameters
        java.util.List<Object> parameters = new java.util.ArrayList<>();
        if (!parametersStr.isEmpty()) {
            String[] paramArray = parametersStr.split(",");
            for (String param : paramArray) {
                Object resolvedParam = resolveValue(param.trim(), context);
                parameters.add(resolvedParam);
            }
        }

        // Handle mathematical functions
        switch (functionName.toLowerCase()) {
            case "min":
                return evaluateMinFunction(parameters);
            case "max":
                return evaluateMaxFunction(parameters);
            case "round":
                return evaluateRoundFunction(parameters);
            case "abs":
                return evaluateAbsFunction(parameters);
            case "floor":
                return evaluateFloorFunction(parameters);
            case "ceil":
                return evaluateCeilFunction(parameters);

            // Financial functions
            case "npv":
                return evaluateNPVFunction(parameters);
            case "irr":
                return evaluateIRRFunction(parameters);
            case "pmt":
                return evaluatePMTFunction(parameters);
            case "pv":
                return evaluatePVFunction(parameters);
            case "fv":
                return evaluateFVFunction(parameters);
            case "compound_interest":
                return evaluateCompoundInterestFunction(parameters);
            case "simple_interest":
                return evaluateSimpleInterestFunction(parameters);
            case "loan_payment":
                return evaluateLoanPaymentFunction(parameters);
            case "debt_to_income":
                return evaluateDebtToIncomeFunction(parameters);
            case "loan_to_value":
                return evaluateLoanToValueFunction(parameters);
            case "credit_utilization":
                return evaluateCreditUtilizationFunction(parameters);

            // Risk and statistical functions
            case "var":
            case "value_at_risk":
                return evaluateVaRFunction(parameters);
            case "sharpe_ratio":
                return evaluateSharpeRatioFunction(parameters);
            case "volatility":
                return evaluateVolatilityFunction(parameters);
            case "correlation":
                return evaluateCorrelationFunction(parameters);
            case "beta":
                return evaluateBetaFunction(parameters);

            // Date/Time functions
            case "days_between":
                return evaluateDaysBetweenFunction(parameters);
            case "months_between":
                return evaluateMonthsBetweenFunction(parameters);
            case "years_between":
                return evaluateYearsBetweenFunction(parameters);
            case "age_in_years":
                return evaluateAgeInYearsFunction(parameters);

            default:
                String operationId = context.getOperationId();
                JsonLogger.warn(log, operationId, "Unknown function: " + functionName);
                return functionCall; // Return as-is if unknown
        }
    }

    private Object evaluateMinFunction(java.util.List<Object> parameters) {
        if (parameters.isEmpty()) {
            throw new IllegalArgumentException("min function requires at least 1 parameter");
        }

        return parameters.stream()
                .filter(p -> p instanceof Number)
                .map(p -> ((Number) p).doubleValue())
                .min(Double::compareTo)
                .orElseThrow(() -> new IllegalArgumentException("min function requires numeric parameters"));
    }

    private Object evaluateMaxFunction(java.util.List<Object> parameters) {
        if (parameters.isEmpty()) {
            throw new IllegalArgumentException("max function requires at least 1 parameter");
        }

        return parameters.stream()
                .filter(p -> p instanceof Number)
                .map(p -> ((Number) p).doubleValue())
                .max(Double::compareTo)
                .orElseThrow(() -> new IllegalArgumentException("max function requires numeric parameters"));
    }

    private Object evaluateRoundFunction(java.util.List<Object> parameters) {
        if (parameters.size() != 1) {
            throw new IllegalArgumentException("round function requires exactly 1 parameter");
        }

        Object param = parameters.get(0);
        if (!(param instanceof Number)) {
            throw new IllegalArgumentException("round function requires a numeric parameter");
        }

        return Math.round(((Number) param).doubleValue());
    }

    private Object evaluateAbsFunction(java.util.List<Object> parameters) {
        if (parameters.size() != 1) {
            throw new IllegalArgumentException("abs function requires exactly 1 parameter");
        }

        Object param = parameters.get(0);
        if (!(param instanceof Number)) {
            throw new IllegalArgumentException("abs function requires a numeric parameter");
        }

        return Math.abs(((Number) param).doubleValue());
    }

    private Object evaluateFloorFunction(java.util.List<Object> parameters) {
        if (parameters.size() != 1) {
            throw new IllegalArgumentException("floor function requires exactly 1 parameter");
        }

        Object param = parameters.get(0);
        if (!(param instanceof Number)) {
            throw new IllegalArgumentException("floor function requires a numeric parameter");
        }

        return Math.floor(((Number) param).doubleValue());
    }

    private Object evaluateCeilFunction(java.util.List<Object> parameters) {
        if (parameters.size() != 1) {
            throw new IllegalArgumentException("ceil function requires exactly 1 parameter");
        }

        Object param = parameters.get(0);
        if (!(param instanceof Number)) {
            throw new IllegalArgumentException("ceil function requires a numeric parameter");
        }

        return Math.ceil(((Number) param).doubleValue());
    }

    // ===== FINANCIAL FUNCTIONS =====

    /**
     * Calculate Net Present Value (NPV)
     * Parameters: discount_rate, cash_flows...
     */
    private Object evaluateNPVFunction(java.util.List<Object> parameters) {
        if (parameters.size() < 2) {
            throw new IllegalArgumentException("npv function requires at least 2 parameters: discount_rate, cash_flows...");
        }

        double discountRate = ((Number) parameters.get(0)).doubleValue();
        double npv = 0.0;

        for (int i = 1; i < parameters.size(); i++) {
            double cashFlow = ((Number) parameters.get(i)).doubleValue();
            npv += cashFlow / Math.pow(1 + discountRate, i);
        }

        return npv;
    }

    /**
     * Calculate Internal Rate of Return (IRR) using Newton-Raphson method
     * Parameters: cash_flows...
     */
    private Object evaluateIRRFunction(java.util.List<Object> parameters) {
        if (parameters.size() < 2) {
            throw new IllegalArgumentException("irr function requires at least 2 cash flow parameters");
        }

        double[] cashFlows = parameters.stream()
                .mapToDouble(p -> ((Number) p).doubleValue())
                .toArray();

        return calculateIRR(cashFlows);
    }

    /**
     * Calculate Payment (PMT) for a loan
     * Parameters: rate, nper, pv, [fv], [type]
     */
    private Object evaluatePMTFunction(java.util.List<Object> parameters) {
        if (parameters.size() < 3) {
            throw new IllegalArgumentException("pmt function requires at least 3 parameters: rate, nper, pv");
        }

        double rate = ((Number) parameters.get(0)).doubleValue();
        double nper = ((Number) parameters.get(1)).doubleValue();
        double pv = ((Number) parameters.get(2)).doubleValue();
        double fv = parameters.size() > 3 ? ((Number) parameters.get(3)).doubleValue() : 0.0;
        double type = parameters.size() > 4 ? ((Number) parameters.get(4)).doubleValue() : 0.0;

        if (rate == 0) {
            return -(pv + fv) / nper;
        }

        double pvif = Math.pow(1 + rate, nper);
        double pmt = rate / (pvif - 1) * -(pv * pvif + fv);

        if (type != 0) {
            pmt = pmt / (1 + rate);
        }

        return pmt;
    }

    /**
     * Calculate Present Value (PV)
     * Parameters: rate, nper, pmt, [fv], [type]
     */
    private Object evaluatePVFunction(java.util.List<Object> parameters) {
        if (parameters.size() < 3) {
            throw new IllegalArgumentException("pv function requires at least 3 parameters: rate, nper, pmt");
        }

        double rate = ((Number) parameters.get(0)).doubleValue();
        double nper = ((Number) parameters.get(1)).doubleValue();
        double pmt = ((Number) parameters.get(2)).doubleValue();
        double fv = parameters.size() > 3 ? ((Number) parameters.get(3)).doubleValue() : 0.0;
        double type = parameters.size() > 4 ? ((Number) parameters.get(4)).doubleValue() : 0.0;

        if (rate == 0) {
            return -(fv + pmt * nper);
        }

        double pvif = Math.pow(1 + rate, nper);
        double pv = -(fv + pmt * (pvif - 1) / rate) / pvif;

        if (type != 0) {
            pv = pv / (1 + rate);
        }

        return pv;
    }

    /**
     * Calculate Future Value (FV)
     * Parameters: rate, nper, pmt, [pv], [type]
     */
    private Object evaluateFVFunction(java.util.List<Object> parameters) {
        if (parameters.size() < 3) {
            throw new IllegalArgumentException("fv function requires at least 3 parameters: rate, nper, pmt");
        }

        double rate = ((Number) parameters.get(0)).doubleValue();
        double nper = ((Number) parameters.get(1)).doubleValue();
        double pmt = ((Number) parameters.get(2)).doubleValue();
        double pv = parameters.size() > 3 ? ((Number) parameters.get(3)).doubleValue() : 0.0;
        double type = parameters.size() > 4 ? ((Number) parameters.get(4)).doubleValue() : 0.0;

        if (rate == 0) {
            return -(pv + pmt * nper);
        }

        double pvif = Math.pow(1 + rate, nper);
        double fv = -(pv * pvif + pmt * (pvif - 1) / rate);

        if (type != 0) {
            fv = fv * (1 + rate);
        }

        return fv;
    }

    /**
     * Calculate Compound Interest
     * Parameters: principal, rate, time, [compound_frequency]
     */
    private Object evaluateCompoundInterestFunction(java.util.List<Object> parameters) {
        if (parameters.size() < 3) {
            throw new IllegalArgumentException("compound_interest function requires at least 3 parameters: principal, rate, time");
        }

        double principal = ((Number) parameters.get(0)).doubleValue();
        double rate = ((Number) parameters.get(1)).doubleValue();
        double time = ((Number) parameters.get(2)).doubleValue();
        double compoundFrequency = parameters.size() > 3 ? ((Number) parameters.get(3)).doubleValue() : 1.0;

        return principal * Math.pow(1 + rate / compoundFrequency, compoundFrequency * time);
    }

    /**
     * Calculate Simple Interest
     * Parameters: principal, rate, time
     */
    private Object evaluateSimpleInterestFunction(java.util.List<Object> parameters) {
        if (parameters.size() != 3) {
            throw new IllegalArgumentException("simple_interest function requires exactly 3 parameters: principal, rate, time");
        }

        double principal = ((Number) parameters.get(0)).doubleValue();
        double rate = ((Number) parameters.get(1)).doubleValue();
        double time = ((Number) parameters.get(2)).doubleValue();

        return principal * (1 + rate * time);
    }

    /**
     * Calculate Loan Payment
     * Parameters: principal, rate, term_months
     */
    private Object evaluateLoanPaymentFunction(java.util.List<Object> parameters) {
        if (parameters.size() != 3) {
            throw new IllegalArgumentException("loan_payment function requires exactly 3 parameters: principal, rate, term_months");
        }

        double principal = ((Number) parameters.get(0)).doubleValue();
        double annualRate = ((Number) parameters.get(1)).doubleValue();
        double termMonths = ((Number) parameters.get(2)).doubleValue();

        double monthlyRate = annualRate / 12.0;
        if (monthlyRate == 0) {
            return principal / termMonths;
        }

        return principal * (monthlyRate * Math.pow(1 + monthlyRate, termMonths)) /
               (Math.pow(1 + monthlyRate, termMonths) - 1);
    }

    /**
     * Calculate Debt-to-Income Ratio
     * Parameters: total_debt, gross_income
     */
    private Object evaluateDebtToIncomeFunction(java.util.List<Object> parameters) {
        if (parameters.size() != 2) {
            throw new IllegalArgumentException("debt_to_income function requires exactly 2 parameters: total_debt, gross_income");
        }

        double totalDebt = ((Number) parameters.get(0)).doubleValue();
        double grossIncome = ((Number) parameters.get(1)).doubleValue();

        if (grossIncome == 0) {
            return Double.POSITIVE_INFINITY;
        }

        return totalDebt / grossIncome;
    }

    /**
     * Calculate Loan-to-Value Ratio
     * Parameters: loan_amount, property_value
     */
    private Object evaluateLoanToValueFunction(java.util.List<Object> parameters) {
        if (parameters.size() != 2) {
            throw new IllegalArgumentException("loan_to_value function requires exactly 2 parameters: loan_amount, property_value");
        }

        double loanAmount = ((Number) parameters.get(0)).doubleValue();
        double propertyValue = ((Number) parameters.get(1)).doubleValue();

        if (propertyValue == 0) {
            return Double.POSITIVE_INFINITY;
        }

        return loanAmount / propertyValue;
    }

    /**
     * Calculate Credit Utilization Ratio
     * Parameters: credit_used, credit_limit
     */
    private Object evaluateCreditUtilizationFunction(java.util.List<Object> parameters) {
        if (parameters.size() != 2) {
            throw new IllegalArgumentException("credit_utilization function requires exactly 2 parameters: credit_used, credit_limit");
        }

        double creditUsed = ((Number) parameters.get(0)).doubleValue();
        double creditLimit = ((Number) parameters.get(1)).doubleValue();

        if (creditLimit == 0) {
            return Double.POSITIVE_INFINITY;
        }

        return creditUsed / creditLimit;
    }

    // ===== RISK AND STATISTICAL FUNCTIONS =====

    /**
     * Calculate Value at Risk (VaR)
     * Parameters: portfolio_value, confidence_level, volatility, [time_horizon]
     */
    private Object evaluateVaRFunction(java.util.List<Object> parameters) {
        if (parameters.size() < 3) {
            throw new IllegalArgumentException("var function requires at least 3 parameters: portfolio_value, confidence_level, volatility");
        }

        double portfolioValue = ((Number) parameters.get(0)).doubleValue();
        double confidenceLevel = ((Number) parameters.get(1)).doubleValue();
        double volatility = ((Number) parameters.get(2)).doubleValue();
        double timeHorizon = parameters.size() > 3 ? ((Number) parameters.get(3)).doubleValue() : 1.0;

        // Z-score for common confidence levels
        double zScore = getZScore(confidenceLevel);

        return portfolioValue * zScore * volatility * Math.sqrt(timeHorizon);
    }

    /**
     * Calculate Sharpe Ratio
     * Parameters: portfolio_return, risk_free_rate, portfolio_volatility
     */
    private Object evaluateSharpeRatioFunction(java.util.List<Object> parameters) {
        if (parameters.size() != 3) {
            throw new IllegalArgumentException("sharpe_ratio function requires exactly 3 parameters: portfolio_return, risk_free_rate, portfolio_volatility");
        }

        double portfolioReturn = ((Number) parameters.get(0)).doubleValue();
        double riskFreeRate = ((Number) parameters.get(1)).doubleValue();
        double portfolioVolatility = ((Number) parameters.get(2)).doubleValue();

        if (portfolioVolatility == 0) {
            return Double.POSITIVE_INFINITY;
        }

        return (portfolioReturn - riskFreeRate) / portfolioVolatility;
    }

    /**
     * Calculate Volatility (standard deviation)
     * Parameters: returns...
     */
    private Object evaluateVolatilityFunction(java.util.List<Object> parameters) {
        if (parameters.size() < 2) {
            throw new IllegalArgumentException("volatility function requires at least 2 return values");
        }

        double[] returns = parameters.stream()
                .mapToDouble(p -> ((Number) p).doubleValue())
                .toArray();

        return calculateStandardDeviation(returns);
    }

    /**
     * Calculate Correlation between two series
     * Parameters: series1..., separator, series2...
     */
    private Object evaluateCorrelationFunction(java.util.List<Object> parameters) {
        if (parameters.size() < 4) {
            throw new IllegalArgumentException("correlation function requires at least 4 parameters (2 values for each series)");
        }

        // Find separator (typically a string like "|" or "," to separate the two series)
        int separatorIndex = -1;
        for (int i = 0; i < parameters.size(); i++) {
            if (parameters.get(i) instanceof String) {
                separatorIndex = i;
                break;
            }
        }

        if (separatorIndex == -1 || separatorIndex == 0 || separatorIndex == parameters.size() - 1) {
            throw new IllegalArgumentException("correlation function requires a separator string between the two series");
        }

        double[] series1 = parameters.subList(0, separatorIndex).stream()
                .mapToDouble(p -> ((Number) p).doubleValue())
                .toArray();

        double[] series2 = parameters.subList(separatorIndex + 1, parameters.size()).stream()
                .mapToDouble(p -> ((Number) p).doubleValue())
                .toArray();

        return calculateCorrelation(series1, series2);
    }

    /**
     * Calculate Beta (systematic risk)
     * Parameters: asset_returns..., separator, market_returns...
     */
    private Object evaluateBetaFunction(java.util.List<Object> parameters) {
        if (parameters.size() < 4) {
            throw new IllegalArgumentException("beta function requires at least 4 parameters (2 values for each series)");
        }

        // Find separator
        int separatorIndex = -1;
        for (int i = 0; i < parameters.size(); i++) {
            if (parameters.get(i) instanceof String) {
                separatorIndex = i;
                break;
            }
        }

        if (separatorIndex == -1 || separatorIndex == 0 || separatorIndex == parameters.size() - 1) {
            throw new IllegalArgumentException("beta function requires a separator string between asset and market returns");
        }

        double[] assetReturns = parameters.subList(0, separatorIndex).stream()
                .mapToDouble(p -> ((Number) p).doubleValue())
                .toArray();

        double[] marketReturns = parameters.subList(separatorIndex + 1, parameters.size()).stream()
                .mapToDouble(p -> ((Number) p).doubleValue())
                .toArray();

        double correlation = calculateCorrelation(assetReturns, marketReturns);
        double assetVolatility = calculateStandardDeviation(assetReturns);
        double marketVolatility = calculateStandardDeviation(marketReturns);

        return correlation * (assetVolatility / marketVolatility);
    }

    // ===== DATE/TIME FUNCTIONS =====

    /**
     * Calculate days between two dates
     * Parameters: start_date, end_date
     */
    private Object evaluateDaysBetweenFunction(java.util.List<Object> parameters) {
        if (parameters.size() != 2) {
            throw new IllegalArgumentException("days_between function requires exactly 2 parameters: start_date, end_date");
        }

        java.time.LocalDate startDate = parseDate(parameters.get(0));
        java.time.LocalDate endDate = parseDate(parameters.get(1));

        return java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
    }

    /**
     * Calculate months between two dates
     * Parameters: start_date, end_date
     */
    private Object evaluateMonthsBetweenFunction(java.util.List<Object> parameters) {
        if (parameters.size() != 2) {
            throw new IllegalArgumentException("months_between function requires exactly 2 parameters: start_date, end_date");
        }

        java.time.LocalDate startDate = parseDate(parameters.get(0));
        java.time.LocalDate endDate = parseDate(parameters.get(1));

        return java.time.temporal.ChronoUnit.MONTHS.between(startDate, endDate);
    }

    /**
     * Calculate years between two dates
     * Parameters: start_date, end_date
     */
    private Object evaluateYearsBetweenFunction(java.util.List<Object> parameters) {
        if (parameters.size() != 2) {
            throw new IllegalArgumentException("years_between function requires exactly 2 parameters: start_date, end_date");
        }

        java.time.LocalDate startDate = parseDate(parameters.get(0));
        java.time.LocalDate endDate = parseDate(parameters.get(1));

        return java.time.temporal.ChronoUnit.YEARS.between(startDate, endDate);
    }

    /**
     * Calculate age in years from birth date
     * Parameters: birth_date, [reference_date]
     */
    private Object evaluateAgeInYearsFunction(java.util.List<Object> parameters) {
        if (parameters.size() < 1 || parameters.size() > 2) {
            throw new IllegalArgumentException("age_in_years function requires 1 or 2 parameters: birth_date, [reference_date]");
        }

        java.time.LocalDate birthDate = parseDate(parameters.get(0));
        java.time.LocalDate referenceDate = parameters.size() > 1 ?
            parseDate(parameters.get(1)) : java.time.LocalDate.now();

        return java.time.temporal.ChronoUnit.YEARS.between(birthDate, referenceDate);
    }

    // ===== HELPER METHODS =====

    /**
     * Calculate IRR using Newton-Raphson method
     */
    private double calculateIRR(double[] cashFlows) {
        double rate = 0.1; // Initial guess
        double tolerance = 1e-6;
        int maxIterations = 100;

        for (int i = 0; i < maxIterations; i++) {
            double npv = 0;
            double dnpv = 0;

            for (int j = 0; j < cashFlows.length; j++) {
                npv += cashFlows[j] / Math.pow(1 + rate, j);
                if (j > 0) {
                    dnpv -= j * cashFlows[j] / Math.pow(1 + rate, j + 1);
                }
            }

            if (Math.abs(npv) < tolerance) {
                return rate;
            }

            if (Math.abs(dnpv) < tolerance) {
                throw new IllegalArgumentException("IRR calculation failed: derivative too small");
            }

            rate = rate - npv / dnpv;
        }

        throw new IllegalArgumentException("IRR calculation failed to converge");
    }

    /**
     * Get Z-score for confidence level
     */
    private double getZScore(double confidenceLevel) {
        // Common confidence levels and their Z-scores
        if (Math.abs(confidenceLevel - 0.90) < 0.001) return 1.282;
        if (Math.abs(confidenceLevel - 0.95) < 0.001) return 1.645;
        if (Math.abs(confidenceLevel - 0.99) < 0.001) return 2.326;

        // For other confidence levels, use approximation
        // This is a simplified implementation; in production, use proper statistical libraries
        return -Math.sqrt(2) * erfInv(2 * (1 - confidenceLevel) - 1);
    }

    /**
     * Inverse error function approximation
     */
    private double erfInv(double x) {
        // Simplified approximation for inverse error function
        double a = 0.147;
        double ln = Math.log(1 - x * x);
        double part1 = 2 / (Math.PI * a) + ln / 2;
        double part2 = ln / a;

        return Math.signum(x) * Math.sqrt(Math.sqrt(part1 * part1 - part2) - part1);
    }

    /**
     * Calculate standard deviation
     */
    private double calculateStandardDeviation(double[] values) {
        if (values.length < 2) {
            return 0.0;
        }

        double mean = java.util.Arrays.stream(values).average().orElse(0.0);
        double variance = java.util.Arrays.stream(values)
                .map(x -> Math.pow(x - mean, 2))
                .average()
                .orElse(0.0);

        return Math.sqrt(variance);
    }

    /**
     * Calculate correlation between two series
     */
    private double calculateCorrelation(double[] series1, double[] series2) {
        if (series1.length != series2.length || series1.length < 2) {
            throw new IllegalArgumentException("Series must have the same length and at least 2 values");
        }

        double mean1 = java.util.Arrays.stream(series1).average().orElse(0.0);
        double mean2 = java.util.Arrays.stream(series2).average().orElse(0.0);

        double numerator = 0.0;
        double sumSq1 = 0.0;
        double sumSq2 = 0.0;

        for (int i = 0; i < series1.length; i++) {
            double diff1 = series1[i] - mean1;
            double diff2 = series2[i] - mean2;

            numerator += diff1 * diff2;
            sumSq1 += diff1 * diff1;
            sumSq2 += diff2 * diff2;
        }

        double denominator = Math.sqrt(sumSq1 * sumSq2);
        return denominator == 0 ? 0.0 : numerator / denominator;
    }

    /**
     * Parse date from various formats
     */
    private java.time.LocalDate parseDate(Object dateObj) {
        if (dateObj == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }

        if (dateObj instanceof java.time.LocalDate) {
            return (java.time.LocalDate) dateObj;
        }

        if (dateObj instanceof String) {
            String dateStr = dateObj.toString();
            try {
                // Try ISO format first (YYYY-MM-DD)
                return java.time.LocalDate.parse(dateStr);
            } catch (Exception e) {
                try {
                    // Try other common formats
                    java.time.format.DateTimeFormatter formatter =
                        java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy");
                    return java.time.LocalDate.parse(dateStr, formatter);
                } catch (Exception e2) {
                    try {
                        java.time.format.DateTimeFormatter formatter2 =
                            java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");
                        return java.time.LocalDate.parse(dateStr, formatter2);
                    } catch (Exception e3) {
                        throw new IllegalArgumentException("Unable to parse date: " + dateStr);
                    }
                }
            }
        }

        if (dateObj instanceof Number) {
            // Assume Unix timestamp (seconds since epoch)
            long timestamp = ((Number) dateObj).longValue();
            return java.time.Instant.ofEpochSecond(timestamp)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate();
        }

        throw new IllegalArgumentException("Unsupported date format: " + dateObj.getClass());
    }

    /**
     * Check if a string looks like an arithmetic expression
     * This helps distinguish between actual arithmetic expressions and strings that happen to contain operators
     */
    private boolean isArithmeticExpression(String value) {
        // Don't treat as arithmetic if it starts with regex-like patterns
        if (value.startsWith(".*") || value.startsWith("\\")) {
            return false;
        }

        // Don't treat as arithmetic if it starts with ^ at the beginning (regex anchor)
        // but allow ^ in the middle for power operations
        if (value.startsWith("^")) {
            return false;
        }

        // Don't treat as arithmetic if it contains regex-specific characters
        // But allow ?? for null coalescing, decimal numbers, and ^ for power operations
        if (value.contains("\\") || value.contains("$") ||
            value.contains("[") || value.contains("]") ||
            (value.contains("?") && !value.contains("??")) ||
            value.contains("|")) {
            return false;
        }

        // Allow dots only if they appear to be decimal numbers
        if (value.contains(".")) {
            // Check if dots are part of decimal numbers - allow expressions with decimal numbers
            if (!value.matches(".*\\d+\\.\\d+.*")) {
                return false;
            }
        }

        // Only treat as arithmetic if it contains operators AND looks like a mathematical expression
        boolean hasOperators = value.contains("+") || value.contains("-") ||
                              value.contains("*") || value.contains("/") ||
                              value.contains("^") || value.contains("??");

        if (!hasOperators) {
            return false;
        }

        // Additional check: should contain alphanumeric characters or underscores (variable names)
        // and not be just operators and special characters
        return value.matches(".*[a-zA-Z0-9_].*");
    }

    /**
     * Evaluate arithmetic expressions like "REQUESTED_LOAN_AMOUNT / ANNUAL_INCOME"
     */
    private Object evaluateArithmeticExpression(String expression, EvaluationContext context) {
        try {
            // Simple expression parser for basic arithmetic
            return parseAndEvaluateExpression(expression.trim(), context);
        } catch (Exception e) {
            String operationId = context.getOperationId();
            JsonLogger.error(log, operationId, "Failed to evaluate arithmetic expression: " + expression, e);
            // Return the original expression when evaluation fails
            throw e;
        }
    }

    /**
     * Parse and evaluate a simple arithmetic expression
     */
    private Object parseAndEvaluateExpression(String expression, EvaluationContext context) {
        return parseAndEvaluateExpression(expression, context, 0);
    }

    /**
     * Parse and evaluate a simple arithmetic expression with recursion depth tracking
     */
    private Object parseAndEvaluateExpression(String expression, EvaluationContext context, int depth) {
        if (depth > 50) {
            throw new IllegalArgumentException("Expression too complex or contains circular references: " + expression);
        }

        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("Expression cannot be null or empty");
        }

        expression = expression.trim();

        // Handle parentheses first - process innermost parentheses
        int maxIterations = 100; // Prevent infinite loops
        int iterations = 0;
        while (expression.contains("(") && iterations < maxIterations) {
            iterations++;
            int start = expression.lastIndexOf('(');
            int end = expression.indexOf(')', start);
            if (end == -1) {
                // Check if this is a simple case that should throw vs complex case that should be handled gracefully
                if (iterations == 1 && expression.matches(".*\\([^()]*$")) {
                    // Simple case: single unmatched opening parenthesis - throw exception
                    String operationId = context.getOperationId();
                    Map<String, Object> data = new HashMap<>();
                    data.put("expression", expression);
                    data.put("openParenIndex", start);
                    IllegalArgumentException exception = new IllegalArgumentException("Mismatched parentheses in expression: " + expression);
                    JsonLogger.error(log, operationId, "Mismatched parentheses in expression", exception, data);
                    throw exception;
                } else {
                    // Complex case: handle gracefully to prevent infinite loops
                    String operationId = context.getOperationId();
                    Map<String, Object> data = new HashMap<>();
                    data.put("expression", expression);
                    data.put("openParenIndex", start);
                    JsonLogger.warn(log, operationId, "Complex mismatched parentheses - removing unmatched opening parenthesis", data);

                    // Remove the unmatched opening parenthesis and continue
                    expression = expression.substring(0, start) + expression.substring(start + 1);
                    continue;
                }
            }

            String subExpression = expression.substring(start + 1, end);
            if (subExpression.trim().isEmpty()) {
                String operationId = context.getOperationId();
                Map<String, Object> data = new HashMap<>();
                data.put("expression", expression);
                data.put("emptyParentheses", true);
                JsonLogger.warn(log, operationId, "Empty parentheses found in expression", data);
            }

            Object subResult = parseAndEvaluateExpression(subExpression, context, depth + 1);

            // Properly replace the parenthetical expression with its result
            String beforeParen = expression.substring(0, start);
            String afterParen = expression.substring(end + 1);
            expression = beforeParen + subResult.toString() + afterParen;
        }

        // Handle any remaining unmatched closing parentheses
        while (expression.contains(")")) {
            int closeIndex = expression.indexOf(')');
            String operationId = context.getOperationId();
            Map<String, Object> data = new HashMap<>();
            data.put("expression", expression);
            data.put("closeParenIndex", closeIndex);
            JsonLogger.warn(log, operationId, "Unmatched closing parenthesis in expression - removing it", data);

            // Remove the unmatched closing parenthesis
            expression = expression.substring(0, closeIndex) + expression.substring(closeIndex + 1);
        }

        // Handle null coalescing operator (??) first (lowest precedence)
        expression = evaluateNullCoalescing(expression, context, depth + 1);

        // Handle power operations first (highest precedence)
        expression = evaluateOperators(expression, context, new String[]{"^"}, depth + 1);

        // Handle multiplication and division (higher precedence)
        expression = evaluateOperators(expression, context, new String[]{"*", "/"}, depth + 1);

        // Then handle addition and subtraction (lower precedence)
        expression = evaluateOperators(expression, context, new String[]{"+", "-"}, depth + 1);

        // Final result should be a single value - avoid infinite recursion
        String finalExpression = expression.trim();
        if (finalExpression.matches("^[0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?$")) {
            // It's a number, convert to proper numeric type
            try {
                if (finalExpression.contains(".")) {
                    return Double.parseDouble(finalExpression);
                } else {
                    long longValue = Long.parseLong(finalExpression);
                    // Return Integer if it fits in int range, otherwise Long
                    if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                        return (int) longValue;
                    } else {
                        return longValue;
                    }
                }
            } catch (NumberFormatException e) {
                return finalExpression;
            }
        } else if (finalExpression.startsWith("\"") && finalExpression.endsWith("\"")) {
            // It's a quoted string, return as-is
            return finalExpression;
        } else {
            // Try to resolve as variable, but with depth tracking
            String resolvedValue = resolveValueWithDepth(finalExpression, context, depth + 1);
            // If the resolved value is a number, convert it
            if (resolvedValue.matches("^[0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?$")) {
                try {
                    if (resolvedValue.contains(".")) {
                        return Double.parseDouble(resolvedValue);
                    } else {
                        long longValue = Long.parseLong(resolvedValue);
                        // Return Integer if it fits in int range, otherwise Long
                        if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                            return (int) longValue;
                        } else {
                            return longValue;
                        }
                    }
                } catch (NumberFormatException e) {
                    return resolvedValue;
                }
            }
            return resolvedValue;
        }
    }

    /**
     * Resolve value with recursion depth tracking to prevent infinite loops
     */
    private String resolveValueWithDepth(String name, EvaluationContext context, int depth) {
        if (depth > 50) {
            throw new IllegalArgumentException("Variable resolution too deep or contains circular references: " + name);
        }

        // For simple numeric values, return as-is
        if (name.matches("^[0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?$")) {
            return name;
        }

        // For quoted strings, return as-is
        if (name.startsWith("\"") && name.endsWith("\"")) {
            return name;
        }

        // Try to get the value from context
        Object valueObj = context.getValue(name);
        if (valueObj != null) {
            String value = valueObj.toString();
            if (!value.equals(name)) {
                // If the value is different from the name, try to resolve it further
                if (value.matches("^[0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?$") || (value.startsWith("\"") && value.endsWith("\""))) {
                    return value;
                } else {
                    return resolveValueWithDepth(value, context, depth + 1);
                }
            }
        }

        // Return the original name if we can't resolve it
        return name;
    }

    /**
     * Evaluate null coalescing operator (??)
     */
    private String evaluateNullCoalescing(String expression, EvaluationContext context) {
        return evaluateNullCoalescing(expression, context, 0);
    }

    /**
     * Evaluate null coalescing operator (??) with depth tracking
     */
    private String evaluateNullCoalescing(String expression, EvaluationContext context, int depth) {
        while (expression.contains("??")) {
            int operatorIndex = expression.indexOf("??");

            // Find the left operand
            String leftPart = expression.substring(0, operatorIndex).trim();
            String rightPart = expression.substring(operatorIndex + 2).trim();

            // Extract the immediate left operand (handle spaces and operators)
            String leftOperand = extractLeftOperand(leftPart);
            String beforeLeft = leftPart.substring(0, leftPart.length() - leftOperand.length()).trim();

            // Extract the immediate right operand
            String rightOperand = extractRightOperand(rightPart);
            String afterRight = rightPart.substring(rightOperand.length()).trim();

            // Resolve the left operand with depth tracking
            Object leftValue = resolveValueWithDepth(leftOperand.trim(), context, depth + 1);

            // If left value is null, use the right operand, otherwise use left value
            Object result = (leftValue == null) ? resolveValueWithDepth(rightOperand.trim(), context, depth + 1) : leftValue;

            // Handle null result
            if (result == null) {
                result = "null";
            }

            // Reconstruct the expression with the result
            String resultStr = result.toString();
            if (beforeLeft.isEmpty() && afterRight.isEmpty()) {
                expression = resultStr;
            } else {
                expression = beforeLeft + " " + resultStr + " " + afterRight;
            }
            expression = expression.trim();
        }
        return expression;
    }

    /**
     * Extract the leftmost operand from the end of a string
     */
    private String extractLeftOperand(String leftPart) {
        if (leftPart.isEmpty()) return "";

        // Handle parentheses - if ends with ), find matching (
        if (leftPart.endsWith(")")) {
            int parenCount = 1;
            int i = leftPart.length() - 2;
            while (i >= 0 && parenCount > 0) {
                if (leftPart.charAt(i) == ')') parenCount++;
                else if (leftPart.charAt(i) == '(') parenCount--;
                i--;
            }
            return leftPart.substring(i + 1);
        }

        // Work backwards to find the operand boundary
        int i = leftPart.length() - 1;
        while (i >= 0 && (Character.isLetterOrDigit(leftPart.charAt(i)) || leftPart.charAt(i) == '_')) {
            i--;
        }
        return leftPart.substring(i + 1);
    }

    /**
     * Extract the rightmost operand from the beginning of a string
     */
    private String extractRightOperand(String rightPart) {
        if (rightPart.isEmpty()) return "";

        // Handle parentheses - if starts with (, find matching )
        if (rightPart.startsWith("(")) {
            int parenCount = 1;
            int i = 1;
            while (i < rightPart.length() && parenCount > 0) {
                if (rightPart.charAt(i) == '(') parenCount++;
                else if (rightPart.charAt(i) == ')') parenCount--;
                i++;
            }
            return rightPart.substring(0, i);
        }

        // Work forwards to find the operand boundary
        int i = 0;
        while (i < rightPart.length() && (Character.isLetterOrDigit(rightPart.charAt(i)) || rightPart.charAt(i) == '_')) {
            i++;
        }
        return rightPart.substring(0, i);
    }

    /**
     * Evaluate operators with the same precedence level
     */
    private String evaluateOperators(String expression, EvaluationContext context, String[] operators) {
        return evaluateOperators(expression, context, operators, 0);
    }

    /**
     * Evaluate operators with the same precedence level with depth tracking
     */
    private String evaluateOperators(String expression, EvaluationContext context, String[] operators, int depth) {
        for (String op : operators) {
            // Process from left to right for same precedence operators
            int opIndex = 0;
            while ((opIndex = expression.indexOf(op, opIndex)) != -1) {
                // Skip if this operator is part of a negative number (e.g., "-5")
                if (op.equals("-") && opIndex == 0) {
                    opIndex++;
                    continue;
                }
                if (op.equals("-") && opIndex > 0 && "+-*/".contains(String.valueOf(expression.charAt(opIndex - 1)))) {
                    opIndex++;
                    continue;
                }

                // Find left operand (scan backwards)
                int leftStart = opIndex - 1;
                while (leftStart >= 0 && Character.isWhitespace(expression.charAt(leftStart))) {
                    leftStart--;
                }
                int leftEnd = leftStart + 1;
                while (leftStart >= 0 && !isOperator(expression.charAt(leftStart))) {
                    leftStart--;
                }
                leftStart++;
                String leftOperand = expression.substring(leftStart, leftEnd).trim();

                // Find right operand (scan forwards)
                int rightStart = opIndex + op.length();
                while (rightStart < expression.length() && Character.isWhitespace(expression.charAt(rightStart))) {
                    rightStart++;
                }
                int rightEnd = rightStart;
                while (rightEnd < expression.length() && !isOperator(expression.charAt(rightEnd))) {
                    rightEnd++;
                }
                String rightOperand = expression.substring(rightStart, rightEnd).trim();

                if (leftOperand.isEmpty() || rightOperand.isEmpty()) {
                    opIndex++;
                    continue;
                }

                // Resolve operands with depth tracking
                String leftValueStr = resolveValueWithDepth(leftOperand, context, depth + 1);
                String rightValueStr = resolveValueWithDepth(rightOperand, context, depth + 1);

                // Convert to numbers for arithmetic operations
                Object leftValue = convertToNumber(leftValueStr);
                Object rightValue = convertToNumber(rightValueStr);

                // Perform operation
                Object result = performArithmetic(leftValue, rightValue, op);

                // Replace the entire operation with the result
                String beforeOp = expression.substring(0, leftStart);
                String afterOp = expression.substring(rightEnd);
                expression = beforeOp + result.toString() + afterOp;

                // Reset index to start of replacement
                opIndex = leftStart;
            }
        }
        return expression;
    }

    /**
     * Check if a character is an arithmetic operator
     */
    private boolean isOperator(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/' || c == '^';
    }

    /**
     * Convert a string value to a number for arithmetic operations
     */
    private Object convertToNumber(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot convert null to number");
        }

        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            } else {
                long longValue = Long.parseLong(value);
                // Return Integer if it fits in int range, otherwise Long
                if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                    return (int) longValue;
                } else {
                    return longValue;
                }
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Arithmetic operations require numeric values");
        }
    }

    /**
     * Perform basic arithmetic operations
     */
    private Object performArithmetic(Object left, Object right, String operator) {
        if (!(left instanceof Number) || !(right instanceof Number)) {
            throw new IllegalArgumentException("Arithmetic operations require numeric values");
        }

        double leftVal = ((Number) left).doubleValue();
        double rightVal = ((Number) right).doubleValue();

        return switch (operator) {
            case "+" -> leftVal + rightVal;
            case "-" -> leftVal - rightVal;
            case "*" -> leftVal * rightVal;
            case "/" -> {
                if (rightVal == 0) {
                    throw new IllegalArgumentException("Division by zero");
                }
                yield leftVal / rightVal;
            }
            case "^" -> Math.pow(leftVal, rightVal);
            default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
        };
    }

    /**
     * Check if a string is a quoted string literal
     */
    private boolean isQuotedString(String value) {
        if (value.length() < 2) {
            return false;
        }
        return (value.startsWith("\"") && value.endsWith("\"")) ||
               (value.startsWith("'") && value.endsWith("'"));
    }

    /**
     * Extract value from quoted string with validation
     */
    private String extractQuotedStringValue(String quotedString, EvaluationContext context) {
        if (quotedString.length() < 2) {
            String operationId = context.getOperationId();
            Map<String, Object> data = new HashMap<>();
            data.put("quotedString", quotedString);
            JsonLogger.warn(log, operationId, "Invalid quoted string format", data);
            return quotedString;
        }

        char startQuote = quotedString.charAt(0);
        char endQuote = quotedString.charAt(quotedString.length() - 1);

        if (startQuote != endQuote) {
            String operationId = context.getOperationId();
            Map<String, Object> data = new HashMap<>();
            data.put("quotedString", quotedString);
            data.put("startQuote", String.valueOf(startQuote));
            data.put("endQuote", String.valueOf(endQuote));
            JsonLogger.warn(log, operationId, "Mismatched quotes in string literal", data);
            return quotedString;
        }

        return quotedString.substring(1, quotedString.length() - 1);
    }
}
