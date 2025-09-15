#!/usr/bin/env python3
"""
Firefly Rule Engine Python Runtime Library

This module provides all the built-in functions and utilities needed
to execute compiled Python rules from the Firefly Rule Engine.

Copyright 2025 Firefly Software Solutions Inc
Licensed under the Apache License, Version 2.0
"""

import math
import re
import statistics
import datetime
import json
import requests
import hashlib
import uuid
from typing import Any, Dict, List, Optional, Union
from decimal import Decimal, ROUND_HALF_UP

# Version information
__version__ = "1.0.0"
__author__ = "Firefly Software Solutions Inc"

# Import all modules to make them available
from .core import *
from .financial import *
from .validation import *
from .utilities import *
from .rest_client import *
from .json_utils import *
from .security import *
from .logging_utils import *
from .interactive import *
from .string_functions import *
from .datetime_functions import *

# Export all public functions
__all__ = [
    # Core utilities
    'get_nested_value',
    'get_indexed_value',
    'is_empty',
    'list_remove',
    'circuit_breaker_action',
    'safe_divide',
    'coerce_to_number',
    'coerce_to_boolean',

    # Financial functions
    'firefly_calculate_loan_payment',
    'firefly_calculate_compound_interest',
    'firefly_calculate_amortization',
    'firefly_debt_to_income_ratio',
    'firefly_credit_utilization',
    'firefly_loan_to_value',
    'firefly_calculate_apr',
    'firefly_calculate_credit_score',
    'firefly_calculate_risk_score',
    'firefly_payment_history_score',
    'firefly_calculate_rate',

    # Validation functions
    'firefly_is_valid_credit_score',
    'firefly_is_valid_ssn',
    'firefly_is_valid_account',
    'firefly_is_valid_routing',
    'firefly_is_business_day',
    'firefly_age_meets_requirement',
    'firefly_validate_email',
    'firefly_validate_phone',

    # Basic validation functions
    'firefly_is_positive',
    'firefly_is_negative',
    'firefly_is_zero',
    'firefly_is_non_zero',
    'firefly_is_null',
    'firefly_is_not_null',
    'firefly_is_empty',
    'firefly_is_not_empty',
    'firefly_is_numeric',
    'firefly_is_not_numeric',
    'firefly_is_email',
    'firefly_is_phone',
    'firefly_is_date',
    'firefly_is_percentage',
    'firefly_is_currency',
    'firefly_is_credit_score',
    'firefly_is_ssn',
    'firefly_is_account_number',
    'firefly_is_routing_number',
    'firefly_is_weekend',
    'firefly_age_at_least',
    'firefly_age_less_than',

    # Utility functions
    'firefly_format_currency',
    'firefly_format_percentage',
    'firefly_generate_account_number',
    'firefly_generate_transaction_id',
    'firefly_distance_between',
    'firefly_is_valid',
    'firefly_in_range',
    'firefly_substring',
    'firefly_format_date',
    'firefly_calculate_age',

    # REST API functions
    'firefly_rest_get',
    'firefly_rest_post',
    'firefly_rest_put',
    'firefly_rest_delete',
    'firefly_rest_patch',
    'firefly_rest_call',

    # JSON functions
    'firefly_json_get',
    'firefly_json_exists',
    'firefly_json_size',
    'firefly_json_type',
    'json_path_get',

    # Security functions
    'firefly_encrypt',
    'firefly_decrypt',
    'firefly_mask_data',

    # Logging functions
    'firefly_audit',
    'firefly_audit_log',
    'firefly_send_notification',
    'firefly_log',

    # Interactive functions
    'get_user_input',
    'collect_inputs',
    'configure_constants_interactively',
    'print_firefly_header',
    'print_execution_results',
    'print_firefly_footer',
    'execute_rule_interactively',

    # Additional core functions
    'firefly_average',
    'firefly_between',
    'firefly_not_between',
    'firefly_exists',
    'firefly_not_exists',
    'firefly_size',
    'firefly_count',
    'firefly_first',
    'firefly_last',
    'firefly_is_number',
    'firefly_is_string',
    'firefly_is_boolean',
    'firefly_is_list',
    'firefly_tonumber',
    'firefly_tostring',
    'firefly_toboolean',

    # String functions
    'firefly_upper',
    'firefly_lower',
    'firefly_trim',
    'firefly_length',
    'firefly_contains',
    'firefly_startswith',
    'firefly_endswith',
    'firefly_replace',
    'firefly_matches',
    'firefly_not_matches',
    'firefly_length_equals',
    'firefly_length_greater_than',
    'firefly_length_less_than',

    # Date/time functions
    'firefly_now',
    'firefly_today',
    'firefly_dateadd',
    'firefly_datediff',
    'firefly_time_hour',

    # Additional financial functions
    'firefly_calculate_debt_ratio',
    'firefly_calculate_ltv',
    'firefly_calculate_payment_schedule',
]

# Global configuration
CONFIG = {
    'rest_timeout': 30,
    'currency_symbol': '$',
    'date_format': '%Y-%m-%d',
    'decimal_places': 2,
    'audit_enabled': True,
    'logging_level': 'INFO'
}

def configure(**kwargs):
    """Configure the runtime library settings"""
    CONFIG.update(kwargs)

def get_version():
    """Get the runtime library version"""
    return __version__
