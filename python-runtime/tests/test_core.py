#!/usr/bin/env python3
"""
Tests for core functions in the Firefly Runtime
"""

import unittest
import sys
import os

# Add the parent directory to the path so we can import firefly_runtime
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

from firefly_runtime import *


class TestCoreFunctions(unittest.TestCase):
    """Test core functions"""

    def test_get_nested_value(self):
        """Test get_nested_value function"""
        context = {
            'user': {
                'profile': {
                    'name': 'John Doe',
                    'age': 30
                },
                'settings': {
                    'theme': 'dark'
                }
            },
            'score': 750
        }
        
        # Test valid paths
        self.assertEqual(get_nested_value(context, 'user.profile.name'), 'John Doe')
        self.assertEqual(get_nested_value(context, 'user.profile.age'), 30)
        self.assertEqual(get_nested_value(context, 'user.settings.theme'), 'dark')
        self.assertEqual(get_nested_value(context, 'score'), 750)
        
        # Test invalid paths
        self.assertIsNone(get_nested_value(context, 'user.profile.invalid'))
        self.assertIsNone(get_nested_value(context, 'invalid.path'))
        self.assertIsNone(get_nested_value(context, ''))

    def test_get_indexed_value(self):
        """Test get_indexed_value function"""
        context = {
            'scores': [100, 200, 300],
            'users': [
                {'name': 'Alice'},
                {'name': 'Bob'},
                {'name': 'Charlie'}
            ]
        }
        
        # Test valid indices
        self.assertEqual(get_indexed_value(context, 'scores', 0), 100)
        self.assertEqual(get_indexed_value(context, 'scores', 2), 300)
        self.assertEqual(get_indexed_value(context, 'users', 1), {'name': 'Bob'})
        
        # Test invalid indices
        self.assertIsNone(get_indexed_value(context, 'scores', 5))
        self.assertIsNone(get_indexed_value(context, 'scores', -1))
        self.assertIsNone(get_indexed_value(context, 'invalid', 0))

    def test_is_empty(self):
        """Test is_empty function"""
        # Test empty values
        self.assertTrue(is_empty(None))
        self.assertTrue(is_empty(""))
        self.assertTrue(is_empty([]))
        self.assertTrue(is_empty({}))
        self.assertTrue(is_empty(()))
        self.assertTrue(is_empty(set()))
        
        # Test non-empty values
        self.assertFalse(is_empty("test"))
        self.assertFalse(is_empty([1]))
        self.assertFalse(is_empty({"a": 1}))
        self.assertFalse(is_empty((1,)))
        self.assertFalse(is_empty({1}))
        self.assertFalse(is_empty(0))
        self.assertFalse(is_empty(False))

    def test_list_remove(self):
        """Test list_remove function"""
        # Test normal case
        original = [1, 2, 3, 2, 4]
        result = list_remove(original, 2)
        self.assertEqual(result, [1, 3, 4])
        
        # Test removing non-existent value
        result = list_remove([1, 2, 3], 5)
        self.assertEqual(result, [1, 2, 3])
        
        # Test empty list
        result = list_remove([], 1)
        self.assertEqual(result, [])
        
        # Test non-list input
        result = list_remove("not a list", 1)
        self.assertEqual(result, "not a list")

    def test_safe_divide(self):
        """Test safe_divide function"""
        # Test normal division
        self.assertEqual(safe_divide(10, 2), 5.0)
        self.assertEqual(safe_divide(7, 3), 7/3)
        
        # Test division by zero
        self.assertEqual(safe_divide(10, 0), 0)
        self.assertEqual(safe_divide(10, 0, 999), 999)
        
        # Test invalid inputs
        self.assertEqual(safe_divide("invalid", 2), 0)
        self.assertEqual(safe_divide(10, "invalid"), 0)

    def test_coerce_to_number(self):
        """Test coerce_to_number function"""
        # Test valid numbers
        self.assertEqual(coerce_to_number(123), 123)
        self.assertEqual(coerce_to_number(123.45), 123.45)
        self.assertEqual(coerce_to_number("123"), 123.0)
        self.assertEqual(coerce_to_number("123.45"), 123.45)
        
        # Test invalid inputs
        self.assertEqual(coerce_to_number("invalid"), 0)
        self.assertEqual(coerce_to_number(None), 0)
        self.assertEqual(coerce_to_number([]), 0)
        
        # Test with custom default
        self.assertEqual(coerce_to_number("invalid", -1), -1)

    def test_coerce_to_boolean(self):
        """Test coerce_to_boolean function"""
        # Test boolean values
        self.assertTrue(coerce_to_boolean(True))
        self.assertFalse(coerce_to_boolean(False))
        
        # Test numbers
        self.assertTrue(coerce_to_boolean(1))
        self.assertTrue(coerce_to_boolean(-1))
        self.assertTrue(coerce_to_boolean(0.1))
        self.assertFalse(coerce_to_boolean(0))
        
        # Test strings
        self.assertTrue(coerce_to_boolean("true"))
        self.assertTrue(coerce_to_boolean("yes"))
        self.assertTrue(coerce_to_boolean("1"))
        self.assertTrue(coerce_to_boolean("on"))
        self.assertTrue(coerce_to_boolean("enabled"))
        self.assertFalse(coerce_to_boolean("false"))
        self.assertFalse(coerce_to_boolean("no"))
        self.assertFalse(coerce_to_boolean(""))
        
        # Test None
        self.assertFalse(coerce_to_boolean(None))
        
        # Test collections
        self.assertTrue(coerce_to_boolean([1]))
        self.assertTrue(coerce_to_boolean({"a": 1}))
        self.assertFalse(coerce_to_boolean([]))
        self.assertFalse(coerce_to_boolean({}))

    def test_circuit_breaker_action(self):
        """Test circuit_breaker_action function"""
        # This is a placeholder function, just test it doesn't crash
        result = circuit_breaker_action("test_action", 10)
        self.assertTrue(result)


if __name__ == '__main__':
    unittest.main()
