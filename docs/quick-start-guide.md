# Firefly Rule Engine - Quick Start Guide

**Get started with the Firefly Rule Engine in 15 minutes**

*A beginner-friendly introduction to writing your first rules*

---

## What You'll Learn

- How to write a basic rule in 5 minutes
- Essential DSL syntax for common scenarios
- Step-by-step examples with explanations
- When to use simple vs. complex syntax

---

## Your First Rule in 5 Minutes

Let's create a simple credit approval rule:

```yaml
name: "Basic Credit Check"
description: "Approve loans for customers with good credit"

inputs: [creditScore, annualIncome]
output: {decision: text, reason: text}

when:
  - creditScore >= 650
  - annualIncome > 40000

then:
  - set decision to "APPROVED"
  - set reason to "Meets minimum requirements"

else:
  - set decision to "DECLINED"
  - set reason to "Credit score or income too low"
```

**That's it!** You've written your first rule. Let's break it down:

### Required Sections

Every rule needs these four sections:

1. **`name`** - A human-readable identifier
2. **`description`** - What the rule does
3. **`inputs`** - Variables that come from your application
4. **`output`** - Variables the rule will return

### Logic Sections

Choose **one** of these approaches:

- **Simple Logic**: `when` + `then` + `else` (recommended for beginners)
- **Complex Logic**: `conditions` blocks (for advanced scenarios)
- **Multiple Rules**: `rules` array (for sequential processing)

---

## Essential Syntax Patterns

### 1. Basic Comparisons

```yaml
when:
  - creditScore >= 650        # Greater than or equal
  - age < 65                  # Less than
  - status == "ACTIVE"        # Equals
  - type != "SUSPENDED"       # Not equals
```

### 2. Setting Variables

```yaml
then:
  - set approval_status to "APPROVED"
  - set risk_level to "LOW"
  - set processing_date to "2025-01-15"
```

### 3. Simple Calculations

```yaml
then:
  - calculate monthly_income as annualIncome / 12
  - calculate debt_ratio as monthlyDebt / monthly_income
  - calculate total_score as creditScore + incomeScore
```

### 4. Basic Conditions

```yaml
when:
  - age between 18 and 65
  - status in_list ["ACTIVE", "PENDING"]
  - email contains "@company.com"
  - exists guarantorInfo
```

---

## Common Beginner Examples

### Example 1: Age Verification

```yaml
name: "Age Verification"
description: "Check if customer meets age requirements"

inputs: [customerAge]
output: {eligible: boolean, message: text}

when:
  - customerAge >= 18
  - customerAge <= 65

then:
  - set eligible to true
  - set message to "Age requirements met"

else:
  - set eligible to false
  - set message to "Must be between 18 and 65 years old"
```

### Example 2: Income-Based Pricing

```yaml
name: "Pricing Tier Assignment"
description: "Assign pricing tier based on annual income"

inputs: [annualIncome]
output: {tier: text, discount: number}

when:
  - annualIncome > 0

then:
  - if annualIncome >= 100000 then set tier to "PREMIUM"
  - if annualIncome >= 50000 then set tier to "STANDARD"
  - if annualIncome < 50000 then set tier to "BASIC"
  
  - if tier == "PREMIUM" then set discount to 15
  - if tier == "STANDARD" then set discount to 10
  - if tier == "BASIC" then set discount to 5

else:
  - set tier to "INVALID"
  - set discount to 0
```

### Example 3: Multi-Factor Validation

```yaml
name: "Account Validation"
description: "Validate customer account information"

inputs: [email, phone, creditScore]
output: {valid: boolean, issues: list}

when:
  - email is_email
  - phone is_phone
  - creditScore is_credit_score

then:
  - set valid to true
  - set issues to []

else:
  - set valid to false
  - set issues to []
  - if email is_not_email then append "Invalid email format" to issues
  - if phone is_not_phone then append "Invalid phone format" to issues
  - if creditScore is_not_credit_score then append "Invalid credit score" to issues
```

---

## Variable Naming Rules

The rule engine uses naming conventions to understand your data:

| Type | Format | Example | Source |
|------|--------|---------|---------|
| **Input Variables** | `camelCase` | `creditScore`, `annualIncome` | From your API call |
| **System Constants** | `UPPER_CASE` | `MIN_CREDIT_SCORE` | From database |
| **Computed Variables** | `snake_case` | `debt_ratio`, `final_score` | Created by rules |

```yaml
inputs: [creditScore, annualIncome]  # camelCase from API

when:
  - creditScore >= MIN_CREDIT_SCORE  # UPPER_CASE from database

then:
  - calculate debt_ratio as monthlyDebt / annualIncome  # snake_case computed
```

---

## When to Use What

### Use Simple Syntax When:
- ✅ You have straightforward if/then logic
- ✅ You're new to the rule engine
- ✅ Your conditions fit on one line each
- ✅ You need quick, readable rules

### Consider Complex Syntax When:
- 🔄 You need nested conditional logic
- 🔄 You have many interconnected conditions
- 🔄 You need structured decision trees
- 🔄 You're building reusable rule components

### Use Multiple Rules When:
- 🔄 You need sequential processing steps
- 🔄 Later rules depend on earlier rule results
- 🔄 You want to break complex logic into stages
- 🔄 You need different validation phases

---

## Next Steps

Once you're comfortable with these basics:

1. **📖 Explore Patterns**: Check out the [Common Patterns Guide](common-patterns-guide.md) for real-world examples
2. **🏗️ Learn Advanced Features**: See the [Full YAML DSL Reference](yaml-dsl-reference.md) for all capabilities
3. **🎯 Follow Best Practices**: Review the [Governance Guidelines](governance-guidelines.md) for team standards
4. **💡 Try the Tutorial**: Work through the [B2B Credit Scoring Tutorial](b2b-credit-scoring-tutorial.md)

---

## Quick Reference Card

### Essential Operators
```yaml
# Comparisons
>=, <=, >, <, ==, !=
between, not_between
in_list, not_in_list

# String checks
contains, starts_with, ends_with
is_email, is_phone

# Existence
exists, is_null, is_not_null

# Actions
set variable to value
calculate variable as expression
if condition then action
```

### Basic Structure Template
```yaml
name: "Your Rule Name"
description: "What this rule does"

inputs: [input1, input2]
output: {result1: type, result2: type}

when:
  - condition1
  - condition2

then:
  - action1
  - action2

else:
  - fallback_action
```

---

*Ready to write more sophisticated rules? Continue with the [Common Patterns Guide](common-patterns-guide.md) →*
