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

import com.firefly.rules.core.dsl.ASTVisitor;
import com.firefly.rules.core.dsl.action.*;
import com.firefly.rules.core.dsl.condition.ComparisonCondition;
import com.firefly.rules.core.dsl.condition.ExpressionCondition;
import com.firefly.rules.core.dsl.condition.LogicalCondition;
import com.firefly.rules.core.dsl.expression.*;
import com.firefly.rules.core.services.JsonPathService;
import com.firefly.rules.core.services.RestCallService;
import lombok.extern.slf4j.Slf4j;

/**
 * AST visitor that executes actions.
 * Replaces the string-based action execution with structured AST traversal.
 */
@Slf4j
public class ActionExecutor implements ASTVisitor<Void> {

    private final EvaluationContext context;
    private final ExpressionEvaluator expressionEvaluator;

    public ActionExecutor(EvaluationContext context) {
        this.context = context;
        this.expressionEvaluator = new ExpressionEvaluator(context);
    }

    public ActionExecutor(EvaluationContext context, RestCallService restCallService, JsonPathService jsonPathService) {
        this.context = context;
        this.expressionEvaluator = new ExpressionEvaluator(context, restCallService, jsonPathService);
    }
    
    // Action visitors
    
    @Override
    public Void visitAssignmentAction(AssignmentAction node) {
        Object value = node.getValue().accept(expressionEvaluator);
        
        switch (node.getOperator()) {
            case ASSIGN -> context.setComputedVariable(node.getVariableName(), value);
            case ADD_ASSIGN -> {
                Object currentValue = context.getVariable(node.getVariableName());
                if (currentValue instanceof Number && value instanceof Number) {
                    java.math.BigDecimal current = toBigDecimal(currentValue);
                    java.math.BigDecimal addValue = toBigDecimal(value);
                    context.setComputedVariable(node.getVariableName(), current.add(addValue));
                } else {
                    context.setComputedVariable(node.getVariableName(),
                        currentValue.toString() + value.toString());
                }
            }
            case SUBTRACT_ASSIGN -> {
                Object currentValue = context.getVariable(node.getVariableName());
                if (currentValue instanceof Number && value instanceof Number) {
                    java.math.BigDecimal current = toBigDecimal(currentValue);
                    java.math.BigDecimal subtractValue = toBigDecimal(value);
                    context.setComputedVariable(node.getVariableName(), current.subtract(subtractValue));
                }
            }
            case MULTIPLY_ASSIGN -> {
                Object currentValue = context.getVariable(node.getVariableName());
                if (currentValue instanceof Number && value instanceof Number) {
                    java.math.BigDecimal current = toBigDecimal(currentValue);
                    java.math.BigDecimal multiplyValue = toBigDecimal(value);
                    context.setComputedVariable(node.getVariableName(), current.multiply(multiplyValue));
                }
            }
            case DIVIDE_ASSIGN -> {
                Object currentValue = context.getVariable(node.getVariableName());
                if (currentValue instanceof Number && value instanceof Number) {
                    java.math.BigDecimal current = toBigDecimal(currentValue);
                    java.math.BigDecimal divisor = toBigDecimal(value);
                    if (divisor.compareTo(java.math.BigDecimal.ZERO) != 0) {
                        context.setComputedVariable(node.getVariableName(),
                            current.divide(divisor, 10, java.math.RoundingMode.HALF_UP));
                    } else {
                        log.error("Division by zero in assignment action");
                    }
                }
            }
        }
        
        log.debug("Executed assignment: {} {} {}", 
            node.getVariableName(), node.getOperator().getSymbol(), value);
        return null;
    }
    
    @Override
    public Void visitFunctionCallAction(FunctionCallAction node) {
        // Evaluate arguments
        Object[] args = node.getArguments().stream()
                .map(arg -> arg.accept(expressionEvaluator))
                .toArray();
        
        // Execute function call
        Object result = executeFunction(node.getFunctionName(), args);
        log.info("Executing function call: {} with {} arguments",
            node.getFunctionName(), args.length);

        // If there's a result variable, store the result
        if (node.hasResultVariable()) {
            context.setComputedVariable(node.getResultVariable(), result);
        }
        
        return null;
    }
    
    @Override
    public Void visitConditionalAction(ConditionalAction node) {
        // Evaluate condition
        Object conditionResult = node.getCondition().accept(expressionEvaluator);
        boolean conditionMet = toBoolean(conditionResult);
        
        log.debug("Conditional action condition evaluated to: {}", conditionMet);
        
        if (conditionMet) {
            // Execute then actions
            if (node.getThenActions() != null) {
                for (Action action : node.getThenActions()) {
                    action.accept(this);
                }
            }
        } else {
            // Execute else actions
            if (node.getElseActions() != null) {
                for (Action action : node.getElseActions()) {
                    action.accept(this);
                }
            }
        }
        
        return null;
    }
    
    @Override
    public Void visitCalculateAction(CalculateAction node) {
        Object result = node.getExpression().accept(expressionEvaluator);
        context.setComputedVariable(node.getResultVariable(), result);
        
        log.debug("Calculated {} = {}", node.getResultVariable(), result);
        return null;
    }
    
    @Override
    public Void visitSetAction(SetAction node) {
        Object value = node.getValue().accept(expressionEvaluator);
        context.setComputedVariable(node.getVariableName(), value);
        
        log.debug("Set {} = {}", node.getVariableName(), value);
        return null;
    }
    
    // Expression visitors (not used in action execution)
    
    @Override
    public Void visitBinaryExpression(BinaryExpression node) {
        throw new UnsupportedOperationException("Expressions cannot be executed as actions");
    }
    
    @Override
    public Void visitUnaryExpression(UnaryExpression node) {
        throw new UnsupportedOperationException("Expressions cannot be executed as actions");
    }
    
    @Override
    public Void visitVariableExpression(VariableExpression node) {
        throw new UnsupportedOperationException("Expressions cannot be executed as actions");
    }
    
    @Override
    public Void visitLiteralExpression(LiteralExpression node) {
        throw new UnsupportedOperationException("Expressions cannot be executed as actions");
    }
    
    @Override
    public Void visitFunctionCallExpression(FunctionCallExpression node) {
        throw new UnsupportedOperationException("Expressions cannot be executed as actions");
    }
    
    @Override
    public Void visitArithmeticExpression(ArithmeticExpression node) {
        throw new UnsupportedOperationException("Expressions cannot be executed as actions");
    }
    
    // Condition visitors (not used in action execution)
    
    @Override
    public Void visitComparisonCondition(ComparisonCondition node) {
        throw new UnsupportedOperationException("Conditions cannot be executed as actions");
    }
    
    @Override
    public Void visitLogicalCondition(LogicalCondition node) {
        throw new UnsupportedOperationException("Conditions cannot be executed as actions");
    }
    
    @Override
    public Void visitExpressionCondition(ExpressionCondition node) {
        throw new UnsupportedOperationException("Conditions cannot be executed as actions");
    }
    
    // Helper methods
    
    private boolean toBoolean(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).doubleValue() != 0;
        if (value instanceof String) return !((String) value).isEmpty();
        return true;
    }

    private java.math.BigDecimal toBigDecimal(Object value) {
        if (value == null) return java.math.BigDecimal.ZERO;
        if (value instanceof java.math.BigDecimal) return (java.math.BigDecimal) value;
        if (value instanceof Number) return java.math.BigDecimal.valueOf(((Number) value).doubleValue());
        try {
            return new java.math.BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return java.math.BigDecimal.ZERO;
        }
    }

    private Object executeFunction(String functionName, Object[] args) {
        // Built-in action functions
        return switch (functionName.toLowerCase()) {
            case "log", "print" -> {
                String message = args.length > 0 ? args[0].toString() : "";
                String level = args.length > 1 ? args[1].toString() : "INFO";
                log.info("[{}] {}", level, message);
                yield null;
            }
            case "validate", "check" -> {
                // Simple validation function
                boolean isValid = args.length > 0 ? toBoolean(args[0]) : false;
                String message = args.length > 1 ? args[1].toString() : "Validation";
                if (!isValid) {
                    log.warn("Validation failed: {}", message);
                }
                yield isValid;
            }
            case "format" -> {
                // Simple string formatting
                String template = args.length > 0 ? args[0].toString() : "";
                for (int i = 1; i < args.length; i++) {
                    template = template.replace("{" + (i-1) + "}", args[i].toString());
                }
                yield template;
            }
            case "notify", "alert" -> {
                String message = args.length > 0 ? args[0].toString() : "";
                log.info("NOTIFICATION: {}", message);
                yield null;
            }
            case "audit_log" -> {
                String event = args.length > 0 ? args[0].toString() : "";
                String details = args.length > 1 ? args[1].toString() : "";
                log.info("AUDIT: {} - {}", event, details);
                yield null;
            }
            case "send_notification" -> {
                String recipient = args.length > 0 ? args[0].toString() : "";
                String message = args.length > 1 ? args[1].toString() : "";
                log.info("NOTIFICATION TO {}: {}", recipient, message);
                yield null;
            }
            // Delegate calculation functions to expression evaluator
            case "calculate_loan_payment", "calculate_compound_interest", "calculate_amortization",
                 "debt_to_income_ratio", "credit_utilization", "loan_to_value", "calculate_apr",
                 "calculate_credit_score", "calculate_risk_score", "payment_history_score",
                 "is_valid_credit_score", "is_valid_ssn", "is_valid_account", "is_valid_routing",
                 "is_business_day", "age_meets_requirement", "format_currency", "format_percentage",
                 "generate_account_number", "generate_transaction_id", "distance_between",
                 "time_hour", "is_valid", "in_range" -> {
                // These functions are handled by the expression evaluator
                yield expressionEvaluator.visitFunctionCallExpression(
                    new FunctionCallExpression(
                        null, functionName,
                        java.util.Arrays.stream(args)
                            .map(arg -> new LiteralExpression(null, arg))
                            .collect(java.util.stream.Collectors.toList())
                    )
                );
            }
            default -> {
                log.warn("Unknown function: {}", functionName);
                yield null;
            }
        };
    }

    @Override
    public Void visitArithmeticAction(ArithmeticAction node) {
        log.debug("Executing arithmetic action: {} {} {} {}",
                 node.getOperation().getKeyword(), node.getValue(),
                 node.getOperation().getPreposition(), node.getVariableName());

        Object value = node.getValue().accept(expressionEvaluator);
        Object current = context.getVariable(node.getVariableName());

        if (current instanceof Number && value instanceof Number) {
            java.math.BigDecimal currentNum = toBigDecimal(current);
            java.math.BigDecimal valueNum = toBigDecimal(value);
            java.math.BigDecimal result;

            switch (node.getOperation()) {
                case ADD -> result = currentNum.add(valueNum);
                case SUBTRACT -> result = currentNum.subtract(valueNum);
                case MULTIPLY -> result = currentNum.multiply(valueNum);
                case DIVIDE -> {
                    if (valueNum.compareTo(java.math.BigDecimal.ZERO) != 0) {
                        result = currentNum.divide(valueNum, 10, java.math.RoundingMode.HALF_UP);
                    } else {
                        log.warn("Division by zero attempted in arithmetic action");
                        result = null;
                    }
                }
                default -> {
                    log.warn("Unknown arithmetic operation: {}", node.getOperation());
                    result = current instanceof java.math.BigDecimal ? (java.math.BigDecimal) current : toBigDecimal(current);
                }
            }

            context.setComputedVariable(node.getVariableName(), result);
        } else {
            log.warn("Arithmetic action requires numeric operands: {} ({}), {} ({})",
                    current, current != null ? current.getClass().getSimpleName() : "null",
                    value, value != null ? value.getClass().getSimpleName() : "null");
        }

        return null;
    }

    @Override
    public Void visitListAction(ListAction node) {
        log.debug("Executing list action: {} {} {} {}",
                 node.getOperation().getKeyword(), node.getValue(),
                 node.getOperation().getPreposition(), node.getListVariable());

        Object value = node.getValue().accept(expressionEvaluator);
        Object current = context.getVariable(node.getListVariable());

        java.util.List<Object> list;
        if (current instanceof java.util.List) {
            list = new java.util.ArrayList<>((java.util.List<?>) current);
        } else {
            list = new java.util.ArrayList<>();
            if (current != null) {
                list.add(current);
            }
        }

        switch (node.getOperation()) {
            case APPEND -> list.add(value);
            case PREPEND -> list.add(0, value);
            case REMOVE -> list.remove(value);
        }

        context.setComputedVariable(node.getListVariable(), list);
        return null;
    }

    @Override
    public Void visitCircuitBreakerAction(CircuitBreakerAction node) {
        log.info("Circuit breaker triggered: {}", node.getMessage());

        // Create a circuit breaker exception to stop rule execution
        throw new RuntimeException("Circuit breaker: " + node.getMessage());
    }

    @Override
    public Void visitJsonPathExpression(JsonPathExpression node) {
        // Expressions don't execute actions, so return null
        return null;
    }

    @Override
    public Void visitRestCallExpression(RestCallExpression node) {
        // Expressions don't execute actions, so return null
        return null;
    }
}
