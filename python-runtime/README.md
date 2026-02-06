# Firefly Framework Rule Engine Python Runtime

The Firefly Framework Rule Engine Python Runtime provides all the built-in functions and utilities needed to execute compiled Python rules from the Firefly Framework Rule Engine.

## 🚀 Installation

### Option 1: Global Installation (macOS - Recommended for Development)

For macOS systems, you can install the runtime globally:

```bash
# Install dependencies globally
pip3 install --break-system-packages requests cryptography urllib3

# Navigate to the runtime directory
cd python-runtime

# Install firefly_runtime globally
pip3 install --break-system-packages -e .

# Verify installation
python3 -c "import firefly_runtime; print(f'Firefly Runtime v{firefly_runtime.__version__} installed successfully!')"
```

### Option 2: Install from Source (Development with Virtual Environment)

```bash
# Clone the repository
git clone https://github.com/fireflyframework/fireflyframework-rule-engine.git
cd fireflyframework-rule-engine/python-runtime

# Create virtual environment
python3 -m venv firefly-env
source firefly-env/bin/activate

# Install in development mode
pip install -e .
```

### Option 3: Install from Package (Production)

```bash
# Install from PyPI (when published)
pip install firefly-rule-engine-runtime

# Or install from wheel file
pip install firefly_rule_engine_runtime-1.0.0-py3-none-any.whl
```

### Option 4: Install Dependencies Only

If you want to include the runtime files directly in your project:

```bash
# Install required dependencies
pip install -r requirements.txt
```

### Option 5: Build and Install from Source

```bash
# Clone and build
git clone https://github.com/fireflyframework/fireflyframework-rule-engine.git
cd fireflyframework-rule-engine/python-runtime

# Build wheel
python setup.py bdist_wheel

# Install the built wheel
pip install dist/firefly_runtime-1.0.0-py3-none-any.whl
```

### Verify Installation

```bash
# Check if firefly_runtime is installed
python -c "import firefly_runtime; print(f'Firefly Runtime v{firefly_runtime.__version__} installed successfully!')"

# Test basic functionality
python -c "from firefly_runtime import *; print('All functions imported successfully!')"
```

## 📦 Dependencies

The runtime requires the following Python packages:

- `requests>=2.31.0` - For REST API functions
- `cryptography>=41.0.0` - For security/encryption functions
- `urllib3>=2.0.0` - Enhanced HTTP client with retry capabilities

## 🔧 Usage

### Import Statement

All compiled Python rules from the Firefly Framework Rule Engine start with:

```python
from firefly_runtime import *
```

This imports all the necessary functions, constants, and utilities needed for rule execution.

### Basic Usage

```python
from firefly_runtime import *

# Initialize constants dictionary (required)
constants = {}

# Your compiled rule function
def my_rule(context):
    # Rule logic here
    return {'result': 'success'}

# Execute the rule
context = {'input_value': 100}
result = my_rule(context)
print(result)
```

### Interactive Execution

The runtime provides utilities for interactive rule execution:

```python
from firefly_runtime import *

# Initialize constants
constants = {}

def my_rule(context):
    if context.get('score', 0) > 80:
        context['result'] = 'passed'
    else:
        context['result'] = 'failed'
    return {'result': context.get('result')}

# Interactive execution with full UI
if __name__ == "__main__":
    # Print header
    print_firefly_header("My Test Rule", "A simple test rule", "1.0")
    
    # Collect inputs
    input_definitions = {
        'score': 'number',
        'name': 'text'
    }
    context = collect_inputs(input_definitions)
    
    # Execute rule
    print("\n🚀 Executing rule...")
    result = my_rule(context)
    
    # Print results
    print_execution_results(result)
    print_firefly_footer()
```

### Constants Configuration

```python
from firefly_runtime import *

# Initialize constants
constants = {
    'MIN_SCORE': 650,
    'MAX_AMOUNT': 100000
}

# Interactive constant configuration
constants_need_config = ['INTEREST_RATE', 'PROCESSING_FEE']
constants_values = configure_constants_interactively(constants_need_config)
constants.update(constants_values)
```

## 🛠️ Available Functions

### Core Functions
- `get_nested_value()` - Access nested dictionary values
- `get_indexed_value()` - Access list/array values by index
- `is_empty()` - Check if value is empty
- `list_remove()` - Remove items from lists

### Financial Functions
- `firefly_calculate_loan_payment()` - Calculate loan payments
- `firefly_calculate_compound_interest()` - Calculate compound interest
- `firefly_debt_to_income_ratio()` - Calculate debt-to-income ratio
- `firefly_credit_utilization()` - Calculate credit utilization
- `firefly_loan_to_value()` - Calculate loan-to-value ratio

### Validation Functions
- `firefly_is_valid_credit_score()` - Validate credit scores
- `firefly_is_valid_ssn()` - Validate Social Security Numbers
- `firefly_is_valid_account()` - Validate account numbers
- `firefly_validate_email()` - Validate email addresses
- `firefly_validate_phone()` - Validate phone numbers

### Utility Functions
- `firefly_format_currency()` - Format currency values
- `firefly_format_percentage()` - Format percentage values
- `firefly_generate_account_number()` - Generate account numbers
- `firefly_calculate_age()` - Calculate age from birthdate

### REST API Functions
- `firefly_rest_get()` - HTTP GET requests
- `firefly_rest_post()` - HTTP POST requests
- `firefly_rest_put()` - HTTP PUT requests
- `firefly_rest_delete()` - HTTP DELETE requests

### JSON Functions
- `firefly_json_get()` - Extract values from JSON
- `firefly_json_exists()` - Check if JSON path exists
- `firefly_json_size()` - Get JSON array/object size

### Security Functions
- `firefly_encrypt()` - Encrypt sensitive data
- `firefly_decrypt()` - Decrypt sensitive data
- `firefly_mask_data()` - Mask sensitive information

### Interactive Functions
- `get_user_input()` - Get user input with type conversion
- `collect_inputs()` - Collect multiple inputs
- `configure_constants_interactively()` - Configure constants
- `print_firefly_header()` - Print rule header
- `print_execution_results()` - Print formatted results
- `print_firefly_footer()` - Print rule footer
- `execute_rule_interactively()` - Full interactive execution

## ⚙️ Configuration

```python
from firefly_runtime import configure

# Configure runtime settings
configure(
    rest_timeout=60,
    currency_symbol='€',
    date_format='%d/%m/%Y',
    decimal_places=4,
    audit_enabled=True,
    logging_level='DEBUG'
)
```

## 📝 Examples

### Simple Rule Execution

```python
from firefly_runtime import *

constants = {}

def credit_check(context):
    score = context.get('credit_score', 0)
    if score >= 750:
        context['decision'] = 'APPROVED'
        context['rate'] = 3.5
    elif score >= 650:
        context['decision'] = 'APPROVED'
        context['rate'] = 5.5
    else:
        context['decision'] = 'DECLINED'
        context['rate'] = None
    
    return {
        'decision': context.get('decision'),
        'rate': context.get('rate')
    }

# Execute
result = credit_check({'credit_score': 720})
print(result)  # {'decision': 'APPROVED', 'rate': 5.5}
```

### Complex Business Rule

```python
from firefly_runtime import *

constants = {
    'MIN_CREDIT_SCORE': 650,
    'MAX_DEBT_RATIO': 0.4,
    'MIN_INCOME': 50000
}

def loan_approval(context):
    # Validation
    if not firefly_is_positive(context.get('income', 0)):
        return {'decision': 'DECLINED', 'reason': 'Invalid income'}
    
    # Business logic
    credit_score = context.get('credit_score', 0)
    debt_ratio = firefly_debt_to_income_ratio(
        context.get('monthly_debt', 0),
        context.get('monthly_income', 0)
    )
    
    if (credit_score >= constants['MIN_CREDIT_SCORE'] and 
        debt_ratio <= constants['MAX_DEBT_RATIO'] and
        context.get('income', 0) >= constants['MIN_INCOME']):
        
        context['decision'] = 'APPROVED'
        context['rate'] = firefly_calculate_rate(credit_score)
    else:
        context['decision'] = 'DECLINED'
        context['reason'] = 'Does not meet criteria'
    
    return {
        'decision': context.get('decision'),
        'rate': context.get('rate'),
        'reason': context.get('reason')
    }
```

## 📄 License

Copyright 2024-2026 Firefly Software Solutions Inc

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
