# Firefly Rule Engine - Common Patterns Guide

**Real-world patterns organized by complexity tier**

*Discover the right features for your use case*

---

## How to Use This Guide

This guide organizes patterns by complexity to help you:
- 🎯 **Find the right approach** for your specific needs
- 📈 **Progress gradually** from simple to advanced features
- 🔍 **Discover capabilities** you might not know about
- ⚖️ **Choose appropriate complexity** for your team's context

### Complexity Tiers

| Tier | When to Use | Team Experience | Rule Maintenance |
|------|-------------|-----------------|------------------|
| **🟢 Basic** | Simple business rules | New to rule engine | Easy to modify |
| **🟡 Intermediate** | Multi-step logic | Some experience | Moderate complexity |
| **🔴 Advanced** | Complex workflows | Experienced team | Requires expertise |

---

## 🟢 Basic Patterns

*Perfect for getting started and handling common business scenarios*

### Pattern: Simple Approval Logic

**Use Case**: Basic yes/no decisions based on thresholds

```yaml
name: "Loan Pre-Approval"
description: "Quick pre-approval based on credit score and income"

inputs: [creditScore, annualIncome]
output: {approved: boolean, message: text}

when:
  - creditScore >= 650
  - annualIncome >= 40000

then:
  - set approved to true
  - set message to "Pre-approved for loan"

else:
  - set approved to false
  - set message to "Does not meet minimum requirements"
```

**Why This Pattern**: Simple, readable, easy to modify thresholds.

### Pattern: Tiered Classification

**Use Case**: Categorizing customers, products, or risk levels

```yaml
name: "Customer Tier Assignment"
description: "Assign customer tier based on account value"

inputs: [accountBalance, yearsWithBank]
output: {tier: text, benefits: list}

when:
  - accountBalance > 0

then:
  - set benefits to []
  
  # Tier assignment with cascading logic
  - if accountBalance >= 100000 then set tier to "PLATINUM"
  - if accountBalance >= 50000 and accountBalance < 100000 then set tier to "GOLD"
  - if accountBalance >= 10000 and accountBalance < 50000 then set tier to "SILVER"
  - if accountBalance < 10000 then set tier to "BRONZE"
  
  # Benefits based on tier
  - if tier == "PLATINUM" then append "Priority Support" to benefits
  - if tier == "PLATINUM" then append "Fee Waivers" to benefits
  - if tier in_list ["PLATINUM", "GOLD"] then append "Investment Advice" to benefits
  - if tier in_list ["PLATINUM", "GOLD", "SILVER"] then append "Online Banking" to benefits

else:
  - set tier to "INACTIVE"
  - set benefits to []
```

**Why This Pattern**: Clear tier logic, easy to add new tiers or benefits.

### Pattern: Data Validation

**Use Case**: Validating input data before processing

```yaml
name: "Application Data Validation"
description: "Validate customer application data"

inputs: [email, phone, ssn, birthDate]
output: {valid: boolean, errors: list}

when:
  - exists email
  - exists phone

then:
  - set errors to []
  - set valid to true
  
  # Individual field validation
  - if email is_not_email then append "Invalid email format" to errors
  - if phone is_not_phone then append "Invalid phone number" to errors
  - if ssn is_not_ssn then append "Invalid SSN format" to errors
  - if birthDate is_not_date then append "Invalid birth date" to errors
  
  # Update validity based on errors
  - calculate error_count as size(errors)
  - if error_count > 0 then set valid to false

else:
  - set valid to false
  - set errors to ["Missing required fields: email, phone"]
```

**Why This Pattern**: Comprehensive validation, clear error reporting.

---

## 🟡 Intermediate Patterns

*For teams ready to handle more sophisticated business logic*

### Pattern: Multi-Factor Risk Scoring

**Use Case**: Calculating risk scores from multiple factors

```yaml
name: "Credit Risk Assessment"
description: "Calculate risk score from multiple financial factors"

inputs: [creditScore, annualIncome, existingDebt, employmentYears]
output: {riskScore: number, riskLevel: text, factors: list}

when:
  - creditScore > 0
  - annualIncome > 0

then:
  - set factors to []
  
  # Credit score component (40% weight)
  - if creditScore >= 750 then set creditComponent to 40
  - if creditScore >= 700 and creditScore < 750 then set creditComponent to 30
  - if creditScore >= 650 and creditScore < 700 then set creditComponent to 20
  - if creditScore < 650 then set creditComponent to 10
  
  # Income component (30% weight)
  - if annualIncome >= 100000 then set incomeComponent to 30
  - if annualIncome >= 60000 and annualIncome < 100000 then set incomeComponent to 20
  - if annualIncome >= 40000 and annualIncome < 60000 then set incomeComponent to 15
  - if annualIncome < 40000 then set incomeComponent to 5
  
  # Debt ratio component (20% weight)
  - calculate debtRatio as existingDebt / annualIncome
  - if debtRatio <= 0.2 then set debtComponent to 20
  - if debtRatio > 0.2 and debtRatio <= 0.4 then set debtComponent to 15
  - if debtRatio > 0.4 and debtRatio <= 0.6 then set debtComponent to 10
  - if debtRatio > 0.6 then set debtComponent to 5
  
  # Employment stability (10% weight)
  - if employmentYears >= 5 then set employmentComponent to 10
  - if employmentYears >= 2 and employmentYears < 5 then set employmentComponent to 7
  - if employmentYears >= 1 and employmentYears < 2 then set employmentComponent to 5
  - if employmentYears < 1 then set employmentComponent to 2
  
  # Calculate final score
  - calculate riskScore as creditComponent + incomeComponent + debtComponent + employmentComponent
  
  # Determine risk level
  - if riskScore >= 80 then set riskLevel to "LOW"
  - if riskScore >= 60 and riskScore < 80 then set riskLevel to "MEDIUM"
  - if riskScore >= 40 and riskScore < 60 then set riskLevel to "HIGH"
  - if riskScore < 40 then set riskLevel to "VERY_HIGH"
  
  # Document contributing factors
  - append "Credit Score: " + creditScore + " (Weight: " + creditComponent + ")" to factors
  - append "Annual Income: " + annualIncome + " (Weight: " + incomeComponent + ")" to factors
  - append "Debt Ratio: " + debtRatio + " (Weight: " + debtComponent + ")" to factors
  - append "Employment Years: " + employmentYears + " (Weight: " + employmentComponent + ")" to factors

else:
  - set riskScore to 0
  - set riskLevel to "INVALID"
  - set factors to ["Invalid input data"]
```

**Why This Pattern**: Transparent scoring, weighted factors, detailed audit trail.

### Pattern: Sequential Processing with Dependencies

**Use Case**: Multi-stage processes where later stages depend on earlier results

```yaml
name: "Loan Application Processing"
description: "Multi-stage loan processing with dependencies"

inputs: [applicantData, loanAmount, loanPurpose]
output: {stage: text, decision: text, details: object}

rules:
  - name: "Initial Eligibility Check"
    when:
      - exists applicantData
      - loanAmount > 0
      - loanAmount <= 500000
    then:
      - calculate applicantAge as json_get(applicantData, "age")
      - calculate applicantIncome as json_get(applicantData, "annualIncome")
      - calculate applicantCredit as json_get(applicantData, "creditScore")
      
      - set stage to "ELIGIBILITY_PASSED"
      - set eligibilityDetails to {
          "age": applicantAge,
          "income": applicantIncome,
          "creditScore": applicantCredit,
          "requestedAmount": loanAmount
        }
    else:
      - set stage to "ELIGIBILITY_FAILED"
      - set decision to "REJECTED"
      - circuit_breaker "Application does not meet basic eligibility"

  - name: "Risk Assessment"
    when:
      - stage == "ELIGIBILITY_PASSED"
      - applicantAge >= 18
      - applicantIncome >= 30000
      - applicantCredit >= 600
    then:
      - calculate loanToIncomeRatio as loanAmount / applicantIncome
      - calculate riskScore as (applicantCredit * 0.6) + ((applicantIncome / 1000) * 0.4)
      
      - if riskScore >= 500 and loanToIncomeRatio <= 5 then set riskLevel to "LOW"
      - if riskScore >= 400 and loanToIncomeRatio <= 7 then set riskLevel to "MEDIUM"
      - if riskScore >= 300 and loanToIncomeRatio <= 10 then set riskLevel to "HIGH"
      - if riskScore < 300 or loanToIncomeRatio > 10 then set riskLevel to "UNACCEPTABLE"
      
      - set stage to "RISK_ASSESSED"
      - set riskDetails to {
          "riskScore": riskScore,
          "riskLevel": riskLevel,
          "loanToIncomeRatio": loanToIncomeRatio
        }
    else:
      - set stage to "RISK_FAILED"
      - set decision to "REJECTED"

  - name: "Final Decision"
    when:
      - stage == "RISK_ASSESSED"
      - riskLevel in_list ["LOW", "MEDIUM", "HIGH"]
    then:
      - if riskLevel == "LOW" then set decision to "APPROVED"
      - if riskLevel == "MEDIUM" then set decision to "APPROVED_WITH_CONDITIONS"
      - if riskLevel == "HIGH" then set decision to "MANUAL_REVIEW_REQUIRED"
      
      - set stage to "COMPLETED"
      - set details to {
          "eligibility": eligibilityDetails,
          "risk": riskDetails,
          "finalDecision": decision,
          "processedAt": now()
        }
    else:
      - set decision to "REJECTED"
      - set stage to "COMPLETED"
```

**Why This Pattern**: Clear stage progression, dependency management, comprehensive audit trail.

---

## 🔴 Advanced Patterns

*For experienced teams handling complex business workflows*

### Pattern: External API Integration with Fallbacks

**Use Case**: Enriching data from external services with error handling

```yaml
name: "Customer Data Enrichment with Fallbacks"
description: "Fetch customer data from multiple sources with fallback logic"

inputs: [customerId, requireCreditCheck]
output: {enrichedData: object, dataQuality: text, sources: list}

when:
  - customerId is_not_null
  - customerId is_not_empty

then:
  - set sources to []
  - set enrichedData to {}
  
  # Primary data source - Customer API
  - calculate customerApiResponse as rest_get("https://api.customer-service.com/customers/" + customerId)
  - calculate customerApiSuccess as json_exists(customerApiResponse, "personalInfo")
  
  - if customerApiSuccess == true then append "CUSTOMER_API" to sources
  - if customerApiSuccess == true then calculate personalInfo as json_get(customerApiResponse, "personalInfo")
  - if customerApiSuccess == true then calculate contactInfo as json_get(customerApiResponse, "contactInfo")
  
  # Credit bureau integration (conditional)
  - if requireCreditCheck == true then calculate creditResponse as rest_get("https://api.credit-bureau.com/reports/" + customerId)
  - if requireCreditCheck == true then calculate creditSuccess as json_exists(creditResponse, "creditScore")
  - if creditSuccess == true then append "CREDIT_BUREAU" to sources
  - if creditSuccess == true then calculate creditInfo as json_get(creditResponse, "creditData")
  
  # Fallback to internal database if external APIs fail
  - if customerApiSuccess == false then calculate internalData as rest_get("https://internal-api.company.com/customers/" + customerId)
  - if customerApiSuccess == false then calculate internalSuccess as json_exists(internalData, "basicInfo")
  - if internalSuccess == true then append "INTERNAL_DB" to sources
  - if internalSuccess == true then calculate personalInfo as json_get(internalData, "basicInfo")
  
  # Assemble enriched data object
  - if exists personalInfo then calculate enrichedData as json_set(enrichedData, "personal", personalInfo)
  - if exists contactInfo then calculate enrichedData as json_set(enrichedData, "contact", contactInfo)
  - if exists creditInfo then calculate enrichedData as json_set(enrichedData, "credit", creditInfo)
  
  # Determine data quality
  - calculate sourceCount as size(sources)
  - if sourceCount >= 2 then set dataQuality to "HIGH"
  - if sourceCount == 1 then set dataQuality to "MEDIUM"
  - if sourceCount == 0 then set dataQuality to "LOW"
  
  # Add metadata
  - calculate enrichedData as json_set(enrichedData, "metadata", {
      "sources": sources,
      "quality": dataQuality,
      "enrichedAt": now(),
      "customerId": customerId
    })

else:
  - set enrichedData to {}
  - set dataQuality to "INVALID"
  - set sources to []
```

**Why This Pattern**: Resilient data integration, multiple fallbacks, quality tracking.

---

## Pattern Selection Guide

### Choose Basic Patterns When:
- ✅ Your team is new to rule engines
- ✅ Rules change frequently and need easy modification
- ✅ Business logic is straightforward
- ✅ You need quick time-to-market

### Choose Intermediate Patterns When:
- 🔄 You need multi-factor decision making
- 🔄 Your team has some rule engine experience
- 🔄 Business logic has moderate complexity
- 🔄 You need audit trails and transparency

### Choose Advanced Patterns When:
- 🔴 You're integrating with external systems
- 🔴 Your team has strong technical expertise
- 🔴 You need sophisticated error handling
- 🔴 Performance and resilience are critical

---

## Next Steps

- **📚 Learn More**: Explore the [Full YAML DSL Reference](yaml-dsl-reference.md) for complete syntax
- **🏛️ Set Standards**: Review [Governance Guidelines](governance-guidelines.md) for team practices
- **🎓 Practice**: Try the [B2B Credit Scoring Tutorial](b2b-credit-scoring-tutorial.md)
- **⚡ Get Started**: Return to [Quick Start Guide](quick-start-guide.md) for basics

---

*Need help choosing the right pattern? Check the [Governance Guidelines](governance-guidelines.md) →*
