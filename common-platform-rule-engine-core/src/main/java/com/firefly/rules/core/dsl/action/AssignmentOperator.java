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

package com.firefly.rules.core.dsl.action;

/**
 * Enumeration of assignment operators.
 */
public enum AssignmentOperator {
    ASSIGN("to", "="),
    ADD_ASSIGN("add", "+="),
    SUBTRACT_ASSIGN("subtract", "-="),
    MULTIPLY_ASSIGN("multiply", "*="),
    DIVIDE_ASSIGN("divide", "/=");
    
    private final String keyword;
    private final String symbol;
    
    AssignmentOperator(String keyword, String symbol) {
        this.keyword = keyword;
        this.symbol = symbol;
    }
    
    public String getKeyword() {
        return keyword;
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public static AssignmentOperator fromKeyword(String keyword) {
        for (AssignmentOperator op : values()) {
            if (op.keyword.equals(keyword)) {
                return op;
            }
        }
        throw new IllegalArgumentException("Unknown assignment operator: " + keyword);
    }
    
    public static AssignmentOperator fromSymbol(String symbol) {
        for (AssignmentOperator op : values()) {
            if (symbol.equals(op.symbol)) {
                return op;
            }
        }
        throw new IllegalArgumentException("Unknown assignment operator symbol: " + symbol);
    }
}
