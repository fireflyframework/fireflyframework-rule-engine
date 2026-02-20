#!/usr/bin/env python3
"""
Date and time functions for the Firefly Rule Engine Python Runtime
"""

from datetime import datetime, timedelta
from typing import Any, Union

try:
    from dateutil.relativedelta import relativedelta
    _HAS_DATEUTIL = True
except ImportError:
    _HAS_DATEUTIL = False


def firefly_now() -> datetime:
    """Get current date and time."""
    return datetime.now()


def firefly_today() -> datetime:
    """Get current date (midnight)."""
    return datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)


def firefly_dateadd(date_value: Any, amount: Union[int, float], unit: str) -> datetime:
    """Add time to a date."""
    try:
        if isinstance(date_value, str):
            base_date = datetime.fromisoformat(date_value.replace('Z', '+00:00'))
        elif isinstance(date_value, datetime):
            base_date = date_value
        else:
            base_date = datetime.now()
        
        amount_int = int(amount)
        unit_lower = unit.lower()
        
        if unit_lower in ['day', 'days']:
            return base_date + timedelta(days=amount_int)
        elif unit_lower in ['month', 'months']:
            if _HAS_DATEUTIL:
                return base_date + relativedelta(months=amount_int)
            # Fallback: approximate month addition
            return base_date + timedelta(days=amount_int * 30)
        elif unit_lower in ['year', 'years']:
            if _HAS_DATEUTIL:
                return base_date + relativedelta(years=amount_int)
            # Fallback: approximate year addition
            return base_date + timedelta(days=amount_int * 365)
        elif unit_lower in ['hour', 'hours']:
            return base_date + timedelta(hours=amount_int)
        elif unit_lower in ['minute', 'minutes']:
            return base_date + timedelta(minutes=amount_int)
        elif unit_lower in ['second', 'seconds']:
            return base_date + timedelta(seconds=amount_int)
        else:
            return base_date
            
    except (ValueError, TypeError):
        return datetime.now()


def firefly_datediff(start_date: Any, end_date: Any, unit: str) -> int:
    """Calculate difference between two dates."""
    try:
        if isinstance(start_date, str):
            start = datetime.fromisoformat(start_date.replace('Z', '+00:00'))
        elif isinstance(start_date, datetime):
            start = start_date
        else:
            start = datetime.now()
            
        if isinstance(end_date, str):
            end = datetime.fromisoformat(end_date.replace('Z', '+00:00'))
        elif isinstance(end_date, datetime):
            end = end_date
        else:
            end = datetime.now()
        
        diff = end - start
        unit_lower = unit.lower()
        
        if unit_lower in ['day', 'days']:
            return diff.days
        elif unit_lower in ['hour', 'hours']:
            return int(diff.total_seconds() / 3600)
        elif unit_lower in ['minute', 'minutes']:
            return int(diff.total_seconds() / 60)
        elif unit_lower in ['second', 'seconds']:
            return int(diff.total_seconds())
        elif unit_lower in ['month', 'months']:
            if _HAS_DATEUTIL:
                rd = relativedelta(end, start)
                return rd.years * 12 + rd.months
            return int(diff.days / 30)
        elif unit_lower in ['year', 'years']:
            if _HAS_DATEUTIL:
                rd = relativedelta(end, start)
                return rd.years
            return int(diff.days / 365)
        else:
            return diff.days
            
    except (ValueError, TypeError):
        return 0


def firefly_time_hour(timestamp: Any) -> int:
    """Extract hour from timestamp."""
    try:
        if isinstance(timestamp, str):
            dt = datetime.fromisoformat(timestamp.replace('Z', '+00:00'))
        elif isinstance(timestamp, datetime):
            dt = timestamp
        else:
            dt = datetime.now()
        
        return dt.hour
        
    except (ValueError, TypeError):
        return 0
