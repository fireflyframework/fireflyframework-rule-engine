# Python Compilation Example

This example demonstrates how to use the Firefly Framework Rule Engine Python Compiler to convert YAML DSL rules into executable Python code.

## Example Rule

```yaml
name: "Credit Approval Rule"
description: "Determines if a loan application should be approved"
version: "1.0"

inputs:
  creditScore: "number"
  annualIncome: "number"
  loanAmount: "number"
  employmentYears: "number"

outputs:
  approved: "boolean"
  reason: "string"
  maxLoanAmount: "number"

rules:
  - when:
      - creditScore >= 650
      - annualIncome >= 30000
      - loanAmount <= (annualIncome * 4)
      - employmentYears >= 2
    then:
      - set approved to true
      - set reason to "Application approved"
      - calculate maxLoanAmount as (annualIncome * 4)
    else:
      - set approved to false
      - set reason to "Application denied - insufficient criteria"
      - set maxLoanAmount to 0
```

## Generated Python Code

```python
from firefly_runtime import *

def credit_approval_rule(context):
    """
    Credit Approval Rule
    Determines if a loan application should be approved
    Version: 1.0
    """
    
    # Rule 1
    if (context.get('creditScore', 0) >= 650 and 
        context.get('annualIncome', 0) >= 30000 and 
        context.get('loanAmount', 0) <= (context.get('annualIncome', 0) * 4) and 
        context.get('employmentYears', 0) >= 2):
        
        context['approved'] = True
        context['reason'] = "Application approved"
        context['maxLoanAmount'] = (context.get('annualIncome', 0) * 4)
    else:
        context['approved'] = False
        context['reason'] = "Application denied - insufficient criteria"
        context['maxLoanAmount'] = 0
    
    return {
        'approved': context.get('approved'),
        'reason': context.get('reason'),
        'maxLoanAmount': context.get('maxLoanAmount')
    }
```

## Usage

### Java Side (Compilation)

```java
@Autowired
private PythonCompilationService pythonCompilationService;

// Compile the rule
String yamlDsl = "..."; // Your YAML DSL
PythonCompiledRule compiledRule = pythonCompilationService.compileRule(yamlDsl, "credit_approval");

// Get the generated Python code
String pythonCode = compiledRule.getPythonCode();

// Save to file or execute
Files.write(Paths.get("credit_approval_rule.py"), pythonCode.getBytes());
```

### Python Side (Execution)

```python
# Import the generated rule
from credit_approval_rule import credit_approval_rule

# Prepare input data
input_data = {
    'creditScore': 720,
    'annualIncome': 75000,
    'loanAmount': 250000,
    'employmentYears': 5
}

# Execute the rule
result = credit_approval_rule(input_data)

print(f"Approved: {result['approved']}")
print(f"Reason: {result['reason']}")
print(f"Max Loan Amount: ${result['maxLoanAmount']:,}")
```

## Advanced Features

### REST API Calls

```yaml
rules:
  - when:
      - true
    then:
      - calculate creditBureauData as rest_get("https://api.creditbureau.com/score", {"Authorization": "Bearer token"})
      - set externalScore to json_get(creditBureauData, "score")
```

Generated Python:
```python
context['creditBureauData'] = firefly_rest_call("GET", "https://api.creditbureau.com/score", None, {"Authorization": "Bearer token"})
context['externalScore'] = firefly_json_get(context.get('creditBureauData'), "score")
```

### Financial Functions

```yaml
rules:
  - when:
      - true
    then:
      - calculate monthlyPayment as loan_payment(loanAmount, 0.05, 360)
      - calculate debtToIncomeRatio as (monthlyPayment * 12) / annualIncome
```

Generated Python:
```python
context['monthlyPayment'] = firefly_loan_payment(context.get('loanAmount', 0), 0.05, 360)
context['debtToIncomeRatio'] = (context.get('monthlyPayment', 0) * 12) / context.get('annualIncome', 0)
```

## Benefits

1. **Performance**: Compiled Python code runs faster than interpreted DSL
2. **Portability**: Rules can run in Python environments without Java
3. **Integration**: Easy to integrate with Python ML/AI pipelines
4. **Debugging**: Generated Python code is readable and debuggable
5. **Scalability**: Can be deployed to serverless Python functions

## Runtime Dependencies

The generated Python code requires the `firefly_runtime` package:

```bash
pip install -r python-runtime/requirements.txt
```

Or install the package:

```bash
cd python-runtime
pip install .
```
