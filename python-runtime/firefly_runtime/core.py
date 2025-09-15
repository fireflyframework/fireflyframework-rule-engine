#!/usr/bin/env python3
"""
Core utilities for the Firefly Rule Engine Python Runtime

This module provides essential utility functions for variable access,
data manipulation, and control flow operations.
"""

from typing import Any, Dict, List, Optional, Union


def get_nested_value(context: Dict[str, Any], path: str) -> Any:
    """
    Get a nested value from a context dictionary using dot notation.

    Args:
        context: The context dictionary
        path: Dot-separated path (e.g., "user.profile.name")

    Returns:
        The value at the specified path, or None if not found
    """
    if not path:
        return None

    keys = path.split('.')
    current = context

    for key in keys:
        if isinstance(current, dict) and key in current:
            current = current[key]
        else:
            return None

    return current


def get_indexed_value(context: Dict[str, Any], variable_name: str, index: int) -> Any:
    """
    Get a value from a list variable by index.

    Args:
        context: The context dictionary
        variable_name: Name of the list variable
        index: Index to access

    Returns:
        The value at the specified index, or None if not found
    """
    value = context.get(variable_name)

    if isinstance(value, (list, tuple)) and 0 <= index < len(value):
        return value[index]

    return None


def is_empty(value: Any) -> bool:
    """
    Check if a value is empty (None, empty string, empty list, etc.).

    Args:
        value: The value to check

    Returns:
        True if the value is considered empty, False otherwise
    """
    if value is None:
        return True

    if isinstance(value, (str, list, dict, tuple, set)):
        return len(value) == 0

    return False


def list_remove(lst: List[Any], value: Any) -> List[Any]:
    """
    Remove all occurrences of a value from a list.

    Args:
        lst: The list to modify
        value: The value to remove

    Returns:
        The modified list
    """
    if not isinstance(lst, list):
        return lst

    return [item for item in lst if item != value]


def circuit_breaker_action(action: str, threshold: Union[int, float]) -> bool:
    """
    Execute a circuit breaker action.

    Args:
        action: The action to perform
        threshold: The threshold value

    Returns:
        True if the action was successful, False otherwise
    """
    # This is a placeholder implementation
    # In a real system, this would integrate with a circuit breaker library
    print(f"Circuit breaker action: {action} with threshold: {threshold}")
    return True


def safe_divide(numerator: Union[int, float], denominator: Union[int, float],
                default: Union[int, float] = 0) -> Union[int, float]:
    """
    Safely divide two numbers, returning a default value if division by zero.

    Args:
        numerator: The numerator
        denominator: The denominator
        default: Default value to return if division by zero

    Returns:
        The result of the division or the default value
    """
    try:
        if denominator == 0:
            return default
        return numerator / denominator
    except (TypeError, ZeroDivisionError):
        return default


def safe_convert_to_number(value: Any, default: Union[int, float] = 0) -> Union[int, float]:
    """
    Safely convert a value to a number.

    Args:
        value: The value to convert
        default: Default value if conversion fails

    Returns:
        The converted number or the default value
    """
    if isinstance(value, (int, float)):
        return value

    if isinstance(value, str):
        try:
            # Try integer first
            if '.' not in value:
                return int(value)
            else:
                return float(value)
        except ValueError:
            return default

    return default


def coerce_to_boolean(value: Any) -> bool:
    """
    Convert a value to a boolean using rule engine semantics.

    Args:
        value: The value to convert

    Returns:
        The boolean representation of the value
    """
    if isinstance(value, bool):
        return value

    if value is None:
        return False

    if isinstance(value, (int, float)):
        return value != 0

    if isinstance(value, str):
        return value.lower() in ('true', 'yes', '1', 'on', 'enabled')

    if isinstance(value, (list, dict, tuple, set)):
        return len(value) > 0


# Aliases for backward compatibility
coerce_to_number = safe_convert_to_number


def deep_merge_dicts(dict1: Dict[str, Any], dict2: Dict[str, Any]) -> Dict[str, Any]:
    """
    Deep merge two dictionaries.

    Args:
        dict1: First dictionary
        dict2: Second dictionary (takes precedence)

    Returns:
        Merged dictionary
    """
    result = dict1.copy()

    for key, value in dict2.items():
        if key in result and isinstance(result[key], dict) and isinstance(value, dict):
            result[key] = deep_merge_dicts(result[key], value)
        else:
            result[key] = value

    return result


# Additional utility functions for DSL support

def firefly_average(*args) -> float:
    """Calculate the average of numeric values."""
    numbers = []
    for arg in args:
        if isinstance(arg, (list, tuple)):
            numbers.extend([coerce_to_number(x) for x in arg])
        else:
            numbers.append(coerce_to_number(arg))

    if not numbers:
        return 0.0
    return sum(numbers) / len(numbers)


def firefly_between(value: Any, min_val: Any, max_val: Any) -> bool:
    """Check if value is between min and max (inclusive)."""
    try:
        val = coerce_to_number(value)
        min_num = coerce_to_number(min_val)
        max_num = coerce_to_number(max_val)
        return min_num <= val <= max_num
    except (ValueError, TypeError):
        return False


def firefly_not_between(value: Any, min_val: Any, max_val: Any) -> bool:
    """Check if value is NOT between min and max."""
    return not firefly_between(value, min_val, max_val)


def firefly_exists(value: Any) -> bool:
    """Check if value exists (not None)."""
    return value is not None


def firefly_not_exists(value: Any) -> bool:
    """Check if value does not exist (is None)."""
    return value is None


def firefly_size(value: Any) -> int:
    """Get the size/length of a value."""
    if hasattr(value, '__len__'):
        return len(value)
    return 0


def firefly_count(value: Any) -> int:
    """Alias for firefly_size."""
    return firefly_size(value)


def firefly_first(value: Any) -> Any:
    """Get the first element of a list/sequence."""
    if isinstance(value, (list, tuple)) and len(value) > 0:
        return value[0]
    return None


def firefly_last(value: Any) -> Any:
    """Get the last element of a list/sequence."""
    if isinstance(value, (list, tuple)) and len(value) > 0:
        return value[-1]
    return None


# Type checking functions
def firefly_is_number(value: Any) -> bool:
    """Check if value is a number."""
    return isinstance(value, (int, float))


def firefly_is_string(value: Any) -> bool:
    """Check if value is a string."""
    return isinstance(value, str)


def firefly_is_boolean(value: Any) -> bool:
    """Check if value is a boolean."""
    return isinstance(value, bool)


def firefly_is_list(value: Any) -> bool:
    """Check if value is a list."""
    return isinstance(value, list)


# Type conversion functions
def firefly_tonumber(value: Any, default: Union[int, float] = 0) -> Union[int, float]:
    """Convert value to number."""
    return coerce_to_number(value, default)


def firefly_tostring(value: Any) -> str:
    """Convert value to string."""
    if value is None:
        return ""
    return str(value)


def firefly_toboolean(value: Any) -> bool:
    """Convert value to boolean."""
    return coerce_to_boolean(value)
