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

package com.firefly.rules.core.dsl.ast.visitor;

import com.firefly.rules.core.dsl.ast.ASTVisitor;
import com.firefly.rules.core.dsl.ast.action.*;
import com.firefly.rules.core.dsl.ast.condition.ComparisonCondition;
import com.firefly.rules.core.dsl.ast.condition.ExpressionCondition;
import com.firefly.rules.core.dsl.ast.condition.LogicalCondition;
import com.firefly.rules.core.dsl.ast.expression.*;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

/**
 * AST visitor that evaluates expressions to their values.
 * Replaces the string-based expression evaluation with structured AST traversal.
 */
@Slf4j
public class ExpressionEvaluator implements ASTVisitor<Object> {
    
    private final EvaluationContext context;
    
    public ExpressionEvaluator(EvaluationContext context) {
        this.context = context;
    }
    
    // Expression visitors
    
    @Override
    public Object visitBinaryExpression(BinaryExpression node) {
        Object leftValue = node.getLeft().accept(this);
        Object rightValue = node.getRight().accept(this);
        
        return switch (node.getOperator()) {
            case ADD -> add(leftValue, rightValue);
            case SUBTRACT -> subtract(leftValue, rightValue);
            case MULTIPLY -> multiply(leftValue, rightValue);
            case DIVIDE -> divide(leftValue, rightValue);
            case MODULO -> modulo(leftValue, rightValue);
            case POWER -> power(leftValue, rightValue);
            case EQUALS -> equals(leftValue, rightValue);
            case NOT_EQUALS -> !equals(leftValue, rightValue);
            case GREATER_THAN -> greaterThan(leftValue, rightValue);
            case LESS_THAN -> lessThan(leftValue, rightValue);
            case GREATER_EQUAL -> greaterThanOrEqual(leftValue, rightValue);
            case LESS_EQUAL -> lessThanOrEqual(leftValue, rightValue);
            case CONTAINS -> contains(leftValue, rightValue);
            case NOT_CONTAINS -> !contains(leftValue, rightValue);
            case STARTS_WITH -> startsWith(leftValue, rightValue);
            case ENDS_WITH -> endsWith(leftValue, rightValue);
            case MATCHES -> matches(leftValue, rightValue);
            case NOT_MATCHES -> !matches(leftValue, rightValue);
            case IN_LIST -> inList(leftValue, rightValue);
            case NOT_IN_LIST -> !inList(leftValue, rightValue);
            case BETWEEN -> throw new IllegalStateException("BETWEEN operator should be handled in ComparisonCondition, not BinaryExpression");
            case NOT_BETWEEN -> throw new IllegalStateException("NOT_BETWEEN operator should be handled in ComparisonCondition, not BinaryExpression");
            case AND -> toBoolean(leftValue) && toBoolean(rightValue);
            case OR -> toBoolean(leftValue) || toBoolean(rightValue);
            // Handle additional aliases and operators
            case LESS_THAN_OR_EQUAL -> lessThanOrEqual(leftValue, rightValue);
            case GREATER_THAN_OR_EQUAL -> greaterThanOrEqual(leftValue, rightValue);
            case IN -> inList(leftValue, rightValue);
        };
    }
    
    @Override
    public Object visitUnaryExpression(UnaryExpression node) {
        Object operandValue = node.getOperand().accept(this);
        
        return switch (node.getOperator()) {
            case NEGATE -> negate(operandValue);
            case POSITIVE -> operandValue; // No-op for positive
            case NOT -> !toBoolean(operandValue);
            case EXISTS -> operandValue != null;
            case IS_NULL -> operandValue == null;
            case IS_NOT_NULL -> operandValue != null;
            case IS_NUMBER -> operandValue instanceof Number;
            case IS_STRING -> operandValue instanceof String;
            case IS_BOOLEAN -> operandValue instanceof Boolean;
            case IS_LIST -> operandValue instanceof List;
            case TO_UPPER -> operandValue != null ? operandValue.toString().toUpperCase() : null;
            case TO_LOWER -> operandValue != null ? operandValue.toString().toLowerCase() : null;
            case TRIM -> operandValue != null ? operandValue.toString().trim() : null;
            case LENGTH -> operandValue != null ? operandValue.toString().length() : 0;
            // Handle aliases
            case MINUS -> negate(operandValue);
            case PLUS -> operandValue; // No-op for positive
        };
    }
    
    @Override
    public Object visitVariableExpression(VariableExpression node) {
        Object value = context.getVariable(node.getVariableName());
        
        // Handle property access
        if (node.hasPropertyAccess() && value != null) {
            String propertyPath = String.join(".", node.getPropertyPath());
            return evaluatePropertyAccess(value, propertyPath);
        }
        
        // Handle index access
        if (node.hasIndexAccess() && value instanceof List) {
            Object indexValue = node.getIndexExpression().accept(this);
            if (indexValue instanceof Number) {
                int index = ((Number) indexValue).intValue();
                List<?> list = (List<?>) value;
                if (index >= 0 && index < list.size()) {
                    return list.get(index);
                }
            }
        }
        
        return value;
    }
    
    @Override
    public Object visitLiteralExpression(LiteralExpression node) {
        return node.getValue();
    }
    
    @Override
    public Object visitFunctionCallExpression(FunctionCallExpression node) {
        String functionName = node.getFunctionName();
        Object[] args = node.hasArguments() ?
            node.getArguments().stream().map(arg -> arg.accept(this)).toArray() :
            new Object[0];

        // Built-in mathematical functions
        return switch (functionName.toLowerCase()) {
            case "max" -> evaluateMax(args);
            case "min" -> evaluateMin(args);
            case "abs" -> evaluateAbs(args);
            case "round" -> evaluateRound(args);
            case "ceil" -> evaluateCeil(args);
            case "floor" -> evaluateFloor(args);
            case "sqrt" -> evaluateSqrt(args);
            case "pow" -> evaluatePow(args);

            // String functions
            case "length", "len" -> evaluateLength(args);
            case "substring", "substr" -> evaluateSubstring(args);
            case "upper", "uppercase" -> evaluateUpper(args);
            case "lower", "lowercase" -> evaluateLower(args);
            case "trim" -> evaluateTrim(args);
            case "contains" -> evaluateContains(args);
            case "startswith" -> evaluateStartsWith(args);
            case "endswith" -> evaluateEndsWith(args);
            case "replace" -> evaluateReplace(args);

            // Date/time functions
            case "now" -> evaluateNow(args);
            case "today" -> evaluateToday(args);
            case "dateadd" -> evaluateDateAdd(args);
            case "datediff" -> evaluateDateDiff(args);

            // List functions
            case "size", "count" -> evaluateSize(args);
            case "sum" -> evaluateSum(args);
            case "avg", "average" -> evaluateAverage(args);
            case "first" -> evaluateFirst(args);
            case "last" -> evaluateLast(args);

            // Type conversion functions
            case "tonumber", "number" -> evaluateToNumber(args);
            case "tostring", "string" -> evaluateToString(args);
            case "toboolean", "boolean" -> evaluateToBoolean(args);

            // Financial calculation functions
            case "calculate_loan_payment" -> calculateLoanPayment(args);
            case "calculate_compound_interest" -> calculateCompoundInterest(args);
            case "calculate_amortization" -> calculateAmortization(args);
            case "debt_to_income_ratio" -> debtToIncomeRatio(args);
            case "credit_utilization" -> creditUtilization(args);
            case "loan_to_value" -> loanToValue(args);
            case "calculate_apr" -> calculateAPR(args);
            case "calculate_credit_score" -> calculateCreditScore(args);
            case "calculate_risk_score" -> calculateRiskScore(args);
            case "payment_history_score" -> paymentHistoryScore(args);

            // Financial validation functions
            case "is_valid_credit_score" -> isValidCreditScore(args);
            case "is_valid_ssn" -> isValidSSN(args);
            case "is_valid_account" -> isValidAccount(args);
            case "is_valid_routing" -> isValidRouting(args);
            case "is_business_day" -> isBusinessDayFunction(args);
            case "age_meets_requirement" -> ageMeetsRequirement(args);

            // Utility functions
            case "format_currency" -> formatCurrency(args);
            case "format_percentage" -> formatPercentage(args);
            case "generate_account_number" -> generateAccountNumber(args);
            case "generate_transaction_id" -> generateTransactionId(args);
            case "distance_between" -> distanceBetween(args);
            case "time_hour" -> timeHour(args);
            case "is_valid" -> isValid(args);
            case "in_range" -> inRange(args);

            // Logging/Auditing functions
            case "audit" -> audit(args);
            case "audit_log" -> auditLog(args);
            case "send_notification" -> sendNotification(args);

            // Data Security functions
            case "encrypt" -> encrypt(args);
            case "decrypt" -> decrypt(args);
            case "mask_data" -> maskData(args);

            // Advanced Financial functions
            case "calculate_debt_ratio" -> calculateDebtRatio(args);
            case "calculate_ltv" -> calculateLTV(args);
            case "calculate_payment_schedule" -> calculatePaymentSchedule(args);

            default -> {
                log.warn("Unknown function: {}", functionName);
                yield null;
            }
        };
    }
    
    @Override
    public Object visitArithmeticExpression(ArithmeticExpression node) {
        List<Object> operandValues = node.getOperands().stream()
                .map(operand -> operand.accept(this))
                .toList();
        
        return switch (node.getOperation()) {
            case ADD -> operandValues.stream()
                    .map(this::toNumber)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            case MULTIPLY -> operandValues.stream()
                    .map(this::toNumber)
                    .reduce(BigDecimal.ONE, BigDecimal::multiply);
            case MIN -> operandValues.stream()
                    .map(this::toNumber)
                    .min(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
            case MAX -> operandValues.stream()
                    .map(this::toNumber)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
            case SUM -> operandValues.stream()
                    .map(this::toNumber)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            case AVERAGE -> {
                BigDecimal sum = operandValues.stream()
                        .map(this::toNumber)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                yield sum.divide(BigDecimal.valueOf(operandValues.size()), 10, BigDecimal.ROUND_HALF_UP);
            }
            default -> {
                if (operandValues.size() >= 2) {
                    BigDecimal first = toNumber(operandValues.get(0));
                    BigDecimal second = toNumber(operandValues.get(1));
                    yield switch (node.getOperation()) {
                        case SUBTRACT -> first.subtract(second);
                        case DIVIDE -> first.divide(second, 10, BigDecimal.ROUND_HALF_UP);
                        case MODULO -> first.remainder(second);
                        case POWER -> BigDecimal.valueOf(Math.pow(first.doubleValue(), second.doubleValue()));
                        default -> first;
                    };
                } else if (operandValues.size() == 1) {
                    BigDecimal value = toNumber(operandValues.get(0));
                    yield switch (node.getOperation()) {
                        case ABS -> value.abs();
                        case ROUND -> BigDecimal.valueOf(Math.round(value.doubleValue()));
                        case FLOOR -> BigDecimal.valueOf(Math.floor(value.doubleValue()));
                        case CEIL -> BigDecimal.valueOf(Math.ceil(value.doubleValue()));
                        case SQRT -> BigDecimal.valueOf(Math.sqrt(value.doubleValue()));
                        default -> value;
                    };
                } else {
                    yield BigDecimal.ZERO;
                }
            }
        };
    }
    
    // Condition visitors (return Boolean)
    
    @Override
    public Object visitComparisonCondition(ComparisonCondition node) {
        Object leftValue = node.getLeft().accept(this);
        Object rightValue = node.getRight() != null ? node.getRight().accept(this) : null;
        Object rangeEndValue = node.getRangeEnd() != null ? node.getRangeEnd().accept(this) : null;
        
        return switch (node.getOperator()) {
            case EQUALS -> equals(leftValue, rightValue);
            case NOT_EQUALS -> !equals(leftValue, rightValue);
            case GREATER_THAN -> greaterThan(leftValue, rightValue);
            case LESS_THAN -> lessThan(leftValue, rightValue);
            case GREATER_EQUAL -> greaterThanOrEqual(leftValue, rightValue);
            case LESS_EQUAL -> lessThanOrEqual(leftValue, rightValue);
            case CONTAINS -> contains(leftValue, rightValue);
            case NOT_CONTAINS -> !contains(leftValue, rightValue);
            case STARTS_WITH -> startsWith(leftValue, rightValue);
            case ENDS_WITH -> endsWith(leftValue, rightValue);
            case MATCHES -> matches(leftValue, rightValue);
            case NOT_MATCHES -> !matches(leftValue, rightValue);
            case IN_LIST -> inList(leftValue, rightValue);
            case NOT_IN_LIST -> !inList(leftValue, rightValue);
            case BETWEEN -> between(leftValue, rightValue, rangeEndValue);
            case NOT_BETWEEN -> !between(leftValue, rightValue, rangeEndValue);
            case EXISTS -> leftValue != null;
            case NOT_EXISTS -> leftValue == null;
            case IS_NULL -> leftValue == null;
            case IS_NOT_NULL -> leftValue != null;

            // Basic validation operators
            case IS_EMPTY -> isEmpty(leftValue);
            case IS_NOT_EMPTY -> !isEmpty(leftValue);
            case IS_NUMERIC -> isNumeric(leftValue);
            case IS_NOT_NUMERIC -> !isNumeric(leftValue);
            case IS_EMAIL -> isEmail(leftValue);
            case IS_PHONE -> isPhone(leftValue);
            case IS_DATE -> isDate(leftValue);

            // Financial validation operators
            case IS_POSITIVE -> isPositive(leftValue);
            case IS_NEGATIVE -> isNegative(leftValue);
            case IS_ZERO -> isZero(leftValue);
            case IS_PERCENTAGE -> isPercentage(leftValue);
            case IS_CURRENCY -> isCurrency(leftValue);
            case IS_CREDIT_SCORE -> isCreditScore(leftValue);
            case IS_SSN -> isSSN(leftValue);
            case IS_ACCOUNT_NUMBER -> isAccountNumber(leftValue);
            case IS_ROUTING_NUMBER -> isRoutingNumber(leftValue);

            // Date/time validation operators
            case IS_BUSINESS_DAY -> isBusinessDay(leftValue);
            case IS_WEEKEND -> isWeekend(leftValue);
            case AGE_AT_LEAST -> ageAtLeast(leftValue, rightValue);
            case AGE_LESS_THAN -> ageLessThan(leftValue, rightValue);

            // Length operators
            case LENGTH_EQUALS -> lengthEquals(leftValue, rightValue);
            case LENGTH_GREATER_THAN -> lengthGreaterThan(leftValue, rightValue);
            case LENGTH_LESS_THAN -> lengthLessThan(leftValue, rightValue);

            // Additional financial operators
            case IS_NON_ZERO -> isNonZero(leftValue);

            // Handle aliases
            case LESS_THAN_OR_EQUAL -> lessThanOrEqual(leftValue, rightValue);
            case GREATER_THAN_OR_EQUAL -> greaterThanOrEqual(leftValue, rightValue);
            case IN -> inList(leftValue, rightValue);
            case NOT_IN -> !inList(leftValue, rightValue);
        };
    }
    
    @Override
    public Object visitLogicalCondition(LogicalCondition node) {
        return switch (node.getOperator()) {
            case AND -> node.getOperands().stream()
                    .allMatch(operand -> toBoolean(operand.accept(this)));
            case OR -> node.getOperands().stream()
                    .anyMatch(operand -> toBoolean(operand.accept(this)));
            case NOT -> !toBoolean(node.getOperands().get(0).accept(this));
        };
    }
    
    @Override
    public Object visitExpressionCondition(ExpressionCondition node) {
        return toBoolean(node.getExpression().accept(this));
    }
    
    // Action visitors (not used in expression evaluation)
    
    @Override
    public Object visitAssignmentAction(AssignmentAction node) {
        throw new UnsupportedOperationException("Actions cannot be evaluated as expressions");
    }
    
    @Override
    public Object visitFunctionCallAction(FunctionCallAction node) {
        throw new UnsupportedOperationException("Actions cannot be evaluated as expressions");
    }
    
    @Override
    public Object visitConditionalAction(ConditionalAction node) {
        throw new UnsupportedOperationException("Actions cannot be evaluated as expressions");
    }
    
    @Override
    public Object visitCalculateAction(CalculateAction node) {
        throw new UnsupportedOperationException("Actions cannot be evaluated as expressions");
    }
    
    @Override
    public Object visitSetAction(SetAction node) {
        throw new UnsupportedOperationException("Actions cannot be evaluated as expressions");
    }

    @Override
    public Object visitArithmeticAction(ArithmeticAction node) {
        throw new UnsupportedOperationException("Actions cannot be evaluated as expressions");
    }

    @Override
    public Object visitListAction(ListAction node) {
        throw new UnsupportedOperationException("Actions cannot be evaluated as expressions");
    }

    @Override
    public Object visitCircuitBreakerAction(CircuitBreakerAction node) {
        throw new UnsupportedOperationException("Actions cannot be evaluated as expressions");
    }

    // Helper methods for operations
    
    private Object add(Object left, Object right) {
        if (left instanceof String || right instanceof String) {
            return toString(left) + toString(right);
        }
        return toNumber(left).add(toNumber(right));
    }
    
    private Object subtract(Object left, Object right) {
        return toNumber(left).subtract(toNumber(right));
    }
    
    private Object multiply(Object left, Object right) {
        return toNumber(left).multiply(toNumber(right));
    }
    
    private Object divide(Object left, Object right) {
        BigDecimal rightNum = toNumber(right);
        if (rightNum.compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("Division by zero");
        }
        return toNumber(left).divide(rightNum, 10, BigDecimal.ROUND_HALF_UP);
    }
    
    private Object modulo(Object left, Object right) {
        return toNumber(left).remainder(toNumber(right));
    }
    
    private Object power(Object left, Object right) {
        return BigDecimal.valueOf(Math.pow(toNumber(left).doubleValue(), toNumber(right).doubleValue()));
    }
    
    private Object negate(Object value) {
        return toNumber(value).negate();
    }
    
    private boolean equals(Object left, Object right) {
        if (left == null && right == null) return true;
        if (left == null || right == null) return false;
        
        if (left instanceof Number && right instanceof Number) {
            return toNumber(left).compareTo(toNumber(right)) == 0;
        }
        
        return left.equals(right);
    }
    
    private boolean greaterThan(Object left, Object right) {
        return toNumber(left).compareTo(toNumber(right)) > 0;
    }
    
    private boolean lessThan(Object left, Object right) {
        return toNumber(left).compareTo(toNumber(right)) < 0;
    }
    
    private boolean greaterThanOrEqual(Object left, Object right) {
        return toNumber(left).compareTo(toNumber(right)) >= 0;
    }
    
    private boolean lessThanOrEqual(Object left, Object right) {
        return toNumber(left).compareTo(toNumber(right)) <= 0;
    }
    
    private boolean contains(Object left, Object right) {
        return toString(left).contains(toString(right));
    }
    
    private boolean startsWith(Object left, Object right) {
        return toString(left).startsWith(toString(right));
    }
    
    private boolean endsWith(Object left, Object right) {
        return toString(left).endsWith(toString(right));
    }
    
    private boolean matches(Object left, Object right) {
        try {
            String leftStr = toString(left);
            String rightStr = toString(right);

            Pattern pattern = Pattern.compile(rightStr);
            // Use find() for partial matching, which is more common in DSLs
            return pattern.matcher(leftStr).find();
        } catch (Exception e) {
            log.warn("Invalid regex pattern: {}", right);
            return false;
        }
    }
    
    private boolean inList(Object left, Object right) {
        if (right instanceof List) {
            return ((List<?>) right).contains(left);
        }
        return false;
    }
    
    private boolean between(Object value, Object min, Object max) {
        if (max == null) return false;
        BigDecimal val = toNumber(value);
        BigDecimal minVal = toNumber(min);
        BigDecimal maxVal = toNumber(max);
        return val.compareTo(minVal) >= 0 && val.compareTo(maxVal) <= 0;
    }
    
    private BigDecimal toNumber(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
    
    private boolean toBoolean(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).doubleValue() != 0;
        if (value instanceof String) return !((String) value).isEmpty();
        return true;
    }
    
    private String toString(Object value) {
        return value != null ? value.toString() : "";
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    // Built-in function implementations

    private Object evaluateMax(Object[] args) {
        if (args.length == 0) return null;
        BigDecimal max = toBigDecimal(args[0]);
        for (int i = 1; i < args.length; i++) {
            BigDecimal current = toBigDecimal(args[i]);
            if (current != null && (max == null || current.compareTo(max) > 0)) {
                max = current;
            }
        }
        return max;
    }

    private Object evaluateMin(Object[] args) {
        if (args.length == 0) return null;
        BigDecimal min = toBigDecimal(args[0]);
        for (int i = 1; i < args.length; i++) {
            BigDecimal current = toBigDecimal(args[i]);
            if (current != null && (min == null || current.compareTo(min) < 0)) {
                min = current;
            }
        }
        return min;
    }

    private Object evaluateAbs(Object[] args) {
        if (args.length != 1) return null;
        BigDecimal value = toBigDecimal(args[0]);
        return value != null ? value.abs() : null;
    }

    private Object evaluateRound(Object[] args) {
        if (args.length == 0 || args.length > 2) return null;
        BigDecimal value = toBigDecimal(args[0]);
        if (value == null) return null;

        int scale = args.length > 1 ? toBigDecimal(args[1]).intValue() : 0;
        return value.setScale(scale, java.math.RoundingMode.HALF_UP);
    }

    private Object evaluateCeil(Object[] args) {
        if (args.length != 1) return null;
        BigDecimal value = toBigDecimal(args[0]);
        return value != null ? value.setScale(0, java.math.RoundingMode.CEILING) : null;
    }

    private Object evaluateFloor(Object[] args) {
        if (args.length != 1) return null;
        BigDecimal value = toBigDecimal(args[0]);
        return value != null ? value.setScale(0, java.math.RoundingMode.FLOOR) : null;
    }

    private Object evaluateSqrt(Object[] args) {
        if (args.length != 1) return null;
        BigDecimal value = toBigDecimal(args[0]);
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) return null;
        return BigDecimal.valueOf(Math.sqrt(value.doubleValue()));
    }

    private Object evaluatePow(Object[] args) {
        if (args.length != 2) return null;
        BigDecimal base = toBigDecimal(args[0]);
        BigDecimal exponent = toBigDecimal(args[1]);
        if (base == null || exponent == null) return null;
        return BigDecimal.valueOf(Math.pow(base.doubleValue(), exponent.doubleValue()));
    }

    // String functions

    private Object evaluateLength(Object[] args) {
        if (args.length != 1) return null;
        String str = toString(args[0]);
        return BigDecimal.valueOf(str.length());
    }

    private Object evaluateSubstring(Object[] args) {
        if (args.length < 2 || args.length > 3) return null;
        String str = toString(args[0]);
        int start = toBigDecimal(args[1]).intValue();
        int end = args.length > 2 ? toBigDecimal(args[2]).intValue() : str.length();

        try {
            return str.substring(Math.max(0, start), Math.min(str.length(), end));
        } catch (Exception e) {
            return "";
        }
    }

    private Object evaluateUpper(Object[] args) {
        if (args.length != 1) return null;
        return toString(args[0]).toUpperCase();
    }

    private Object evaluateLower(Object[] args) {
        if (args.length != 1) return null;
        return toString(args[0]).toLowerCase();
    }

    private Object evaluateTrim(Object[] args) {
        if (args.length != 1) return null;
        return toString(args[0]).trim();
    }

    private Object evaluateContains(Object[] args) {
        if (args.length != 2) return null;
        String str = toString(args[0]);
        String search = toString(args[1]);
        return str.contains(search);
    }

    private Object evaluateStartsWith(Object[] args) {
        if (args.length != 2) return null;
        String str = toString(args[0]);
        String prefix = toString(args[1]);
        return str.startsWith(prefix);
    }

    private Object evaluateEndsWith(Object[] args) {
        if (args.length != 2) return null;
        String str = toString(args[0]);
        String suffix = toString(args[1]);
        return str.endsWith(suffix);
    }

    private Object evaluateReplace(Object[] args) {
        if (args.length != 3) return null;
        String str = toString(args[0]);
        String search = toString(args[1]);
        String replacement = toString(args[2]);
        return str.replace(search, replacement);
    }

    // Date/time functions (simplified implementations)

    private Object evaluateNow(Object[] args) {
        return java.time.LocalDateTime.now().toString();
    }

    private Object evaluateToday(Object[] args) {
        return java.time.LocalDate.now().toString();
    }

    private Object evaluateDateAdd(Object[] args) {
        if (args.length != 3) {
            log.warn("DateAdd function requires 3 arguments: date, amount, unit");
            return null;
        }

        try {
            // Parse the date
            java.time.LocalDate date = parseDate(args[0]);
            if (date == null) {
                log.warn("Invalid date format in dateadd: {}", args[0]);
                return null;
            }

            // Parse the amount
            int amount = toNumber(args[1]).intValue();

            // Parse the unit
            String unit = toString(args[2]).toLowerCase();

            java.time.LocalDate result = switch (unit) {
                case "days", "day", "d" -> date.plusDays(amount);
                case "weeks", "week", "w" -> date.plusWeeks(amount);
                case "months", "month", "m" -> date.plusMonths(amount);
                case "years", "year", "y" -> date.plusYears(amount);
                default -> {
                    log.warn("Unsupported date unit in dateadd: {}. Supported units: days, weeks, months, years", unit);
                    yield null;
                }
            };

            return result != null ? result.toString() : null;

        } catch (Exception e) {
            log.warn("Error in dateadd function: {}", e.getMessage());
            return null;
        }
    }

    private Object evaluateDateDiff(Object[] args) {
        if (args.length < 2 || args.length > 3) {
            log.warn("DateDiff function requires 2 or 3 arguments: date1, date2, [unit]");
            return null;
        }

        try {
            // Parse the dates
            java.time.LocalDate date1 = parseDate(args[0]);
            java.time.LocalDate date2 = parseDate(args[1]);

            if (date1 == null || date2 == null) {
                log.warn("Invalid date format in datediff: {} or {}", args[0], args[1]);
                return null;
            }

            // Parse the unit (default to days)
            String unit = args.length > 2 ? toString(args[2]).toLowerCase() : "days";

            java.time.Period period = java.time.Period.between(date1, date2);
            long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(date1, date2);

            return switch (unit) {
                case "days", "day", "d" -> BigDecimal.valueOf(daysDiff);
                case "weeks", "week", "w" -> BigDecimal.valueOf(daysDiff / 7);
                case "months", "month", "m" -> BigDecimal.valueOf(period.toTotalMonths());
                case "years", "year", "y" -> BigDecimal.valueOf(period.getYears());
                default -> {
                    log.warn("Unsupported date unit in datediff: {}. Supported units: days, weeks, months, years", unit);
                    yield null;
                }
            };

        } catch (Exception e) {
            log.warn("Error in datediff function: {}", e.getMessage());
            return null;
        }
    }

    // List functions

    private Object evaluateSize(Object[] args) {
        if (args.length != 1) return null;
        Object value = args[0];
        if (value instanceof List) {
            return BigDecimal.valueOf(((List<?>) value).size());
        }
        if (value instanceof String) {
            return BigDecimal.valueOf(((String) value).length());
        }
        return BigDecimal.ZERO;
    }

    private Object evaluateSum(Object[] args) {
        if (args.length != 1) return null;
        Object value = args[0];
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            BigDecimal sum = BigDecimal.ZERO;
            for (Object item : list) {
                BigDecimal num = toBigDecimal(item);
                if (num != null) {
                    sum = sum.add(num);
                }
            }
            return sum;
        }
        return BigDecimal.ZERO;
    }

    private Object evaluateAverage(Object[] args) {
        if (args.length != 1) return null;
        Object value = args[0];
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.isEmpty()) return BigDecimal.ZERO;

            BigDecimal sum = BigDecimal.ZERO;
            int count = 0;
            for (Object item : list) {
                BigDecimal num = toBigDecimal(item);
                if (num != null) {
                    sum = sum.add(num);
                    count++;
                }
            }
            return count > 0 ? sum.divide(BigDecimal.valueOf(count), 10, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;
        }
        return BigDecimal.ZERO;
    }

    private Object evaluateFirst(Object[] args) {
        if (args.length != 1) return null;
        Object value = args[0];
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            return list.isEmpty() ? null : list.get(0);
        }
        return null;
    }

    private Object evaluateLast(Object[] args) {
        if (args.length != 1) return null;
        Object value = args[0];
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            return list.isEmpty() ? null : list.get(list.size() - 1);
        }
        return null;
    }

    // Type conversion functions

    private Object evaluateToNumber(Object[] args) {
        if (args.length != 1) return null;
        return toBigDecimal(args[0]);
    }

    private Object evaluateToString(Object[] args) {
        if (args.length != 1) return null;
        return toString(args[0]);
    }

    private Object evaluateToBoolean(Object[] args) {
        if (args.length != 1) return null;
        return toBoolean(args[0]);
    }

    // Property access implementation

    private Object evaluatePropertyAccess(Object object, String propertyPath) {
        if (object == null || propertyPath == null) return null;

        String[] parts = propertyPath.split("\\.");
        Object current = object;

        for (String part : parts) {
            if (current == null) return null;

            // Handle Map access
            if (current instanceof java.util.Map) {
                current = ((java.util.Map<?, ?>) current).get(part);
            }
            // Handle List access with numeric index
            else if (current instanceof List && part.matches("\\d+")) {
                List<?> list = (List<?>) current;
                int index = Integer.parseInt(part);
                current = (index >= 0 && index < list.size()) ? list.get(index) : null;
            }
            // Handle bean property access using reflection
            else {
                current = getPropertyValue(current, part);
            }
        }

        return current;
    }

    private Object getPropertyValue(Object object, String propertyName) {
        try {
            // Try getter method first
            String getterName = "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
            java.lang.reflect.Method getter = object.getClass().getMethod(getterName);
            return getter.invoke(object);
        } catch (Exception e) {
            try {
                // Try boolean getter
                String booleanGetterName = "is" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
                java.lang.reflect.Method booleanGetter = object.getClass().getMethod(booleanGetterName);
                return booleanGetter.invoke(object);
            } catch (Exception e2) {
                try {
                    // Try direct field access
                    java.lang.reflect.Field field = object.getClass().getDeclaredField(propertyName);
                    field.setAccessible(true);
                    return field.get(object);
                } catch (Exception e3) {
                    log.warn("Could not access property '{}' on object of type {}", propertyName, object.getClass().getSimpleName());
                    return null;
                }
            }
        }
    }

    // Validation helper methods

    private boolean isEmpty(Object value) {
        if (value == null) return true;
        if (value instanceof String) return ((String) value).trim().isEmpty();
        if (value instanceof java.util.Collection) return ((java.util.Collection<?>) value).isEmpty();
        if (value instanceof java.util.Map) return ((java.util.Map<?, ?>) value).isEmpty();
        if (value.getClass().isArray()) return java.lang.reflect.Array.getLength(value) == 0;
        return false;
    }

    private boolean isNumeric(Object value) {
        if (value == null) return false;
        if (value instanceof Number) return true;
        if (value instanceof String) {
            try {
                new java.math.BigDecimal((String) value);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    private boolean isEmail(Object value) {
        if (value == null) return false;
        String str = toString(value);
        // Basic email validation regex
        String emailRegex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
        return str.matches(emailRegex);
    }

    private boolean isPhone(Object value) {
        if (value == null) return false;
        String str = toString(value).replaceAll("[\\s\\-\\(\\)\\.]", "");
        // Basic phone validation - 10 or 11 digits, optionally starting with +1
        return str.matches("^(\\+?1)?[0-9]{10}$");
    }

    private boolean isDate(Object value) {
        if (value == null) return false;
        if (value instanceof java.time.LocalDate || value instanceof java.time.LocalDateTime ||
            value instanceof java.util.Date) return true;

        String str = toString(value);
        // Try common date formats
        String[] dateFormats = {
            "\\d{4}-\\d{2}-\\d{2}",           // YYYY-MM-DD
            "\\d{2}/\\d{2}/\\d{4}",           // MM/DD/YYYY
            "\\d{2}-\\d{2}-\\d{4}",           // MM-DD-YYYY
            "\\d{4}/\\d{2}/\\d{2}"            // YYYY/MM/DD
        };

        for (String format : dateFormats) {
            if (str.matches(format)) return true;
        }
        return false;
    }

    /**
     * Parse a date from various formats
     */
    private java.time.LocalDate parseDate(Object value) {
        if (value == null) return null;

        // Handle already parsed date objects
        if (value instanceof java.time.LocalDate) {
            return (java.time.LocalDate) value;
        }
        if (value instanceof java.time.LocalDateTime) {
            return ((java.time.LocalDateTime) value).toLocalDate();
        }
        if (value instanceof java.util.Date) {
            return ((java.util.Date) value).toInstant()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate();
        }

        // Parse string dates
        String str = toString(value);

        // Try ISO format first (YYYY-MM-DD)
        try {
            return java.time.LocalDate.parse(str);
        } catch (Exception ignored) {}

        // Try other common formats
        java.time.format.DateTimeFormatter[] formatters = {
            java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            java.time.format.DateTimeFormatter.ofPattern("MM-dd-yyyy"),
            java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")
        };

        for (java.time.format.DateTimeFormatter formatter : formatters) {
            try {
                return java.time.LocalDate.parse(str, formatter);
            } catch (Exception ignored) {}
        }

        return null;
    }

    private boolean isPositive(Object value) {
        if (!isNumeric(value)) return false;
        BigDecimal num = toNumber(value);
        return num.compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean isNegative(Object value) {
        if (!isNumeric(value)) return false;
        BigDecimal num = toNumber(value);
        return num.compareTo(BigDecimal.ZERO) < 0;
    }

    private boolean isZero(Object value) {
        if (!isNumeric(value)) return false;
        BigDecimal num = toNumber(value);
        return num.compareTo(BigDecimal.ZERO) == 0;
    }

    private boolean isPercentage(Object value) {
        if (!isNumeric(value)) return false;
        BigDecimal num = toNumber(value);
        return num.compareTo(BigDecimal.ZERO) >= 0 && num.compareTo(BigDecimal.valueOf(100)) <= 0;
    }

    private boolean isCurrency(Object value) {
        if (value == null) return false;
        String str = toString(value);
        // Basic currency validation - optional $ sign, digits with optional decimal
        return str.matches("^\\$?\\d+(\\.\\d{2})?$");
    }

    private boolean isCreditScore(Object value) {
        if (!isNumeric(value)) return false;
        BigDecimal num = toNumber(value);
        return num.compareTo(BigDecimal.valueOf(300)) >= 0 && num.compareTo(BigDecimal.valueOf(850)) <= 0;
    }

    private boolean isSSN(Object value) {
        if (value == null) return false;
        String str = toString(value).replaceAll("[\\s\\-]", "");
        // SSN validation - 9 digits
        return str.matches("^\\d{9}$");
    }

    private boolean isAccountNumber(Object value) {
        if (value == null) return false;
        String str = toString(value).replaceAll("[\\s\\-]", "");
        // Account number validation - 8 to 17 digits
        return str.matches("^\\d{8,17}$");
    }

    private boolean isRoutingNumber(Object value) {
        if (value == null) return false;
        String str = toString(value).replaceAll("[\\s\\-]", "");
        // Routing number validation - exactly 9 digits with checksum
        if (!str.matches("^\\d{9}$")) return false;

        // ABA routing number checksum validation
        int[] weights = {3, 7, 1, 3, 7, 1, 3, 7, 1};
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += Character.getNumericValue(str.charAt(i)) * weights[i];
        }
        return sum % 10 == 0;
    }

    private boolean isBusinessDay(Object value) {
        if (value == null) return false;

        try {
            java.time.LocalDate date;
            if (value instanceof java.time.LocalDate) {
                date = (java.time.LocalDate) value;
            } else if (value instanceof java.time.LocalDateTime) {
                date = ((java.time.LocalDateTime) value).toLocalDate();
            } else {
                // Try to parse string as date
                String str = toString(value);
                date = java.time.LocalDate.parse(str);
            }

            java.time.DayOfWeek dayOfWeek = date.getDayOfWeek();
            return dayOfWeek != java.time.DayOfWeek.SATURDAY && dayOfWeek != java.time.DayOfWeek.SUNDAY;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isWeekend(Object value) {
        return !isBusinessDay(value) && isDate(value);
    }

    private boolean ageAtLeast(Object birthDateValue, Object minAgeValue) {
        if (birthDateValue == null || minAgeValue == null) return false;

        try {
            java.time.LocalDate birthDate;
            if (birthDateValue instanceof java.time.LocalDate) {
                birthDate = (java.time.LocalDate) birthDateValue;
            } else {
                String str = toString(birthDateValue);
                birthDate = java.time.LocalDate.parse(str);
            }

            int minAge = toNumber(minAgeValue).intValue();
            java.time.LocalDate now = java.time.LocalDate.now();
            int actualAge = java.time.Period.between(birthDate, now).getYears();

            return actualAge >= minAge;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean ageLessThan(Object birthDateValue, Object maxAgeValue) {
        if (birthDateValue == null || maxAgeValue == null) return false;

        try {
            java.time.LocalDate birthDate;
            if (birthDateValue instanceof java.time.LocalDate) {
                birthDate = (java.time.LocalDate) birthDateValue;
            } else {
                String str = toString(birthDateValue);
                birthDate = java.time.LocalDate.parse(str);
            }

            int maxAge = toNumber(maxAgeValue).intValue();
            java.time.LocalDate now = java.time.LocalDate.now();
            int actualAge = java.time.Period.between(birthDate, now).getYears();

            return actualAge < maxAge;
        } catch (Exception e) {
            return false;
        }
    }

    // Length validation operators

    private boolean lengthEquals(Object value, Object expectedLength) {
        if (value == null || expectedLength == null) return false;

        int actualLength = getLength(value);
        int expected = toNumber(expectedLength).intValue();

        return actualLength == expected;
    }

    private boolean lengthGreaterThan(Object value, Object minLength) {
        if (value == null || minLength == null) return false;

        int actualLength = getLength(value);
        int min = toNumber(minLength).intValue();

        return actualLength > min;
    }

    private boolean lengthLessThan(Object value, Object maxLength) {
        if (value == null || maxLength == null) return false;

        int actualLength = getLength(value);
        int max = toNumber(maxLength).intValue();

        return actualLength < max;
    }

    private int getLength(Object value) {
        if (value instanceof String) {
            return ((String) value).length();
        }
        if (value instanceof List) {
            return ((List<?>) value).size();
        }
        if (value instanceof Object[]) {
            return ((Object[]) value).length;
        }
        return 0;
    }

    // Additional financial operators

    private boolean isNonZero(Object value) {
        if (value == null) return false;

        try {
            BigDecimal number = toNumber(value);
            return number.compareTo(BigDecimal.ZERO) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    // Financial calculation functions

    private Object calculateLoanPayment(Object[] args) {
        if (args.length < 3) return null;
        try {
            BigDecimal principal = toNumber(args[0]);
            BigDecimal annualRate = toNumber(args[1]);
            int termMonths = toNumber(args[2]).intValue();

            if (annualRate.compareTo(BigDecimal.ZERO) == 0) {
                return principal.divide(BigDecimal.valueOf(termMonths), 2, BigDecimal.ROUND_HALF_UP);
            }

            BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(1200), 10, BigDecimal.ROUND_HALF_UP);
            BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
            BigDecimal onePlusRPowN = onePlusR.pow(termMonths);

            BigDecimal numerator = principal.multiply(monthlyRate).multiply(onePlusRPowN);
            BigDecimal denominator = onePlusRPowN.subtract(BigDecimal.ONE);

            BigDecimal result = numerator.divide(denominator, 2, BigDecimal.ROUND_HALF_UP);

            // If 4th parameter is provided, it's the result variable name - store the result
            if (args.length >= 4 && args[3] != null && context != null) {
                String resultVar = args[3].toString();
                context.setComputedVariable(resultVar, result);
            }

            return result;
        } catch (Exception e) {
            log.warn("Error calculating loan payment: {}", e.getMessage());
            return null;
        }
    }

    private Object calculateCompoundInterest(Object[] args) {
        if (args.length < 4) return null;
        try {
            BigDecimal principal = toNumber(args[0]);
            BigDecimal annualRate = toNumber(args[1]);
            int compoundingFrequency = toNumber(args[2]).intValue();
            int years = toNumber(args[3]).intValue();

            BigDecimal rate = annualRate.divide(BigDecimal.valueOf(100 * compoundingFrequency), 10, BigDecimal.ROUND_HALF_UP);
            int totalPeriods = compoundingFrequency * years;

            BigDecimal onePlusRate = BigDecimal.ONE.add(rate);
            BigDecimal compoundFactor = onePlusRate.pow(totalPeriods);

            return principal.multiply(compoundFactor);
        } catch (Exception e) {
            log.warn("Error calculating compound interest: {}", e.getMessage());
            return null;
        }
    }

    private Object calculateAmortization(Object[] args) {
        if (args.length < 3) return null;
        try {
            BigDecimal principal = toNumber(args[0]);
            BigDecimal annualRate = toNumber(args[1]);
            int termMonths = toNumber(args[2]).intValue();

            BigDecimal monthlyPayment = (BigDecimal) calculateLoanPayment(args);
            if (monthlyPayment == null) return null;

            BigDecimal totalPayments = monthlyPayment.multiply(BigDecimal.valueOf(termMonths));
            BigDecimal totalInterest = totalPayments.subtract(principal);

            java.util.Map<String, Object> result = new java.util.HashMap<>();
            result.put("monthly_payment", monthlyPayment);
            result.put("total_payments", totalPayments);
            result.put("total_interest", totalInterest);
            result.put("principal", principal);

            return result;
        } catch (Exception e) {
            log.warn("Error calculating amortization: {}", e.getMessage());
            return null;
        }
    }

    private Object debtToIncomeRatio(Object[] args) {
        if (args.length < 2) return null;
        try {
            BigDecimal monthlyDebt = toNumber(args[0]);
            BigDecimal monthlyIncome = toNumber(args[1]);

            if (monthlyIncome.compareTo(BigDecimal.ZERO) == 0) return null;

            // Return as decimal ratio (0.333), not percentage (33.33)
            return monthlyDebt.divide(monthlyIncome, 4, BigDecimal.ROUND_HALF_UP);
        } catch (Exception e) {
            log.warn("Error calculating debt-to-income ratio: {}", e.getMessage());
            return null;
        }
    }

    private Object creditUtilization(Object[] args) {
        if (args.length < 2) return null;
        try {
            BigDecimal currentBalance = toNumber(args[0]);
            BigDecimal creditLimit = toNumber(args[1]);

            if (creditLimit.compareTo(BigDecimal.ZERO) == 0) return null;

            return currentBalance.divide(creditLimit, 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        } catch (Exception e) {
            log.warn("Error calculating credit utilization: {}", e.getMessage());
            return null;
        }
    }

    private Object loanToValue(Object[] args) {
        if (args.length < 2) return null;
        try {
            BigDecimal loanAmount = toNumber(args[0]);
            BigDecimal propertyValue = toNumber(args[1]);

            if (propertyValue.compareTo(BigDecimal.ZERO) == 0) return null;

            return loanAmount.divide(propertyValue, 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        } catch (Exception e) {
            log.warn("Error calculating loan-to-value ratio: {}", e.getMessage());
            return null;
        }
    }

    private Object calculateAPR(Object[] args) {
        if (args.length < 4) return null;
        try {
            BigDecimal loanAmount = toNumber(args[0]);
            BigDecimal totalFees = toNumber(args[1]);
            BigDecimal monthlyPayment = toNumber(args[2]);
            int termMonths = toNumber(args[3]).intValue();

            // Simplified APR calculation
            BigDecimal totalPayments = monthlyPayment.multiply(BigDecimal.valueOf(termMonths));
            BigDecimal totalCost = totalPayments.add(totalFees);
            BigDecimal totalInterest = totalCost.subtract(loanAmount);

            BigDecimal avgBalance = loanAmount.divide(BigDecimal.valueOf(2), 10, BigDecimal.ROUND_HALF_UP);
            BigDecimal annualInterest = totalInterest.divide(BigDecimal.valueOf(termMonths / 12.0), 10, BigDecimal.ROUND_HALF_UP);

            return annualInterest.divide(avgBalance, 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        } catch (Exception e) {
            log.warn("Error calculating APR: {}", e.getMessage());
            return null;
        }
    }

    private Object calculateCreditScore(Object[] args) {
        // Simplified credit score calculation based on common factors
        if (args.length < 5) return null;
        try {
            BigDecimal paymentHistory = toNumber(args[0]); // 0-100
            BigDecimal creditUtilization = toNumber(args[1]); // 0-100
            BigDecimal creditHistoryLength = toNumber(args[2]); // years
            BigDecimal creditMix = toNumber(args[3]); // 0-100
            BigDecimal newCredit = toNumber(args[4]); // 0-100

            // FICO score weights: Payment History (35%), Credit Utilization (30%),
            // Credit History Length (15%), Credit Mix (10%), New Credit (10%)
            BigDecimal score = paymentHistory.multiply(BigDecimal.valueOf(0.35))
                    .add(creditUtilization.multiply(BigDecimal.valueOf(0.30)))
                    .add(creditHistoryLength.multiply(BigDecimal.valueOf(3)).multiply(BigDecimal.valueOf(0.15))) // Scale years
                    .add(creditMix.multiply(BigDecimal.valueOf(0.10)))
                    .add(newCredit.multiply(BigDecimal.valueOf(0.10)));

            // Scale to 300-850 range
            BigDecimal scaledScore = score.multiply(BigDecimal.valueOf(5.5)).add(BigDecimal.valueOf(300));

            // Ensure within valid range
            if (scaledScore.compareTo(BigDecimal.valueOf(300)) < 0) return 300;
            if (scaledScore.compareTo(BigDecimal.valueOf(850)) > 0) return 850;

            return scaledScore.intValue();
        } catch (Exception e) {
            log.warn("Error calculating credit score: {}", e.getMessage());
            return null;
        }
    }

    private Object calculateRiskScore(Object[] args) {
        // Simplified risk score calculation
        if (args.length < 4) return null;
        try {
            BigDecimal creditScore = toNumber(args[0]);
            BigDecimal debtToIncome = toNumber(args[1]);
            BigDecimal employmentYears = toNumber(args[2]);
            BigDecimal loanToValue = toNumber(args[3]);

            BigDecimal riskScore = BigDecimal.ZERO;

            // Credit score factor (lower score = higher risk)
            if (creditScore.compareTo(BigDecimal.valueOf(650)) < 0) {
                riskScore = riskScore.add(BigDecimal.valueOf(30));
            } else if (creditScore.compareTo(BigDecimal.valueOf(700)) < 0) {
                riskScore = riskScore.add(BigDecimal.valueOf(15));
            }

            // Debt-to-income factor
            if (debtToIncome.compareTo(BigDecimal.valueOf(40)) > 0) {
                riskScore = riskScore.add(BigDecimal.valueOf(25));
            } else if (debtToIncome.compareTo(BigDecimal.valueOf(30)) > 0) {
                riskScore = riskScore.add(BigDecimal.valueOf(10));
            }

            // Employment stability factor
            if (employmentYears.compareTo(BigDecimal.valueOf(2)) < 0) {
                riskScore = riskScore.add(BigDecimal.valueOf(20));
            }

            // Loan-to-value factor
            if (loanToValue.compareTo(BigDecimal.valueOf(80)) > 0) {
                riskScore = riskScore.add(BigDecimal.valueOf(15));
            }

            return riskScore;
        } catch (Exception e) {
            log.warn("Error calculating risk score: {}", e.getMessage());
            return null;
        }
    }

    private Object paymentHistoryScore(Object[] args) {
        if (args.length < 3) return null;
        try {
            int totalPayments = toNumber(args[0]).intValue();
            int latePayments = toNumber(args[1]).intValue();
            int missedPayments = toNumber(args[2]).intValue();

            if (totalPayments == 0) return 0;

            BigDecimal onTimePayments = BigDecimal.valueOf(totalPayments - latePayments - missedPayments);
            BigDecimal onTimeRatio = onTimePayments.divide(BigDecimal.valueOf(totalPayments), 4, BigDecimal.ROUND_HALF_UP);

            // Penalize late and missed payments more heavily
            BigDecimal latePenalty = BigDecimal.valueOf(latePayments * 5);
            BigDecimal missedPenalty = BigDecimal.valueOf(missedPayments * 15);

            BigDecimal score = onTimeRatio.multiply(BigDecimal.valueOf(100))
                    .subtract(latePenalty)
                    .subtract(missedPenalty);

            // Ensure score is between 0 and 100
            if (score.compareTo(BigDecimal.ZERO) < 0) return 0;
            if (score.compareTo(BigDecimal.valueOf(100)) > 0) return 100;

            return score;
        } catch (Exception e) {
            log.warn("Error calculating payment history score: {}", e.getMessage());
            return null;
        }
    }

    // Financial validation functions

    private Object isValidCreditScore(Object[] args) {
        if (args.length < 1) return false;
        return isCreditScore(args[0]);
    }

    private Object isValidSSN(Object[] args) {
        if (args.length < 1) return false;
        return isSSN(args[0]);
    }

    private Object isValidAccount(Object[] args) {
        if (args.length < 1) return false;
        return isAccountNumber(args[0]);
    }

    private Object isValidRouting(Object[] args) {
        if (args.length < 1) return false;
        return isRoutingNumber(args[0]);
    }

    private Object isBusinessDayFunction(Object[] args) {
        if (args.length < 1) return false;
        return isBusinessDay(args[0]);
    }

    private Object ageMeetsRequirement(Object[] args) {
        if (args.length < 2) return false;
        return ageAtLeast(args[0], args[1]);
    }

    // Utility functions

    private Object formatCurrency(Object[] args) {
        if (args.length < 1) return null;
        try {
            BigDecimal amount = toNumber(args[0]);
            String currency = args.length > 1 ? toString(args[1]) : "USD";

            java.text.NumberFormat formatter = java.text.NumberFormat.getCurrencyInstance();
            if ("USD".equals(currency)) {
                formatter = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.US);
            }

            return formatter.format(amount);
        } catch (Exception e) {
            log.warn("Error formatting currency: {}", e.getMessage());
            return null;
        }
    }

    private Object formatPercentage(Object[] args) {
        if (args.length < 1) return null;
        try {
            BigDecimal value = toNumber(args[0]);
            int decimals = args.length > 1 ? toNumber(args[1]).intValue() : 2;

            java.text.NumberFormat formatter = java.text.NumberFormat.getPercentInstance();
            formatter.setMaximumFractionDigits(decimals);
            formatter.setMinimumFractionDigits(decimals);

            return formatter.format(value.divide(BigDecimal.valueOf(100), 10, BigDecimal.ROUND_HALF_UP));
        } catch (Exception e) {
            log.warn("Error formatting percentage: {}", e.getMessage());
            return null;
        }
    }

    private Object generateAccountNumber(Object[] args) {
        // Generate a random 12-digit account number
        java.util.Random random = new java.util.Random();
        StringBuilder accountNumber = new StringBuilder();

        for (int i = 0; i < 12; i++) {
            accountNumber.append(random.nextInt(10));
        }

        return accountNumber.toString();
    }

    private Object generateTransactionId(Object[] args) {
        // Generate a UUID-based transaction ID
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private Object distanceBetween(Object[] args) {
        if (args.length < 4) return null;
        try {
            double lat1 = toNumber(args[0]).doubleValue();
            double lon1 = toNumber(args[1]).doubleValue();
            double lat2 = toNumber(args[2]).doubleValue();
            double lon2 = toNumber(args[3]).doubleValue();

            // Haversine formula for calculating distance between two points on Earth
            double R = 6371; // Earth's radius in kilometers

            double dLat = Math.toRadians(lat2 - lat1);
            double dLon = Math.toRadians(lon2 - lon1);

            double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                    Math.sin(dLon / 2) * Math.sin(dLon / 2);

            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
            double distance = R * c;

            return BigDecimal.valueOf(distance);
        } catch (Exception e) {
            log.warn("Error calculating distance: {}", e.getMessage());
            return null;
        }
    }

    private Object timeHour(Object[] args) {
        if (args.length < 1) return null;
        try {
            String timestamp = toString(args[0]);

            // Try to parse as ISO timestamp
            if (timestamp.contains("T")) {
                java.time.LocalDateTime dateTime = java.time.LocalDateTime.parse(timestamp.substring(0, 19));
                return dateTime.getHour();
            }

            // Try to parse as time only (HH:mm:ss)
            if (timestamp.contains(":")) {
                String[] parts = timestamp.split(":");
                return Integer.parseInt(parts[0]);
            }

            return null;
        } catch (Exception e) {
            log.warn("Error extracting hour from timestamp: {}", e.getMessage());
            return null;
        }
    }

    private Object isValid(Object[] args) {
        if (args.length < 2) return false;
        try {
            Object value = args[0];
            String validationType = toString(args[1]);

            return switch (validationType.toLowerCase()) {
                case "email", "email_format" -> isEmail(value);
                case "phone", "phone_format" -> isPhone(value);
                case "ssn", "ssn_format" -> isSSN(value);
                case "credit_score" -> isCreditScore(value);
                case "account_number" -> isAccountNumber(value);
                case "routing_number" -> isRoutingNumber(value);
                case "numeric" -> isNumeric(value);
                case "date" -> isDate(value);
                case "positive" -> isPositive(value);
                case "negative" -> isNegative(value);
                case "currency" -> isCurrency(value);
                case "percentage" -> isPercentage(value);
                default -> false;
            };
        } catch (Exception e) {
            log.warn("Error in validation: {}", e.getMessage());
            return false;
        }
    }

    private Object inRange(Object[] args) {
        if (args.length < 3) return false;
        try {
            BigDecimal value = toNumber(args[0]);
            BigDecimal min = toNumber(args[1]);
            BigDecimal max = toNumber(args[2]);

            return value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
        } catch (Exception e) {
            log.warn("Error checking range: {}", e.getMessage());
            return false;
        }
    }

    // Logging/Auditing functions

    private Object audit(Object[] args) {
        if (args.length < 2) return null;

        String event = toString(args[0]);
        String details = toString(args[1]);

        log.info("AUDIT: {} - {}", event, details);

        // In a real implementation, this would write to an audit log
        return true;
    }

    private Object auditLog(Object[] args) {
        if (args.length < 1) return null;

        String message = toString(args[0]);
        String level = args.length > 1 ? toString(args[1]) : "INFO";

        switch (level.toUpperCase()) {
            case "ERROR" -> log.error("AUDIT: {}", message);
            case "WARN" -> log.warn("AUDIT: {}", message);
            case "DEBUG" -> log.debug("AUDIT: {}", message);
            default -> log.info("AUDIT: {}", message);
        }

        return true;
    }

    private Object sendNotification(Object[] args) {
        if (args.length < 2) return null;

        String recipient = toString(args[0]);
        String message = toString(args[1]);
        String type = args.length > 2 ? toString(args[2]) : "EMAIL";

        log.info("NOTIFICATION [{}] to {}: {}", type, recipient, message);

        // In a real implementation, this would send actual notifications
        return true;
    }

    // Data Security functions

    private Object encrypt(Object[] args) {
        if (args.length < 1) return null;

        String data = toString(args[0]);
        String algorithm = args.length > 1 ? toString(args[1]) : "AES";

        // Simplified encryption simulation
        String encrypted = "ENC[" + algorithm + "]:" + java.util.Base64.getEncoder().encodeToString(data.getBytes());

        log.debug("Encrypted data using {}", algorithm);
        return encrypted;
    }

    private Object decrypt(Object[] args) {
        if (args.length < 1) return null;

        String encryptedData = toString(args[0]);

        // Simplified decryption simulation
        if (encryptedData.startsWith("ENC[") && encryptedData.contains("]:")) {
            String base64Data = encryptedData.substring(encryptedData.indexOf("]:") + 2);
            try {
                String decrypted = new String(java.util.Base64.getDecoder().decode(base64Data));
                log.debug("Decrypted data");
                return decrypted;
            } catch (Exception e) {
                log.warn("Failed to decrypt data: {}", e.getMessage());
                return null;
            }
        }

        return encryptedData; // Return as-is if not encrypted
    }

    private Object maskData(Object[] args) {
        if (args.length < 1) return null;

        String data = toString(args[0]);
        String maskChar = args.length > 1 ? toString(args[1]) : "*";
        int visibleChars = args.length > 2 ? toNumber(args[2]).intValue() : 4;

        if (data.length() <= visibleChars) {
            return maskChar.repeat(data.length());
        }

        String masked = data.substring(0, visibleChars) + maskChar.repeat(data.length() - visibleChars);
        log.debug("Masked data, showing {} characters", visibleChars);
        return masked;
    }

    // Advanced Financial functions

    private Object calculateDebtRatio(Object[] args) {
        if (args.length < 2) return null;

        try {
            BigDecimal totalDebt = toNumber(args[0]);
            BigDecimal totalIncome = toNumber(args[1]);

            if (totalIncome.compareTo(BigDecimal.ZERO) == 0) {
                return null; // Avoid division by zero
            }

            BigDecimal ratio = totalDebt.divide(totalIncome, 4, BigDecimal.ROUND_HALF_UP);
            return ratio.multiply(BigDecimal.valueOf(100)); // Return as percentage
        } catch (Exception e) {
            log.warn("Error calculating debt ratio: {}", e.getMessage());
            return null;
        }
    }

    private Object calculateLTV(Object[] args) {
        if (args.length < 2) return null;

        try {
            BigDecimal loanAmount = toNumber(args[0]);
            BigDecimal propertyValue = toNumber(args[1]);

            if (propertyValue.compareTo(BigDecimal.ZERO) == 0) {
                return null; // Avoid division by zero
            }

            BigDecimal ltv = loanAmount.divide(propertyValue, 4, BigDecimal.ROUND_HALF_UP);
            return ltv.multiply(BigDecimal.valueOf(100)); // Return as percentage
        } catch (Exception e) {
            log.warn("Error calculating LTV: {}", e.getMessage());
            return null;
        }
    }

    private Object calculatePaymentSchedule(Object[] args) {
        if (args.length < 3) return null;

        try {
            BigDecimal principal = toNumber(args[0]);
            BigDecimal annualRate = toNumber(args[1]);
            int termMonths = toNumber(args[2]).intValue();

            BigDecimal monthlyPayment = (BigDecimal) calculateLoanPayment(args);
            if (monthlyPayment == null) return null;

            java.util.List<java.util.Map<String, Object>> schedule = new java.util.ArrayList<>();
            BigDecimal remainingBalance = principal;
            BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(1200), 10, BigDecimal.ROUND_HALF_UP);

            for (int month = 1; month <= termMonths; month++) {
                BigDecimal interestPayment = remainingBalance.multiply(monthlyRate);
                BigDecimal principalPayment = monthlyPayment.subtract(interestPayment);
                remainingBalance = remainingBalance.subtract(principalPayment);

                java.util.Map<String, Object> payment = new java.util.HashMap<>();
                payment.put("month", month);
                payment.put("payment", monthlyPayment);
                payment.put("principal", principalPayment);
                payment.put("interest", interestPayment);
                payment.put("balance", remainingBalance);

                schedule.add(payment);

                if (remainingBalance.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
            }

            return schedule;
        } catch (Exception e) {
            log.warn("Error calculating payment schedule: {}", e.getMessage());
            return null;
        }
    }
}
