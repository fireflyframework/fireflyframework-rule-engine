# YAML DSL Reference Guide

The Firefly Rule Engine uses an intuitive YAML-based Domain Specific Language (DSL) for defining business rules. This comprehensive guide covers all syntax, operators, functions, and features available in the DSL, ensuring alignment with the actual implementation.

## 🚀 Quick Start

The YAML DSL is designed to be readable by both technical and business users. Here's a simple example:

```yaml
name: "Credit Approval"
description: "Basic credit assessment rule"

inputs:
  - creditScore    # Runtime data from your API
  - annualIncome   # Runtime data from your API

when:
  - creditScore at_least MIN_CREDIT_SCORE    # MIN_CREDIT_SCORE from database
  - annualIncome at_least 50000

then:
  - set is_approved to true                  # Computed variable
  - calculate approval_score as creditScore * 0.8

output:
  is_approved: boolean
  approval_score: number
```

## Table of Contents

- [DSL Structure Overview](#dsl-structure-overview)
- [Variable Types and Naming](#variable-types-and-naming)
- [Rule Sections](#rule-sections)
- [Condition Syntax](#condition-syntax)
- [Comparison Operators](#comparison-operators)
- [Logical Operators](#logical-operators)
- [Action Syntax](#action-syntax)
- [Built-in Functions](#built-in-functions)
- [Arithmetic Operations](#arithmetic-operations)
- [Output Definitions](#output-definitions)
- [Multiple Rules](#multiple-rules)
- [Advanced Features](#advanced-features)
- [Banking Examples](#banking-examples)
- [Best Practices](#best-practices)

## DSL Structure Overview

The Firefly YAML DSL is built around a clear, hierarchical structure that separates different concerns:

### 🏗️ **Core Architecture**

```yaml
# 📋 Rule Metadata (Required)
name: "Human-readable rule name"
description: "What this rule does and why"
version: "1.0.0"                    # Optional: Version tracking

# 🏷️ Additional Metadata (Optional)
metadata:
  tags: ["credit", "risk"]
  author: "Risk Team"
  category: "Credit Assessment"

# 📥 Runtime Inputs (Required)
inputs:
  - creditScore      # camelCase: Values from API requests
  - annualIncome     # camelCase: Dynamic runtime data

# 🔍 Conditional Logic (Required - choose one approach)
when:                              # Simple syntax
  - creditScore at_least MIN_CREDIT_SCORE
  - annualIncome greater_than 50000

# OR use complex conditions block
conditions:                        # Advanced syntax
  if:
    and:
      - compare: { left: creditScore, operator: ">=", right: MIN_CREDIT_SCORE }
      - compare: { left: annualIncome, operator: ">", right: 50000 }

# ⚡ Actions (Required)
then:                              # Execute when conditions are true
  - calculate debt_ratio as monthlyDebt / (annualIncome / 12)
  - set approval_status to "APPROVED"
  - call log with ["Application approved", "INFO"]

else:                              # Execute when conditions are false
  - set approval_status to "REJECTED"
  - set rejection_reason to "Insufficient income or credit score"

# 📤 Output Mapping (Required)
output:
  approval_status: text
  debt_ratio: number
  rejection_reason: text

# 🔧 Advanced Features (Optional)
circuit_breaker:
  enabled: true
  failure_threshold: 5
  timeout_duration: "30s"
```

### 📋 **Valid Top-Level Sections**

The DSL recognizes these top-level sections:

| Section | Required | Purpose | Example |
|---------|----------|---------|---------|
| `name` | ✅ | Human-readable rule name | `"Credit Assessment"` |
| `description` | ✅ | Rule purpose and behavior | `"Evaluates creditworthiness"` |
| `version` | ❌ | Version tracking | `"1.2.0"` |
| `metadata` | ❌ | Additional rule information | Tags, author, category |
| `inputs` | ✅ | Runtime variables from API | `[creditScore, annualIncome]` |
| `when` | ✅* | Simple condition syntax | List of condition strings |
| `conditions` | ✅* | Complex condition blocks | Structured condition objects |
| `then` | ✅ | Actions when conditions are true | List of action strings |
| `else` | ❌ | Actions when conditions are false | List of action strings |
| `rules` | ✅* | Multiple sub-rules | Array of rule objects |
| `output` | ✅ | Output variable mapping | Variable name to type mapping |
| `circuit_breaker` | ❌ | Resilience configuration | Circuit breaker settings |

**Note**: You must have either `when`/`conditions` OR `rules` for rule logic.

## Variable Types and Naming

The Firefly Rule Engine uses **strict naming conventions** to automatically determine variable types and sources. This eliminates ambiguity and ensures proper variable resolution.

### 🎯 **The Three Variable Types**

Understanding these three types is crucial for writing effective rules:

#### 1. 🔄 **Input Variables** (camelCase)
- **Source**: Passed via API requests in the `inputData` parameter
- **Naming**: `camelCase` (e.g., `creditScore`, `annualIncome`, `employmentYears`)
- **Purpose**: Dynamic runtime values that change with each evaluation
- **Validation**: Must match keys in your API request's `inputData`

```yaml
inputs:
  - creditScore        # ✅ camelCase: from API inputData
  - annualIncome       # ✅ camelCase: from API inputData
  - employmentYears    # ✅ camelCase: from API inputData
  - requestedAmount    # ✅ camelCase: from API inputData
```

#### 2. 🏛️ **System Constants** (UPPER_CASE_WITH_UNDERSCORES)
- **Source**: Stored in the database and loaded automatically
- **Naming**: `UPPER_CASE_WITH_UNDERSCORES` (e.g., `MIN_CREDIT_SCORE`, `MAX_LOAN_AMOUNT`)
- **Purpose**: Configuration values, thresholds, business parameters
- **Management**: Created and updated via the Constants API

```yaml
when:
  - creditScore at_least MIN_CREDIT_SCORE    # ✅ UPPER_CASE: from database
  - requestedAmount less_than MAX_LOAN_AMOUNT
  - calculate risk_factor as score * RISK_MULTIPLIER
```

#### 3. 🧮 **Computed Variables** (snake_case)
- **Source**: Created during rule execution via actions
- **Naming**: `snake_case` (e.g., `debt_to_income`, `final_score`, `approval_status`)
- **Purpose**: Intermediate calculations, derived values, final results
- **Lifecycle**: Exist only during rule execution

```yaml
then:
  - calculate debt_to_income as totalDebt / annualIncome    # ✅ snake_case: computed
  - calculate loan_ratio as loanAmount / propertyValue      # ✅ snake_case: computed
  - set approval_status to "APPROVED"                       # ✅ snake_case: computed
  - set final_score to 85                                   # ✅ snake_case: computed
```

### 🔄 **Variable Resolution Priority**

When the rule engine encounters a variable name, it searches in this order:

1. **Computed Variables** (highest priority) - Values calculated during execution
2. **Input Variables** (medium priority) - Values from API request
3. **System Constants** (lowest priority) - Values from database

This priority system ensures that computed values can override inputs, and inputs can override constants when needed.

### ❌ **Common Naming Mistakes**

```yaml
# DON'T mix naming conventions - this creates ambiguity:
inputs:
  - CREDIT_SCORE       # ❌ Looks like a constant, not input variable
  - annual_income      # ❌ Inconsistent with camelCase convention

then:
  - set FINAL_SCORE to 85    # ❌ Computed variables should be snake_case
  - calculate debtRatio as debt / income  # ❌ Should be debt_ratio
```

### ✅ **Validation and Enforcement**

The rule engine validates naming conventions and will reject rules that:
- Include `UPPER_CASE` variables in the `inputs` section
- Include `snake_case` variables in the `inputs` section
- Use inconsistent naming patterns
- Reference undefined input variables

## Rule Sections

### 📥 **The `inputs` Section**

**Purpose**: Declares runtime variables that your application passes via API requests.

**Rules**:
- ✅ **ONLY** include variables from your API's `inputData` parameter
- ✅ **Must** use `camelCase` naming convention
- ❌ **Never** include database constants or computed variables

```yaml
inputs:
  - creditScore          # ✅ From API inputData
  - annualIncome         # ✅ From API inputData
  - employmentYears      # ✅ From API inputData
  - requestedAmount      # ✅ From API inputData
```

**API Integration Example**:
```yaml
# Your API Request:
POST /api/v1/rules/evaluate
{
  "ruleDefinition": "...",
  "inputData": {
    "creditScore": 750,        # ← Matches inputs section
    "annualIncome": 85000      # ← Matches inputs section
  }
}
```

### 📋 **Rule Metadata Sections**

#### Required Metadata
```yaml
name: "Credit Assessment Rule"           # ✅ Required: Human-readable name
description: "Evaluates creditworthiness based on score and income"  # ✅ Required
```

#### Optional Metadata
```yaml
version: "1.2.0"                        # ❌ Optional: Version tracking
metadata:                               # ❌ Optional: Additional information
  tags: ["credit", "risk-assessment"]
  priority: 1
  author: "Risk Team"
  category: "Credit Scoring"
  businessOwner: "Credit Department"
  approver: "Risk Manager"
  riskLevel: "HIGH"
  regulatoryRequirements: ["Basel III", "CCAR"]
```

## Condition Syntax

The DSL supports two approaches for defining conditions: **Simple Syntax** (recommended) and **Complex Syntax** (for advanced use cases).

### 🎯 **Simple Syntax** (Recommended)

Use the `when` section with natural language expressions:

```yaml
when:
  - creditScore at_least 650                    # Simple comparison
  - annualIncome greater_than 40000             # Numeric comparison
  - employmentYears at_least 1                  # Another condition
  - customerType in_list ["PREMIUM", "GOLD"]   # List membership
```

### 🔗 **Logical Operators in Simple Syntax**

Combine conditions using `AND`, `OR`, and `NOT`:

```yaml
when:
  # Implicit AND (all conditions must be true)
  - creditScore at_least 650
  - annualIncome greater_than 40000

  # Explicit AND within a single line
  - creditScore at_least 650 AND annualIncome greater_than 40000

  # OR operator
  - creditScore at_least 750 OR annualIncome greater_than 100000

  # NOT operator
  - NOT (accountStatus equals "SUSPENDED")

  # Complex combinations
  - (creditScore at_least 650 AND annualIncome greater_than 40000) OR hasGuarantor equals true
```

### 🏗️ **Complex Syntax** (Advanced)

For sophisticated conditional logic, use the `conditions` block:

```yaml
conditions:
  if:
    and:                                        # All conditions must be true
      - compare:
          left: creditScore
          operator: "at_least"
          right: MIN_CREDIT_SCORE
      - or:                                     # At least one must be true
          - compare:
              left: annualIncome
              operator: "greater_than"
              right: 50000
          - compare:
              left: hasGuarantor
              operator: "equals"
              right: true
  then:
    actions:
      - set:
          variable: "is_eligible"
          value: true
  else:
    actions:
      - set:
          variable: "is_eligible"
          value: false
```

## Comparison Operators

The DSL provides a comprehensive set of comparison operators for different data types and use cases.

### 🔢 **Numeric Comparisons**

| Operator | Aliases | Description | Example |
|----------|---------|-------------|---------|
| `greater_than` | `>` | Greater than | `creditScore greater_than 700` |
| `less_than` | `<` | Less than | `age less_than 65` |
| `at_least` | `>=`, `greater_than_or_equal` | Greater than or equal | `income at_least 50000` |
| `less_than_or_equal` | `<=`, `at_most` | Less than or equal | `debtRatio less_than_or_equal 0.4` |
| `equals` | `==` | Equal to | `accountType equals "CHECKING"` |
| `not_equals` | `!=` | Not equal to | `status not_equals "CLOSED"` |

### 📊 **Range Comparisons**

| Operator | Description | Example |
|----------|-------------|---------|
| `between` | Value between two numbers (inclusive) | `creditScore between 600 and 750` |
| `not_between` | Value outside range | `age not_between 18 and 65` |

### 📝 **String Comparisons**

| Operator | Description | Example |
|----------|-------------|---------|
| `contains` | String contains substring | `companyName contains "CORP"` |
| `not_contains` | String doesn't contain substring | `email not_contains "temp"` |
| `starts_with` | String starts with prefix | `accountNumber starts_with "CHK"` |
| `ends_with` | String ends with suffix | `email ends_with "@company.com"` |
| `matches` | Regex pattern match | `phoneNumber matches "^\\+1"` |
| `not_matches` | Regex pattern doesn't match | `ssn not_matches "^000"` |

### 📋 **List/Array Comparisons**

| Operator | Aliases | Description | Example |
|----------|---------|-------------|---------|
| `in_list` | `in` | Value is in list | `riskLevel in_list ["HIGH", "CRITICAL"]` |
| `not_in_list` | `not_in` | Value is not in list | `status not_in_list ["CLOSED", "SUSPENDED"]` |

### ✅ **Validation Operators**

| Operator | Description | Example |
|----------|-------------|---------|
| `is_null` | Value is null/empty | `middleName is_null` |
| `is_not_null` | Value is not null | `ssn is_not_null` |
| `is_empty` | String/collection is empty | `comments is_empty` |
| `is_not_empty` | String/collection is not empty | `references is_not_empty` |
| `is_numeric` | Value is a number | `inputValue is_numeric` |
| `is_not_numeric` | Value is not a number | `accountType is_not_numeric` |
| `is_email` | Valid email format | `contactEmail is_email` |
| `is_phone` | Valid phone format | `phoneNumber is_phone` |
| `is_date` | Valid date format | `birthDate is_date` |

### 📏 **Length Operators**

| Operator | Description | Example |
|----------|-------------|---------|
| `length_equals` | String/array length equals | `ssn length_equals 9` |
| `length_greater_than` | String/array length greater than | `password length_greater_than 8` |
| `length_less_than` | String/array length less than | `comment length_less_than 500` |

### 💰 **Financial and Business Operators**

| Operator | Description | Example |
|----------|-------------|---------|
| `is_positive` | Value is positive (> 0) | `accountBalance is_positive` |
| `is_negative` | Value is negative (< 0) | `overdraftAmount is_negative` |
| `is_zero` | Value equals zero | `pendingBalance is_zero` |
| `is_non_zero` | Value is not zero | `transactionAmount is_non_zero` |
| `is_percentage` | Value is valid percentage (0-100 or 0.0-1.0) | `interestRate is_percentage` |
| `is_currency` | Value is valid currency amount (non-negative) | `loanAmount is_currency` |
| `is_credit_score` | Value is valid credit score (300-850) | `creditScore is_credit_score` |
| `is_ssn` | Value is valid SSN format (XXX-XX-XXXX) | `socialSecurityNumber is_ssn` |
| `is_account_number` | Value is valid account number (8-17 digits) | `accountNumber is_account_number` |
| `is_routing_number` | Value is valid routing number (9 digits with checksum) | `routingNumber is_routing_number` |
| `is_business_day` | Date is a business day (Monday-Friday) | `transactionDate is_business_day` |
| `is_weekend` | Date is a weekend (Saturday-Sunday) | `applicationDate is_weekend` |
| `age_at_least` | Age meets minimum requirement | `birthDate age_at_least 18` |
| `age_less_than` | Age is below threshold | `birthDate age_less_than 65` |

### 🔍 **Special Operators**

| Operator | Description | Example |
|----------|-------------|---------|
| `exists` | Variable exists in context | `optionalField exists` |

## Logical Operators

Logical operators allow you to combine multiple conditions to create complex business logic.

### 🔗 **AND Operator**

**Implicit AND**: All conditions in a `when` block must be true by default:

```yaml
when:
  - creditScore at_least 650        # Must be true
  - annualIncome greater_than 40000 # AND must be true
  - employmentYears at_least 1      # AND must be true
```

**Explicit AND**: Combine conditions on a single line:

```yaml
when:
  - creditScore at_least 650 AND annualIncome greater_than 40000
  - employmentYears at_least 1 AND hasValidId equals true
```

### 🔀 **OR Operator**

At least one condition must be true:

```yaml
when:
  - creditScore at_least 750 OR annualIncome greater_than 100000
  - hasGuarantor equals true OR collateralValue greater_than 50000
```

### ❌ **NOT Operator**

Negate a condition:

```yaml
when:
  - NOT (accountStatus equals "SUSPENDED")
  - NOT (creditScore less_than 600)
  - customerType not_equals "BLACKLISTED"  # Alternative syntax
```

### 🧩 **Complex Combinations**

Use parentheses to group conditions:

```yaml
when:
  - (creditScore at_least 650 AND annualIncome greater_than 40000) OR hasGuarantor equals true
  - NOT (accountStatus in_list ["SUSPENDED", "CLOSED"]) AND customerType equals "PREMIUM"
  - (age at_least 18 AND age less_than 65) OR hasParentalConsent equals true
```

## Action Syntax

Actions define what happens when conditions are met (`then`) or not met (`else`). The DSL supports multiple action types for different use cases.

### 🎯 **Variable Assignment**

Set computed variables to specific values:

```yaml
then:
  - set is_eligible to true                    # Boolean value
  - set risk_score to 75                       # Numeric value
  - set approval_reason to "Meets all criteria" # String value
  - set approval_date to "2025-01-15"          # Date value
  - set customer_tier to "PREMIUM"             # Enum-like value
```

### 🧮 **Arithmetic Operations**

#### Calculate (Expression Evaluation)
```yaml
then:
  - calculate debt_ratio as totalDebt / annualIncome
  - calculate monthly_payment as loanAmount * INTEREST_RATE / 12
  - calculate weighted_score as (creditScore * 0.4) + (incomeScore * 0.6)
  - calculate final_amount as principal * (1 + rate) ** years
```

#### Arithmetic Actions
```yaml
then:
  - add 10 to base_score                       # Increment by value
  - subtract penalty_points from final_score   # Decrement by value
  - multiply risk_factor by 1.5                # Multiply by factor
  - divide monthly_payment by 12               # Divide by value
```

### 🔀 **Conditional Actions**

Execute actions based on inline conditions:

```yaml
then:
  - if creditScore greater_than 750 then set tier to "PRIME"
  - if annualIncome less_than 50000 then add 5 to risk_score
  - if accountAge at_least 24 then set loyalty_bonus to 100
  - if hasGuarantor equals true then subtract 10 from risk_score
```

### 📞 **Function Calls**

Call built-in or custom functions:

```yaml
then:
  - call log with ["Processing application", "INFO"]
  - call notify with ["customer_email", "Application approved"]
  - call calculate with ["principal * rate * time", "simple_interest"]
  - call encrypt with ["ssn", "encrypted_ssn"]
  - call validate_data with ["email", "email_format"]
```

### 📝 **List Operations**

Manipulate arrays and lists:

```yaml
then:
  - append "HIGH_RISK_MERCHANT" to risk_factors
  - prepend "PRIORITY" to processing_flags
  - remove "TEMPORARY" from account_flags
```

### ⚡ **Circuit Breaker Actions**

Stop rule execution when risk thresholds are exceeded:

```yaml
then:
  - if risk_score greater_than 90 then circuit_breaker "HIGH_RISK_DETECTED"
  - if fraud_indicators greater_than 3 then circuit_breaker "FRAUD_SUSPECTED"
  - if system_load greater_than 0.95 then circuit_breaker "SYSTEM_OVERLOAD"
```

**Circuit Breaker Behavior:**
- Immediately stops rule execution
- Returns the circuit breaker message as the result
- Logs the circuit breaker activation for monitoring
- Can be used for risk management, fraud prevention, or system protection

## Built-in Functions

The DSL provides a comprehensive set of built-in functions for common operations.

### 📊 **Logging and Auditing**

| Function | Parameters | Description | Example |
|----------|------------|-------------|---------|
| `log` | `[message, level]` | Log a message | `call log with ["Processing started", "INFO"]` |
| `audit` | `[message, level]` | Audit trail entry | `call audit with ["Decision made", "INFO"]` |

### 📧 **Notifications**

| Function | Parameters | Description | Example |
|----------|------------|-------------|---------|
| `notify` | `[recipient, message]` | Send notification | `call notify with ["admin@company.com", "High risk detected"]` |

### 🧮 **Mathematical Functions**

| Function | Parameters | Description | Example |
|----------|------------|-------------|---------|
| `calculate` | `[expression, result_var]` | Evaluate expression | `call calculate with ["principal * rate * time", "interest"]` |
| `apply_discount` | `[amount, rate, result_var]` | Apply discount | `call apply_discount with [1000, 0.1, "discounted_amount"]` |
| `calculate_interest` | `[principal, rate, time, frequency, result_var]` | Compound interest | `call calculate_interest with [10000, 0.05, 2, 12, "final_amount"]` |

### 🔐 **Data Security**

| Function | Parameters | Description | Example |
|----------|------------|-------------|---------|
| `encrypt` | `[data, result_var]` | Encrypt sensitive data | `call encrypt with ["ssn", "encrypted_ssn"]` |
| `decrypt` | `[encrypted_data, result_var]` | Decrypt data | `call decrypt with ["encrypted_ssn", "decrypted_ssn"]` |
| `mask_data` | `[data, mask_type, result_var]` | Mask sensitive data | `call mask_data with ["1234567890", "ssn", "masked_ssn"]` |

### ✅ **Data Validation**

| Function | Parameters | Description | Example |
|----------|------------|-------------|---------|
| `validate_data` | `[data, validation_type]` | Validate data format | `call validate_data with ["email@test.com", "email"]` |

### 🏦 **Financial Validation Functions**

| Function | Parameters | Description | Example |
|----------|------------|-------------|---------|
| `is_valid_credit_score` | `[score]` | Validate credit score (300-850) | `is_valid_credit_score(creditScore)` |
| `is_valid_ssn` | `[ssn]` | Validate SSN format | `is_valid_ssn("123-45-6789")` |
| `is_valid_account` | `[account_number]` | Validate account number | `is_valid_account("12345678")` |
| `is_valid_routing` | `[routing_number]` | Validate routing number with checksum | `is_valid_routing("021000021")` |
| `is_business_day` | `[date]` | Check if date is business day | `is_business_day("2025-01-15")` |
| `age_meets_requirement` | `[birth_date, min_age]` | Check age requirement | `age_meets_requirement("1990-01-01", 18)` |
| `debt_to_income_ratio` | `[monthly_debt, monthly_income]` | Calculate DTI ratio | `debt_to_income_ratio(2000, 8000)` |
| `credit_utilization` | `[balance, limit]` | Calculate credit utilization | `credit_utilization(1500, 5000)` |
| `loan_to_value` | `[loan_amount, property_value]` | Calculate LTV ratio | `loan_to_value(200000, 250000)` |
| `payment_history_score` | `[on_time, total_payments]` | Calculate payment history score | `payment_history_score(23, 24)` |

### 💰 **Financial Calculation Functions**

| Function | Parameters | Description | Example |
|----------|------------|-------------|---------|
| `calculate_loan_payment` | `[principal, rate, term, result_var]` | Calculate monthly loan payment | `call calculate_loan_payment with [200000, 0.045, 360, "monthly_payment"]` |
| `calculate_compound_interest` | `[principal, rate, time, frequency, result_var]` | Calculate compound interest | `call calculate_compound_interest with [10000, 0.05, 2, 12, "final_amount"]` |
| `calculate_credit_score` | `[payment_history, utilization, length, new_credit, mix, result_var]` | Calculate credit score | `call calculate_credit_score with [0.95, 0.3, 10, 2, 5, "calculated_score"]` |
| `calculate_debt_ratio` | `[total_debt, total_income, result_var]` | Calculate debt-to-income ratio | `call calculate_debt_ratio with [3000, 10000, "debt_ratio"]` |
| `calculate_ltv` | `[loan_amount, property_value, result_var]` | Calculate loan-to-value ratio | `call calculate_ltv with [180000, 200000, "ltv_ratio"]` |
| `calculate_payment_schedule` | `[principal, rate, term, result_var]` | Generate payment schedule | `call calculate_payment_schedule with [100000, 0.04, 360, "schedule"]` |
| `calculate_amortization` | `[principal, rate, term, result_var]` | Calculate amortization details | `call calculate_amortization with [100000, 0.04, 360, "amortization"]` |
| `calculate_apr` | `[loan_amount, total_cost, term, result_var]` | Calculate APR including fees | `call calculate_apr with [200000, 220000, 360, "apr_rate"]` |
| `calculate_risk_score` | `[credit_score, debt_ratio, income, employment_years, loan_amount, result_var]` | Calculate risk score | `call calculate_risk_score with [750, 0.3, 80000, 5, 200000, "risk_score"]` |

### 🔧 **Utility Functions**

| Function | Parameters | Description | Example |
|----------|------------|-------------|---------|
| `format_currency` | `[amount, result_var]` | Format as currency | `call format_currency with [1234.56, "formatted_amount"]` |
| `format_percentage` | `[decimal_value, result_var]` | Format as percentage | `call format_percentage with [0.045, "formatted_rate"]` |
| `generate_account_number` | `[result_var]` | Generate account number | `call generate_account_number with ["account_number"]` |
| `generate_transaction_id` | `[result_var]` | Generate transaction ID | `call generate_transaction_id with ["transaction_id"]` |
| `audit_log` | `[action, details]` | Create audit log entry | `call audit_log with ["APPROVAL", "Credit approved"]` |
| `send_notification` | `[recipient, message, channel]` | Send notification | `call send_notification with ["user@email.com", "Approved", "EMAIL"]` |

### 🔍 **Conditional Functions**

| Function | Parameters | Description | Example |
|----------|------------|-------------|---------|
| `is_valid` | `[value, validation_type]` | Check if value is valid | `is_valid(email, "email_format")` |
| `exists` | `[variable_name]` | Check if variable exists | `exists("optional_field")` |
| `matches` | `[value, pattern]` | Regex pattern matching | `matches(phone, "^\\+1")` |
| `in_range` | `[value, min, max]` | Check if value in range | `in_range(age, 18, 65)` |
| `distance_between` | `[lat1, lon1, lat2, lon2]` | Calculate distance | `distance_between(40.7128, -74.0060, 34.0522, -118.2437)` |
| `time_hour` | `[timestamp]` | Extract hour from time | `time_hour("2025-01-15T14:30:00Z")` |

## Arithmetic Operations

The DSL supports comprehensive arithmetic operations for calculations.

### 🔢 **Basic Operations**

| Operation | Aliases | Description | Example |
|-----------|---------|-------------|---------|
| `add` | `+` | Addition | `calculate total as amount + tax` |
| `subtract` | `-` | Subtraction | `calculate net as gross - deductions` |
| `multiply` | `*` | Multiplication | `calculate area as length * width` |
| `divide` | `/` | Division | `calculate rate as amount / principal` |
| `modulo` | `mod`, `%` | Remainder | `calculate remainder as amount mod 100` |
| `power` | `pow`, `**` | Exponentiation | `calculate compound as principal * (1 + rate) ** years` |

### 📊 **Mathematical Functions**

| Function | Description | Example |
|----------|-------------|---------|
| `abs` | Absolute value | `calculate absolute as abs(difference)` |
| `min` | Minimum value | `calculate minimum as min(value1, value2, value3)` |
| `max` | Maximum value | `calculate maximum as max(value1, value2, value3)` |
| `round` | Round to nearest integer | `calculate rounded as round(decimal_value)` |
| `floor` | Round down | `calculate floored as floor(decimal_value)` |
| `ceil` | Round up | `calculate ceiling as ceil(decimal_value)` |

### 🧮 **Complex Expressions**

```yaml
then:
  # Multi-step calculations
  - calculate monthly_income as annualIncome / 12
  - calculate debt_to_income as (monthlyDebt + estimatedPayment) / monthly_income
  - calculate weighted_score as (creditScore * 0.4) + (incomeScore * 0.3) + (historyScore * 0.3)

  # Using parentheses for order of operations
  - calculate final_rate as BASE_RATE + (risk_score * 0.01) + ((loan_amount / 100000) * 0.005)

  # Mathematical functions
  - calculate payment as loan_amount * (rate / 12) / (1 - (1 + rate / 12) ** (-360))
```

## Output Definitions

Define what data the rule should return and how computed variables map to output fields.

### 📤 **Basic Output Mapping**

```yaml
output:
  is_eligible: boolean           # Maps computed variable to output type
  risk_score: number            # Numeric output
  approval_reason: text         # String output
  monthly_payment: number       # Calculated value
  decision_date: date          # Date/timestamp
```

### 🎯 **Supported Data Types**

| Type | Description | Example Values |
|------|-------------|----------------|
| `boolean` | True/false values | `true`, `false` |
| `number` | Numeric values (integer or decimal) | `750`, `3.14`, `-100` |
| `text` | String values | `"APPROVED"`, `"High Risk"` |
| `date` | Date/timestamp values | `"2025-01-15"`, `"2025-01-15T10:30:00Z"` |
| `object` | Complex objects | `{"key": "value"}` |
| `array` | Lists of values | `["item1", "item2"]` |

### 🔗 **Variable Mapping**

```yaml
then:
  - set approval_status to "APPROVED"        # Creates computed variable
  - calculate final_score as creditScore * 0.8

output:
  approval_status: text                      # Maps computed variable to output
  final_score: number                        # Maps computed variable to output
  credit_score_input: creditScore            # Maps input variable to output
  constant_value: MIN_CREDIT_SCORE           # Maps constant to output
```

## Multiple Rules

For complex workflows, organize logic into multiple sub-rules that execute sequentially.

### 🏗️ **Sub-Rules Structure**

```yaml
name: "Credit Risk Assessment"
description: "Multi-step credit evaluation process"

inputs:
  - creditScore        # camelCase: input variables
  - annualIncome       # camelCase: from API request
  - monthlyDebt        # camelCase: runtime data

rules:
  - name: "Calculate Risk Metrics"
    then:
      - calculate debt_ratio as monthlyDebt / (annualIncome / 12)    # snake_case: computed
      - calculate income_multiple as requestedAmount / annualIncome

  - name: "Assess Credit Tier"
    when: creditScore at_least MIN_CREDIT_SCORE                     # UPPER_CASE: constant
    then:
      - set credit_tier to "PRIME"                                  # snake_case: computed
      - set base_rate to 3.5

  - name: "Assess Near Prime"
    when: creditScore between 650 and 749
    then:
      - set credit_tier to "NEAR_PRIME"
      - set base_rate to 4.5

  - name: "Assess Subprime"
    when: creditScore less_than 650
    then:
      - set credit_tier to "SUBPRIME"
      - set base_rate to 6.5

output:
  credit_tier: text
  base_rate: number
  debt_ratio: number
```

### 🔀 **Mixed Simple and Complex Syntax**

You can mix simple (`when`/`then`) and complex (`conditions`) syntax within the same rule definition:

```yaml
name: "Mixed Syntax Assessment"
description: "Demonstrates mixing simple and complex syntax in sub-rules"

inputs:
  - creditScore
  - annualIncome
  - accountAge

rules:
  - name: "Simple Initial Check"
    when: creditScore at_least 600                    # Simple syntax
    then:
      - set initial_eligible to true
    elseActions:                                      # Optional else for simple syntax
      - set initial_eligible to false

  - name: "Complex Final Decision"
    conditions:                                       # Complex syntax
      if:
        and:
          - compare:
              left: initial_eligible
              operator: "=="
              right: true
          - or:
              - compare:
                  left: annualIncome
                  operator: ">"
                  right: 75000
              - compare:
                  left: accountAge
                  operator: ">="
                  right: 24
      then:
        actions:
          - set:
              variable: "final_approval"
              value: "APPROVED"
      else:
        actions:
          - set:
              variable: "final_approval"
              value: "DECLINED"

output:
  initial_eligible: boolean
  final_approval: text
```

### ⚡ **Execution Flow**

1. **Sequential Execution**: Sub-rules execute in the order defined
2. **Shared Context**: All sub-rules share the same variable context
3. **Conditional Execution**: Sub-rules with `when` conditions only execute if conditions are met
4. **Variable Accumulation**: Computed variables from earlier rules are available to later rules
5. **Syntax Flexibility**: Each sub-rule can use either simple or complex syntax independently

## Advanced Features

### 🔧 **Circuit Breaker**

Add resilience and fault tolerance to your rules:

```yaml
circuit_breaker:
  enabled: true                    # Enable circuit breaker
  failure_threshold: 5             # Number of failures before opening
  timeout_duration: "30s"          # How long to wait before retry
  recovery_timeout: "60s"          # Time to wait before closing circuit
```

**Use Cases**:
- External API calls that might fail
- Database operations that could timeout
- Complex calculations that might error

### 🏛️ **System Constants**

Reference predefined values stored in the database:

```yaml
when:
  - creditScore at_least MIN_CREDIT_SCORE        # From constants table
  - loanAmount less_than_or_equal MAX_LOAN_AMOUNT
  - calculate risk_factor as score * RISK_MULTIPLIER

then:
  - set base_rate to BASE_INTEREST_RATE
  - add PROCESSING_FEE to total_cost
```

**Constant Management**:
- Created via Constants API: `POST /api/v1/constants`
- Updated without changing rule definitions
- Supports versioning and audit trails
- Types: `NUMBER`, `TEXT`, `BOOLEAN`, `DATE`

### 🔗 **Variable References**

Reference variables created in earlier rules or actions:

```yaml
rules:
  - name: "Calculate Ratios"
    then:
      - calculate debt_to_income as monthlyDebt / (annualIncome / 12)
      - calculate loan_to_value as loanAmount / propertyValue

  - name: "Risk Assessment"
    when: debt_to_income greater_than 0.4        # References computed variable
    then:
      - set risk_level to "HIGH"
      - add 20 to risk_score

  - name: "Final Decision"
    when:
      - risk_level equals "HIGH"                 # References computed variable
      - loan_to_value greater_than 0.8           # References computed variable
    then:
      - set final_decision to "DECLINED"
```

### 🎛️ **Complex Conditional Blocks**

For sophisticated logic, use the advanced conditions syntax:

```yaml
conditions:
  if:
    and:
      - compare: { left: creditScore, operator: ">=", right: 650 }
      - or:
          - compare: { left: annualIncome, operator: ">", right: 50000 }
          - compare: { left: hasGuarantor, operator: "==", right: true }
  then:
    actions:
      - set: { variable: "is_eligible", value: true }
      - call: { function: "log", parameters: ["Eligibility approved"] }
  else:
    actions:
      - set: { variable: "is_eligible", value: false }
      - set: { variable: "rejection_reason", value: "Insufficient qualifications" }
```

## Banking Examples

### Advanced Credit Assessment with Financial Operators

```yaml
name: "Comprehensive Credit Assessment"
description: "Advanced creditworthiness evaluation using financial operators and validation"

inputs:
  - creditScore          # camelCase: from API request
  - annualIncome         # camelCase: from API request
  - monthlyDebt          # camelCase: from API request
  - socialSecurityNumber # camelCase: from API request
  - accountNumber        # camelCase: from API request
  - routingNumber        # camelCase: from API request
  - birthDate            # camelCase: from API request
  - requestedAmount      # camelCase: from API request
  - employmentYears      # camelCase: from API request

when:
  # Financial validation using new operators
  - creditScore is_credit_score                    # Validate credit score range (300-850)
  - socialSecurityNumber is_ssn                    # Validate SSN format
  - accountNumber is_account_number                # Validate account number
  - routingNumber is_routing_number                # Validate routing number with checksum
  - birthDate age_at_least 18                      # Age validation
  - annualIncome is_positive                       # Income must be positive
  - requestedAmount is_currency                    # Valid currency amount

then:
  # Financial calculations using new functions
  - call calculate_debt_ratio with [monthlyDebt, annualIncome / 12, "debt_to_income"]
  - call calculate_loan_payment with [requestedAmount, 0.045, 360, "monthly_payment"]
  - call calculate_credit_score with [0.95, debt_to_income, employmentYears, 1, 5, "calculated_score"]
  - call calculate_risk_score with [creditScore, debt_to_income, annualIncome, employmentYears, requestedAmount, "risk_score"]

  # Business logic with financial operators
  - if debt_to_income is_percentage and debt_to_income less_than 0.3 then set risk_level to "LOW"
  - if debt_to_income between 0.3 and 0.5 then set risk_level to "MEDIUM"
  - if debt_to_income greater_than 0.5 then set risk_level to "HIGH"

  # Generate account details
  - call generate_account_number with ["loan_account_number"]
  - call generate_transaction_id with ["application_id"]

  # Format outputs
  - call format_currency with [monthly_payment, "formatted_payment"]
  - call format_percentage with [debt_to_income, "formatted_dti"]

  # Set approval status
  - set is_eligible to true
  - call audit_log with ["CREDIT_APPROVED", "Application approved with comprehensive validation"]

else:
  - set is_eligible to false
  - set risk_level to "UNQUALIFIED"
  - set rejection_reason to "Failed validation or minimum requirements"
  - call audit_log with ["CREDIT_DENIED", "Application denied due to validation failure"]

output:
  is_eligible: boolean
  risk_level: text
  debt_to_income: number
  monthly_payment: number
  calculated_score: number
  risk_score: number
  loan_account_number: text
  application_id: text
  formatted_payment: text
  formatted_dti: text
  rejection_reason: text
```

### Credit Scoring Rule

```yaml
name: "Personal Credit Assessment"
description: "Evaluate individual creditworthiness for personal loans"

inputs:
  - creditScore          # camelCase: from API request
  - annualIncome         # camelCase: from API request
  - employmentYears      # camelCase: from API request
  - existingDebt         # camelCase: from API request
  - requestedAmount      # camelCase: from API request

when:
  - creditScore at_least MIN_CREDIT_SCORE      # MIN_CREDIT_SCORE: database constant
  - annualIncome at_least MIN_ANNUAL_INCOME    # MIN_ANNUAL_INCOME: database constant
  - employmentYears at_least 1

then:
  - calculate debt_to_income as existingDebt / annualIncome        # snake_case computed variable
  - calculate loan_to_income as requestedAmount / annualIncome     # snake_case computed variable
  - if debt_to_income less_than 0.3 then set risk_level to "LOW"
  - if debt_to_income between 0.3 and 0.5 then set risk_level to "MEDIUM"
  - if debt_to_income greater_than 0.5 then set risk_level to "HIGH"
  - set is_eligible to true                                       # snake_case computed variable
  - set base_score to 70                                          # snake_case computed variable

else:
  - set is_eligible to false                                      # snake_case computed variable
  - set risk_level to "UNQUALIFIED"                               # snake_case computed variable
  - set base_score to 0                                           # snake_case computed variable
  - set rejection_reason to "Does not meet minimum requirements"  # snake_case computed variable

output:
  is_eligible: boolean      # snake_case computed variable
  risk_level: text          # snake_case computed variable
  base_score: number        # snake_case computed variable
  debt_to_income_ratio: debt_to_income
  rejection_reason: text
```

### AML Risk Assessment

```yaml
name: "AML Transaction Monitoring"
description: "Detect suspicious transaction patterns for AML compliance"

inputs:
  - transactionAmount        # camelCase: from API request
  - customerRiskProfile      # camelCase: from API request
  - transactionFreq24h       # camelCase: from API request
  - accountAgeDays           # camelCase: from API request
  - geographicRiskScore      # camelCase: from API request
  - transactionType          # camelCase: from API request

when:
  - transactionAmount greater_than AML_THRESHOLD_AMOUNT    # AML_THRESHOLD_AMOUNT: database constant
  - customerRiskProfile in_list ["HIGH", "UNKNOWN"]
  - transactionFreq24h greater_than MAX_DAILY_TRANSACTIONS    # MAX_DAILY_TRANSACTIONS: database constant

then:
  - set aml_risk_score to 85                                  # snake_case computed variable
  - set requires_manual_review to true                        # snake_case computed variable
  - set compliance_flag to "AML_REVIEW_REQUIRED"              # snake_case computed variable
  - set review_priority to "HIGH"                             # snake_case computed variable
  - call log_suspicious_activity with [customerId, transactionId]

else:
  - calculate base_risk as geographicRiskScore * 0.3          # snake_case computed variable
  - add transactionAmount / 1000 to base_risk
  - if accountAgeDays less_than 30 then add 10 to base_risk
  - set aml_risk_score to base_risk                           # snake_case computed variable
  - set requires_manual_review to false                       # snake_case computed variable
  - if base_risk greater_than 50 then set compliance_flag to "MONITOR"

output:
  aml_risk_score: number           # snake_case computed variable
  requires_manual_review: boolean  # snake_case computed variable
  compliance_flag: text            # snake_case computed variable
  review_priority: text            # snake_case computed variable
```

### Fraud Detection Rule

```yaml
name: "Real-time Fraud Detection"
description: "Identify potentially fraudulent transactions"

inputs:
  - transactionAmount      # camelCase: from API request
  - merchantCategory       # camelCase: from API request
  - transactionTime        # camelCase: from API request
  - customerLocation       # camelCase: from API request
  - cardPresent            # camelCase: from API request
  - velocity1h             # camelCase: from API request
  - velocity24h            # camelCase: from API request

rules:
  - name: "High Amount Check"
    when: transactionAmount greater_than FRAUD_AMOUNT_THRESHOLD    # FRAUD_AMOUNT_THRESHOLD: database constant
    then:
      - add 30 to fraud_score                                      # snake_case computed variable
      - set high_amount_flag to true                               # snake_case computed variable

  - name: "Velocity Check"
    when: velocity1h greater_than MAX_HOURLY_TRANSACTIONS OR velocity24h greater_than MAX_DAILY_TRANSACTIONS
    then:
      - add 25 to fraud_score                                      # snake_case computed variable
      - set velocity_flag to true                                  # snake_case computed variable

  - name: "High Risk Merchant"
    when: merchantCategory in_list ["ATM", "CASH_ADVANCE", "GAMBLING"]
    then:
      - add 20 to fraud_score                                      # snake_case computed variable
      - set merchant_risk_flag to true                             # snake_case computed variable

  - name: "Off-hours Transaction"
    when: transactionTime between "23:00" and "06:00"
    then:
      - add 15 to fraud_score                                      # snake_case computed variable
      - set time_risk_flag to true                                 # snake_case computed variable

  - name: "Card Not Present"
    when: cardPresent equals false
    then:
      - add 10 to fraud_score                                      # snake_case computed variable
      - set cnp_flag to true                                       # snake_case computed variable

  - name: "Final Decision"
    when: fraud_score greater_than 70
    then:
      - set decision to "DECLINE"
      - set reason to "High fraud risk detected"
      - call block_card with [CARD_NUMBER]
    else:
      - if fraud_score greater_than 40 then set decision to "REVIEW"
      - if fraud_score less_than_or_equal 40 then set decision to "APPROVE"

output:
  decision: text
  fraud_score: number
  reason: text
  high_amount_flag: boolean
  velocity_flag: boolean
  merchant_risk_flag: boolean
```

### Loan Origination Workflow

```yaml
name: "Mortgage Loan Origination"
description: "Complete mortgage application evaluation workflow"

inputs:
  - creditScore            # camelCase: from API request
  - annualIncome           # camelCase: from API request
  - monthlyDebtPayments    # camelCase: from API request
  - downPayment            # camelCase: from API request
  - propertyValue          # camelCase: from API request
  - employmentType         # camelCase: from API request
  - employmentYears        # camelCase: from API request

rules:
  - name: "Calculate Key Ratios"
    then:
      - calculate loan_amount as propertyValue - downPayment          # snake_case computed variable
      - calculate ltv_ratio as loan_amount / propertyValue            # snake_case computed variable
      - calculate monthly_income as annualIncome / 12                 # snake_case computed variable
      - calculate estimated_payment as loan_amount * PAYMENT_RATE     # PAYMENT_RATE: database constant
      - calculate dti_ratio as (monthlyDebtPayments + estimated_payment) / monthly_income

  - name: "Credit Score Assessment"
    when: creditScore at_least EXCELLENT_CREDIT_THRESHOLD             # EXCELLENT_CREDIT_THRESHOLD: database constant
    then:
      - set credit_tier to "EXCELLENT"                                # snake_case computed variable
      - set base_rate to EXCELLENT_BASE_RATE                          # EXCELLENT_BASE_RATE: database constant
    else:
      - if creditScore at_least GOOD_CREDIT_THRESHOLD then set credit_tier to "GOOD"
      - if creditScore at_least GOOD_CREDIT_THRESHOLD then set base_rate to GOOD_BASE_RATE
      - if creditScore at_least FAIR_CREDIT_THRESHOLD then set credit_tier to "FAIR"
      - if creditScore at_least FAIR_CREDIT_THRESHOLD then set base_rate to FAIR_BASE_RATE
      - if creditScore less_than FAIR_CREDIT_THRESHOLD then set credit_tier to "POOR"
      - if creditScore less_than FAIR_CREDIT_THRESHOLD then set base_rate to POOR_BASE_RATE

  - name: "LTV Assessment"
    when: ltv_ratio less_than_or_equal 0.8
    then:
      - set ltv_category to "STANDARD"
      - set pmi_required to false
    else:
      - set ltv_category to "HIGH_LTV"
      - set pmi_required to true
      - add 0.25 to base_rate

  - name: "DTI Assessment"
    when: dti_ratio less_than_or_equal 0.28
    then:
      - set dti_category to "EXCELLENT"
    else:
      - if dti_ratio less_than_or_equal 0.36 then set dti_category to "ACCEPTABLE"
      - if dti_ratio greater_than 0.36 then set dti_category to "HIGH"
      - if dti_ratio greater_than 0.43 then set dti_category to "EXCESSIVE"

  - name: "Employment Verification"
    when: EMPLOYMENT_TYPE equals "W2" AND EMPLOYMENT_YEARS at_least 2
    then:
      - set employment_stability to "STABLE"
    else:
      - if EMPLOYMENT_TYPE equals "SELF_EMPLOYED" then set employment_stability to "VARIABLE"
      - if EMPLOYMENT_YEARS less_than 2 then set employment_stability to "UNSTABLE"

  - name: "Final Decision"
    when:
      - credit_tier in_list ["EXCELLENT", "GOOD"]
      - dti_category in_list ["EXCELLENT", "ACCEPTABLE"]
      - employment_stability equals "STABLE"
    then:
      - set loan_decision to "APPROVED"
      - set final_rate to base_rate
      - calculate monthly_payment as loan_amount * (final_rate / 12) / (1 - (1 + final_rate / 12)^(-360))
    else:
      - if credit_tier equals "FAIR" AND dti_category not_equals "EXCESSIVE" then set loan_decision to "CONDITIONAL_APPROVAL"
      - if credit_tier equals "POOR" OR dti_category equals "EXCESSIVE" then set loan_decision to "DECLINED"

output:
  loan_decision: text
  credit_tier: text
  final_rate: number
  monthly_payment: number
  loan_amount: number
  ltv_ratio: number
  dti_ratio: number
  pmi_required: boolean
  employment_stability: text
```

## Best Practices

### 📋 **1. Rule Naming and Documentation**

**✅ Do:**
- Use descriptive names that clearly indicate the rule's purpose
- Include comprehensive descriptions explaining business logic
- Add metadata for governance and compliance tracking
- Use consistent naming patterns across your organization

```yaml
name: "Personal Loan Credit Assessment"
description: "Evaluates creditworthiness for personal loans based on credit score, income, and debt-to-income ratio. Implements Basel III guidelines for risk assessment."
version: "2.1.0"
metadata:
  tags: ["credit", "personal-loan", "risk-assessment"]
  author: "Risk Management Team"
  category: "Credit Scoring"
  businessOwner: "Credit Department"
  approver: "Chief Risk Officer"
  riskLevel: "MEDIUM"
  regulatoryRequirements: ["Basel III", "GDPR"]
```

### 🔍 **2. Input Validation**

**✅ Do:**
- Always validate input data types and ranges
- Use validation operators (`is_numeric`, `is_not_null`, etc.)
- Handle edge cases gracefully
- Provide meaningful error messages

```yaml
when:
  - creditScore is_numeric AND creditScore between 300 and 850
  - annualIncome is_not_null AND annualIncome greater_than 0
  - employmentYears is_numeric AND employmentYears at_least 0

then:
  - if creditScore is_not_numeric then set validation_error to "Invalid credit score format"
  - if annualIncome is_null then set validation_error to "Annual income is required"
```

### ⚡ **3. Performance Optimization**

**✅ Do:**
- Order conditions by likelihood (most likely to fail first)
- Use specific operators rather than complex expressions
- Minimize nested conditions where possible
- Use constants instead of hardcoded values

```yaml
# ✅ Good: Most restrictive condition first
when:
  - creditScore at_least 650                    # Most likely to fail
  - annualIncome greater_than 50000             # Less likely to fail
  - employmentYears at_least 2                  # Least likely to fail

# ❌ Avoid: Complex nested conditions
when:
  - (creditScore at_least 650 AND (annualIncome greater_than 50000 OR (hasGuarantor equals true AND guarantorIncome greater_than 30000)))
```

### 🛡️ **4. Error Handling**

**✅ Do:**
- Always provide `else` actions for fallback scenarios
- Use circuit breakers for external dependencies
- Log important decision points for audit trails
- Handle null and undefined values

```yaml
when:
  - creditScore at_least MIN_CREDIT_SCORE
  - annualIncome at_least MIN_ANNUAL_INCOME

then:
  - set approval_status to "APPROVED"
  - call log with ["Application approved", "INFO"]

else:
  - set approval_status to "DECLINED"
  - set decline_reason to "Does not meet minimum requirements"
  - call log with ["Application declined", "WARN"]
  - call notify with ["risk_team@company.com", "High decline rate detected"]
```

### 🔧 **5. Maintainability**

**✅ Do:**
- Break complex rules into multiple sub-rules
- Use constants for frequently changed values
- Keep individual rules focused on single responsibilities
- Use meaningful variable names

```yaml
# ✅ Good: Focused sub-rules
rules:
  - name: "Calculate Financial Ratios"
    then:
      - calculate debt_to_income as monthlyDebt / (annualIncome / 12)
      - calculate loan_to_income as requestedAmount / annualIncome

  - name: "Assess Credit Risk"
    when: debt_to_income less_than MAX_DTI_RATIO
    then:
      - set credit_risk to "LOW"

  - name: "Make Final Decision"
    when: credit_risk equals "LOW"
    then:
      - set final_decision to "APPROVED"
```

### 🧪 **6. Testing and Validation**

**✅ Do:**
- Test with boundary values
- Verify both positive and negative scenarios
- Include edge cases in test data
- Use the validation API before deployment

```yaml
# Test scenarios to consider:
# - Minimum qualifying values (creditScore = 650, income = 50000)
# - Maximum values (creditScore = 850, income = 1000000)
# - Edge cases (creditScore = 649, income = 49999)
# - Null/empty values
# - Invalid data types
```

### 🏗️ **7. Variable Management**

**✅ Do:**
- Follow strict naming conventions
- Document variable sources and purposes
- Use descriptive names for computed variables
- Avoid variable name conflicts

```yaml
# ✅ Good: Clear variable naming
inputs:
  - creditScore          # camelCase: from API
  - annualIncome         # camelCase: from API

when:
  - creditScore at_least MIN_CREDIT_SCORE    # UPPER_CASE: constant

then:
  - calculate debt_to_income_ratio as monthlyDebt / (annualIncome / 12)  # snake_case: computed
  - set final_approval_status to "APPROVED"                             # snake_case: computed
```

## Common Patterns

### 🎯 **Risk Scoring Pattern**

Accumulate risk factors and make decisions based on total score:

```yaml
rules:
  - name: "Initialize Risk Score"
    then:
      - set risk_score to 0

  - name: "Credit Score Factor"
    when: creditScore less_than 650
    then:
      - add 30 to risk_score
      - append "LOW_CREDIT_SCORE" to risk_factors

  - name: "Income Factor"
    when: annualIncome less_than 50000
    then:
      - add 20 to risk_score
      - append "LOW_INCOME" to risk_factors

  - name: "Employment Factor"
    when: employmentYears less_than 2
    then:
      - add 15 to risk_score
      - append "SHORT_EMPLOYMENT" to risk_factors

  - name: "Final Risk Decision"
    then:
      - if risk_score greater_than 50 then set risk_level to "HIGH"
      - if risk_score between 25 and 50 then set risk_level to "MEDIUM"
      - if risk_score less_than 25 then set risk_level to "LOW"
```

### 🏆 **Tiered Decision Pattern**

Multiple tiers with different thresholds and benefits:

```yaml
rules:
  - name: "Premium Tier"
    when: creditScore at_least 750 AND annualIncome at_least 100000
    then:
      - set customer_tier to "PREMIUM"
      - set interest_rate to 3.5
      - set credit_limit to 100000

  - name: "Standard Tier"
    when: creditScore at_least 650 AND annualIncome at_least 50000
    then:
      - set customer_tier to "STANDARD"
      - set interest_rate to 4.5
      - set credit_limit to 50000

  - name: "Basic Tier"
    when: creditScore at_least 600
    then:
      - set customer_tier to "BASIC"
      - set interest_rate to 6.5
      - set credit_limit to 25000

  - name: "Declined"
    else:
      - set customer_tier to "DECLINED"
      - set decline_reason to "Does not meet minimum requirements"
```

### 🏛️ **Compliance Flag Pattern**

Set multiple compliance flags based on different regulatory requirements:

```yaml
rules:
  - name: "AML Compliance Checks"
    then:
      - if transactionAmount greater_than 10000 then set ctr_required to true
      - if customerType equals "PEP" then set enhanced_dd_required to true
      - if sourceCountry in_list SANCTIONS_LIST then set ofac_review_required to true
      - if transactionType equals "WIRE" then set wire_review_required to true

  - name: "KYC Requirements"
    then:
      - if accountAge less_than 90 then set kyc_refresh_required to true
      - if hasHighRiskOccupation equals true then set enhanced_kyc_required to true

  - name: "Regulatory Reporting"
    then:
      - if ctr_required equals true then call log with ["CTR filing required", "COMPLIANCE"]
      - if enhanced_dd_required equals true then call notify with ["compliance@company.com", "Enhanced DD required"]
```

### 🔄 **Workflow State Pattern**

Manage complex workflows with state transitions:

```yaml
rules:
  - name: "Initial Review"
    when: application_status equals "SUBMITTED"
    then:
      - set application_status to "UNDER_REVIEW"
      - set review_start_date to current_date
      - call notify with ["applicant_email", "Application under review"]

  - name: "Credit Check"
    when: application_status equals "UNDER_REVIEW"
    then:
      - if creditScore at_least MIN_CREDIT_SCORE then set credit_check_status to "PASSED"
      - if creditScore less_than MIN_CREDIT_SCORE then set credit_check_status to "FAILED"

  - name: "Income Verification"
    when: credit_check_status equals "PASSED"
    then:
      - if annualIncome at_least MIN_INCOME then set income_verification to "PASSED"
      - if annualIncome less_than MIN_INCOME then set income_verification to "FAILED"

  - name: "Final Decision"
    when: credit_check_status equals "PASSED" AND income_verification equals "PASSED"
    then:
      - set application_status to "APPROVED"
      - call notify with ["applicant_email", "Application approved"]
    else:
      - set application_status to "DECLINED"
      - call notify with ["applicant_email", "Application declined"]
```

### 🧮 **Complex Calculation Pattern**

Multi-step financial calculations:

```yaml
rules:
  - name: "Calculate Base Metrics"
    then:
      - calculate monthly_income as annualIncome / 12
      - calculate monthly_debt as existingDebt / 12
      - calculate debt_to_income as monthly_debt / monthly_income

  - name: "Calculate Loan Metrics"
    then:
      - calculate loan_to_income as requestedAmount / annualIncome
      - calculate estimated_payment as requestedAmount * PAYMENT_RATE
      - calculate total_monthly_debt as monthly_debt + estimated_payment
      - calculate new_debt_to_income as total_monthly_debt / monthly_income

  - name: "Risk Adjustment"
    then:
      - calculate base_rate as BASE_INTEREST_RATE
      - if debt_to_income greater_than 0.3 then add 0.5 to base_rate
      - if creditScore less_than 700 then add 1.0 to base_rate
      - if loanAmount greater_than 100000 then add 0.25 to base_rate
      - set final_interest_rate to base_rate
```

---

## 🎉 Conclusion

This YAML DSL Reference Guide provides comprehensive documentation of the Firefly Rule Engine's domain-specific language. The syntax is designed to be:

- **Intuitive** for business users and developers
- **Powerful** enough for complex financial rules
- **Maintainable** with clear separation of concerns
- **Scalable** for enterprise-grade applications

### 📚 **Next Steps**

1. **Start Simple**: Begin with basic rules using the simple syntax
2. **Test Thoroughly**: Use the validation API to verify your rules
3. **Iterate**: Gradually add complexity as needed
4. **Monitor**: Use logging and audit functions for observability
5. **Optimize**: Apply best practices for performance and maintainability

### 🔗 **Related Documentation**

- [API Documentation](api-documentation.md) - Complete API reference
- [Developer Guide](developer-guide.md) - Setup and development instructions
- [Architecture Overview](architecture.md) - System architecture and design
- [Inputs Section Guide](inputs-section-guide.md) - Detailed guide on input variables

For questions or support, please refer to the project documentation or contact the development team.
