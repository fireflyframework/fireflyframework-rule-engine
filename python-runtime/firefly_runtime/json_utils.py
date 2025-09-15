#!/usr/bin/env python3
"""
JSON utility functions for the Firefly Rule Engine Python Runtime

This module provides JSON path and manipulation functions equivalent to the Java implementation.
"""

import json
import re
from typing import Any, Dict, List, Optional, Union


def firefly_json_get(data: Union[Dict, List, str], path: str) -> Any:
    """
    Get value from JSON data using JSONPath-like syntax.

    Args:
        data: JSON data (dict, list, or JSON string)
        path: JSONPath expression (simplified)

    Returns:
        The value at the specified path, or None if not found
    """
    if isinstance(data, str):
        try:
            data = json.loads(data)
        except json.JSONDecodeError:
            return None

    return json_path_get(data, path)


def json_path_get(data: Any, path: str) -> Any:
    """
    Extract value using simplified JSONPath syntax.

    Supports:
    - $.field - root field access
    - $.field.subfield - nested field access
    - $.array[0] - array index access
    - $.array[*] - all array elements
    - $..field - recursive field search

    Args:
        data: The data to search
        path: JSONPath expression

    Returns:
        The value at the specified path, or None if not found
    """
    if not path or not path.startswith('$'):
        return None

    # Remove the root '$' and split by '.'
    path = path[1:]
    if path.startswith('.'):
        path = path[1:]

    if not path:
        return data

    # Handle recursive search (..)
    if path.startswith('.'):
        field = path[1:]
        return _recursive_search(data, field)

    # Split path into segments
    segments = _parse_path_segments(path)
    current = data

    for segment in segments:
        current = _navigate_segment(current, segment)
        if current is None:
            return None

    return current


def firefly_json_exists(data: Union[Dict, List, str], path: str) -> bool:
    """
    Check if a path exists in JSON data.

    Args:
        data: JSON data
        path: JSONPath expression

    Returns:
        True if path exists, False otherwise
    """
    result = firefly_json_get(data, path)
    return result is not None


def firefly_json_size(data: Union[Dict, List, str], path: str = None) -> int:
    """
    Get the size of a JSON structure or array at a specific path.

    Args:
        data: JSON data
        path: Optional JSONPath expression

    Returns:
        Size of the structure
    """
    if path:
        target = firefly_json_get(data, path)
    else:
        if isinstance(data, str):
            try:
                target = json.loads(data)
            except json.JSONDecodeError:
                return 0
        else:
            target = data

    if isinstance(target, (list, dict, str)):
        return len(target)

    return 0


def firefly_json_type(data: Union[Dict, List, str], path: str = None) -> str:
    """
    Get the type of a value in JSON data.

    Args:
        data: JSON data
        path: Optional JSONPath expression

    Returns:
        Type name (object, array, string, number, boolean, null)
    """
    if path:
        target = firefly_json_get(data, path)
    else:
        if isinstance(data, str):
            try:
                target = json.loads(data)
            except json.JSONDecodeError:
                return 'string'
        else:
            target = data

    if target is None:
        return 'null'
    elif isinstance(target, bool):
        return 'boolean'
    elif isinstance(target, int):
        return 'number'
    elif isinstance(target, float):
        return 'number'
    elif isinstance(target, str):
        return 'string'
    elif isinstance(target, list):
        return 'array'
    elif isinstance(target, dict):
        return 'object'
    else:
        return 'unknown'


# Helper functions for JSONPath processing

def _parse_path_segments(path: str) -> List[str]:
    """Parse path into segments, handling array indices."""
    segments = []
    current = ""
    in_brackets = False

    for char in path:
        if char == '[':
            if current:
                segments.append(current)
                current = ""
            in_brackets = True
            current += char
        elif char == ']':
            current += char
            segments.append(current)
            current = ""
            in_brackets = False
        elif char == '.' and not in_brackets:
            if current:
                segments.append(current)
                current = ""
        else:
            current += char

    if current:
        segments.append(current)

    return segments


def _navigate_segment(data: Any, segment: str) -> Any:
    """Navigate a single path segment."""
    if segment.startswith('[') and segment.endswith(']'):
        # Array index access
        index_str = segment[1:-1]
        if index_str == '*':
            # Return all elements if it's an array
            return data if isinstance(data, list) else None

        try:
            index = int(index_str)
            if isinstance(data, list) and 0 <= index < len(data):
                return data[index]
        except ValueError:
            pass

        return None
    else:
        # Object field access
        if isinstance(data, dict):
            return data.get(segment)

        return None


def _recursive_search(data: Any, field: str) -> Any:
    """Recursively search for a field in nested structures."""
    if isinstance(data, dict):
        if field in data:
            return data[field]

        for value in data.values():
            result = _recursive_search(value, field)
            if result is not None:
                return result

    elif isinstance(data, list):
        for item in data:
            result = _recursive_search(item, field)
            if result is not None:
                return result

    return None
