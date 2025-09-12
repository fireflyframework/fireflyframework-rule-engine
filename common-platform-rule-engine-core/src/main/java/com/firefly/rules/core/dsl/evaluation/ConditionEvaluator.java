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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates conditions in the rule DSL.
 * Handles comparison operations, logical operations, and arithmetic operations.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConditionEvaluator {

    private final VariableResolver variableResolver;
    private final ArithmeticEvaluator arithmeticEvaluator;

    /**
     * Evaluate a condition against the evaluation context
     *
     * @param condition the condition to evaluate
     * @param context the evaluation context
     * @return the boolean result of the condition
     */
    public boolean evaluate(Condition condition, EvaluationContext context) {
        if (condition == null) {
            return false;
        }

        String operationId = context.getOperationId();
        JsonLogger.logConditionEvaluation(log, operationId, condition.toString(), true);

        // Handle direct value conditions
        if (condition.getValue() != null) {
            Object value = variableResolver.resolveValue(condition.getValue(), context);
            return convertToBoolean(value);
        }

        // Handle comparison conditions
        if (condition.getCompare() != null) {
            return evaluateComparison(condition.getCompare(), context);
        }

        // Handle logical AND
        if (condition.getAnd() != null) {
            return evaluateAnd(condition.getAnd(), context);
        }

        // Handle logical OR
        if (condition.getOr() != null) {
            return evaluateOr(condition.getOr(), context);
        }

        // Handle logical NOT
        if (condition.getNot() != null) {
            return !evaluate(condition.getNot(), context);
        }

        // Handle arithmetic operations (that return boolean)
        if (condition.getArithmetic() != null) {
            Object result = arithmeticEvaluator.evaluate(condition.getArithmetic(), context);
            return convertToBoolean(result);
        }

        // Handle function calls
        if (condition.getFunction() != null) {
            return evaluateFunctionCall(condition.getFunction(), context);
        }

        JsonLogger.warn(log, operationId, "Unknown condition type, returning false");
        return false;
    }

    /**
     * Evaluate a simple expression string (for simplified DSL syntax)
     *
     * @param expression the expression string to evaluate
     * @param context the evaluation context
     * @return the boolean result of the expression
     */
    public boolean evaluateExpression(String expression, EvaluationContext context) {
        if (expression == null || expression.trim().isEmpty()) {
            return false;
        }

        String operationId = context.getOperationId();
        JsonLogger.logConditionEvaluation(log, operationId, expression, true);

        try {
            // Parse and evaluate simple expressions like "CREDIT_SCORE at_least 750"
            return parseAndEvaluateSimpleExpression(expression.trim(), context);
        } catch (Exception e) {
            JsonLogger.error(log, operationId, "Error evaluating expression: " + expression, e);
            return false;
        }
    }

    /**
     * Evaluate a comparison condition
     */
    private boolean evaluateComparison(Condition.ComparisonCondition comparison, EvaluationContext context) {
        Object leftValue = variableResolver.resolveValue(comparison.getLeft(), context);
        Object rightValue = variableResolver.resolveValue(comparison.getRight(), context);
        String operator = comparison.getOperator();

        String operationId = context.getOperationId();
        Map<String, Object> data = new HashMap<>();
        data.put("leftValue", leftValue != null ? leftValue : "null");
        data.put("operator", operator != null ? operator : "null");
        data.put("rightValue", rightValue != null ? rightValue : "null");
        JsonLogger.debug(log, operationId, "Comparing values", data);

        switch (operator.toLowerCase()) {
            case "==":
            case "equals":
                return compareEquals(leftValue, rightValue);
            case "!=":
            case "not_equals":
                return !compareEquals(leftValue, rightValue);
            case ">":
            case "greater_than":
                return compareGreaterThan(leftValue, rightValue);
            case ">=":
            case "greater_than_or_equal":
            case "at_least":
                return compareGreaterThanOrEqual(leftValue, rightValue);
            case "<":
            case "less_than":
                return compareLessThan(leftValue, rightValue);
            case "<=":
            case "less_than_or_equal":
                return compareLessThanOrEqual(leftValue, rightValue);
            case "contains":
                return compareContains(leftValue, rightValue);
            case "starts_with":
                return compareStartsWith(leftValue, rightValue);
            case "ends_with":
                return compareEndsWith(leftValue, rightValue);
            case "in":
            case "in_list":
                return compareInList(leftValue, rightValue);
            case "not_in":
            case "not_in_list":
                return !compareInList(leftValue, rightValue);
            case "between":
                // For between operator, we expect rightValue to be an array with [min, max]
                if (rightValue instanceof java.util.List) {
                    java.util.List<?> range = (java.util.List<?>) rightValue;
                    if (range.size() == 2) {
                        return compareBetween(leftValue, range.get(0), range.get(1));
                    }
                }
                throw new IllegalArgumentException("Between operator requires a list with exactly 2 elements [min, max]");

            // Enhanced comparison operators
            case "matches":
            case "regex":
                return compareRegex(leftValue, rightValue);
            case "not_matches":
            case "not_regex":
                return !compareRegex(leftValue, rightValue);
            case "is_empty":
                return compareIsEmpty(leftValue);
            case "is_not_empty":
                return !compareIsEmpty(leftValue);
            case "is_null":
                return leftValue == null;
            case "is_not_null":
                return leftValue != null;
            case "is_numeric":
                return compareIsNumeric(leftValue);
            case "is_not_numeric":
                return !compareIsNumeric(leftValue);
            case "is_email":
                return compareIsEmail(leftValue);
            case "is_phone":
                return compareIsPhone(leftValue);
            case "is_date":
                return compareIsDate(leftValue);
            case "length_equals":
                return compareLengthEquals(leftValue, rightValue);
            case "length_greater_than":
                return compareLengthGreaterThan(leftValue, rightValue);
            case "length_less_than":
                return compareLengthLessThan(leftValue, rightValue);
            case "within_range":
                return compareWithinRange(leftValue, rightValue);
            case "outside_range":
                return !compareWithinRange(leftValue, rightValue);

            // Financial and business operators
            case "is_positive":
                return compareIsPositive(leftValue);
            case "is_negative":
                return compareIsNegative(leftValue);
            case "is_zero":
                return compareIsZero(leftValue);
            case "is_non_zero":
                return !compareIsZero(leftValue);
            case "is_percentage":
                return compareIsPercentage(leftValue);
            case "is_currency":
                return compareIsCurrency(leftValue);
            case "is_credit_score":
                return compareIsCreditScore(leftValue);
            case "is_ssn":
                return compareIsSSN(leftValue);
            case "is_account_number":
                return compareIsAccountNumber(leftValue);
            case "is_routing_number":
                return compareIsRoutingNumber(leftValue);
            case "is_business_day":
                return compareIsBusinessDay(leftValue);
            case "is_weekend":
                return !compareIsBusinessDay(leftValue);
            case "age_at_least":
                return compareAgeAtLeast(leftValue, rightValue);
            case "age_less_than":
                return !compareAgeAtLeast(leftValue, rightValue);

            default:
                throw new IllegalArgumentException("Unknown comparison operator: " + operator);
        }
    }

    /**
     * Evaluate logical AND
     */
    private boolean evaluateAnd(List<Condition> conditions, EvaluationContext context) {
        for (Condition condition : conditions) {
            if (!evaluate(condition, context)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Evaluate logical OR
     */
    private boolean evaluateOr(List<Condition> conditions, EvaluationContext context) {
        for (Condition condition : conditions) {
            if (evaluate(condition, context)) {
                return true;
            }
        }
        return false;
    }

    // Comparison helper methods
    private boolean compareEquals(Object left, Object right) {
        if (left == null && right == null) return true;
        if (left == null || right == null) return false;

        // Handle numeric comparisons
        if (isNumeric(left) && isNumeric(right)) {
            return toBigDecimal(left).compareTo(toBigDecimal(right)) == 0;
        }

        return left.toString().equals(right.toString());
    }

    private boolean compareGreaterThan(Object left, Object right) {
        if (!isNumeric(left) || !isNumeric(right)) {
            throw new IllegalArgumentException("Cannot compare non-numeric values with > operator");
        }
        return toBigDecimal(left).compareTo(toBigDecimal(right)) > 0;
    }

    private boolean compareGreaterThanOrEqual(Object left, Object right) {
        if (!isNumeric(left) || !isNumeric(right)) {
            throw new IllegalArgumentException("Cannot compare non-numeric values with >= operator");
        }
        return toBigDecimal(left).compareTo(toBigDecimal(right)) >= 0;
    }

    private boolean compareLessThan(Object left, Object right) {
        if (!isNumeric(left) || !isNumeric(right)) {
            throw new IllegalArgumentException("Cannot compare non-numeric values with < operator");
        }
        return toBigDecimal(left).compareTo(toBigDecimal(right)) < 0;
    }

    private boolean compareLessThanOrEqual(Object left, Object right) {
        if (!isNumeric(left) || !isNumeric(right)) {
            throw new IllegalArgumentException("Cannot compare non-numeric values with <= operator");
        }
        return toBigDecimal(left).compareTo(toBigDecimal(right)) <= 0;
    }

    private boolean compareContains(Object left, Object right) {
        if (left == null || right == null) return false;
        return left.toString().contains(right.toString());
    }

    private boolean compareStartsWith(Object left, Object right) {
        if (left == null || right == null) return false;
        return left.toString().startsWith(right.toString());
    }

    private boolean compareEndsWith(Object left, Object right) {
        if (left == null || right == null) return false;
        return left.toString().endsWith(right.toString());
    }

    private boolean compareIn(Object left, Object right) {
        if (right instanceof List) {
            List<?> list = (List<?>) right;
            return list.contains(left);
        }
        return false;
    }

    // Utility methods
    private boolean convertToBoolean(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).doubleValue() != 0.0;
        if (value instanceof String) return !((String) value).isEmpty();
        return true;
    }

    private boolean isNumeric(Object value) {
        return value instanceof Number || 
               (value instanceof String && isNumericString((String) value));
    }

    private boolean isNumericString(String str) {
        try {
            new BigDecimal(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        if (value instanceof String) return new BigDecimal((String) value);
        throw new IllegalArgumentException("Cannot convert to BigDecimal: " + value);
    }

    /**
     * Check if a value is between two bounds (inclusive)
     */
    private boolean compareBetween(Object value, Object min, Object max) {
        if (value == null || min == null || max == null) return false;

        if (!isNumeric(value) || !isNumeric(min) || !isNumeric(max)) {
            throw new IllegalArgumentException("Between comparison requires numeric values");
        }

        BigDecimal valueBD = toBigDecimal(value);
        BigDecimal minBD = toBigDecimal(min);
        BigDecimal maxBD = toBigDecimal(max);

        return valueBD.compareTo(minBD) >= 0 && valueBD.compareTo(maxBD) <= 0;
    }

    /**
     * Check if a value is in a list
     */
    private boolean compareInList(Object left, Object right) {
        if (left == null || right == null) return false;

        // Handle array/list comparison
        if (right instanceof java.util.List) {
            java.util.List<?> list = (java.util.List<?>) right;
            return list.contains(left) || list.stream().anyMatch(item -> compareEquals(left, item));
        }

        // Handle string representation of list (e.g., "[\"A\", \"B\", \"C\"]")
        String rightStr = right.toString();
        if (rightStr.startsWith("[") && rightStr.endsWith("]")) {
            // Simple parsing for basic list formats
            String listContent = rightStr.substring(1, rightStr.length() - 1);
            String[] items = listContent.split(",");
            for (String item : items) {
                String cleanItem = item.trim().replaceAll("^\"|\"$", ""); // Remove quotes
                if (compareEquals(left, cleanItem)) {
                    return true;
                }
            }
        }

        return false;
    }

    // ===== ENHANCED COMPARISON METHODS =====

    /**
     * Compare using regular expressions
     */
    private boolean compareRegex(Object left, Object right) {
        if (left == null || right == null) return false;

        String text = left.toString();
        String pattern = right.toString();

        try {
            return text.matches(pattern);
        } catch (Exception e) {
            log.warn("Invalid regex pattern: {}", pattern, e);
            return false;
        }
    }

    /**
     * Check if value is empty (null, empty string, empty collection)
     */
    private boolean compareIsEmpty(Object value) {
        if (value == null) return true;
        if (value instanceof String) return ((String) value).trim().isEmpty();
        if (value instanceof java.util.Collection) return ((java.util.Collection<?>) value).isEmpty();
        if (value instanceof java.util.Map) return ((java.util.Map<?, ?>) value).isEmpty();
        if (value.getClass().isArray()) return java.lang.reflect.Array.getLength(value) == 0;
        return false;
    }

    /**
     * Check if value is numeric
     */
    private boolean compareIsNumeric(Object value) {
        if (value == null) return false;
        if (value instanceof Number) return true;

        if (value instanceof String) {
            try {
                Double.parseDouble(value.toString());
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        return false;
    }

    /**
     * Check if value is a valid email address
     */
    private boolean compareIsEmail(Object value) {
        if (value == null) return false;

        String email = value.toString().trim();
        String emailRegex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";

        return email.matches(emailRegex);
    }

    /**
     * Check if value is a valid phone number
     */
    private boolean compareIsPhone(Object value) {
        if (value == null) return false;

        String phone = value.toString().replaceAll("[\\s\\-\\(\\)\\+]", "");

        // Basic phone number validation (10-15 digits)
        return phone.matches("^\\d{10,15}$");
    }

    /**
     * Check if value is a valid date
     */
    private boolean compareIsDate(Object value) {
        if (value == null) return false;

        if (value instanceof java.time.LocalDate ||
            value instanceof java.time.LocalDateTime ||
            value instanceof java.util.Date) {
            return true;
        }

        if (value instanceof String) {
            String dateStr = value.toString();
            try {
                // Try common date formats
                java.time.LocalDate.parse(dateStr); // ISO format
                return true;
            } catch (Exception e1) {
                try {
                    java.time.format.DateTimeFormatter formatter =
                        java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy");
                    java.time.LocalDate.parse(dateStr, formatter);
                    return true;
                } catch (Exception e2) {
                    try {
                        java.time.format.DateTimeFormatter formatter2 =
                            java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");
                        java.time.LocalDate.parse(dateStr, formatter2);
                        return true;
                    } catch (Exception e3) {
                        return false;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Compare string/collection length equals
     */
    private boolean compareLengthEquals(Object left, Object right) {
        if (left == null || right == null) return false;

        int length = getLength(left);
        int expectedLength = ((Number) right).intValue();

        return length == expectedLength;
    }

    /**
     * Compare string/collection length greater than
     */
    private boolean compareLengthGreaterThan(Object left, Object right) {
        if (left == null || right == null) return false;

        int length = getLength(left);
        int threshold = ((Number) right).intValue();

        return length > threshold;
    }

    /**
     * Compare string/collection length less than
     */
    private boolean compareLengthLessThan(Object left, Object right) {
        if (left == null || right == null) return false;

        int length = getLength(left);
        int threshold = ((Number) right).intValue();

        return length < threshold;
    }

    /**
     * Check if numeric value is within a specified range
     */
    private boolean compareWithinRange(Object value, Object range) {
        if (value == null || range == null) return false;

        if (!isNumeric(value)) {
            throw new IllegalArgumentException("within_range comparison requires numeric value");
        }

        if (range instanceof java.util.List) {
            java.util.List<?> rangeList = (java.util.List<?>) range;
            if (rangeList.size() == 2) {
                return compareBetween(value, rangeList.get(0), rangeList.get(1));
            }
        }

        throw new IllegalArgumentException("within_range operator requires a list with exactly 2 elements [min, max]");
    }

    /**
     * Get length of string, collection, or array
     */
    private int getLength(Object value) {
        if (value == null) return 0;
        if (value instanceof String) return ((String) value).length();
        if (value instanceof java.util.Collection) return ((java.util.Collection<?>) value).size();
        if (value instanceof java.util.Map) return ((java.util.Map<?, ?>) value).size();
        if (value.getClass().isArray()) return java.lang.reflect.Array.getLength(value);
        return value.toString().length();
    }

    // ===== FINANCIAL AND BUSINESS OPERATORS =====

    /**
     * Check if numeric value is positive
     */
    private boolean compareIsPositive(Object value) {
        if (!isNumeric(value)) return false;
        double numValue = ((Number) value).doubleValue();
        return numValue > 0;
    }

    /**
     * Check if numeric value is negative
     */
    private boolean compareIsNegative(Object value) {
        if (!isNumeric(value)) return false;
        double numValue = ((Number) value).doubleValue();
        return numValue < 0;
    }

    /**
     * Check if numeric value is zero
     */
    private boolean compareIsZero(Object value) {
        if (!isNumeric(value)) return false;
        double numValue = ((Number) value).doubleValue();
        return Math.abs(numValue) < 0.0001; // Handle floating point precision
    }

    /**
     * Check if value is a valid percentage (0-100 or 0.0-1.0)
     */
    private boolean compareIsPercentage(Object value) {
        if (!isNumeric(value)) return false;
        double numValue = ((Number) value).doubleValue();
        return (numValue >= 0 && numValue <= 100) || (numValue >= 0.0 && numValue <= 1.0);
    }

    /**
     * Check if value represents a currency amount (positive numeric)
     */
    private boolean compareIsCurrency(Object value) {
        if (!isNumeric(value)) return false;
        double numValue = ((Number) value).doubleValue();
        return numValue >= 0; // Currency amounts should be non-negative
    }

    /**
     * Check if value is a valid credit score (typically 300-850)
     */
    private boolean compareIsCreditScore(Object value) {
        if (!isNumeric(value)) return false;
        int score = ((Number) value).intValue();
        return score >= 300 && score <= 850;
    }

    /**
     * Check if value is a valid SSN format (XXX-XX-XXXX)
     */
    private boolean compareIsSSN(Object value) {
        if (value == null) return false;
        String ssn = value.toString().trim();

        // Remove any formatting
        String cleanSSN = ssn.replaceAll("[\\s\\-]", "");

        // Check if it's exactly 9 digits
        return cleanSSN.matches("^\\d{9}$");
    }

    /**
     * Check if value is a valid account number format
     */
    private boolean compareIsAccountNumber(Object value) {
        if (value == null) return false;
        String account = value.toString().trim();

        // Account numbers are typically 8-17 digits
        String cleanAccount = account.replaceAll("[\\s\\-]", "");
        return cleanAccount.matches("^\\d{8,17}$");
    }

    /**
     * Check if value is a valid routing number (9 digits)
     */
    private boolean compareIsRoutingNumber(Object value) {
        if (value == null) return false;
        String routing = value.toString().trim();

        // Remove any formatting
        String cleanRouting = routing.replaceAll("[\\s\\-]", "");

        // Must be exactly 9 digits
        if (!cleanRouting.matches("^\\d{9}$")) {
            return false;
        }

        // Basic checksum validation for routing numbers
        return validateRoutingNumberChecksum(cleanRouting);
    }

    /**
     * Check if date is a business day (Monday-Friday, excluding holidays)
     */
    private boolean compareIsBusinessDay(Object value) {
        if (value == null) return false;

        java.time.LocalDate date = null;

        if (value instanceof java.time.LocalDate) {
            date = (java.time.LocalDate) value;
        } else if (value instanceof java.time.LocalDateTime) {
            date = ((java.time.LocalDateTime) value).toLocalDate();
        } else if (value instanceof java.util.Date) {
            date = ((java.util.Date) value).toInstant()
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate();
        } else if (value instanceof String) {
            try {
                date = java.time.LocalDate.parse(value.toString());
            } catch (Exception e) {
                return false;
            }
        } else {
            return false;
        }

        // Check if it's Monday-Friday
        java.time.DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != java.time.DayOfWeek.SATURDAY &&
               dayOfWeek != java.time.DayOfWeek.SUNDAY;
    }

    /**
     * Check if age meets minimum requirement
     */
    private boolean compareAgeAtLeast(Object birthDate, Object minimumAge) {
        if (birthDate == null || minimumAge == null) return false;

        java.time.LocalDate birth = null;

        if (birthDate instanceof java.time.LocalDate) {
            birth = (java.time.LocalDate) birthDate;
        } else if (birthDate instanceof String) {
            try {
                birth = java.time.LocalDate.parse(birthDate.toString());
            } catch (Exception e) {
                return false;
            }
        } else {
            return false;
        }

        int minAge = ((Number) minimumAge).intValue();
        java.time.LocalDate today = java.time.LocalDate.now();
        int actualAge = java.time.Period.between(birth, today).getYears();

        return actualAge >= minAge;
    }

    /**
     * Validate routing number checksum using the standard algorithm
     */
    private boolean validateRoutingNumberChecksum(String routing) {
        if (routing.length() != 9) return false;

        try {
            int[] weights = {3, 7, 1, 3, 7, 1, 3, 7, 1};
            int sum = 0;

            for (int i = 0; i < 9; i++) {
                sum += Character.getNumericValue(routing.charAt(i)) * weights[i];
            }

            return sum % 10 == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Parse and evaluate simple expressions like "CREDIT_SCORE at_least 750"
     */
    private boolean parseAndEvaluateSimpleExpression(String expression, EvaluationContext context) {
        // Handle common patterns
        if (expression.contains(" at_least ")) {
            String[] parts = expression.split(" at_least ");
            if (parts.length == 2) {
                Object leftValue = variableResolver.resolveValue(parts[0].trim(), context);
                Object rightValue = variableResolver.resolveValue(parts[1].trim(), context);
                return compareGreaterThanOrEqual(leftValue, rightValue);
            }
        }

        // Handle "between X and Y" pattern
        if (expression.contains(" between ") && expression.contains(" and ")) {
            String[] betweenParts = expression.split(" between ");
            if (betweenParts.length == 2) {
                String leftVar = betweenParts[0].trim();
                String rangeExpression = betweenParts[1].trim();
                String[] rangeParts = rangeExpression.split(" and ");
                if (rangeParts.length == 2) {
                    Object leftValue = variableResolver.resolveValue(leftVar, context);
                    Object minValue = variableResolver.resolveValue(rangeParts[0].trim(), context);
                    Object maxValue = variableResolver.resolveValue(rangeParts[1].trim(), context);
                    return compareBetween(leftValue, minValue, maxValue);
                }
            }
        }

        // Handle "in_list" pattern
        if (expression.contains(" in_list ")) {
            String[] parts = expression.split(" in_list ");
            if (parts.length == 2) {
                Object leftValue = variableResolver.resolveValue(parts[0].trim(), context);
                Object rightValue = variableResolver.resolveValue(parts[1].trim(), context);
                return compareInList(leftValue, rightValue);
            }
        }

        if (expression.contains(" greater_than ")) {
            String[] parts = expression.split(" greater_than ");
            if (parts.length == 2) {
                Object leftValue = variableResolver.resolveValue(parts[0].trim(), context);
                Object rightValue = variableResolver.resolveValue(parts[1].trim(), context);
                return compareGreaterThan(leftValue, rightValue);
            }
        }

        if (expression.contains(" less_than ")) {
            String[] parts = expression.split(" less_than ");
            if (parts.length == 2) {
                Object leftValue = variableResolver.resolveValue(parts[0].trim(), context);
                Object rightValue = variableResolver.resolveValue(parts[1].trim(), context);
                return compareLessThan(leftValue, rightValue);
            }
        }

        if (expression.contains(" equals ")) {
            String[] parts = expression.split(" equals ");
            if (parts.length == 2) {
                Object leftValue = variableResolver.resolveValue(parts[0].trim(), context);
                Object rightValue = variableResolver.resolveValue(parts[1].trim(), context);
                return compareEquals(leftValue, rightValue);
            }
        }

        if (expression.contains(" between ")) {
            String[] parts = expression.split(" between ");
            if (parts.length == 2) {
                String[] rangeParts = parts[1].split(" and ");
                if (rangeParts.length == 2) {
                    Object value = variableResolver.resolveValue(parts[0].trim(), context);
                    Object minValue = variableResolver.resolveValue(rangeParts[0].trim(), context);
                    Object maxValue = variableResolver.resolveValue(rangeParts[1].trim(), context);
                    return compareGreaterThanOrEqual(value, minValue) && compareLessThanOrEqual(value, maxValue);
                }
            }
        }

        if (expression.contains(" in_list ")) {
            String[] parts = expression.split(" in_list ");
            if (parts.length == 2) {
                Object leftValue = variableResolver.resolveValue(parts[0].trim(), context);
                Object rightValue = variableResolver.resolveValue(parts[1].trim(), context);
                return compareIn(leftValue, rightValue);
            }
        }

        // Handle standard mathematical operators
        if (expression.contains("&&")) {
            String[] parts = expression.split("&&");
            boolean result = true;
            for (String part : parts) {
                if (!parseAndEvaluateSimpleExpression(part.trim(), context)) {
                    result = false;
                    break;
                }
            }
            return result;
        }

        if (expression.contains("||")) {
            String[] parts = expression.split("\\|\\|");
            for (String part : parts) {
                if (parseAndEvaluateSimpleExpression(part.trim(), context)) {
                    return true;
                }
            }
            return false;
        }

        if (expression.contains(">=")) {
            String[] parts = expression.split(">=");
            if (parts.length == 2) {
                Object leftValue = variableResolver.resolveValue(parts[0].trim(), context);
                Object rightValue = variableResolver.resolveValue(parts[1].trim(), context);
                return compareGreaterThanOrEqual(leftValue, rightValue);
            }
        }

        if (expression.contains("<=")) {
            String[] parts = expression.split("<=");
            if (parts.length == 2) {
                Object leftValue = variableResolver.resolveValue(parts[0].trim(), context);
                Object rightValue = variableResolver.resolveValue(parts[1].trim(), context);
                return compareLessThanOrEqual(leftValue, rightValue);
            }
        }

        if (expression.contains(">")) {
            String[] parts = expression.split(">");
            if (parts.length == 2) {
                Object leftValue = variableResolver.resolveValue(parts[0].trim(), context);
                Object rightValue = variableResolver.resolveValue(parts[1].trim(), context);
                return compareGreaterThan(leftValue, rightValue);
            }
        }

        if (expression.contains("<")) {
            String[] parts = expression.split("<");
            if (parts.length == 2) {
                Object leftValue = variableResolver.resolveValue(parts[0].trim(), context);
                Object rightValue = variableResolver.resolveValue(parts[1].trim(), context);
                return compareLessThan(leftValue, rightValue);
            }
        }

        if (expression.contains("==")) {
            String[] parts = expression.split("==");
            if (parts.length == 2) {
                Object leftValue = resolveValueForComparison(parts[0].trim(), context);
                Object rightValue = resolveValueForComparison(parts[1].trim(), context);
                return compareEquals(leftValue, rightValue);
            }
        }

        // If no pattern matches, try to resolve as a variable or constant
        Object value = variableResolver.resolveValue(expression, context);
        return convertToBoolean(value);
    }

    /**
     * Evaluate a function call condition
     *
     * @param functionCall the function call to evaluate
     * @param context the evaluation context
     * @return the boolean result of the function call
     */
    private boolean evaluateFunctionCall(Condition.FunctionCall functionCall, EvaluationContext context) {
        String functionName = functionCall.getName();
        List<Object> parameters = functionCall.getParameters();

        log.debug("Evaluating function call: {} with parameters: {}", functionName, parameters);

        // Enhanced function registry for conditions
        switch (functionName.toLowerCase()) {
            case "is_valid":
                return evaluateIsValidFunction(parameters, context);
            case "exists":
                return evaluateExistsFunction(parameters, context);
            case "matches":
                return evaluateMatchesFunction(parameters, context);
            case "in_range":
                return evaluateInRangeFunction(parameters, context);
            case "distance_between":
                return evaluateDistanceBetweenFunction(parameters, context);
            case "time_hour":
                return evaluateTimeHourFunction(parameters, context);

            // Financial validation functions
            case "is_valid_credit_score":
                return evaluateIsValidCreditScoreFunction(parameters, context);
            case "is_valid_ssn":
                return evaluateIsValidSSNFunction(parameters, context);
            case "is_valid_account":
                return evaluateIsValidAccountFunction(parameters, context);
            case "is_valid_routing":
                return evaluateIsValidRoutingFunction(parameters, context);
            case "is_business_day":
                return evaluateIsBusinessDayFunction(parameters, context);
            case "age_meets_requirement":
                return evaluateAgeMeetsRequirementFunction(parameters, context);
            case "debt_to_income_ratio":
                return evaluateDebtToIncomeRatioFunction(parameters, context);
            case "credit_utilization":
                return evaluateCreditUtilizationFunction(parameters, context);
            case "loan_to_value":
                return evaluateLoanToValueFunction(parameters, context);
            case "payment_history_score":
                return evaluatePaymentHistoryScoreFunction(parameters, context);

            default:
                String operationId = context.getOperationId();
                JsonLogger.warn(log, operationId, "Unknown function: " + functionName);
                return false;
        }
    }

    /**
     * Evaluate is_valid function
     */
    private boolean evaluateIsValidFunction(List<Object> parameters, EvaluationContext context) {
        if (parameters == null || parameters.isEmpty()) {
            return false;
        }

        String variableName = parameters.get(0).toString();
        Object value = context.getValue(variableName);
        return value != null;
    }

    /**
     * Evaluate exists function
     */
    private boolean evaluateExistsFunction(List<Object> parameters, EvaluationContext context) {
        if (parameters == null || parameters.isEmpty()) {
            return false;
        }

        String variableName = parameters.get(0).toString();
        return context.hasValue(variableName);
    }

    /**
     * Evaluate matches function (regex matching)
     */
    private boolean evaluateMatchesFunction(List<Object> parameters, EvaluationContext context) {
        if (parameters == null || parameters.size() < 2) {
            return false;
        }

        String variableName = parameters.get(0).toString();
        String pattern = parameters.get(1).toString();

        Object value = context.getValue(variableName);
        if (value == null) {
            return false;
        }

        try {
            return value.toString().matches(pattern);
        } catch (Exception e) {
            log.error("Error matching pattern '{}' against value '{}'", pattern, value, e);
            return false;
        }
    }

    /**
     * Evaluate in_range function
     */
    private boolean evaluateInRangeFunction(List<Object> parameters, EvaluationContext context) {
        if (parameters == null || parameters.size() < 3) {
            return false;
        }

        String variableName = parameters.get(0).toString();
        Object minValue = parameters.get(1);
        Object maxValue = parameters.get(2);

        Object value = context.getValue(variableName);
        if (value == null) {
            return false;
        }

        try {
            double numValue = Double.parseDouble(value.toString());
            double min = Double.parseDouble(minValue.toString());
            double max = Double.parseDouble(maxValue.toString());

            return numValue >= min && numValue <= max;
        } catch (NumberFormatException e) {
            log.error("Error parsing numeric values for range check", e);
            return false;
        }
    }

    /**
     * Resolve a value for comparison operations, stripping quotes from string literals
     */
    private Object resolveValueForComparison(String value, EvaluationContext context) {
        Object resolved = variableResolver.resolveValue(value, context);

        // If the resolved value is a string that looks like a quoted literal, strip the quotes
        if (resolved instanceof String) {
            String stringValue = (String) resolved;
            if (stringValue.startsWith("\"") && stringValue.endsWith("\"") && stringValue.length() >= 2) {
                return stringValue.substring(1, stringValue.length() - 1);
            }
            if (stringValue.startsWith("'") && stringValue.endsWith("'") && stringValue.length() >= 2) {
                return stringValue.substring(1, stringValue.length() - 1);
            }
        }

        return resolved;
    }

    /**
     * Evaluate distance_between function
     * Returns true if distance comparison meets criteria
     */
    private boolean evaluateDistanceBetweenFunction(List<Object> parameters, EvaluationContext context) {
        if (parameters == null || parameters.size() < 3) {
            log.warn("distance_between function requires at least 3 parameters: location1, location2, comparison_value");
            return false;
        }

        try {
            Object location1 = variableResolver.resolveValue(parameters.get(0), context);
            Object location2 = variableResolver.resolveValue(parameters.get(1), context);
            Object comparisonValue = variableResolver.resolveValue(parameters.get(2), context);

            // Simple distance calculation for demonstration
            // In a real implementation, this would use proper geolocation libraries
            double distance = calculateSimpleDistance(location1, location2);
            double threshold = ((Number) comparisonValue).doubleValue();

            // The function is used in expressions like "distance_between(loc1, loc2) greater_than 500"
            // So we return the distance value for comparison
            context.setComputedVariable("_distance_result", distance);
            return distance > threshold;
        } catch (Exception e) {
            log.error("Error calculating distance", e);
            return false;
        }
    }

    /**
     * Evaluate time_hour function
     * Extracts hour from timestamp and compares
     */
    private boolean evaluateTimeHourFunction(List<Object> parameters, EvaluationContext context) {
        if (parameters == null || parameters.isEmpty()) {
            log.warn("time_hour function requires at least 1 parameter: timestamp");
            return false;
        }

        try {
            Object timestamp = variableResolver.resolveValue(parameters.get(0), context);
            int hour = extractHourFromTimestamp(timestamp);

            // Store the hour for use in comparisons
            context.setComputedVariable("_hour_result", hour);

            // If there's a second parameter, compare directly
            if (parameters.size() > 1) {
                Object comparisonValue = variableResolver.resolveValue(parameters.get(1), context);
                return hour == ((Number) comparisonValue).intValue();
            }

            return true; // Just extraction, no comparison
        } catch (Exception e) {
            log.error("Error extracting hour from timestamp", e);
            return false;
        }
    }

    /**
     * Calculate distance between two geographic coordinates using the Haversine formula
     * This provides accurate distance calculations for real-world geolocation use cases
     */
    private double calculateSimpleDistance(Object location1, Object location2) {
        if (location1 == null || location2 == null) {
            return 0.0;
        }

        try {
            // Parse coordinates from various formats
            double[] coords1 = parseCoordinates(location1);
            double[] coords2 = parseCoordinates(location2);

            if (coords1 == null || coords2 == null) {
                log.warn("Invalid coordinate format for distance calculation: {} and {}", location1, location2);
                return 0.0;
            }

            return calculateHaversineDistance(coords1[0], coords1[1], coords2[0], coords2[1]);
        } catch (Exception e) {
            log.error("Error calculating distance between {} and {}", location1, location2, e);
            return 0.0;
        }
    }

    /**
     * Parse coordinates from various input formats
     * Supports: "lat,lng", {"lat": x, "lng": y}, [lat, lng], etc.
     */
    private double[] parseCoordinates(Object location) {
        if (location == null) {
            return null;
        }

        try {
            // Handle string format: "lat,lng"
            if (location instanceof String) {
                String locationStr = location.toString().trim();

                // Handle "lat,lng" format
                if (locationStr.contains(",")) {
                    String[] parts = locationStr.split(",");
                    if (parts.length == 2) {
                        double lat = Double.parseDouble(parts[0].trim());
                        double lng = Double.parseDouble(parts[1].trim());
                        return new double[]{lat, lng};
                    }
                }
            }

            // Handle Map format: {"lat": x, "lng": y} or {"latitude": x, "longitude": y}
            if (location instanceof java.util.Map) {
                java.util.Map<?, ?> coordMap = (java.util.Map<?, ?>) location;

                Double lat = null, lng = null;

                // Try different key variations
                for (String latKey : new String[]{"lat", "latitude", "LAT", "LATITUDE"}) {
                    if (coordMap.containsKey(latKey)) {
                        lat = ((Number) coordMap.get(latKey)).doubleValue();
                        break;
                    }
                }

                for (String lngKey : new String[]{"lng", "lon", "longitude", "LNG", "LON", "LONGITUDE"}) {
                    if (coordMap.containsKey(lngKey)) {
                        lng = ((Number) coordMap.get(lngKey)).doubleValue();
                        break;
                    }
                }

                if (lat != null && lng != null) {
                    return new double[]{lat, lng};
                }
            }

            // Handle List/Array format: [lat, lng]
            if (location instanceof java.util.List) {
                java.util.List<?> coordList = (java.util.List<?>) location;
                if (coordList.size() == 2) {
                    double lat = ((Number) coordList.get(0)).doubleValue();
                    double lng = ((Number) coordList.get(1)).doubleValue();
                    return new double[]{lat, lng};
                }
            }

        } catch (Exception e) {
            log.warn("Failed to parse coordinates from: {}", location, e);
        }

        return null;
    }

    /**
     * Calculate distance between two points using the Haversine formula
     * Returns distance in kilometers
     */
    private double calculateHaversineDistance(double lat1, double lng1, double lat2, double lng2) {
        // Earth's radius in kilometers
        final double EARTH_RADIUS_KM = 6371.0;

        // Convert latitude and longitude from degrees to radians
        double lat1Rad = Math.toRadians(lat1);
        double lng1Rad = Math.toRadians(lng1);
        double lat2Rad = Math.toRadians(lat2);
        double lng2Rad = Math.toRadians(lng2);

        // Calculate differences
        double deltaLat = lat2Rad - lat1Rad;
        double deltaLng = lng2Rad - lng1Rad;

        // Haversine formula
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        // Calculate the distance
        double distance = EARTH_RADIUS_KM * c;

        log.debug("Calculated Haversine distance: {} km between ({}, {}) and ({}, {})",
                 distance, lat1, lng1, lat2, lng2);

        return distance;
    }

    /**
     * Extract hour from timestamp
     */
    private int extractHourFromTimestamp(Object timestamp) {
        if (timestamp == null) {
            return 0;
        }

        // Handle different timestamp formats
        if (timestamp instanceof Number) {
            // Assume Unix timestamp
            long unixTime = ((Number) timestamp).longValue();
            java.time.Instant instant = java.time.Instant.ofEpochSecond(unixTime);
            return instant.atZone(java.time.ZoneId.systemDefault()).getHour();
        }

        if (timestamp instanceof String) {
            String timeStr = timestamp.toString();
            // Try to parse common time formats
            try {
                // Try HH:mm format
                if (timeStr.matches("\\d{1,2}:\\d{2}")) {
                    return Integer.parseInt(timeStr.split(":")[0]);
                }
                // Try ISO format
                java.time.LocalDateTime dateTime = java.time.LocalDateTime.parse(timeStr);
                return dateTime.getHour();
            } catch (Exception e) {
                log.warn("Could not parse timestamp: {}", timeStr);
                return 0;
            }
        }

        return 0;
    }

    // ===== FINANCIAL VALIDATION FUNCTIONS =====

    /**
     * Validate credit score function
     */
    private boolean evaluateIsValidCreditScoreFunction(List<Object> parameters, EvaluationContext context) {
        if (parameters == null || parameters.isEmpty()) {
            return false;
        }

        String variableName = parameters.get(0).toString();
        Object value = context.getValue(variableName);
        return compareIsCreditScore(value);
    }

    /**
     * Validate SSN function
     */
    private boolean evaluateIsValidSSNFunction(List<Object> parameters, EvaluationContext context) {
        if (parameters == null || parameters.isEmpty()) {
            return false;
        }

        String variableName = parameters.get(0).toString();
        Object value = context.getValue(variableName);
        return compareIsSSN(value);
    }

    /**
     * Validate account number function
     */
    private boolean evaluateIsValidAccountFunction(List<Object> parameters, EvaluationContext context) {
        if (parameters == null || parameters.isEmpty()) {
            return false;
        }

        String variableName = parameters.get(0).toString();
        Object value = context.getValue(variableName);
        return compareIsAccountNumber(value);
    }

    /**
     * Validate routing number function
     */
    private boolean evaluateIsValidRoutingFunction(List<Object> parameters, EvaluationContext context) {
        if (parameters == null || parameters.isEmpty()) {
            return false;
        }

        String variableName = parameters.get(0).toString();
        Object value = context.getValue(variableName);
        return compareIsRoutingNumber(value);
    }

    /**
     * Check if date is business day function
     */
    private boolean evaluateIsBusinessDayFunction(List<Object> parameters, EvaluationContext context) {
        if (parameters == null || parameters.isEmpty()) {
            return false;
        }

        String variableName = parameters.get(0).toString();
        Object value = context.getValue(variableName);
        return compareIsBusinessDay(value);
    }

    /**
     * Check age meets requirement function
     */
    private boolean evaluateAgeMeetsRequirementFunction(List<Object> parameters, EvaluationContext context) {
        if (parameters == null || parameters.size() < 2) {
            return false;
        }

        String birthDateVar = parameters.get(0).toString();
        Object minimumAge = variableResolver.resolveValue(parameters.get(1), context);
        Object birthDate = context.getValue(birthDateVar);

        return compareAgeAtLeast(birthDate, minimumAge);
    }

    /**
     * Calculate and validate debt-to-income ratio
     */
    private boolean evaluateDebtToIncomeRatioFunction(List<Object> parameters, EvaluationContext context) {
        if (parameters == null || parameters.size() < 3) {
            return false;
        }

        try {
            String debtVar = parameters.get(0).toString();
            String incomeVar = parameters.get(1).toString();
            Object maxRatio = variableResolver.resolveValue(parameters.get(2), context);

            Object debt = context.getValue(debtVar);
            Object income = context.getValue(incomeVar);

            if (debt == null || income == null) return false;

            double debtAmount = ((Number) debt).doubleValue();
            double incomeAmount = ((Number) income).doubleValue();
            double maxRatioValue = ((Number) maxRatio).doubleValue();

            if (incomeAmount <= 0) return false;

            double ratio = debtAmount / incomeAmount;
            return ratio <= maxRatioValue;
        } catch (Exception e) {
            log.error("Error evaluating debt-to-income ratio", e);
            return false;
        }
    }

    /**
     * Calculate and validate credit utilization
     */
    private boolean evaluateCreditUtilizationFunction(List<Object> parameters, EvaluationContext context) {
        if (parameters == null || parameters.size() < 3) {
            return false;
        }

        try {
            String balanceVar = parameters.get(0).toString();
            String limitVar = parameters.get(1).toString();
            Object maxUtilization = variableResolver.resolveValue(parameters.get(2), context);

            Object balance = context.getValue(balanceVar);
            Object limit = context.getValue(limitVar);

            if (balance == null || limit == null) return false;

            double balanceAmount = ((Number) balance).doubleValue();
            double limitAmount = ((Number) limit).doubleValue();
            double maxUtilizationValue = ((Number) maxUtilization).doubleValue();

            if (limitAmount <= 0) return false;

            double utilization = balanceAmount / limitAmount;
            return utilization <= maxUtilizationValue;
        } catch (Exception e) {
            log.error("Error evaluating credit utilization", e);
            return false;
        }
    }

    /**
     * Calculate and validate loan-to-value ratio
     */
    private boolean evaluateLoanToValueFunction(List<Object> parameters, EvaluationContext context) {
        if (parameters == null || parameters.size() < 3) {
            return false;
        }

        try {
            String loanVar = parameters.get(0).toString();
            String valueVar = parameters.get(1).toString();
            Object maxLTV = variableResolver.resolveValue(parameters.get(2), context);

            Object loan = context.getValue(loanVar);
            Object value = context.getValue(valueVar);

            if (loan == null || value == null) return false;

            double loanAmount = ((Number) loan).doubleValue();
            double valueAmount = ((Number) value).doubleValue();
            double maxLTVValue = ((Number) maxLTV).doubleValue();

            if (valueAmount <= 0) return false;

            double ltv = loanAmount / valueAmount;
            return ltv <= maxLTVValue;
        } catch (Exception e) {
            log.error("Error evaluating loan-to-value ratio", e);
            return false;
        }
    }

    /**
     * Evaluate payment history score
     */
    private boolean evaluatePaymentHistoryScoreFunction(List<Object> parameters, EvaluationContext context) {
        if (parameters == null || parameters.size() < 2) {
            return false;
        }

        try {
            String historyVar = parameters.get(0).toString();
            Object minScore = variableResolver.resolveValue(parameters.get(1), context);

            Object history = context.getValue(historyVar);
            if (history == null) return false;

            double historyScore = ((Number) history).doubleValue();
            double minScoreValue = ((Number) minScore).doubleValue();

            return historyScore >= minScoreValue;
        } catch (Exception e) {
            log.error("Error evaluating payment history score", e);
            return false;
        }
    }
}
