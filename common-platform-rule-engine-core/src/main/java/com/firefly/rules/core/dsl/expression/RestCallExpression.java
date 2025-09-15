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
 * Represents a REST API call expression.
 * Examples:
 * - rest_get("https://api.example.com/users/123")
 * - rest_post("https://api.example.com/users", {"name": "John", "email": "john@example.com"})
 * - rest_put("https://api.example.com/users/123", userData, {"Authorization": "Bearer token"})
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RestCallExpression extends Expression {
    
    /**
     * HTTP method (GET, POST, PUT, DELETE, etc.)
     */
    private String httpMethod;
    
    /**
     * URL expression for the REST endpoint
     */
    private Expression urlExpression;
    
    /**
     * Optional request body expression (for POST, PUT, etc.)
     */
    private Expression bodyExpression;
    
    /**
     * Optional headers expression (Map<String, String>)
     */
    private Expression headersExpression;
    
    /**
     * Optional timeout in milliseconds
     */
    private Expression timeoutExpression;

    public RestCallExpression(SourceLocation location, String httpMethod, Expression urlExpression) {
        super(location);
        this.httpMethod = httpMethod.toUpperCase();
        this.urlExpression = urlExpression;
    }
    
    public RestCallExpression(SourceLocation location, String httpMethod, Expression urlExpression, 
                             Expression bodyExpression, Expression headersExpression, Expression timeoutExpression) {
        super(location);
        this.httpMethod = httpMethod.toUpperCase();
        this.urlExpression = urlExpression;
        this.bodyExpression = bodyExpression;
        this.headersExpression = headersExpression;
        this.timeoutExpression = timeoutExpression;
    }
    
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitRestCallExpression(this);
    }
    
    @Override
    public ExpressionType getExpressionType() {
        // REST calls return JSON objects/arrays
        return ExpressionType.ANY;
    }
    
    @Override
    public boolean isConstant() {
        // REST calls are never constant since they involve external API calls
        return false;
    }
    
    @Override
    public boolean hasVariableReferences() {
        return (urlExpression != null && urlExpression.hasVariableReferences()) ||
               (bodyExpression != null && bodyExpression.hasVariableReferences()) ||
               (headersExpression != null && headersExpression.hasVariableReferences()) ||
               (timeoutExpression != null && timeoutExpression.hasVariableReferences());
    }
    
    @Override
    public String toDebugString() {
        StringBuilder sb = new StringBuilder();
        sb.append("RestCall(").append(httpMethod).append(" ");
        sb.append(urlExpression != null ? urlExpression.toDebugString() : "null");
        
        if (bodyExpression != null) {
            sb.append(", body=").append(bodyExpression.toDebugString());
        }
        if (headersExpression != null) {
            sb.append(", headers=").append(headersExpression.toDebugString());
        }
        if (timeoutExpression != null) {
            sb.append(", timeout=").append(timeoutExpression.toDebugString());
        }
        
        sb.append(")");
        return sb.toString();
    }
}
