#!/usr/bin/env python3
"""
Logging and auditing functions for the Firefly Rule Engine Python Runtime

This module provides logging, auditing, and notification functions.
"""

import logging
import json
import uuid
from datetime import datetime
from typing import Any, Dict, Optional, Union


# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)

logger = logging.getLogger('firefly_runtime')

# Global audit configuration
_audit_config = {
    'enabled': True,
    'log_level': 'INFO',
    'include_context': True,
    'max_context_size': 1000
}


def configure_logging(level: str = 'INFO', format_string: Optional[str] = None) -> None:
    """
    Configure the logging system.

    Args:
        level: Logging level (DEBUG, INFO, WARNING, ERROR, CRITICAL)
        format_string: Optional custom format string
    """
    numeric_level = getattr(logging, level.upper(), logging.INFO)
    logger.setLevel(numeric_level)

    if format_string:
        handler = logging.StreamHandler()
        formatter = logging.Formatter(format_string)
        handler.setFormatter(formatter)
        logger.handlers = [handler]


def configure_audit(enabled: bool = True, log_level: str = 'INFO',
                   include_context: bool = True, max_context_size: int = 1000) -> None:
    """
    Configure audit settings.

    Args:
        enabled: Whether auditing is enabled
        log_level: Audit log level
        include_context: Whether to include context in audit logs
        max_context_size: Maximum size of context data to log
    """
    global _audit_config
    _audit_config.update({
        'enabled': enabled,
        'log_level': log_level,
        'include_context': include_context,
        'max_context_size': max_context_size
    })


def firefly_log(message: str, level: str = 'INFO', **kwargs) -> None:
    """
    Log a message with optional additional data.

    Args:
        message: Log message
        level: Log level
        **kwargs: Additional data to include in log
    """
    numeric_level = getattr(logging, level.upper(), logging.INFO)

    if kwargs:
        extra_data = json.dumps(kwargs, default=str)
        full_message = f"{message} | Data: {extra_data}"
    else:
        full_message = message

    logger.log(numeric_level, full_message)


def firefly_audit(action: str, rule_name: Optional[str] = None,
                 context: Optional[Dict[str, Any]] = None,
                 result: Optional[Any] = None, **kwargs) -> str:
    """
    Create an audit log entry.

    Args:
        action: Action being audited
        rule_name: Name of the rule being executed
        context: Execution context
        result: Execution result
        **kwargs: Additional audit data

    Returns:
        Audit ID
    """
    if not _audit_config['enabled']:
        return ""

    audit_id = str(uuid.uuid4())
    timestamp = datetime.utcnow().isoformat()

    audit_entry = {
        'audit_id': audit_id,
        'timestamp': timestamp,
        'action': action,
        'rule_name': rule_name,
        'result': _truncate_data(result, _audit_config['max_context_size']),
        **kwargs
    }

    if _audit_config['include_context'] and context:
        audit_entry['context'] = _truncate_data(context, _audit_config['max_context_size'])

    audit_message = f"AUDIT: {json.dumps(audit_entry, default=str)}"
    numeric_level = getattr(logging, _audit_config['log_level'].upper(), logging.INFO)
    logger.log(numeric_level, audit_message)

    return audit_id


def firefly_audit_log(event_type: str, data: Dict[str, Any],
                     severity: str = 'INFO') -> str:
    """
    Create a structured audit log entry.

    Args:
        event_type: Type of event being logged
        data: Event data
        severity: Event severity

    Returns:
        Audit ID
    """
    return firefly_audit(
        action=event_type,
        severity=severity,
        **data
    )


def firefly_send_notification(recipient: str, subject: str, message: str,
                             notification_type: str = 'email') -> bool:
    """
    Send a notification (placeholder implementation).

    Args:
        recipient: Notification recipient
        subject: Notification subject
        message: Notification message
        notification_type: Type of notification (email, sms, webhook)

    Returns:
        True if notification was sent successfully
    """
    # This is a placeholder implementation
    # In a real system, this would integrate with notification services

    notification_data = {
        'recipient': recipient,
        'subject': subject,
        'message': message,
        'type': notification_type,
        'timestamp': datetime.utcnow().isoformat()
    }

    firefly_log(
        f"Notification sent to {recipient}",
        level='INFO',
        notification=notification_data
    )

    return True


def _truncate_data(data: Any, max_size: int) -> Any:
    """
    Truncate data if it exceeds the maximum size.

    Args:
        data: Data to truncate
        max_size: Maximum size in characters

    Returns:
        Truncated data
    """
    if data is None:
        return None

    data_str = json.dumps(data, default=str)

    if len(data_str) <= max_size:
        return data

    # Truncate and add indicator
    truncated_str = data_str[:max_size - 20] + "...[TRUNCATED]"

    try:
        return json.loads(truncated_str)
    except json.JSONDecodeError:
        return truncated_str


def get_audit_stats() -> Dict[str, Any]:
    """
    Get audit system statistics.

    Returns:
        Dictionary with audit statistics
    """
    return {
        'audit_enabled': _audit_config['enabled'],
        'log_level': _audit_config['log_level'],
        'include_context': _audit_config['include_context'],
        'max_context_size': _audit_config['max_context_size'],
        'timestamp': datetime.utcnow().isoformat()
    }
