#!/usr/bin/env python3
"""Utility functions for the Firefly Rule Engine Python Runtime"""

import uuid
import random
from datetime import datetime, timedelta
from typing import Any, Union
import math


def firefly_format_currency(amount: Union[int, float], symbol: str = '$') -> str:
    """Format amount as currency"""
    return f"{symbol}{amount:,.2f}"


def firefly_format_percentage(value: Union[int, float], decimals: int = 2) -> str:
    """Format value as percentage"""
    return f"{value:.{decimals}f}%"


def firefly_generate_account_number() -> str:
    """Generate a random account number"""
    return ''.join([str(random.randint(0, 9)) for _ in range(12)])


def firefly_generate_transaction_id() -> str:
    """Generate a unique transaction ID"""
    return str(uuid.uuid4())


def firefly_distance_between(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Calculate distance between two coordinates in miles"""
    R = 3959  # Earth's radius in miles
    lat1_rad, lon1_rad = math.radians(lat1), math.radians(lon1)
    lat2_rad, lon2_rad = math.radians(lat2), math.radians(lon2)

    dlat = lat2_rad - lat1_rad
    dlon = lon2_rad - lon1_rad

    a = math.sin(dlat/2)**2 + math.cos(lat1_rad) * math.cos(lat2_rad) * math.sin(dlon/2)**2
    c = 2 * math.asin(math.sqrt(a))

    return R * c


def firefly_is_valid(value: Any) -> bool:
    """Check if value is valid (not None, not empty)"""
    return value is not None and value != ""


def firefly_in_range(value: Union[int, float], min_val: Union[int, float], max_val: Union[int, float]) -> bool:
    """Check if value is in range"""
    try:
        return min_val <= float(value) <= max_val
    except (ValueError, TypeError):
        return False


def firefly_substring(text: str, start: int, length: int = None) -> str:
    """Extract substring"""
    if not isinstance(text, str):
        return ""
    if length is None:
        return text[start:]
    return text[start:start+length]


def firefly_format_date(date_obj: datetime, format_str: str = '%Y-%m-%d') -> str:
    """Format date object"""
    return date_obj.strftime(format_str)


def firefly_calculate_age(birth_date: str) -> int:
    """Calculate age from birth date"""
    try:
        birth = datetime.strptime(birth_date, '%Y-%m-%d')
        today = datetime.now()
        return today.year - birth.year - ((today.month, today.day) < (birth.month, birth.day))
    except ValueError:
        return 0
