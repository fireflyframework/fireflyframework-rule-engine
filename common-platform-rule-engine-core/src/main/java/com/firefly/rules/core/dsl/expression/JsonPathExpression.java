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

/**
 * Represents a JSON path expression for accessing nested JSON values.
 * Examples: 
 * - user.name (access name property of user object)
 * - users[0].email (access email of first user in array)
 * - response.data.items[2].price (deep nested access)
 * - todos.length (get array length)
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class JsonPathExpression extends Expression {
    
    /**
     * The source expression that contains the JSON data
     */
    private Expression sourceExpression;
    
    /**
     * The JSON path to access (e.g., "user.name", "items[0].price")
     */
    private String jsonPath;

    public JsonPathExpression(SourceLocation location, Expression sourceExpression, String jsonPath) {
        super(location);
        this.sourceExpression = sourceExpression;
        this.jsonPath = jsonPath;
    }
    
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitJsonPathExpression(this);
    }
    
    @Override
    public ExpressionType getExpressionType() {
        // JSON path can return any type depending on the path
        return ExpressionType.ANY;
    }
    
    @Override
    public boolean isConstant() {
        // JSON path expressions are not constant since they depend on runtime data
        return false;
    }
    
    @Override
    public boolean hasVariableReferences() {
        return sourceExpression != null && sourceExpression.hasVariableReferences();
    }
    
    @Override
    public String toDebugString() {
        return String.format("JsonPath(%s.%s)", 
            sourceExpression != null ? sourceExpression.toDebugString() : "null", 
            jsonPath);
    }
}
