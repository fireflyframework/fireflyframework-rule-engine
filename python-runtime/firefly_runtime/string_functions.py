#!/usr/bin/env python3
"""
String manipulation functions for the Firefly Rule Engine Python Runtime
"""

import re
from typing import Any, Union


def firefly_upper(value: Any) -> str:
    """Convert string to uppercase."""
    return str(value).upper() if value is not None else ""


def firefly_lower(value: Any) -> str:
    """Convert string to lowercase."""
    return str(value).lower() if value is not None else ""


def firefly_trim(value: Any) -> str:
    """Remove leading and trailing whitespace."""
    return str(value).strip() if value is not None else ""


def firefly_length(value: Any) -> int:
    """Get the length of a string or collection."""
    if value is None:
        return 0
    if hasattr(value, '__len__'):
        return len(value)
    return len(str(value))


def firefly_contains(text: Any, substring: Any) -> bool:
    """Check if text contains substring."""
    if text is None or substring is None:
        return False
    return str(substring) in str(text)


def firefly_startswith(text: Any, prefix: Any) -> bool:
    """Check if text starts with prefix."""
    if text is None or prefix is None:
        return False
    return str(text).startswith(str(prefix))


def firefly_endswith(text: Any, suffix: Any) -> bool:
    """Check if text ends with suffix."""
    if text is None or suffix is None:
        return False
    return str(text).endswith(str(suffix))


def firefly_replace(text: Any, old: Any, new: Any) -> str:
    """Replace occurrences of old with new in text."""
    if text is None:
        return ""
    return str(text).replace(str(old), str(new))


def firefly_matches(text: Any, pattern: Any) -> bool:
    """Check if text matches regex pattern."""
    if text is None or pattern is None:
        return False
    try:
        return bool(re.match(str(pattern), str(text)))
    except re.error:
        return False


def firefly_not_matches(text: Any, pattern: Any) -> bool:
    """Check if text does NOT match regex pattern."""
    return not firefly_matches(text, pattern)


# Length comparison functions
def firefly_length_equals(value: Any, length: Union[int, float]) -> bool:
    """Check if value length equals specified length."""
    try:
        return firefly_length(value) == int(length)
    except (ValueError, TypeError):
        return False


def firefly_length_greater_than(value: Any, length: Union[int, float]) -> bool:
    """Check if value length is greater than specified length."""
    try:
        return firefly_length(value) > int(length)
    except (ValueError, TypeError):
        return False


def firefly_length_less_than(value: Any, length: Union[int, float]) -> bool:
    """Check if value length is less than specified length."""
    try:
        return firefly_length(value) < int(length)
    except (ValueError, TypeError):
        return False
