#!/usr/bin/env python3
"""
REST API client functions for the Firefly Rule Engine Python Runtime

This module provides HTTP client functionality equivalent to the Java implementation.
"""

import requests
import json
from typing import Any, Dict, Optional, Union
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry


# Global session with retry strategy
session = requests.Session()
retry_strategy = Retry(
    total=3,
    status_forcelist=[429, 500, 502, 503, 504],
    allowed_methods=["HEAD", "GET", "OPTIONS", "POST", "PUT", "PATCH", "DELETE"],
    backoff_factor=1
)
adapter = HTTPAdapter(max_retries=retry_strategy)
session.mount("http://", adapter)
session.mount("https://", adapter)


def firefly_rest_get(url: str, headers: Optional[Dict[str, str]] = None,
                    timeout: int = 30) -> Dict[str, Any]:
    """
    Perform HTTP GET request.

    Args:
        url: The URL to request
        headers: Optional headers dictionary
        timeout: Request timeout in seconds

    Returns:
        Dictionary with response data
    """
    try:
        response = session.get(url, headers=headers or {}, timeout=timeout)
        return {
            'status_code': response.status_code,
            'headers': dict(response.headers),
            'data': _parse_response_data(response),
            'success': response.status_code < 400
        }
    except requests.RequestException as e:
        return {
            'status_code': 0,
            'headers': {},
            'data': None,
            'success': False,
            'error': str(e)
        }


def firefly_rest_post(url: str, body: Optional[Union[Dict, str]] = None,
                     headers: Optional[Dict[str, str]] = None,
                     timeout: int = 30) -> Dict[str, Any]:
    """
    Perform HTTP POST request.

    Args:
        url: The URL to request
        body: Request body (dict or string)
        headers: Optional headers dictionary
        timeout: Request timeout in seconds

    Returns:
        Dictionary with response data
    """
    try:
        headers = headers or {}
        if isinstance(body, dict):
            headers.setdefault('Content-Type', 'application/json')
            data = json.dumps(body)
        else:
            data = body

        response = session.post(url, data=data, headers=headers, timeout=timeout)
        return {
            'status_code': response.status_code,
            'headers': dict(response.headers),
            'data': _parse_response_data(response),
            'success': response.status_code < 400
        }
    except requests.RequestException as e:
        return {
            'status_code': 0,
            'headers': {},
            'data': None,
            'success': False,
            'error': str(e)
        }


def firefly_rest_put(url: str, body: Optional[Union[Dict, str]] = None,
                    headers: Optional[Dict[str, str]] = None,
                    timeout: int = 30) -> Dict[str, Any]:
    """
    Perform HTTP PUT request.

    Args:
        url: The URL to request
        body: Request body (dict or string)
        headers: Optional headers dictionary
        timeout: Request timeout in seconds

    Returns:
        Dictionary with response data
    """
    try:
        headers = headers or {}
        if isinstance(body, dict):
            headers.setdefault('Content-Type', 'application/json')
            data = json.dumps(body)
        else:
            data = body

        response = session.put(url, data=data, headers=headers, timeout=timeout)
        return {
            'status_code': response.status_code,
            'headers': dict(response.headers),
            'data': _parse_response_data(response),
            'success': response.status_code < 400
        }
    except requests.RequestException as e:
        return {
            'status_code': 0,
            'headers': {},
            'data': None,
            'success': False,
            'error': str(e)
        }


def firefly_rest_delete(url: str, headers: Optional[Dict[str, str]] = None,
                       timeout: int = 30) -> Dict[str, Any]:
    """
    Perform HTTP DELETE request.

    Args:
        url: The URL to request
        headers: Optional headers dictionary
        timeout: Request timeout in seconds

    Returns:
        Dictionary with response data
    """
    try:
        response = session.delete(url, headers=headers or {}, timeout=timeout)
        return {
            'status_code': response.status_code,
            'headers': dict(response.headers),
            'data': _parse_response_data(response),
            'success': response.status_code < 400
        }
    except requests.RequestException as e:
        return {
            'status_code': 0,
            'headers': {},
            'data': None,
            'success': False,
            'error': str(e)
        }


def firefly_rest_patch(url: str, body: Optional[Union[Dict, str]] = None,
                      headers: Optional[Dict[str, str]] = None,
                      timeout: int = 30) -> Dict[str, Any]:
    """
    Perform HTTP PATCH request.

    Args:
        url: The URL to request
        body: Request body (dict or string)
        headers: Optional headers dictionary
        timeout: Request timeout in seconds

    Returns:
        Dictionary with response data
    """
    try:
        headers = headers or {}
        if isinstance(body, dict):
            headers.setdefault('Content-Type', 'application/json')
            data = json.dumps(body)
        else:
            data = body

        response = session.patch(url, data=data, headers=headers, timeout=timeout)
        return {
            'status_code': response.status_code,
            'headers': dict(response.headers),
            'data': _parse_response_data(response),
            'success': response.status_code < 400
        }
    except requests.RequestException as e:
        return {
            'status_code': 0,
            'headers': {},
            'data': None,
            'success': False,
            'error': str(e)
        }


def firefly_rest_call(method: str, url: str, body: Optional[Union[Dict, str]] = None,
                     headers: Optional[Dict[str, str]] = None,
                     timeout: int = 30) -> Dict[str, Any]:
    """
    Generic REST call function.

    Args:
        method: HTTP method (GET, POST, PUT, DELETE, PATCH)
        url: The URL to request
        body: Request body (dict or string). Note: body is ignored for GET and DELETE
              requests as per HTTP standards. A warning will be issued if body is
              provided for DELETE requests.
        headers: Optional headers dictionary
        timeout: Request timeout in seconds

    Returns:
        Dictionary with response data

    Note:
        - GET and DELETE requests do not support request bodies per HTTP standards
        - If a body is provided for DELETE, a warning will be issued and the body ignored
        - For GET requests, use query parameters in the URL instead of a body
    """
    method = method.upper()

    if method == 'GET':
        # HTTP GET requests should not have a body according to HTTP standards
        # If a body is provided, log a warning but proceed without it
        if body is not None:
            import warnings
            warnings.warn(
                "GET requests should not include a request body according to HTTP standards. "
                "Use query parameters in the URL instead. The body parameter will be ignored.",
                UserWarning,
                stacklevel=2
            )
        return firefly_rest_get(url, headers, timeout)
    elif method == 'POST':
        return firefly_rest_post(url, body, headers, timeout)
    elif method == 'PUT':
        return firefly_rest_put(url, body, headers, timeout)
    elif method == 'DELETE':
        # HTTP DELETE requests should not have a body according to RFC 7231
        # If a body is provided, log a warning but proceed without it
        if body is not None:
            import warnings
            warnings.warn(
                "DELETE requests should not include a request body according to HTTP standards. "
                "The body parameter will be ignored.",
                UserWarning,
                stacklevel=2
            )
        return firefly_rest_delete(url, headers, timeout)
    elif method == 'PATCH':
        return firefly_rest_patch(url, body, headers, timeout)
    else:
        return {
            'status_code': 0,
            'headers': {},
            'data': None,
            'success': False,
            'error': f'Unsupported HTTP method: {method}'
        }


def _parse_response_data(response: requests.Response) -> Any:
    """
    Parse response data based on content type.

    Args:
        response: The requests Response object

    Returns:
        Parsed response data
    """
    content_type = response.headers.get('content-type', '').lower()

    try:
        if 'application/json' in content_type:
            return response.json()
        elif 'text/' in content_type:
            return response.text
        else:
            return response.content
    except (json.JSONDecodeError, UnicodeDecodeError):
        return response.text if response.text else None


def configure_rest_client(timeout: int = 30, retries: int = 3,
                         backoff_factor: float = 1.0) -> None:
    """
    Configure the global REST client settings.

    Args:
        timeout: Default timeout in seconds
        retries: Number of retry attempts
        backoff_factor: Backoff factor for retries
    """
    global session

    retry_strategy = Retry(
        total=retries,
        status_forcelist=[429, 500, 502, 503, 504],
        method_whitelist=["HEAD", "GET", "OPTIONS", "POST", "PUT", "PATCH", "DELETE"],
        backoff_factor=backoff_factor
    )

    adapter = HTTPAdapter(max_retries=retry_strategy)
    session.mount("http://", adapter)
    session.mount("https://", adapter)
