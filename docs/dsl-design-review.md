# DSL Design Review

A systematic review of the Firefly Rule Engine DSL — its philosophy, the full surface, the
design tensions that exist today, and the direction future evolution should take. This
document is the **canonical answer to "why does the DSL look the way it does"** and the
input for any future syntax discussions.

It is paired with [yaml-dsl-reference.md](yaml-dsl-reference.md) (the user-facing
reference) and [architecture.md](architecture.md) (the implementation map). Read this
when you want to understand the *design choices*, not just the syntax.

## Table of Contents

- [Philosophy — what this DSL is for](#philosophy--what-this-dsl-is-for)
- [The shape of a rule](#the-shape-of-a-rule)
- [The full surface, at a glance](#the-full-surface-at-a-glance)
- [Design tensions that exist today](#design-tensions-that-exist-today)
- [Decisions we have already made](#decisions-we-have-already-made)
- [Future direction — non-breaking improvements](#future-direction--non-breaking-improvements)
- [Out-of-scope / explicit non-goals](#out-of-scope--explicit-non-goals)

---

## Philosophy — what this DSL is for

The DSL is a **YAML-shaped expression-evaluation language** that lives in two worlds at
once:

1. **A configuration artefact** — readable, diffable, editable by non-engineers, fits in
   a database row, ships as part of a deployment.
2. **A small Turing-incomplete program** — has variables, conditions, loops, functions.

Every design decision falls out of holding those two worlds in tension. We optimise for:

- **Reviewability.** A risk-officer reading `creditScore at_least 700` should understand
  it without learning a programming language. We pick prose-style keywords over symbols
  where it doesn't hurt.
- **Editability.** A rule lives in YAML and a database row, not a Java class. No
  recompilation, no redeploy.
- **Safety.** Errors fail loudly. There is no implicit coercion that hides bugs.
- **Narrowness over generality.** We deliberately do *not* try to be Drools / DMN /
  Easy Rules. See [the engine's mental model](yaml-dsl-reference.md#mental-model----what-this-engine-is-and-isnt)
  for what we explicitly say "no" to.

What we are **not** optimising for:

- Maximum expressiveness — we are a smaller language than DRL or MVEL by design.
- Pattern matching across multiple facts — we evaluate over a single input map.
- Inference / forward chaining — each evaluation is a stateless function call.
- Squeezing the last 5% of runtime performance via JIT-compiled rule bytecode.

---

## The shape of a rule

A rule is a YAML document with these top-level keys (canonical names; aliases noted in
the [Synonyms table](yaml-dsl-reference.md#synonyms-and-canonical-forms)):

| Key             | Cardinality | Purpose                                                              |
| --------------- | ----------- | -------------------------------------------------------------------- |
| `name`          | 1           | Identifier (used for audit + metrics)                                |
| `description`   | 0..1        | Human-readable summary                                               |
| `version`       | 0..1        | Tracked metadata; not enforced semantically                          |
| `metadata`      | 0..1        | Free-form `Map<String,Object>`                                       |
| `inputs`        | 0..1        | Input variables, `camelCase` names                                   |
| `outputs`       | 0..1        | Declared outputs (names + types; types are advisory)                 |
| `constants`     | 0..1        | DB-loaded constants (`UPPER_CASE`); auto-detected from rule body too |
| `when`/`then`/`else` (simple syntax) | 0..1 set | Top-level condition / action blocks                      |
| `rules:` (multi-rule syntax) | 0..1 | Sub-rules that share an evaluation context                  |
| `conditions:` (complex syntax) | 0..1 | Structured `if/then/else` block with optional nesting     |

A rule must declare *one* of: `when`/`then`, `rules:`, or `conditions:`. Mixing them in
the same rule scope is parsed but the simple-syntax path wins by precedence — that's a
known design tension (see [§ Multiple condition shapes](#multiple-condition-shapes)).

### Variable tiers

Three tiers, distinguished by naming convention. The convention is enforced at parse /
validation time and used by the engine to pick the right source on lookup.

| Tier           | Convention   | Source                | Lifetime                             |
| -------------- | ------------ | --------------------- | ------------------------------------ |
| **Input**      | `camelCase`  | Request payload       | One evaluation                       |
| **Constant**   | `UPPER_CASE` | Database via `ConstantService` | Auto-loaded per evaluation; cached |
| **Computed**   | `snake_case` | Set inside actions    | One evaluation                       |

Resolution precedence at evaluation time: **computed > input > constant**. So a rule can
locally shadow an input or constant by writing to a `snake_case` variable of the
contextually-equivalent name (though we don't encourage doing it; it makes the rule
harder to read).

### Actions

| Verb                | Form                                  | Used for                              |
| ------------------- | ------------------------------------- | ------------------------------------- |
| `set`               | `set var to <expr>`                   | Assign a literal or simple expression |
| `calculate`         | `calculate var as <pure-math expr>`   | Pure arithmetic                       |
| `run`               | `run var as <any-expr>`               | Function calls, REST, JSON path       |
| `call`              | `call fn with [args...]`              | Side-effecting function call          |
| arithmetic          | `add X to Y`, `subtract X from Y`, `multiply X by Y`, `divide X by Y` | In-place math    |
| list                | `append X to L`, `prepend X to L`, `remove X from L` | In-place list mutation |
| `if/then/else`      | `if <cond> then <action> [else <action>]` | Inline branch                      |
| loops               | `forEach`, `while`, `do: ... while`   | Iteration                             |
| `circuit_breaker`   | `circuit_breaker "MESSAGE"`           | Early termination                     |

### Conditions

| Form                                | Used for                                                              |
| ----------------------------------- | --------------------------------------------------------------------- |
| `var op value` (binary)             | `creditScore at_least 650`                                            |
| `var op` (unary)                    | `email is_email`, `value is_positive`                                 |
| `var between MIN and MAX` (ternary) | Range check                                                           |
| `var in_list [...]`                 | Membership                                                            |
| `var matches "regex"`               | Regex match                                                           |
| Boolean composition                 | `and` / `or` / `not` with `()` grouping                               |

### Expressions

Standard arithmetic (`+`, `-`, `*`, `/`, `%`, `**`), comparisons, logical operators, and
function calls. Operator precedence follows the conventional ordering (`*` before `+`,
comparisons before `and`, `and` before `or`).

---

## The full surface, at a glance

The canonical inventory lives in [yaml-dsl-reference.md](yaml-dsl-reference.md) and is
validated against the actual parser at every build by `DocExamplesValidationTest`. What
follows here is a compressed map for design discussions:

- **3 variable tiers** × naming conventions
- **30+ comparison operators** (binary + unary + ternary `between`)
- **3 logical operators** (`and`/`or`/`not`)
- **6 binary arithmetic operators**, plus unary `-`/`!`
- **16 action verbs**
- **70+ built-in functions** across math, string, date, list, statistical, type
  conversion, financial, validation, REST, JSON, utility, encryption, and audit
- **3 condition-block shapes** (simple `when:`, complex `conditions:` block, sub-rules in
  `rules:`)
- **Custom function registry** as the documented extension point

The number of operators / functions is large by design — these are the vocabulary of
financial / compliance rules and most are single-purpose. We pay for that surface with a
larger reference doc, but we avoid forcing users to write boilerplate for common idioms.

---

## Design tensions that exist today

These are real seams in the design. They're documented here so we don't pretend they
aren't there.

### `calculate` vs `run` — when to use which

`calculate` rejects function calls at parse time:

```yaml
- calculate x as max(a, b)     # parse error: "calculate is for pure math"
- run x as max(a, b)           # OK
```

**Why we kept the split:** the parse-time hard error catches a common typo (using a
function where you meant pure math); it's a free correctness check. If we unified into
one verb, that check would disappear.

**Why it bothers users:** there's no good intuition for *why* the engine distinguishes.
A rule author looking at `set`, `calculate`, `run` sees three "compute and store"
verbs and has to remember the rule.

**Mitigation today:** documented prominently in
[yaml-dsl-reference.md](yaml-dsl-reference.md), with the rule of thumb "use `run`
for anything with parentheses".

**Future direction:** keep the split, but consider a `pure:` parse-time directive on
`run` actions so users who want the safety check can opt-in explicitly. Avoids the
unification problem.

### YAML colon-in-strings trap

YAML interprets `key: value` as a mapping. An action like
`- call log with ["Approved: " + amount]` is mis-parsed because YAML treats `Approved:`
as a key. Users hit this and don't know why.

**Why we don't catch it earlier:** YAML parsing happens before the DSL parser sees the
string. Our parser never receives the bad input.

**Mitigation today:** the synonyms table and several doc examples explicitly say
"wrap actions containing `:` in YAML single-quotes". We could also lean on the
`format()` function (added 26.05.08) so users compose strings without inline `: ` in
the action.

**Future direction:** add a pre-parse linter step that warns when an action string
contains an unquoted `: ` followed by a space. Cheap to implement; high cost-benefit.

### Multiple condition shapes

Three shapes coexist:

1. **Simple** (`when: [...]` + `then: [...]` + optional `else: [...]`) — the most
   common.
2. **Multiple sub-rules** (`rules: [{name, when, then, else}, ...]`) — sequential
   composition with shared state.
3. **Complex conditions block** (`conditions: { if: ..., then: { actions: [...] },
   else: { actions: [...] } }`) — nested if/then/else as a structured map, useful for
   YAML-tooling that prefers maps over the string-DSL.

**Why all three exist:** the simple shape is what most rules use. Sub-rules are for
multi-stage scoring pipelines. The complex shape was added for tooling that wants to
build rules programmatically without going through string parsing.

**Why it's a tension:** a new user looking at the reference sees three syntaxes and
has to learn when to use each. The decision tree is non-obvious.

**Mitigation today:** the reference points users at the simple shape first and only
shows the others as "advanced". The migration guide is explicit about "use sub-rules
for multi-stage, simple form for everything else".

**Future direction:** stop documenting the complex `conditions:` shape as a
first-class option. Treat it as an implementation-detail entry point for programmatic
rule builders.

### Operator symbol vs keyword duality

`equals` / `==`, `greater_than` / `>`, `at_least` / `>=` all parse to the same operator.
Same with `len` / `length`, `count` / `size`, `tonumber` / `number`.

**Why both exist:** keywords read better in prose-style conditions
(`creditScore at_least 650` sounds like English). Symbols read better in expressions
(`creditScore * 0.5 + age`).

**Why it's a tension:** users see two ways to say the same thing and don't know which
is canonical. The synonyms table now documents this explicitly.

**Future direction:** keep both. The dual-spelling cost is a single table row in the
docs; the benefit is rules that read naturally in their context.

### Naming-tier enforcement is convention, not parse-error

Inputs *should* be `camelCase`, computed *should* be `snake_case`, constants *should* be
`UPPER_CASE`. The validator warns when a rule violates this; the parser does not reject.

**Why we don't reject:** breaking change for any existing rule that violated the
convention. The validator catches violations during the existing validation flow.

**Future direction:** offer a strict-mode flag (`--strict-naming` or
`firefly.rules.strict-naming: true`) that promotes the validator warning to a parse
error. Opt-in; never the default.

### Functional list ops take a string name, not a lambda

`filter(items, "is_positive")` rather than `filter(items, item -> item > 0)`.

**Why:** the DSL has no inline-lambda syntax. Adding one is a parser change and
expands the grammar significantly.

**Mitigation today:** the named-function indirection works for both built-in
unary predicates (`is_positive`, `is_email`, …) and registered custom functions, so
users can wrap any one-arg logic and use it. The reference doc has a clear example.

**Future direction:** likely worth adding inline lambda eventually. Sketch:
`filter(items, x => x > 0)` where `=>` introduces a single-arg lambda. Requires
parser work but no breaking change.

### `is_in_range` overlaps with `between`

`amount between 100 and 200` (operator) and `is_in_range(amount, 100, 200)` (function)
do the same thing.

**Why both:** `between` is a condition operator; `is_in_range` is a function that can
be used inside an expression where the binary-operator `between` form doesn't parse.

**Future direction:** keep both, document the rule of thumb ("condition → `between`,
expression → `is_in_range`").

### Output type declarations are advisory

```yaml
outputs:
  approval_status: text
  monthly_payment: number
```

The engine doesn't currently coerce or validate these. A rule declaring
`monthly_payment: number` can return a string and the engine won't notice.

**Why:** at parse time we don't know what type the expression will produce; we'd need
runtime type-checking at output time, which is a non-trivial pass.

**Future direction:** add an opt-in `strict_outputs: true` rule-level flag that turns
this into runtime enforcement.

---

## Decisions we have already made

These were the major open questions before this PR. Each is now closed:

| Question                                                | Decision                                              | Recorded in                          |
| ------------------------------------------------------- | ----------------------------------------------------- | ------------------------------------ |
| Should errors be silent or loud?                        | **Fail loud** everywhere except deliberate REST chain | yaml-dsl-reference Error Behavior table |
| Should `multiply 1.5 by score` and `multiply score by 1.5` both parse? | **Both** parse to the same `ArithmeticAction` | `ArithmeticActionSymmetryTest`     |
| Should the engine ship a Python-compilation tier?       | **No.** Removed in 26.05.08.                          | This PR (commit `1c787db`)           |
| Should there be a top-level `circuit_breaker:` config block? | **No.** Removed; only the action form exists.    | commit `479c172`                     |
| Should the orphan AST nodes (`AssignmentAction`, `ArithmeticExpression`, `JsonPathExpression`, `RestCallExpression`) stay? | **No.** Removed -- they were never produced by any parser path. | commits `479c172` + this PR |
| Should custom functions live in the parser or in a registry? | **Spring registry** (`CustomFunctionRegistry`); checked before built-ins. | commit `3f2cc23` |
| Should `inputs:` accept both singular and plural? Map and list? | **Yes** -- merged into one model field. Documented in the synonyms table. | commit `f8fea5e` |

---

## Future direction — non-breaking improvements

Ranked by value × cost. None of these have committed timelines; this is the design
backlog the audits surfaced.

| Item                                          | Value | Cost | Notes                                                                                                               |
| --------------------------------------------- | ----- | ---- | ------------------------------------------------------------------------------------------------------------------- |
| Inline-lambda syntax for `filter`/`map`/`reduce` | HIGH | M    | `filter(items, x => x > 0)`. Parser change; no breaking impact on existing rules.                                  |
| Pre-parse YAML lint warning for unquoted `:` in actions | HIGH | S | Catches the most common authoring trap before it becomes a confusing YAML error.                                  |
| `strict_outputs: true` rule-level flag        | MED   | M    | Runtime type-coercion / enforcement on declared output types. Opt-in.                                              |
| `strict_naming: true` engine-level flag       | MED   | S    | Promote naming-convention warnings to parse errors. Opt-in.                                                        |
| `pure:` directive on `run` actions            | MED   | S    | Lets a user opt back into the calculate-style parse-time check without using the `calculate` verb.                |
| Per-rule timeout (`timeout: 5s`)              | MED   | L    | Requires Reactor timeout integration + graceful-fail semantics.                                                    |
| Rule composition (`invoke_rule("X", inputs)`) | MED   | L    | Lets one rule call another by name. Needs cycle detection.                                                         |
| Input default values (`inputs: { x: { type: number, default: 0 } }`) | LOW | M | Convenience for missing inputs.                                                          |
| `log(message, level)` as a first-class action | LOW   | S    | We already have `audit_log` for structured events; `log` is debug-only.                                            |
| Source-location annotations in runtime errors | LOW   | M    | Requires tracking YAML row/col through SnakeYAML+Jackson; currently the action's debug string is what users see.   |
| Statistical: `percentile(list, p)`            | LOW   | S    | Complements `median` / `variance` / `stddev` added in 26.05.08.                                                    |
| Advanced math: `log`, `exp`, `sin`, `cos`     | LOW   | S    | Wrappers around `java.lang.Math.*`. Niche.                                                                         |
| Property-based / fuzz tests for the parser    | LOW   | S    | The `DocExamplesValidationTest` + existing test suite already give good coverage; fuzzing is additive.            |

---

## Out-of-scope / explicit non-goals

These come up regularly and the answer is "deliberately no":

- **Pattern matching across multiple facts.** This is a Drools/DMN feature. The engine
  evaluates over a single input map, by design.
- **Forward / backward chaining inference.** Use Drools.
- **Truth maintenance, retraction, working memory.** Each evaluation is stateless.
- **Decision tables (Excel format).** Use Drools DRT or a DMN engine.
- **General-purpose scripting.** The DSL is intentionally Turing-incomplete in spirit —
  no recursive function definitions, no closures, no class definitions, no goto.
- **Pluggable parsers / alternate front-ends.** We have one DSL surface. Different
  syntaxes for the same engine would multiply the docs and audit surface.
- **A separate compilation target** (Python, JavaScript, WASM, …). The Java rule
  evaluator is the canonical execution path. The Python-compilation tier was removed
  in 26.05.08 specifically because keeping it diluted the core mission.

---

## How to use this document

- **If you're writing a new rule** → read [yaml-dsl-reference.md](yaml-dsl-reference.md).
- **If you're integrating from another engine** → read
  [migration-guide.md](migration-guide.md).
- **If you're proposing a change to the DSL itself** → start here, add to the *Design
  tensions* section if you're surfacing a new one, and propose where in the *Future
  direction* table your change should land.

The DSL evolves slowly on purpose. Every new keyword or operator widens the surface
that every future reader has to learn. The bar for "yes, add it" is that the addition
makes existing rules *clearer* — not just more powerful.
