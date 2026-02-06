/*
 * Copyright 2024-2026 Firefly Software Solutions Inc
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

package org.fireflyframework.rules.core.dsl.expression;

/**
 * Enumeration of arithmetic operations.
 */
public enum ArithmeticOperation {
    // Basic arithmetic
    ADD("add", "+", 6),
    SUBTRACT("subtract", "-", 6),
    MULTIPLY("multiply", "*", 7),
    DIVIDE("divide", "/", 7),
    MODULO("modulo", "%", 7),
    POWER("power", "^", 8),

    // Mathematical functions
    ABS("abs", "abs", 9),
    MIN("min", "min", 9),
    MAX("max", "max", 9),
    ROUND("round", "round", 9),
    FLOOR("floor", "floor", 9),
    CEIL("ceil", "ceil", 9),
    SQRT("sqrt", "sqrt", 9),
    SUM("sum", "sum", 9),
    AVERAGE("average", "avg", 9);
    
    private final String name;
    private final String symbol;
    private final int precedence;

    ArithmeticOperation(String name, String symbol, int precedence) {
        this.name = name;
        this.symbol = symbol;
        this.precedence = precedence;
    }

    public String getName() {
        return name;
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public int getPrecedence() {
        return precedence;
    }

    public int getMinOperands() {
        return switch (this) {
            case ABS, ROUND, FLOOR, CEIL, SQRT -> 1;
            case MIN, MAX -> 2;
            case SUM, AVERAGE -> 1; // Can take 1 or more
            default -> 2; // Binary operations
        };
    }

    public int getMaxOperands() {
        return switch (this) {
            case ABS, ROUND, FLOOR, CEIL, SQRT -> 1;
            case SUM, AVERAGE -> Integer.MAX_VALUE; // Can take any number
            default -> 2; // Binary operations
        };
    }
    
    public static ArithmeticOperation fromSymbol(String symbol) {
        for (ArithmeticOperation op : values()) {
            if (op.symbol.equals(symbol)) {
                return op;
            }
        }
        throw new IllegalArgumentException("Unknown arithmetic operation: " + symbol);
    }
}
