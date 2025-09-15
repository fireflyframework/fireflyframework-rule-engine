#!/usr/bin/env python3
"""
Tests for REST client functions in the Firefly Runtime
"""

import unittest
import warnings
from unittest.mock import patch, MagicMock
from firefly_runtime.rest_client import (
    firefly_rest_get,
    firefly_rest_post,
    firefly_rest_put,
    firefly_rest_delete,
    firefly_rest_patch,
    firefly_rest_call
)


class TestRestClientFunctions(unittest.TestCase):
    """Test REST client functions"""

    @patch('firefly_runtime.rest_client.session')
    def test_firefly_rest_get(self, mock_session):
        """Test firefly_rest_get function"""
        # Mock response
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.headers = {'content-type': 'application/json'}
        mock_response.text = '{"message": "success"}'
        mock_session.get.return_value = mock_response
        
        result = firefly_rest_get("https://api.example.com/test")
        
        self.assertEqual(result['status_code'], 200)
        self.assertTrue(result['success'])
        self.assertEqual(result['data'], {"message": "success"})
        mock_session.get.assert_called_once()

    @patch('firefly_runtime.rest_client.session')
    def test_firefly_rest_post(self, mock_session):
        """Test firefly_rest_post function"""
        # Mock response
        mock_response = MagicMock()
        mock_response.status_code = 201
        mock_response.headers = {'content-type': 'application/json'}
        mock_response.text = '{"id": 123}'
        mock_session.post.return_value = mock_response
        
        result = firefly_rest_post("https://api.example.com/create", {"name": "test"})
        
        self.assertEqual(result['status_code'], 201)
        self.assertTrue(result['success'])
        self.assertEqual(result['data'], {"id": 123})
        mock_session.post.assert_called_once()

    @patch('firefly_runtime.rest_client.session')
    def test_firefly_rest_delete(self, mock_session):
        """Test firefly_rest_delete function"""
        # Mock response
        mock_response = MagicMock()
        mock_response.status_code = 204
        mock_response.headers = {}
        mock_response.text = ''
        mock_session.delete.return_value = mock_response
        
        result = firefly_rest_delete("https://api.example.com/delete/123")
        
        self.assertEqual(result['status_code'], 204)
        self.assertTrue(result['success'])
        mock_session.delete.assert_called_once()

    def test_firefly_rest_call_delete_with_body_warning(self):
        """Test that DELETE with body issues a warning"""
        with patch('firefly_runtime.rest_client.firefly_rest_delete') as mock_delete:
            mock_delete.return_value = {'status_code': 204, 'success': True}
            
            with warnings.catch_warnings(record=True) as w:
                warnings.simplefilter("always")
                
                firefly_rest_call("DELETE", "https://api.example.com/delete/123", 
                                body={"should_not_be_here": True})
                
                # Check that a warning was issued
                self.assertEqual(len(w), 1)
                self.assertTrue(issubclass(w[0].category, UserWarning))
                self.assertIn("DELETE requests should not include a request body", str(w[0].message))
                
                # Verify that firefly_rest_delete was called without the body
                mock_delete.assert_called_once_with("https://api.example.com/delete/123", None, 30)

    def test_firefly_rest_call_get_with_body_warning(self):
        """Test that GET with body issues a warning"""
        with patch('firefly_runtime.rest_client.firefly_rest_get') as mock_get:
            mock_get.return_value = {'status_code': 200, 'success': True}
            
            with warnings.catch_warnings(record=True) as w:
                warnings.simplefilter("always")
                
                firefly_rest_call("GET", "https://api.example.com/data", 
                                body={"should_not_be_here": True})
                
                # Check that a warning was issued
                self.assertEqual(len(w), 1)
                self.assertTrue(issubclass(w[0].category, UserWarning))
                self.assertIn("GET requests should not include a request body", str(w[0].message))
                
                # Verify that firefly_rest_get was called without the body
                mock_get.assert_called_once_with("https://api.example.com/data", None, 30)

    def test_firefly_rest_call_post_with_body_no_warning(self):
        """Test that POST with body does not issue a warning"""
        with patch('firefly_runtime.rest_client.firefly_rest_post') as mock_post:
            mock_post.return_value = {'status_code': 201, 'success': True}
            
            with warnings.catch_warnings(record=True) as w:
                warnings.simplefilter("always")
                
                firefly_rest_call("POST", "https://api.example.com/create", 
                                body={"name": "test"})
                
                # Check that no warning was issued
                self.assertEqual(len(w), 0)
                
                # Verify that firefly_rest_post was called with the body
                mock_post.assert_called_once_with("https://api.example.com/create", {"name": "test"}, None, 30)

    def test_firefly_rest_call_unsupported_method(self):
        """Test unsupported HTTP method"""
        result = firefly_rest_call("INVALID", "https://api.example.com/test")
        
        self.assertEqual(result['status_code'], 0)
        self.assertFalse(result['success'])
        self.assertIn("Unsupported HTTP method", result['error'])


if __name__ == '__main__':
    unittest.main()
