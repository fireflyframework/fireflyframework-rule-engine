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

package com.firefly.rules.core.dsl;

import com.firefly.rules.core.dsl.action.*;
import com.firefly.rules.core.dsl.condition.ComparisonCondition;
import com.firefly.rules.core.dsl.condition.ExpressionCondition;
import com.firefly.rules.core.dsl.condition.LogicalCondition;
import com.firefly.rules.core.dsl.expression.*;
import com.firefly.rules.core.dsl.action.WhileAction;
import com.firefly.rules.core.dsl.action.DoWhileAction;

/**
 * Visitor interface for traversing and operating on AST nodes.
 * Implements the visitor pattern to allow different operations on AST nodes
 * without modifying the node classes themselves.
 *
 * @param <T> the return type of visitor operations
 */
public interface ASTVisitor<T> {
    
    // Expression visitors
    T visitBinaryExpression(BinaryExpression node);
    T visitUnaryExpression(UnaryExpression node);
    T visitVariableExpression(VariableExpression node);
    T visitLiteralExpression(LiteralExpression node);
    T visitFunctionCallExpression(FunctionCallExpression node);
    T visitArithmeticExpression(ArithmeticExpression node);
    T visitJsonPathExpression(JsonPathExpression node);
    T visitRestCallExpression(RestCallExpression node);
    
    // Condition visitors
    T visitComparisonCondition(ComparisonCondition node);
    T visitLogicalCondition(LogicalCondition node);
    T visitExpressionCondition(ExpressionCondition node);
    
    // Action visitors
    T visitAssignmentAction(AssignmentAction node);
    T visitFunctionCallAction(FunctionCallAction node);
    T visitConditionalAction(ConditionalAction node);
    T visitCalculateAction(CalculateAction node);
    T visitRunAction(RunAction node);
    T visitSetAction(SetAction node);
    T visitArithmeticAction(ArithmeticAction node);
    T visitListAction(ListAction node);
    T visitCircuitBreakerAction(CircuitBreakerAction node);
    T visitForEachAction(ForEachAction node);
    T visitWhileAction(WhileAction node);
    T visitDoWhileAction(DoWhileAction node);

    // Default implementation for unknown nodes
    default T visitUnknown(ASTNode node) {
        throw new UnsupportedOperationException(
            "Visitor does not support node type: " + node.getClass().getSimpleName());
    }
}
