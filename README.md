# Firefly Framework - Rule Engine

[![CI](https://github.com/fireflyframework/fireflyframework-rule-engine/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-rule-engine/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> YAML DSL-based rule engine with AST processing, audit trails, and reactive APIs for dynamic business rule evaluation.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Custom Functions](#custom-functions)
- [Error Contract](#error-contract)
- [Configuration](#configuration)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

Firefly Framework Rule Engine provides a business rule evaluation system based on a custom YAML DSL. Rules are defined in YAML and parsed into an Abstract Syntax Tree (AST) for efficient evaluation. The engine supports rich conditions, arithmetic, loop constructs, function calls, REST API calls, JsonPath expressions, **decision tables (DMN-style)**, **rule composition (`invoke_rule`)**, and a pluggable function-registry extension point.

The project is structured as a multi-module Maven build with five sub-modules: `interfaces` (DTOs and validation), `models` (R2DBC entities and repositories), `core` (DSL parser, evaluator, services, function registry), `web` (Spring WebFlux REST controllers), and `sdk` (generated client). The engine provides batch evaluation, audit-trail tracking, and a dedicated cache layer.

The YAML DSL supports input/computed/constant variable tiers (with declared **input defaults**), 30+ comparison operators, logical composition (and/or/not), loops (`forEach`, `while`, `do-while`), inline conditionals (`if/then/else`), 80+ built-in functions (math, finance, date, string, list, validation, REST, JSON, type-conversion, statistics, advanced math, hashing, logging), **per-rule timeout**, **drools-style sub-rule priority**, and circuit-breaker actions for early termination.

## Features

- Custom YAML DSL with dedicated lexer + recursive-descent parser + visitor-based evaluator
- 30+ comparison operators including `between`, `in_list`, `matches`, `is_email`, `is_credit_score`, etc.
- Logical composition (`and`, `or`, `not`) with short-circuit evaluation
- Action types: `set`, `calculate`, `run`, `call`, arithmetic (`add`/`subtract`/`multiply`/`divide`), list ops (`append`/`prepend`/`remove`), `forEach`, `while`, `do-while`, `if/then/else`, `circuit_breaker`
- 80+ built-in functions covering math, advanced math (`exp`, `ln`, `sin`, `cos`, `tan`, `atan2`), hashing (`hash`), string, date, list, statistical (`median`, `stddev`, `variance`, `percentile`), financial, validation, REST, JSON path, type conversion, and structured logging
- **Decision Tables (DMN-style)**: tabular `decision_table:` block with `FIRST`, `COLLECT`, `ANY`, and `UNIQUE` hit policies; `=` prefix marks expression outputs
- **Rule Composition**: `invoke_rule(code, "key1", v1, "key2", v2, ...)` calls a stored rule by code and returns its outputs as a Map for chaining
- **Sub-rule Priority (drools-style salience)**: `priority: N` on each sub-rule; higher priority evaluates first, stable on ties
- **Input Defaults**: declare `default:` per input in the `inputs:` block; caller-omitted variables are filled in automatically
- **Per-Rule Timeout**: declare `timeout: 5s` (or `500ms`/raw ms) to bound wall-clock runtime via Reactor `Mono.timeout()`
- Pre-parse YAML lint catches the most common authoring trap (unquoted `:` inside action lines) before SnakeYAML throws a confusing error
- Strict naming validation: input variables (`camelCase`), constants (`UPPER_CASE`), and computed variables (`snake_case`) are validation errors when violated
- Pluggable function registry (`CustomFunctionRegistry`) — register your own `RuleFunction` beans and call them from rules
- Constants tier loaded from the database with TTL caching; auto-detection of `UPPER_CASE` references in the AST
- Reactive evaluation API on Project Reactor; synchronous visitor scheduled on `Schedulers.boundedElastic()` so it never blocks the Netty event loop
- Batch evaluation with bounded concurrency and per-request timeouts
- Rule-definition CRUD with R2DBC persistence and a cached AST
- Audit-trail tracking for every evaluation (correlated, PII-masked)
- YAML DSL validation: syntax, naming-convention, dependency, function-existence
- RFC 7807 problem-detail error responses; correlation IDs propagated across the chain
- Fail-loud error contract: malformed rules, unknown functions, type-coercion errors, and bad regexes surface as `success=false` with precise diagnostics rather than silently flipping to the else branch
- Spring WebFlux controllers; OpenAPI 3 / Swagger UI

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
    <version>26.05.07</version>
</dependency>

<!-- DTOs and interfaces -->
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-rule-engine-interfaces</artifactId>
    <version>26.05.07</version>
</dependency>

<!-- SDK for client integration -->
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-rule-engine-sdk</artifactId>
    <version>26.05.07</version>
</dependency>
```

## Quick Start

### Naming conventions
The DSL is strict about variable naming so the engine can resolve names without ambiguity:

| Tier             | Convention   | Example                              |
| ---------------- | ------------ | ------------------------------------ |
| Input variables  | `camelCase`  | `creditScore`, `annualIncome`        |
| Computed values  | `snake_case` | `debt_to_income`, `risk_tier`        |
| Database constants | `UPPER_CASE` | `MIN_CREDIT_SCORE`, `MAX_DTI`     |

### Example rule (YAML DSL)

```yaml
name: "Credit Eligibility"
description: "Two-stage credit and income gate"
version: "1.0.0"

inputs:
  creditScore: "number"
  annualIncome: "number"

constants:
  - code: MIN_CREDIT_SCORE
    defaultValue: 700
  - code: MIN_INCOME
    defaultValue: 50000

when:
  - creditScore at_least MIN_CREDIT_SCORE
  - annualIncome at_least MIN_INCOME

then:
  - calculate debt_to_income as 0     # placeholder; real rules would compute this
  - set tier to if_else(creditScore at_least 800, "PRIME", "PREFERRED")
  - set eligible to true

else:
  - set tier to "STANDARD"
  - set eligible to false

output:
  eligible: eligible
  tier: tier
```

### Decision Table example (DMN-style)

```yaml
name: "Auto Insurance Premium Table"

inputs:
  creditScore: number
  age: number

decision_table:
  inputs: [creditScore, age]
  outputs: [tier, rate]
  hit_policy: FIRST
  rules:
    - when:
        - creditScore at_least 750
        - age between 25 and 65
      then:
        tier: "PRIME"
        rate: 3.0
    - when:
        - creditScore at_least 650
      then:
        tier: "PREFERRED"
        rate: 5.0
    - otherwise: true
      then:
        tier: "STANDARD"
        rate: 9.0

output:
  tier: tier
  rate: rate
```

### Rule Composition example (`invoke_rule`)

```yaml
name: "Underwriting Orchestrator"

inputs:
  creditScore: number
  annualIncome: number
  existingDebt: number

then:
  - run scoring as invoke_rule("composite_underwriting",
        "creditScore", creditScore,
        "annualIncome", annualIncome,
        "existingDebt", existingDebt)
  - set tier to scoring.tier
  - set approved to scoring.approved

output:
  tier: tier
  approved: approved
```

### Calling the engine from Java

```java
@Service
public class CreditCheckService {

    private final RulesEvaluationService evaluationService;

    public Mono<RulesEvaluationResponseDTO> evaluate(Map<String, Object> inputData) {
        RuleEvaluationByCodeRequestDTO request = RuleEvaluationByCodeRequestDTO.builder()
                .ruleDefinitionCode("credit_eligibility_v1")
                .inputData(inputData)
                .build();
        return evaluationService.evaluateRuleByCode(request, exchange);
    }
}
```

The same evaluation is also reachable via REST: `POST /api/v1/rules/evaluate/direct` (Base64-encoded YAML), `/evaluate/plain` (raw YAML), or `/evaluate/by-code` (stored rule code).

## Custom Functions

You can extend the DSL with your own functions without modifying the engine:

```java
@Configuration
class MyRulesConfig {

    @Bean
    CommandLineRunner registerCustomFunctions(CustomFunctionRegistry registry) {
        return args -> {
            registry.register("regional_risk", a ->
                    Set.of("CA", "NY").contains(a[0]) ? 10 : 0);
            registry.register("fraud_score", a ->
                    fraudService.score(String.valueOf(a[0])));
        };
    }
}
```

Then in a rule:

```yaml
then:
  - run risk_bump as regional_risk(region)
  - run fraud as fraud_score(applicantId)
```

Custom functions are checked **before** the built-in catalog (so they can shadow a built-in if you choose). Names are matched case-insensitively. The same function is callable from both expression contexts (`run`/`calculate`) and `call`-action contexts.

## Error Contract

The engine fails loud by design — errors are never silently swallowed:

| Situation                                    | Result                                                                      |
| -------------------------------------------- | --------------------------------------------------------------------------- |
| Unknown function name                        | `IllegalArgumentException` -> rule reports `success=false` with the name    |
| Non-numeric string in arithmetic             | `IllegalArgumentException` naming the operand                               |
| Bad regex pattern in `matches`               | `IllegalArgumentException` naming the pattern                               |
| Missing bean property                        | `IllegalArgumentException` naming the class + property                      |
| Unknown `is_valid` validation type           | `IllegalArgumentException` listing supported types                          |
| Action throws during execution               | Rule reports `success=false` with action index + debug string + cause       |
| Condition throws during evaluation           | Rule reports `success=false` (does not silently flip to the else branch)    |
| `circuit_breaker` action triggered           | Rule reports `success=true` with `circuitBreakerTriggered=true` + message   |
| REST function HTTP failure                   | Returns a structured `{success:false, error:true, message}` map (chain-friendly) |
| `getCachedAST` / cache read failure          | Treated as cache miss (parse path); logged via `doOnError`                  |
| `set computed_var to null` (e.g., `json_get` missing path) | Variable is stored as null; the rule succeeds                  |

## Configuration

```yaml
firefly:
  rule-engine:
    cache:
      enabled: true
      ttl: 10m
    audit:
      enabled: true

spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/rules
```

## Documentation

Additional documentation is available in the [docs/](docs/) directory:

- [Quick Start Guide](docs/quick-start-guide.md)
- [Architecture](docs/architecture.md)
- [Yaml Dsl Reference](docs/yaml-dsl-reference.md)
- [Migration Guide](docs/migration-guide.md) -- mapping from Drools / Easy Rules / hand-rolled if/else services
- [Api Documentation](docs/api-documentation.md)
- [Developer Guide](docs/developer-guide.md)
- [Configuration Examples](docs/configuration-examples.md)
- [Common Patterns Guide](docs/common-patterns-guide.md)
- [Inputs Section Guide](docs/inputs-section-guide.md)
- [Performance Optimization](docs/performance-optimization.md)
- [Governance Guidelines](docs/governance-guidelines.md)
- [B2B Credit Scoring Tutorial](docs/b2b-credit-scoring-tutorial.md)

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
