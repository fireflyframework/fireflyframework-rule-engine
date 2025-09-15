#!/usr/bin/env python3
"""
Tests for validation functions in the Firefly Runtime
"""

import unittest
import sys
import os

# Add the parent directory to the path so we can import firefly_runtime
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

from firefly_runtime import *


class TestValidationFunctions(unittest.TestCase):
    """Test validation functions"""

    def test_firefly_is_positive(self):
        """Test firefly_is_positive function"""
        self.assertTrue(firefly_is_positive(1))
        self.assertTrue(firefly_is_positive(0.1))
        self.assertTrue(firefly_is_positive(100))
        self.assertFalse(firefly_is_positive(0))
        self.assertFalse(firefly_is_positive(-1))
        self.assertFalse(firefly_is_positive(-0.1))
        self.assertFalse(firefly_is_positive("invalid"))
        self.assertFalse(firefly_is_positive(None))

    def test_firefly_is_negative(self):
        """Test firefly_is_negative function"""
        self.assertTrue(firefly_is_negative(-1))
        self.assertTrue(firefly_is_negative(-0.1))
        self.assertTrue(firefly_is_negative(-100))
        self.assertFalse(firefly_is_negative(0))
        self.assertFalse(firefly_is_negative(1))
        self.assertFalse(firefly_is_negative(0.1))
        self.assertFalse(firefly_is_negative("invalid"))
        self.assertFalse(firefly_is_negative(None))

    def test_firefly_is_zero(self):
        """Test firefly_is_zero function"""
        self.assertTrue(firefly_is_zero(0))
        self.assertTrue(firefly_is_zero(0.0))
        self.assertTrue(firefly_is_zero("0"))
        self.assertFalse(firefly_is_zero(1))
        self.assertFalse(firefly_is_zero(-1))
        self.assertFalse(firefly_is_zero(0.1))
        self.assertFalse(firefly_is_zero("invalid"))
        self.assertFalse(firefly_is_zero(None))

    def test_firefly_is_null(self):
        """Test firefly_is_null function"""
        self.assertTrue(firefly_is_null(None))
        self.assertFalse(firefly_is_null(0))
        self.assertFalse(firefly_is_null(""))
        self.assertFalse(firefly_is_null([]))
        self.assertFalse(firefly_is_null({}))
        self.assertFalse(firefly_is_null(False))

    def test_firefly_is_not_null(self):
        """Test firefly_is_not_null function"""
        self.assertFalse(firefly_is_not_null(None))
        self.assertTrue(firefly_is_not_null(0))
        self.assertTrue(firefly_is_not_null(""))
        self.assertTrue(firefly_is_not_null([]))
        self.assertTrue(firefly_is_not_null({}))
        self.assertTrue(firefly_is_not_null(False))

    def test_firefly_is_empty(self):
        """Test firefly_is_empty function"""
        self.assertTrue(firefly_is_empty(None))
        self.assertTrue(firefly_is_empty(""))
        self.assertTrue(firefly_is_empty([]))
        self.assertTrue(firefly_is_empty({}))
        self.assertTrue(firefly_is_empty(()))
        self.assertTrue(firefly_is_empty(set()))
        self.assertFalse(firefly_is_empty("test"))
        self.assertFalse(firefly_is_empty([1]))
        self.assertFalse(firefly_is_empty({"a": 1}))
        self.assertFalse(firefly_is_empty(0))
        self.assertFalse(firefly_is_empty(False))

    def test_firefly_is_not_empty(self):
        """Test firefly_is_not_empty function"""
        self.assertFalse(firefly_is_not_empty(None))
        self.assertFalse(firefly_is_not_empty(""))
        self.assertFalse(firefly_is_not_empty([]))
        self.assertFalse(firefly_is_not_empty({}))
        self.assertTrue(firefly_is_not_empty("test"))
        self.assertTrue(firefly_is_not_empty([1]))
        self.assertTrue(firefly_is_not_empty({"a": 1}))
        self.assertTrue(firefly_is_not_empty(0))
        self.assertTrue(firefly_is_not_empty(False))

    def test_firefly_is_numeric(self):
        """Test firefly_is_numeric function"""
        self.assertTrue(firefly_is_numeric(1))
        self.assertTrue(firefly_is_numeric(1.5))
        self.assertTrue(firefly_is_numeric("123"))
        self.assertTrue(firefly_is_numeric("123.45"))
        self.assertTrue(firefly_is_numeric("-123"))
        self.assertFalse(firefly_is_numeric("abc"))
        self.assertFalse(firefly_is_numeric(""))
        self.assertFalse(firefly_is_numeric(None))
        self.assertFalse(firefly_is_numeric([]))

    def test_firefly_is_valid_credit_score(self):
        """Test firefly_is_valid_credit_score function"""
        self.assertTrue(firefly_is_valid_credit_score(300))
        self.assertTrue(firefly_is_valid_credit_score(850))
        self.assertTrue(firefly_is_valid_credit_score(720))
        self.assertFalse(firefly_is_valid_credit_score(299))
        self.assertFalse(firefly_is_valid_credit_score(851))
        self.assertFalse(firefly_is_valid_credit_score("invalid"))
        self.assertFalse(firefly_is_valid_credit_score(None))

    def test_firefly_validate_email(self):
        """Test firefly_validate_email function"""
        self.assertTrue(firefly_validate_email("test@example.com"))
        self.assertTrue(firefly_validate_email("user.name@domain.co.uk"))
        self.assertTrue(firefly_validate_email("test+tag@example.org"))
        self.assertFalse(firefly_validate_email("invalid-email"))
        self.assertFalse(firefly_validate_email("@example.com"))
        self.assertFalse(firefly_validate_email("test@"))
        self.assertFalse(firefly_validate_email(""))
        self.assertFalse(firefly_validate_email(None))

    def test_firefly_validate_phone(self):
        """Test firefly_validate_phone function"""
        self.assertTrue(firefly_validate_phone("1234567890"))
        self.assertTrue(firefly_validate_phone("11234567890"))
        self.assertTrue(firefly_validate_phone("(123) 456-7890"))
        self.assertTrue(firefly_validate_phone("123-456-7890"))
        self.assertFalse(firefly_validate_phone("123456789"))  # Too short
        self.assertFalse(firefly_validate_phone("123456789012"))  # Too long
        self.assertFalse(firefly_validate_phone("abc-def-ghij"))
        self.assertFalse(firefly_validate_phone(""))
        self.assertFalse(firefly_validate_phone(None))

    def test_firefly_is_valid_ssn(self):
        """Test firefly_is_valid_ssn function"""
        self.assertTrue(firefly_is_valid_ssn("123-45-6789"))
        self.assertTrue(firefly_is_valid_ssn("123456789"))
        self.assertFalse(firefly_is_valid_ssn("123-45-678"))  # Too short
        self.assertFalse(firefly_is_valid_ssn("123-45-67890"))  # Too long
        self.assertFalse(firefly_is_valid_ssn("abc-de-fghi"))
        self.assertFalse(firefly_is_valid_ssn(""))
        self.assertFalse(firefly_is_valid_ssn(None))

    def test_firefly_is_percentage(self):
        """Test firefly_is_percentage function"""
        self.assertTrue(firefly_is_percentage(0))
        self.assertTrue(firefly_is_percentage(50))
        self.assertTrue(firefly_is_percentage(100))
        self.assertTrue(firefly_is_percentage(25.5))
        self.assertFalse(firefly_is_percentage(-1))
        self.assertFalse(firefly_is_percentage(101))
        self.assertFalse(firefly_is_percentage("invalid"))
        self.assertFalse(firefly_is_percentage(None))

    def test_firefly_is_currency(self):
        """Test firefly_is_currency function"""
        self.assertTrue(firefly_is_currency(0))
        self.assertTrue(firefly_is_currency(100))
        self.assertTrue(firefly_is_currency(99.99))
        self.assertTrue(firefly_is_currency(1000000))
        self.assertFalse(firefly_is_currency(-1))
        self.assertFalse(firefly_is_currency(-0.01))
        self.assertFalse(firefly_is_currency("invalid"))
        self.assertFalse(firefly_is_currency(None))


if __name__ == '__main__':
    unittest.main()
