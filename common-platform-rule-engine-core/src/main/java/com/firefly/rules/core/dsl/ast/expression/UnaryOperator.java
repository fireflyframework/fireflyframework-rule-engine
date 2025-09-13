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

package com.firefly.rules.core.dsl.ast.expression;

/**
 * Enumeration of unary operators.
 */
public enum UnaryOperator {
    // Arithmetic operators
    NEGATE("-", ExpressionType.NUMBER),
    POSITIVE("+", ExpressionType.NUMBER),
    MINUS("-", ExpressionType.NUMBER),  // Alias for NEGATE
    PLUS("+", ExpressionType.NUMBER),   // Alias for POSITIVE

    // Logical operators
    NOT("not", ExpressionType.BOOLEAN),

    // Existence operators
    EXISTS("exists", ExpressionType.BOOLEAN),
    IS_NULL("is_null", ExpressionType.BOOLEAN),
    IS_NOT_NULL("is_not_null", ExpressionType.BOOLEAN),

    // Type checking operators
    IS_NUMBER("is_number", ExpressionType.BOOLEAN),
    IS_STRING("is_string", ExpressionType.BOOLEAN),
    IS_BOOLEAN("is_boolean", ExpressionType.BOOLEAN),
    IS_LIST("is_list", ExpressionType.BOOLEAN),

    // String operators
    TO_UPPER("to_upper", ExpressionType.STRING),
    TO_LOWER("to_lower", ExpressionType.STRING),
    TRIM("trim", ExpressionType.STRING),
    LENGTH("length", ExpressionType.NUMBER);

    private final String symbol;
    private final ExpressionType resultType;

    UnaryOperator(String symbol, ExpressionType resultType) {
        this.symbol = symbol;
        this.resultType = resultType;
    }
    
    public String getSymbol() {
        return symbol;
    }

    public ExpressionType getResultType() {
        return resultType;
    }
    
    public static UnaryOperator fromSymbol(String symbol) {
        for (UnaryOperator op : values()) {
            if (op.symbol.equals(symbol)) {
                return op;
            }
        }
        throw new IllegalArgumentException("Unknown unary operator: " + symbol);
    }
}
