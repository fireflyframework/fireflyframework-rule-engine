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

package com.firefly.rules.core.dsl.ast.action;

import com.firefly.rules.core.dsl.ast.ASTVisitor;
import com.firefly.rules.core.dsl.ast.SourceLocation;
import com.firefly.rules.core.dsl.ast.expression.Expression;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Represents a variable assignment action.
 * Examples: set result to "approved", assign score to calculateScore(customer)
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AssignmentAction extends Action {
    
    /**
     * Name of the variable to assign to
     */
    private String variableName;
    
    /**
     * Expression to evaluate and assign to the variable
     */
    private Expression value;
    
    /**
     * Assignment operator type
     */
    private AssignmentOperator operator;
    
    public AssignmentAction(SourceLocation location, String variableName, Expression value) {
        super(location);
        this.variableName = variableName;
        this.value = value;
        this.operator = AssignmentOperator.ASSIGN;
    }
    
    public AssignmentAction(SourceLocation location, String variableName, Expression value, AssignmentOperator operator) {
        super(location);
        this.variableName = variableName;
        this.value = value;
        this.operator = operator;
    }
    
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitAssignmentAction(this);
    }
    
    @Override
    public boolean hasVariableReferences() {
        return value.hasVariableReferences();
    }
    
    @Override
    public String toDebugString() {
        return String.format("%s %s %s", variableName, operator.getSymbol(), value.toDebugString());
    }
}



