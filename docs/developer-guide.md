# Firefly Rule Engine AST System Deep Dive

This comprehensive guide provides an exhaustive exploration of the Firefly Rule Engine's Abstract Syntax Tree (AST) system architecture. It covers every aspect of how the AST system works, from lexical analysis to rule evaluation, and provides detailed instructions for extending the system with new functionality.

## Table of Contents

- [AST System Overview](#ast-system-overview)
- [Architecture Components](#architecture-components)
- [Lexical Analysis System](#lexical-analysis-system)
- [AST Node Hierarchy](#ast-node-hierarchy)
- [Parsing Pipeline](#parsing-pipeline)
- [Visitor Pattern Implementation](#visitor-pattern-implementation)
- [Evaluation Engine](#evaluation-engine)
- [Validation System](#validation-system)
- [Error Handling](#error-handling)
- [Performance Optimizations](#performance-optimizations)
- [Extending the AST System](#extending-the-ast-system)
- [Best Practices](#best-practices)

## AST System Overview

The Firefly Rule Engine is built on a sophisticated **Abstract Syntax Tree (AST) architecture** that transforms YAML-based business rules into structured, type-safe, and highly performant executable code. This system replaces traditional string-based rule evaluation with a modern compiler-like approach that provides:

### 🎯 **Core Benefits**

- **Type Safety**: All operations are type-checked at parse time
- **Performance**: AST nodes are optimized for fast evaluation
- **Extensibility**: New operators and functions can be added without modifying existing code
- **Debugging**: Rich source location information for error reporting
- **Caching**: Parsed AST trees can be cached for repeated evaluation
- **Validation**: Comprehensive semantic validation before execution

### 🏗️ **System Architecture**

The AST system follows a multi-layered architecture that separates concerns and enables clean extensibility:

```
┌─────────────────────────────────────────────────────────────────┐
│                        Input Layer                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │ YAML Rules  │  │ REST API    │  │ Input Data Map          │  │
│  │ Definition  │  │ Request     │  │ (Runtime Variables)     │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Lexical Analysis Layer                      │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │   Lexer     │  │ Token       │  │ TokenType Enumeration   │  │
│  │ (Scanner)   │  │ Stream      │  │ (200+ token types)      │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Parsing Layer                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │ YAML Parser │  │ DSL Parser  │  │ Specialized Parsers     │  │
│  │             │  │ (Main)      │  │ • ExpressionParser      │  │
│  │             │  │             │  │ • ConditionParser       │  │
│  │             │  │             │  │ • ActionParser          │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                     AST Model Layer                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │ Expression  │  │ Condition   │  │ Action Nodes            │  │
│  │ Nodes       │  │ Nodes       │  │ • SetAction             │  │
│  │ • Binary    │  │ • Comparison│  │ • CalculateAction       │  │
│  │ • Unary     │  │ • Logical   │  │ • FunctionCallAction    │  │
│  │ • Literal   │  │ • Expression│  │ • ConditionalAction     │  │
│  │ • Variable  │  │             │  │ • CircuitBreakerAction  │  │
│  │ • Function  │  │             │  │                         │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Visitor Pattern Layer                        │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │ Expression  │  │ Action      │  │ Validation & Analysis   │  │
│  │ Evaluator   │  │ Executor    │  │ • ValidationVisitor     │  │
│  │             │  │             │  │ • VariableCollector     │  │
│  │             │  │             │  │ • TypeChecker           │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Execution Layer                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │ Evaluation  │  │ Evaluation  │  │ Support Systems         │  │
│  │ Engine      │  │ Context     │  │ • AST Cache Service     │  │
│  │             │  │ • Variables │  │ • Error Handling        │  │
│  │             │  │ • Constants │  │ • Audit Trail           │  │
│  │             │  │ • State     │  │ • Performance Metrics   │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### 🔄 **Processing Flow**

The complete flow from YAML input to evaluation result follows these stages:

1. **YAML Parsing**: Convert YAML string to Map structure using Jackson YAML parser
2. **Lexical Analysis**: Tokenize DSL expressions into token stream using custom Lexer
3. **Syntax Parsing**: Build AST nodes from tokens using recursive descent parsing
4. **Semantic Validation**: Validate AST structure and type compatibility
5. **Variable Collection**: Extract variable references for constant loading
6. **AST Caching**: Cache parsed AST for performance optimization (optional)
7. **Evaluation**: Execute AST using visitor pattern with evaluation context
8. **Result Generation**: Collect outputs and generate evaluation result

## Architecture Components

### 📦 **Core Package Structure**

The AST system is organized into logical packages that separate concerns and enable clean extensibility:

```
com.firefly.rules.core.dsl.ast/
├── lexer/                    # Lexical analysis components
│   ├── Lexer.java           # Main lexer implementation
│   ├── Token.java           # Token representation
│   ├── TokenType.java       # Token type enumeration (200+ types)
│   └── LexerException.java  # Lexer-specific exceptions
├── parser/                   # Parsing components
│   ├── DSLParser.java       # Main DSL parser coordinator
│   ├── ExpressionParser.java # Expression parsing (arithmetic, logical)
│   ├── ConditionParser.java # Condition parsing (comparisons, validations)
│   ├── ActionParser.java    # Action parsing (assignments, calculations)
│   ├── ASTRulesDSLParser.java # YAML to AST converter
│   └── BaseParser.java      # Common parsing utilities
├── expression/              # Expression AST nodes
│   ├── Expression.java      # Base expression class
│   ├── BinaryExpression.java # Binary operations (+, -, *, /, etc.)
│   ├── UnaryExpression.java # Unary operations (-, !, validation ops)
│   ├── LiteralExpression.java # Literal values (numbers, strings, booleans)
│   ├── VariableExpression.java # Variable references
│   ├── FunctionCallExpression.java # Function calls with parameters
│   ├── ArithmeticExpression.java # Complex arithmetic expressions
│   ├── JsonPathExpression.java # JSON path queries
│   ├── RestCallExpression.java # REST API calls
│   ├── BinaryOperator.java  # Binary operator enumeration
│   ├── UnaryOperator.java   # Unary operator enumeration
│   └── ExpressionType.java  # Expression type enumeration
├── condition/               # Condition AST nodes
│   ├── Condition.java       # Base condition class
│   ├── ComparisonCondition.java # Comparison operations (>, <, ==, etc.)
│   ├── LogicalCondition.java # Logical operations (AND, OR, NOT)
│   ├── ExpressionCondition.java # Expression-based conditions
│   └── ComparisonOperator.java # Comparison operator enumeration
├── action/                  # Action AST nodes
│   ├── Action.java          # Base action class
│   ├── SetAction.java       # Variable assignment (set var to value)
│   ├── CalculateAction.java # Arithmetic calculations
│   ├── AssignmentAction.java # Assignment operations (=, +=, etc.)
│   ├── FunctionCallAction.java # Function execution
│   ├── ConditionalAction.java # If-then-else actions
│   ├── ArithmeticAction.java # Arithmetic actions
│   ├── ListAction.java      # List operations (append, prepend, etc.)
│   ├── CircuitBreakerAction.java # Execution control
│   └── AssignmentOperator.java # Assignment operator enumeration
├── visitor/                 # Visitor pattern implementations
│   ├── EvaluationContext.java # Execution context and state
│   ├── ExpressionEvaluator.java # Expression evaluation visitor
│   ├── ActionExecutor.java  # Action execution visitor
│   ├── ValidationVisitor.java # AST validation visitor
│   ├── VariableReferenceCollector.java # Variable extraction visitor
│   └── ValidationError.java # Validation error representation
├── model/                   # AST model classes
│   ├── ASTRulesDSL.java     # Complete rule model
│   └── SourceLocation.java  # Source position tracking
├── evaluation/              # Evaluation engine
│   └── ASTRulesEvaluationEngine.java # Main evaluation orchestrator
├── exception/               # Exception classes
│   ├── ASTException.java    # General AST exceptions
│   ├── LexerException.java  # Lexer exceptions
│   └── ParserException.java # Parser exceptions
├── ASTNode.java            # Base AST node class
└── ASTVisitor.java         # Visitor interface definition
```

### 🎯 **Key Design Principles**

1. **Separation of Concerns**: Each component has a single, well-defined responsibility
2. **Visitor Pattern**: Operations are separated from AST structure for extensibility
3. **Type Safety**: Strong typing throughout the system prevents runtime errors
4. **Immutability**: AST nodes are immutable after construction for thread safety
5. **Error Recovery**: Comprehensive error handling with detailed diagnostics
6. **Performance**: Optimized for high-throughput rule evaluation scenarios
7. **Extensibility**: New operators, functions, and node types can be added easily
8. **Debugging Support**: Rich source location tracking for error reporting
