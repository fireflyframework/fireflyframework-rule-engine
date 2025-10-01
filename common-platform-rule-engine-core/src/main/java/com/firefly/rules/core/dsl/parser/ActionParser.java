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

package com.firefly.rules.core.dsl.parser;

import com.firefly.rules.core.dsl.action.*;
import com.firefly.rules.core.dsl.condition.Condition;
import com.firefly.rules.core.dsl.expression.Expression;
import com.firefly.rules.core.dsl.lexer.Token;
import com.firefly.rules.core.dsl.lexer.TokenType;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser for action statements.
 * Handles various action types like assignments, function calls, and conditional actions.
 */
@Slf4j
public class ActionParser extends BaseParser {
    
    private final ExpressionParser expressionParser;
    private final ConditionParser conditionParser;
    
    public ActionParser(List<Token> tokens) {
        super(tokens);
        this.expressionParser = new ExpressionParser(tokens);
        this.conditionParser = new ConditionParser(tokens);
    }
    
    /**
     * Parse an action statement
     *
     * Action grammar:
     * action         → setAction | calculateAction | runAction | callAction | conditionalAction
     * setAction      → "set" IDENTIFIER "to" expression
     * calculateAction → "calculate" IDENTIFIER "as" expression
     * runAction      → "run" IDENTIFIER "as" expression
     * callAction     → "call" IDENTIFIER "with" "[" expressionList? "]"
     * conditionalAction → "if" condition "then" actionList ( "else" actionList )?
     * actionList     → action ( "," action )*
     * expressionList → expression ( "," expression )*
     */
    public Action parseAction() {
        if (match(TokenType.SET)) {
            return parseSetAction();
        }

        if (match(TokenType.CALCULATE)) {
            return parseCalculateAction();
        }

        if (match(TokenType.RUN)) {
            return parseRunAction();
        }

        if (match(TokenType.CALL)) {
            return parseCallAction();
        }

        if (match(TokenType.IF)) {
            return parseConditionalAction();
        }

        // Arithmetic actions
        if (match(TokenType.ADD)) {
            return parseArithmeticAction(ArithmeticAction.ArithmeticOperationType.ADD);
        }

        if (match(TokenType.SUBTRACT)) {
            return parseArithmeticAction(ArithmeticAction.ArithmeticOperationType.SUBTRACT);
        }

        // Check for multiply/divide keywords using identifier matching
        if (check(TokenType.IDENTIFIER)) {
            String keyword = peek().getLexeme();
            if ("multiply".equals(keyword)) {
                advance();
                return parseArithmeticAction(ArithmeticAction.ArithmeticOperationType.MULTIPLY);
            }
            if ("divide".equals(keyword)) {
                advance();
                return parseArithmeticAction(ArithmeticAction.ArithmeticOperationType.DIVIDE);
            }
        }

        // List actions
        if (match(TokenType.APPEND)) {
            return parseListAction(ListAction.ListOperationType.APPEND);
        }

        if (match(TokenType.PREPEND)) {
            return parseListAction(ListAction.ListOperationType.PREPEND);
        }

        if (match(TokenType.REMOVE)) {
            return parseListAction(ListAction.ListOperationType.REMOVE);
        }

        // Circuit breaker action
        if (match(TokenType.CIRCUIT_BREAKER)) {
            return parseCircuitBreakerAction();
        }

        throw error(
            "Expected action statement",
            "PARSE_002",
            List.of("Use 'set', 'calculate', 'run', 'call', 'if', arithmetic operations, list operations, or 'circuit_breaker' to start an action")
        );
    }
    
    /**
     * Parse a list of actions
     */
    public List<Action> parseActionList() {
        List<Action> actions = new ArrayList<>();
        
        if (!isAtEnd() && !checkAny(TokenType.ELSE, TokenType.EOF)) {
            do {
                actions.add(parseAction());
            } while (match(TokenType.COMMA) && !isAtEnd());
        }
        
        return actions;
    }
    
    /**
     * Parse set action: "set variable to expression"
     */
    private Action parseSetAction() {
        Token setToken = previous();
        
        if (!check(TokenType.IDENTIFIER)) {
            throw error(
                "Expected variable name after 'set'",
                "PARSE_003",
                List.of("Add a variable name after 'set'")
            );
        }
        
        Token variable = advance();
        
        consume(TokenType.TO, "Expected 'to' after variable name in set action");
        
        // Parse the value expression
        this.expressionParser.setCurrentPosition(this.current);
        Expression value = this.expressionParser.parseExpression();
        this.current = this.expressionParser.getCurrentPosition();
        
        return new SetAction(setToken.getLocation(), variable.getLexeme(), value);
    }
    
    /**
     * Parse calculate action: "calculate variable as expression"
     */
    private Action parseCalculateAction() {
        Token calculateToken = previous();

        if (!check(TokenType.IDENTIFIER)) {
            throw error(
                "Expected variable name after 'calculate'",
                "PARSE_004",
                List.of("Add a variable name after 'calculate'")
            );
        }

        Token variable = advance();

        consume(TokenType.AS, "Expected 'as' after variable name in calculate action");

        // Parse the expression
        this.expressionParser.setCurrentPosition(this.current);
        Expression expression = this.expressionParser.parseExpression();
        this.current = this.expressionParser.getCurrentPosition();

        return new CalculateAction(calculateToken.getLocation(), variable.getLexeme(), expression);
    }

    /**
     * Parse run action: "run variable as expression"
     */
    private Action parseRunAction() {
        Token runToken = previous();

        if (!check(TokenType.IDENTIFIER)) {
            throw error(
                "Expected variable name after 'run'",
                "PARSE_006",
                List.of("Add a variable name after 'run'")
            );
        }

        Token variable = advance();

        consume(TokenType.AS, "Expected 'as' after variable name in run action");

        // Parse the expression
        this.expressionParser.setCurrentPosition(this.current);
        Expression expression = this.expressionParser.parseExpression();
        this.current = this.expressionParser.getCurrentPosition();

        return new RunAction(runToken.getLocation(), variable.getLexeme(), expression);
    }

    /**
     * Parse call action: "call function with [arguments]"
     */
    private Action parseCallAction() {
        Token callToken = previous();
        
        if (!check(TokenType.IDENTIFIER)) {
            throw error(
                "Expected function name after 'call'",
                "PARSE_005",
                List.of("Add a function name after 'call'")
            );
        }
        
        Token functionName = advance();
        
        consume(TokenType.WITH, "Expected 'with' after function name in call action");
        consume(TokenType.LBRACKET, "Expected '[' after 'with' in call action");
        
        // Parse arguments
        List<Expression> arguments = new ArrayList<>();
        if (!check(TokenType.RBRACKET)) {
            do {
                this.expressionParser.setCurrentPosition(this.current);
                arguments.add(this.expressionParser.parseExpression());
                this.current = this.expressionParser.getCurrentPosition();
            } while (match(TokenType.COMMA));
        }
        
        consume(TokenType.RBRACKET, "Expected ']' after function arguments");
        
        return new FunctionCallAction(callToken.getLocation(), functionName.getLexeme(), arguments);
    }
    
    /**
     * Parse conditional action: "if condition then actions else actions"
     */
    private Action parseConditionalAction() {
        Token ifToken = previous();
        
        // Parse condition
        this.conditionParser.setCurrentPosition(this.current);
        Condition condition = this.conditionParser.parseCondition();
        this.current = this.conditionParser.getCurrentPosition();
        
        consume(TokenType.THEN, "Expected 'then' after condition in if statement");
        
        // Parse then actions
        List<Action> thenActions = parseActionList();
        
        // Parse optional else actions
        List<Action> elseActions = null;
        if (match(TokenType.ELSE)) {
            elseActions = parseActionList();
        }
        
        return new ConditionalAction(ifToken.getLocation(), condition, thenActions, elseActions);
    }

    /**
     * Parse arithmetic action: "add value to variable", "subtract value from variable", etc.
     */
    private Action parseArithmeticAction(ArithmeticAction.ArithmeticOperationType operation) {
        Token operationToken = previous();

        // Parse the value expression
        this.expressionParser.setCurrentPosition(this.current);
        Expression value = this.expressionParser.parseExpression();
        this.current = this.expressionParser.getCurrentPosition();

        // Expect the preposition (to/from/by)
        String expectedPreposition = operation.getPreposition();
        if (!check(TokenType.TO) && !check(TokenType.FROM) && !check(TokenType.BY)) {
            throw error(
                "Expected '" + expectedPreposition + "' after value in " + operation.getKeyword() + " action",
                "PARSE_006",
                List.of("Add '" + expectedPreposition + "' after the value")
            );
        }

        advance(); // consume the preposition

        if (!check(TokenType.IDENTIFIER)) {
            throw error(
                "Expected variable name after '" + expectedPreposition + "'",
                "PARSE_007",
                List.of("Add a variable name after '" + expectedPreposition + "'")
            );
        }

        Token variable = advance();

        return new ArithmeticAction(operationToken.getLocation(), operation, variable.getLexeme(), value);
    }

    /**
     * Parse list action: "append value to list", "prepend value to list", "remove value from list"
     */
    private Action parseListAction(ListAction.ListOperationType operation) {
        Token operationToken = previous();

        // Parse the value expression
        this.expressionParser.setCurrentPosition(this.current);
        Expression value = this.expressionParser.parseExpression();
        this.current = this.expressionParser.getCurrentPosition();

        // Expect the preposition (to/from)
        String expectedPreposition = operation.getPreposition();
        if (!check(TokenType.TO) && !check(TokenType.FROM)) {
            throw error(
                "Expected '" + expectedPreposition + "' after value in " + operation.getKeyword() + " action",
                "PARSE_008",
                List.of("Add '" + expectedPreposition + "' after the value")
            );
        }

        advance(); // consume the preposition

        if (!check(TokenType.IDENTIFIER)) {
            throw error(
                "Expected list variable name after '" + expectedPreposition + "'",
                "PARSE_009",
                List.of("Add a list variable name after '" + expectedPreposition + "'")
            );
        }

        Token listVariable = advance();

        return new ListAction(operationToken.getLocation(), operation, value, listVariable.getLexeme());
    }

    /**
     * Parse circuit breaker action: "circuit_breaker message"
     */
    private Action parseCircuitBreakerAction() {
        Token circuitBreakerToken = previous();

        if (!check(TokenType.STRING)) {
            throw error(
                "Expected message string after 'circuit_breaker'",
                "PARSE_010",
                List.of("Add a message string after 'circuit_breaker'")
            );
        }

        Token messageToken = advance();
        String message = messageToken.getLexeme();

        // Remove quotes from string literal
        if (message.startsWith("\"") && message.endsWith("\"")) {
            message = message.substring(1, message.length() - 1);
        }

        return new CircuitBreakerAction(circuitBreakerToken.getLocation(), message);
    }
}
