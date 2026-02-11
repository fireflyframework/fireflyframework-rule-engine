# Firefly Framework - Rule Engine

[![CI](https://github.com/fireflyframework/fireflyframework-rule-engine/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-rule-engine/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> YAML DSL-based rule engine with AST processing, Python compilation, audit trails, and reactive APIs for dynamic business rule evaluation.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

Firefly Framework Rule Engine provides a powerful business rule evaluation system based on a custom YAML DSL. Rules are defined in YAML format and parsed into an Abstract Syntax Tree (AST) for efficient evaluation. The engine supports complex conditions, arithmetic operations, loop constructs, function calls, REST API calls, and JsonPath expressions.

The project is structured as a multi-module build with five sub-modules: interfaces (DTOs and validation), models (database entities and repositories), core (DSL parser, evaluator, and services), SDK (client library), and web (REST controllers). It features Python code generation for rule compilation, batch evaluation, audit trail tracking, and caching for rule definitions.

The YAML DSL supports variables, conditionals, loops (while, do-while, for-each), list operations, circuit breaker actions, and nested rule invocations, making it suitable for complex business rule scenarios such as credit scoring, eligibility checks, and pricing calculations.

## Features

- Custom YAML DSL with lexer, parser, and AST-based evaluation
- Condition types: comparison, logical (AND/OR), expression-based
- Action types: set, calculate, conditional, loops (while, do-while, for-each), function calls
- Expression types: arithmetic, binary, unary, literals, variables, JsonPath, REST calls
- Python code generation and compilation for rule optimization
- Batch rule evaluation for processing multiple inputs
- Rule definition CRUD with database persistence via R2DBC
- Constants management for shared rule variables
- Audit trail tracking for all rule evaluations
- YAML DSL validation with syntax and naming convention checks
- Caching for rule definitions and evaluation results
- REST API controllers for evaluation, definitions, constants, audit, and validation
- Reactive APIs using Project Reactor

## Requirements

- Java 21+
- Spring Boot 3.x
- Maven 3.9+
- PostgreSQL database (for rule and audit persistence)

## Installation

The rule engine is a multi-module project. Include the modules you need:

```xml
<!-- Core evaluation engine -->
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-rule-engine-core</artifactId>
    <version>26.01.01</version>
</dependency>

<!-- DTOs and interfaces -->
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-rule-engine-interfaces</artifactId>
    <version>26.01.01</version>
</dependency>

<!-- SDK for client integration -->
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-rule-engine-sdk</artifactId>
    <version>26.01.01</version>
</dependency>
```

## Quick Start

```yaml
# Example rule definition (YAML DSL)
name: credit-score-check
version: 1
inputs:
  - name: creditScore
    type: number
  - name: income
    type: number

rules:
  - name: evaluate-eligibility
    conditions:
      - field: creditScore
        operator: ">="
        value: 700
      - field: income
        operator: ">="
        value: 50000
    actions:
      - set:
          eligible: true
          tier: "premium"
    else:
      - set:
          eligible: false
          tier: "standard"
```

```java
@Service
public class CreditCheckService {

    private final RulesEvaluationService evaluationService;

    public Mono<RulesEvaluationResponseDTO> evaluate(Map<String, Object> inputs) {
        RulesEvaluationRequestDTO request = new RulesEvaluationRequestDTO();
        request.setRuleCode("credit-score-check");
        request.setInputs(inputs);
        return evaluationService.evaluate(request);
    }
}
```

## Configuration

```yaml
firefly:
  rule-engine:
    cache:
      enabled: true
      ttl: 10m
    audit:
      enabled: true
    python-compilation:
      enabled: false

spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/rules
```

## Documentation

Additional documentation is available in the [docs/](docs/) directory:

- [Quick Start Guide](docs/quick-start-guide.md)
- [Architecture](docs/architecture.md)
- [Yaml Dsl Reference](docs/yaml-dsl-reference.md)
- [Api Documentation](docs/api-documentation.md)
- [Developer Guide](docs/developer-guide.md)
- [Configuration Examples](docs/configuration-examples.md)
- [Common Patterns Guide](docs/common-patterns-guide.md)
- [Inputs Section Guide](docs/inputs-section-guide.md)
- [Python Compilation Complete Guide](docs/python-compilation-complete-guide.md)
- [Performance Optimization](docs/performance-optimization.md)
- [Governance Guidelines](docs/governance-guidelines.md)
- [B2B Credit Scoring Tutorial](docs/b2b-credit-scoring-tutorial.md)

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Solutions Inc.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
