#!/usr/bin/env python3
"""
Validation functions for the Firefly Rule Engine Python Runtime
"""

import re
from datetime import datetime
from typing import Any, Union


def firefly_is_valid_credit_score(score: Union[int, float]) -> bool:
    """Check if a credit score is valid (300-850)"""
    try:
        return 300 <= float(score) <= 850
    except (ValueError, TypeError):
        return False


def firefly_is_valid_ssn(ssn: str) -> bool:
    """Check if SSN format is valid"""
    if not isinstance(ssn, str):
        return False
    pattern = r'^\d{3}-\d{2}-\d{4}$|^\d{9}$'
    return bool(re.match(pattern, ssn))


def firefly_is_valid_account(account: str) -> bool:
    """Check if account number format is valid"""
    if not isinstance(account, str):
        return False
    return account.isdigit() and 8 <= len(account) <= 17


def firefly_is_valid_routing(routing: str) -> bool:
    """Check if routing number is valid"""
    if not isinstance(routing, str) or len(routing) != 9 or not routing.isdigit():
        return False
    # Simple checksum validation
    checksum = sum(int(routing[i]) * (3, 7, 1)[i % 3] for i in range(9))
    return checksum % 10 == 0


def firefly_is_business_day(date_str: str) -> bool:
    """Check if date is a business day"""
    try:
        date_obj = datetime.strptime(date_str, '%Y-%m-%d')
        return date_obj.weekday() < 5  # Monday=0, Sunday=6
    except ValueError:
        return False


def firefly_age_meets_requirement(birth_date: str, min_age: int) -> bool:
    """Check if age meets minimum requirement"""
    try:
        birth = datetime.strptime(birth_date, '%Y-%m-%d')
        today = datetime.now()
        age = today.year - birth.year - ((today.month, today.day) < (birth.month, birth.day))
        return age >= min_age
    except ValueError:
        return False


def firefly_validate_email(email: str) -> bool:
    """Validate email format"""
    if not isinstance(email, str):
        return False
    pattern = r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$'
    return bool(re.match(pattern, email))


def firefly_validate_phone(phone: str) -> bool:
    """Validate phone number format"""
    if not isinstance(phone, str):
        return False
    # Remove all non-digits
    digits = re.sub(r'\D', '', phone)
    return len(digits) == 10 or (len(digits) == 11 and digits[0] == '1')


# Basic validation functions used in compiled rules
def firefly_is_positive(value: Union[int, float]) -> bool:
    """Check if value is positive"""
    try:
        return float(value) > 0
    except (ValueError, TypeError):
        return False


def firefly_is_negative(value: Union[int, float]) -> bool:
    """Check if value is negative"""
    try:
        return float(value) < 0
    except (ValueError, TypeError):
        return False


def firefly_is_zero(value: Union[int, float]) -> bool:
    """Check if value is zero"""
    try:
        return float(value) == 0
    except (ValueError, TypeError):
        return False


def firefly_is_non_zero(value: Union[int, float]) -> bool:
    """Check if value is non-zero"""
    try:
        return float(value) != 0
    except (ValueError, TypeError):
        return False


def firefly_is_null(value: Any) -> bool:
    """Check if value is null/None"""
    return value is None


def firefly_is_not_null(value: Any) -> bool:
    """Check if value is not null/None"""
    return value is not None


def firefly_is_empty(value: Any) -> bool:
    """Check if value is empty"""
    if value is None:
        return True
    if isinstance(value, (str, list, dict, tuple, set)):
        return len(value) == 0
    return False


def firefly_is_not_empty(value: Any) -> bool:
    """Check if value is not empty"""
    return not firefly_is_empty(value)


def firefly_is_numeric(value: Any) -> bool:
    """Check if value is numeric"""
    try:
        float(value)
        return True
    except (ValueError, TypeError):
        return False


def firefly_is_not_numeric(value: Any) -> bool:
    """Check if value is not numeric"""
    return not firefly_is_numeric(value)


def firefly_is_email(value: str) -> bool:
    """Check if value is a valid email"""
    return firefly_validate_email(value)


def firefly_is_phone(value: str) -> bool:
    """Check if value is a valid phone number"""
    return firefly_validate_phone(value)


def firefly_is_date(value: str) -> bool:
    """Check if value is a valid date"""
    try:
        datetime.strptime(value, '%Y-%m-%d')
        return True
    except (ValueError, TypeError):
        return False


def firefly_is_percentage(value: Union[int, float]) -> bool:
    """Check if value is a valid percentage (0-100)"""
    try:
        val = float(value)
        return 0 <= val <= 100
    except (ValueError, TypeError):
        return False


def firefly_is_currency(value: Union[int, float]) -> bool:
    """Check if value is a valid currency amount"""
    try:
        val = float(value)
        return val >= 0
    except (ValueError, TypeError):
        return False


def firefly_is_credit_score(value: Union[int, float]) -> bool:
    """Check if value is a valid credit score"""
    return firefly_is_valid_credit_score(value)


def firefly_is_ssn(value: str) -> bool:
    """Check if value is a valid SSN"""
    return firefly_is_valid_ssn(value)


def firefly_is_account_number(value: str) -> bool:
    """Check if value is a valid account number"""
    return firefly_is_valid_account(value)


def firefly_is_routing_number(value: str) -> bool:
    """Check if value is a valid routing number"""
    return firefly_is_valid_routing(value)


def firefly_is_weekend(date_str: str) -> bool:
    """Check if date is a weekend"""
    try:
        date_obj = datetime.strptime(date_str, '%Y-%m-%d')
        return date_obj.weekday() >= 5  # Saturday=5, Sunday=6
    except ValueError:
        return False


def firefly_age_at_least(birth_date: str, min_age: int) -> bool:
    """Check if age is at least minimum"""
    return firefly_age_meets_requirement(birth_date, min_age)


def firefly_age_less_than(birth_date: str, max_age: int) -> bool:
    """Check if age is less than maximum"""
    try:
        birth = datetime.strptime(birth_date, '%Y-%m-%d')
        today = datetime.now()
        age = today.year - birth.year - ((today.month, today.day) < (birth.month, birth.day))
        return age < max_age
    except ValueError:
        return False
