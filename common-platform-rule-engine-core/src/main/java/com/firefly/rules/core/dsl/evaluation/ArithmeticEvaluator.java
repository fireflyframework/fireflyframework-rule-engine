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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates arithmetic operations in the rule DSL.
 * Handles basic mathematical operations with proper type handling.
 */
@Component
@Slf4j
public class ArithmeticEvaluator {

    /**
     * Evaluate an arithmetic operation
     *
     * @param operation the arithmetic operation to evaluate
     * @param context the evaluation context
     * @return the computed result
     */
    public Object evaluate(Condition.ArithmeticOperation operation, EvaluationContext context) {
        if (operation == null || operation.getOperation() == null || operation.getOperands() == null) {
            throw new IllegalArgumentException("Invalid arithmetic operation");
        }

        String op = operation.getOperation().toLowerCase();
        List<Object> operands = operation.getOperands();

        String operationId = context.getOperationId();
        Map<String, Object> data = new HashMap<>();
        data.put("operation", op);
        data.put("operands", operands != null ? operands : "null");
        JsonLogger.debug(log, operationId, "Evaluating arithmetic operation", data);

        // Resolve all operands first
        List<BigDecimal> resolvedOperands = operands.stream()
                .map(operand -> resolveOperand(operand, context))
                .map(this::toBigDecimal)
                .toList();

        if (resolvedOperands.isEmpty()) {
            throw new IllegalArgumentException("Arithmetic operation requires at least one operand");
        }

        return switch (op) {
            case "add", "+" -> add(resolvedOperands);
            case "subtract", "-" -> subtract(resolvedOperands);
            case "multiply", "*" -> multiply(resolvedOperands);
            case "divide", "/" -> divide(resolvedOperands);
            case "modulo", "mod", "%" -> modulo(resolvedOperands);
            case "power", "pow", "**" -> power(resolvedOperands);
            case "abs" -> abs(resolvedOperands);
            case "min" -> min(resolvedOperands);
            case "max" -> max(resolvedOperands);
            case "round" -> round(resolvedOperands);
            case "floor" -> floor(resolvedOperands);
            case "ceil" -> ceil(resolvedOperands);
            default -> throw new IllegalArgumentException("Unknown arithmetic operation: " + op);
        };
    }

    /**
     * Resolve an operand value (may be a variable reference or literal)
     */
    private Object resolveOperand(Object operand, EvaluationContext context) {
        if (operand instanceof String) {
            String stringOperand = (String) operand;

            // Handle plain variable names (check if it exists in context)
            Object value = context.getValue(stringOperand);
            if (value != null) {
                return value;
            }

            // If not a variable, try to parse as a number
            try {
                return new BigDecimal(stringOperand);
            } catch (NumberFormatException e) {
                // If it's not a number and not a variable, treat as literal string
                return stringOperand;
            }
        }
        return operand;
    }

    /**
     * Convert an object to BigDecimal for arithmetic operations
     */
    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot perform arithmetic on null value");
        }
        
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        
        if (value instanceof String) {
            try {
                return new BigDecimal((String) value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Cannot convert string to number: " + value);
            }
        }
        
        throw new IllegalArgumentException("Cannot convert to number: " + value.getClass());
    }

    // Arithmetic operation implementations
    private BigDecimal add(List<BigDecimal> operands) {
        return operands.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal subtract(List<BigDecimal> operands) {
        if (operands.size() < 2) {
            throw new IllegalArgumentException("Subtract operation requires at least 2 operands");
        }
        
        BigDecimal result = operands.get(0);
        for (int i = 1; i < operands.size(); i++) {
            result = result.subtract(operands.get(i));
        }
        return result;
    }

    private BigDecimal multiply(List<BigDecimal> operands) {
        return operands.stream().reduce(BigDecimal.ONE, BigDecimal::multiply);
    }

    private BigDecimal divide(List<BigDecimal> operands) {
        if (operands.size() < 2) {
            throw new IllegalArgumentException("Divide operation requires at least 2 operands");
        }
        
        BigDecimal result = operands.get(0);
        for (int i = 1; i < operands.size(); i++) {
            BigDecimal divisor = operands.get(i);
            if (divisor.compareTo(BigDecimal.ZERO) == 0) {
                throw new ArithmeticException("Division by zero");
            }
            result = result.divide(divisor, 10, RoundingMode.HALF_UP);
        }
        return result;
    }

    private BigDecimal modulo(List<BigDecimal> operands) {
        if (operands.size() != 2) {
            throw new IllegalArgumentException("Modulo operation requires exactly 2 operands");
        }
        
        BigDecimal dividend = operands.get(0);
        BigDecimal divisor = operands.get(1);
        
        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("Modulo by zero");
        }
        
        return dividend.remainder(divisor);
    }

    private BigDecimal power(List<BigDecimal> operands) {
        if (operands.size() != 2) {
            throw new IllegalArgumentException("Power operation requires exactly 2 operands");
        }
        
        BigDecimal base = operands.get(0);
        BigDecimal exponent = operands.get(1);
        
        // For simplicity, convert to double for power operation
        double result = Math.pow(base.doubleValue(), exponent.doubleValue());
        return BigDecimal.valueOf(result);
    }

    private BigDecimal abs(List<BigDecimal> operands) {
        if (operands.size() != 1) {
            throw new IllegalArgumentException("Abs operation requires exactly 1 operand");
        }
        return operands.get(0).abs();
    }

    private BigDecimal min(List<BigDecimal> operands) {
        if (operands.isEmpty()) {
            throw new IllegalArgumentException("Min operation requires at least 1 operand");
        }
        return operands.stream().min(BigDecimal::compareTo).orElseThrow();
    }

    private BigDecimal max(List<BigDecimal> operands) {
        if (operands.isEmpty()) {
            throw new IllegalArgumentException("Max operation requires at least 1 operand");
        }
        return operands.stream().max(BigDecimal::compareTo).orElseThrow();
    }

    private BigDecimal round(List<BigDecimal> operands) {
        if (operands.size() < 1 || operands.size() > 2) {
            throw new IllegalArgumentException("Round operation requires 1 or 2 operands");
        }
        
        BigDecimal value = operands.get(0);
        int scale = operands.size() > 1 ? operands.get(1).intValue() : 0;
        
        return value.setScale(scale, RoundingMode.HALF_UP);
    }

    private BigDecimal floor(List<BigDecimal> operands) {
        if (operands.size() != 1) {
            throw new IllegalArgumentException("Floor operation requires exactly 1 operand");
        }
        return operands.get(0).setScale(0, RoundingMode.DOWN);
    }

    private BigDecimal ceil(List<BigDecimal> operands) {
        if (operands.size() != 1) {
            throw new IllegalArgumentException("Ceil operation requires exactly 1 operand");
        }
        return operands.get(0).setScale(0, RoundingMode.UP);
    }
}
