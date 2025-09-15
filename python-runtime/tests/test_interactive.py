#!/usr/bin/env python3
"""
Tests for interactive functions in the Firefly Runtime
"""

import unittest
import sys
import os
from unittest.mock import patch, MagicMock
from io import StringIO

# Add the parent directory to the path so we can import firefly_runtime
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

from firefly_runtime import *


class TestInteractiveFunctions(unittest.TestCase):
    """Test interactive functions"""

    @patch('builtins.input')
    def test_get_user_input_text(self, mock_input):
        """Test get_user_input with text input"""
        mock_input.return_value = "test value"
        result = get_user_input("Enter text: ", "text")
        self.assertEqual(result, "test value")

    @patch('builtins.input')
    def test_get_user_input_number(self, mock_input):
        """Test get_user_input with number input"""
        mock_input.return_value = "123.45"
        result = get_user_input("Enter number: ", "number")
        self.assertEqual(result, 123.45)
        
        mock_input.return_value = "123"
        result = get_user_input("Enter number: ", "number")
        self.assertEqual(result, 123)

    @patch('builtins.input')
    def test_get_user_input_boolean(self, mock_input):
        """Test get_user_input with boolean input"""
        mock_input.return_value = "true"
        result = get_user_input("Enter boolean: ", "boolean")
        self.assertTrue(result)
        
        mock_input.return_value = "false"
        result = get_user_input("Enter boolean: ", "boolean")
        self.assertFalse(result)
        
        mock_input.return_value = "yes"
        result = get_user_input("Enter boolean: ", "boolean")
        self.assertTrue(result)

    @patch('builtins.input')
    def test_get_user_input_empty(self, mock_input):
        """Test get_user_input with empty input"""
        mock_input.return_value = ""
        result = get_user_input("Enter text: ", "text")
        self.assertIsNone(result)

    @patch('builtins.input')
    def test_get_user_input_invalid_number(self, mock_input):
        """Test get_user_input with invalid number input"""
        # First call returns invalid, second call returns valid
        mock_input.side_effect = ["invalid", "123"]
        result = get_user_input("Enter number: ", "number")
        self.assertEqual(result, 123)

    @patch('firefly_runtime.interactive.get_user_input')
    def test_collect_inputs(self, mock_get_input):
        """Test collect_inputs function"""
        mock_get_input.side_effect = [750, "John Doe", True]
        
        input_definitions = {
            'creditScore': 'number',
            'name': 'text',
            'hasCollateral': 'boolean'
        }
        
        result = collect_inputs(input_definitions)
        
        expected = {
            'creditScore': 750,
            'name': "John Doe",
            'hasCollateral': True
        }
        
        self.assertEqual(result, expected)

    @patch('builtins.input')
    def test_configure_constants_interactively_empty_list(self, mock_input):
        """Test configure_constants_interactively with empty list"""
        result = configure_constants_interactively([])
        self.assertEqual(result, {})

    @patch('builtins.input')
    def test_configure_constants_interactively_with_constants(self, mock_input):
        """Test configure_constants_interactively with constants"""
        # User chooses to configure, then provides values
        mock_input.side_effect = ["y", "650", "0.4"]
        
        constants_need_config = ['MIN_CREDIT_SCORE', 'MAX_DEBT_RATIO']
        result = configure_constants_interactively(constants_need_config)
        
        expected = {
            'MIN_CREDIT_SCORE': 650,
            'MAX_DEBT_RATIO': 0.4
        }
        
        self.assertEqual(result, expected)

    @patch('builtins.input')
    def test_configure_constants_interactively_decline(self, mock_input):
        """Test configure_constants_interactively when user declines"""
        mock_input.return_value = "n"
        
        constants_need_config = ['MIN_CREDIT_SCORE']
        result = configure_constants_interactively(constants_need_config)
        
        self.assertEqual(result, {})

    @patch('sys.stdout', new_callable=StringIO)
    def test_print_firefly_header(self, mock_stdout):
        """Test print_firefly_header function"""
        print_firefly_header("Test Rule", "Test Description", "1.0")
        output = mock_stdout.getvalue()
        
        self.assertIn("FIREFLY RULE ENGINE", output)
        self.assertIn("Test Rule", output)
        self.assertIn("Test Description", output)
        self.assertIn("1.0", output)
        self.assertIn("Firefly Software Solutions Inc", output)

    @patch('sys.stdout', new_callable=StringIO)
    def test_print_execution_results(self, mock_stdout):
        """Test print_execution_results function"""
        result = {
            'decision': 'APPROVED',
            'rate': 5.5,
            'score': 750
        }
        
        print_execution_results(result)
        output = mock_stdout.getvalue()
        
        self.assertIn("Rule executed successfully", output)
        self.assertIn("RESULTS", output)
        self.assertIn("APPROVED", output)

    @patch('sys.stdout', new_callable=StringIO)
    def test_print_firefly_footer(self, mock_stdout):
        """Test print_firefly_footer function"""
        print_firefly_footer()
        output = mock_stdout.getvalue()
        
        self.assertIn("Execution completed successfully", output)
        self.assertIn("Thank you for using Firefly Rule Engine", output)

    @patch('firefly_runtime.interactive.print_firefly_header')
    @patch('firefly_runtime.interactive.collect_inputs')
    @patch('firefly_runtime.interactive.print_execution_results')
    @patch('firefly_runtime.interactive.print_firefly_footer')
    def test_execute_rule_interactively(self, mock_footer, mock_results, mock_collect, mock_header):
        """Test execute_rule_interactively function"""
        # Mock the rule function
        def mock_rule(context):
            return {'result': 'success'}
        
        # Mock inputs
        mock_collect.return_value = {'input1': 'value1'}
        
        # Execute
        execute_rule_interactively(
            rule_function=mock_rule,
            rule_name="Test Rule",
            description="Test Description",
            version="1.0",
            input_definitions={'input1': 'text'}
        )
        
        # Verify calls
        mock_header.assert_called_once_with("Test Rule", "Test Description", "1.0")
        mock_collect.assert_called_once_with({'input1': 'text'})
        mock_results.assert_called_once_with({'result': 'success'})
        mock_footer.assert_called_once()


if __name__ == '__main__':
    unittest.main()
