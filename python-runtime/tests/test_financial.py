#!/usr/bin/env python3
"""
Tests for financial functions in the Firefly Runtime
"""

import unittest
import sys
import os

# Add the parent directory to the path so we can import firefly_runtime
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

from firefly_runtime import *


class TestFinancialFunctions(unittest.TestCase):
    """Test financial functions"""

    def test_firefly_calculate_loan_payment(self):
        """Test firefly_calculate_loan_payment function"""
        # Test normal case
        payment = firefly_calculate_loan_payment(100000, 5.0, 360)  # 30-year mortgage
        self.assertAlmostEqual(payment, 536.82, places=2)
        
        # Test zero interest
        payment = firefly_calculate_loan_payment(12000, 0, 12)
        self.assertEqual(payment, 1000.0)
        
        # Test invalid inputs
        payment = firefly_calculate_loan_payment(0, 5.0, 360)
        self.assertEqual(payment, 0.0)
        
        payment = firefly_calculate_loan_payment(100000, 5.0, 0)
        self.assertEqual(payment, 0.0)

    def test_firefly_debt_to_income_ratio(self):
        """Test firefly_debt_to_income_ratio function"""
        # Test normal case
        ratio = firefly_debt_to_income_ratio(2000, 8000)
        self.assertEqual(ratio, 0.25)
        
        # Test zero income
        ratio = firefly_debt_to_income_ratio(2000, 0)
        self.assertEqual(ratio, 0.0)
        
        # Test zero debt
        ratio = firefly_debt_to_income_ratio(0, 8000)
        self.assertEqual(ratio, 0.0)

    def test_firefly_credit_utilization(self):
        """Test firefly_credit_utilization function"""
        # Test normal case
        utilization = firefly_credit_utilization(2500, 10000)
        self.assertEqual(utilization, 25.0)
        
        # Test zero limit
        utilization = firefly_credit_utilization(2500, 0)
        self.assertEqual(utilization, 0.0)
        
        # Test zero balance
        utilization = firefly_credit_utilization(0, 10000)
        self.assertEqual(utilization, 0.0)

    def test_firefly_loan_to_value(self):
        """Test firefly_loan_to_value function"""
        # Test normal case
        ltv = firefly_loan_to_value(200000, 250000)
        self.assertEqual(ltv, 80.0)
        
        # Test zero property value
        ltv = firefly_loan_to_value(200000, 0)
        self.assertEqual(ltv, 0.0)
        
        # Test zero loan amount
        ltv = firefly_loan_to_value(0, 250000)
        self.assertEqual(ltv, 0.0)

    def test_firefly_calculate_compound_interest(self):
        """Test firefly_calculate_compound_interest function"""
        # Test normal case
        amount = firefly_calculate_compound_interest(1000, 5.0, 1)  # 1 year
        self.assertAlmostEqual(amount, 1051.16, places=2)
        
        # Test zero principal
        amount = firefly_calculate_compound_interest(0, 5.0, 1)
        self.assertEqual(amount, 0.0)
        
        # Test zero rate
        amount = firefly_calculate_compound_interest(1000, 0, 1)
        self.assertEqual(amount, 1000.0)

    def test_firefly_calculate_rate(self):
        """Test firefly_calculate_rate function"""
        # Test excellent credit
        rate = firefly_calculate_rate(800)
        self.assertEqual(rate, 3.5)
        
        # Test very good credit
        rate = firefly_calculate_rate(750)
        self.assertEqual(rate, 4.5)
        
        # Test good credit
        rate = firefly_calculate_rate(700)
        self.assertEqual(rate, 5.5)
        
        # Test fair credit
        rate = firefly_calculate_rate(650)
        self.assertEqual(rate, 7.0)
        
        # Test poor credit
        rate = firefly_calculate_rate(600)
        self.assertEqual(rate, 9.0)
        
        # Test very poor credit
        rate = firefly_calculate_rate(500)
        self.assertEqual(rate, 12.0)
        
        # Test invalid score
        rate = firefly_calculate_rate("invalid")
        self.assertEqual(rate, 12.0)
        
        rate = firefly_calculate_rate(None)
        self.assertEqual(rate, 12.0)

    def test_firefly_calculate_credit_score(self):
        """Test firefly_calculate_credit_score function"""
        factors = {
            'payment_history': 35,
            'credit_utilization': 30,
            'length_of_history': 15,
            'credit_mix': 10,
            'new_credit': 10
        }
        
        score = firefly_calculate_credit_score(factors)
        self.assertIsInstance(score, int)
        self.assertGreaterEqual(score, 300)
        self.assertLessEqual(score, 850)

    def test_firefly_payment_history_score(self):
        """Test firefly_payment_history_score function"""
        # Test perfect payment history
        score = firefly_payment_history_score(12, 12, 0, 0, 0)
        self.assertEqual(score, 100.0)
        
        # Test with some late payments
        score = firefly_payment_history_score(10, 12, 2, 0, 0)
        self.assertEqual(score, 73.33)  # (10/12)*100 - 2*5
        
        # Test no payment history
        score = firefly_payment_history_score(0, 0, 0, 0, 0)
        self.assertEqual(score, 100.0)

    def test_firefly_calculate_apr(self):
        """Test firefly_calculate_apr function"""
        # Test normal case
        apr = firefly_calculate_apr(5.0, 1000, 100000)
        self.assertIsInstance(apr, float)
        self.assertGreater(apr, 5.0)  # APR should be higher than nominal rate

    def test_firefly_calculate_risk_score(self):
        """Test firefly_calculate_risk_score function"""
        # Test low risk
        score = firefly_calculate_risk_score(800, 50000, 0.2, 5, 250000)
        self.assertIsInstance(score, int)
        self.assertGreaterEqual(score, 0)
        self.assertLessEqual(score, 100)
        
        # Test high risk
        score = firefly_calculate_risk_score(500, 30000, 0.6, 1, 100000)
        self.assertIsInstance(score, int)
        self.assertGreaterEqual(score, 0)
        self.assertLessEqual(score, 100)


if __name__ == '__main__':
    unittest.main()
