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

package com.firefly.rules.core.dsl.expression;

import com.firefly.rules.core.dsl.ASTVisitor;
import com.firefly.rules.core.dsl.SourceLocation;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents an arithmetic expression with multiple operands.
 * Examples: add(a, b, c), multiply(x, y), max(values...)
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArithmeticExpression extends Expression {
    
    /**
     * The arithmetic operation to perform
     */
    private ArithmeticOperation operation;
    
    /**
     * Operands for the arithmetic operation
     */
    private List<Expression> operands;
    
    public ArithmeticExpression(SourceLocation location, ArithmeticOperation operation, List<Expression> operands) {
        super(location);
        this.operation = operation;
        this.operands = operands;
    }
    
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitArithmeticExpression(this);
    }
    
    @Override
    public ExpressionType getExpressionType() {
        return ExpressionType.NUMBER;
    }
    
    @Override
    public boolean isConstant() {
        return operands != null && operands.stream().allMatch(Expression::isConstant);
    }
    
    @Override
    public boolean hasVariableReferences() {
        return operands != null && operands.stream().anyMatch(Expression::hasVariableReferences);
    }
    
    @Override
    public String toDebugString() {
        if (operands == null || operands.isEmpty()) {
            return operation.getSymbol() + "()";
        }
        
        String args = operands.stream()
                .map(Expression::toDebugString)
                .collect(Collectors.joining(", "));
        
        return operation.getSymbol() + "(" + args + ")";
    }
    
    /**
     * Get the number of operands
     */
    public int getOperandCount() {
        return operands != null ? operands.size() : 0;
    }
    
    /**
     * Get a specific operand by index
     */
    public Expression getOperand(int index) {
        if (operands == null || index < 0 || index >= operands.size()) {
            throw new IndexOutOfBoundsException("Operand index out of bounds: " + index);
        }
        return operands.get(index);
    }
}



