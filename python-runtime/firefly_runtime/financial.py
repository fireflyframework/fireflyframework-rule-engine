#!/usr/bin/env python3
"""
Financial calculation functions for the Firefly Rule Engine Python Runtime

This module provides comprehensive financial calculation functions
equivalent to those in the Java implementation.
"""

import math
from typing import Union, List, Dict, Any
from decimal import Decimal, ROUND_HALF_UP


def firefly_calculate_loan_payment(principal: Union[int, float],
                                  annual_rate: Union[int, float],
                                  term_months: int) -> float:
    """
    Calculate monthly loan payment using the standard amortization formula.

    Args:
        principal: Loan principal amount
        annual_rate: Annual interest rate (as percentage, e.g., 5.5 for 5.5%)
        term_months: Loan term in months

    Returns:
        Monthly payment amount
    """
    if principal <= 0 or term_months <= 0:
        return 0.0

    if annual_rate <= 0:
        return principal / term_months

    monthly_rate = annual_rate / 100 / 12
    payment = principal * (monthly_rate * (1 + monthly_rate) ** term_months) / \
              ((1 + monthly_rate) ** term_months - 1)

    return round(payment, 2)


def firefly_calculate_compound_interest(principal: Union[int, float],
                                       annual_rate: Union[int, float],
                                       years: Union[int, float],
                                       compounds_per_year: int = 12) -> float:
    """
    Calculate compound interest.

    Args:
        principal: Initial principal amount
        annual_rate: Annual interest rate (as percentage)
        years: Number of years
        compounds_per_year: Number of times interest compounds per year

    Returns:
        Final amount after compound interest
    """
    if principal <= 0 or years <= 0:
        return principal

    if annual_rate <= 0:
        return principal

    rate = annual_rate / 100
    amount = principal * (1 + rate / compounds_per_year) ** (compounds_per_year * years)

    return round(amount, 2)


def firefly_calculate_amortization(principal: Union[int, float],
                                  annual_rate: Union[int, float],
                                  term_months: int,
                                  payment_number: int) -> Dict[str, float]:
    """
    Calculate amortization details for a specific payment.

    Args:
        principal: Loan principal amount
        annual_rate: Annual interest rate (as percentage)
        term_months: Loan term in months
        payment_number: Payment number (1-based)

    Returns:
        Dictionary with payment details
    """
    if payment_number < 1 or payment_number > term_months:
        return {"principal": 0.0, "interest": 0.0, "balance": 0.0}

    monthly_payment = firefly_calculate_loan_payment(principal, annual_rate, term_months)
    monthly_rate = annual_rate / 100 / 12

    remaining_balance = principal

    for i in range(1, payment_number + 1):
        interest_payment = remaining_balance * monthly_rate
        principal_payment = monthly_payment - interest_payment
        remaining_balance -= principal_payment

    return {
        "principal": round(principal_payment, 2),
        "interest": round(interest_payment, 2),
        "balance": round(max(0, remaining_balance), 2)
    }


def firefly_debt_to_income_ratio(monthly_debt: Union[int, float],
                                monthly_income: Union[int, float]) -> float:
    """
    Calculate debt-to-income ratio.

    Args:
        monthly_debt: Total monthly debt payments
        monthly_income: Total monthly income

    Returns:
        Debt-to-income ratio as a percentage
    """
    if monthly_income <= 0:
        return 100.0

    ratio = (monthly_debt / monthly_income) * 100
    return round(ratio, 2)


def firefly_credit_utilization(current_balance: Union[int, float],
                              credit_limit: Union[int, float]) -> float:
    """
    Calculate credit utilization ratio.

    Args:
        current_balance: Current credit card balance
        credit_limit: Credit card limit

    Returns:
        Credit utilization ratio as a percentage
    """
    if credit_limit <= 0:
        return 100.0

    utilization = (current_balance / credit_limit) * 100
    return round(min(100.0, max(0.0, utilization)), 2)


def firefly_loan_to_value(loan_amount: Union[int, float],
                         property_value: Union[int, float]) -> float:
    """
    Calculate loan-to-value ratio.

    Args:
        loan_amount: Loan amount
        property_value: Property value

    Returns:
        Loan-to-value ratio as a percentage
    """
    if property_value <= 0:
        return 100.0

    ltv = (loan_amount / property_value) * 100
    return round(ltv, 2)


def firefly_calculate_apr(loan_amount: Union[int, float],
                         total_cost: Union[int, float],
                         term_years: Union[int, float]) -> float:
    """
    Calculate Annual Percentage Rate (APR).

    Args:
        loan_amount: Principal loan amount
        total_cost: Total cost of the loan
        term_years: Loan term in years

    Returns:
        APR as a percentage
    """
    if loan_amount <= 0 or term_years <= 0:
        return 0.0

    total_interest = total_cost - loan_amount
    apr = (total_interest / loan_amount / term_years) * 100

    return round(apr, 2)


def firefly_calculate_credit_score(payment_history: float,
                                  credit_utilization: float,
                                  credit_history_length: int,
                                  credit_mix: int,
                                  new_credit: int) -> int:
    """
    Calculate a simplified credit score based on key factors.

    Args:
        payment_history: Payment history score (0-100)
        credit_utilization: Credit utilization percentage
        credit_history_length: Length of credit history in months
        credit_mix: Number of different credit types
        new_credit: Number of recent credit inquiries

    Returns:
        Calculated credit score (300-850)
    """
    # Simplified credit score calculation
    base_score = 300

    # Payment history (35% weight)
    payment_score = (payment_history / 100) * 350 * 0.35

    # Credit utilization (30% weight) - lower is better
    utilization_score = max(0, (100 - credit_utilization) / 100) * 350 * 0.30

    # Credit history length (15% weight)
    history_score = min(1.0, credit_history_length / 120) * 350 * 0.15

    # Credit mix (10% weight)
    mix_score = min(1.0, credit_mix / 5) * 350 * 0.10

    # New credit (10% weight) - fewer inquiries is better
    new_credit_score = max(0, (10 - new_credit) / 10) * 350 * 0.10

    total_score = base_score + payment_score + utilization_score + history_score + mix_score + new_credit_score

    return int(round(min(850, max(300, total_score))))


def firefly_calculate_risk_score(credit_score: int,
                                debt_to_income: float,
                                employment_years: Union[int, float],
                                down_payment_percent: float) -> int:
    """
    Calculate a risk score for lending decisions.

    Args:
        credit_score: Credit score (300-850)
        debt_to_income: Debt-to-income ratio percentage
        employment_years: Years of employment
        down_payment_percent: Down payment as percentage of purchase price

    Returns:
        Risk score (0-100, lower is better)
    """
    risk_score = 0

    # Credit score factor (40% weight)
    if credit_score >= 750:
        credit_risk = 0
    elif credit_score >= 700:
        credit_risk = 10
    elif credit_score >= 650:
        credit_risk = 25
    elif credit_score >= 600:
        credit_risk = 40
    else:
        credit_risk = 60

    risk_score += credit_risk * 0.4

    # Debt-to-income factor (30% weight)
    if debt_to_income <= 20:
        dti_risk = 0
    elif debt_to_income <= 30:
        dti_risk = 15
    elif debt_to_income <= 40:
        dti_risk = 30
    else:
        dti_risk = 50

    risk_score += dti_risk * 0.3

    # Employment stability factor (20% weight)
    if employment_years >= 5:
        employment_risk = 0
    elif employment_years >= 2:
        employment_risk = 10
    elif employment_years >= 1:
        employment_risk = 20
    else:
        employment_risk = 35

    risk_score += employment_risk * 0.2

    # Down payment factor (10% weight)
    if down_payment_percent >= 20:
        down_payment_risk = 0
    elif down_payment_percent >= 10:
        down_payment_risk = 10
    elif down_payment_percent >= 5:
        down_payment_risk = 20
    else:
        down_payment_risk = 30

    risk_score += down_payment_risk * 0.1

    return int(round(min(100, max(0, risk_score))))


def firefly_payment_history_score(on_time_payments: int,
                                 total_payments: int,
                                 late_payments_30: int = 0,
                                 late_payments_60: int = 0,
                                 late_payments_90: int = 0) -> float:
    """
    Calculate payment history score.

    Args:
        on_time_payments: Number of on-time payments
        total_payments: Total number of payments
        late_payments_30: Number of 30-day late payments
        late_payments_60: Number of 60-day late payments
        late_payments_90: Number of 90+ day late payments

    Returns:
        Payment history score (0-100)
    """
    if total_payments == 0:
        return 100.0

    base_score = (on_time_payments / total_payments) * 100

    # Penalties for late payments
    penalty = (late_payments_30 * 5) + (late_payments_60 * 10) + (late_payments_90 * 20)

    final_score = max(0, base_score - penalty)

    return round(final_score, 2)


def firefly_calculate_rate(credit_score: Union[int, float]) -> float:
    """
    Calculate interest rate based on credit score.

    Args:
        credit_score: Credit score (300-850)

    Returns:
        Interest rate as percentage
    """
    try:
        score = float(credit_score)

        # Rate calculation based on credit score tiers
        if score >= 800:
            return 3.5  # Excellent credit
        elif score >= 750:
            return 4.5  # Very good credit
        elif score >= 700:
            return 5.5  # Good credit
        elif score >= 650:
            return 7.0  # Fair credit
        elif score >= 600:
            return 9.0  # Poor credit
        else:
            return 12.0  # Very poor credit

    except (ValueError, TypeError):
        return 12.0  # Default high rate for invalid scores


def firefly_calculate_debt_ratio(total_debt: Union[int, float],
                                total_income: Union[int, float]) -> float:
    """
    Calculate debt-to-income ratio (alias for firefly_debt_to_income_ratio).

    Args:
        total_debt: Total monthly debt payments
        total_income: Total monthly income

    Returns:
        Debt-to-income ratio as a percentage
    """
    return firefly_debt_to_income_ratio(total_debt, total_income)


def firefly_calculate_ltv(loan_amount: Union[int, float],
                         property_value: Union[int, float]) -> float:
    """
    Calculate loan-to-value ratio (alias for firefly_loan_to_value).

    Args:
        loan_amount: Loan amount
        property_value: Property value

    Returns:
        LTV ratio as a percentage
    """
    return firefly_loan_to_value(loan_amount, property_value)


def firefly_calculate_payment_schedule(principal: Union[int, float],
                                     annual_rate: Union[int, float],
                                     term_months: int) -> List[Dict[str, float]]:
    """
    Calculate complete payment schedule for a loan.

    Args:
        principal: Loan principal amount
        annual_rate: Annual interest rate (as percentage)
        term_months: Loan term in months

    Returns:
        List of payment details for each month
    """
    schedule = []
    remaining_balance = float(principal)
    monthly_payment = firefly_calculate_loan_payment(principal, annual_rate, term_months)
    monthly_rate = annual_rate / 100 / 12

    for payment_num in range(1, term_months + 1):
        if remaining_balance <= 0:
            break

        interest_payment = remaining_balance * monthly_rate
        principal_payment = min(monthly_payment - interest_payment, remaining_balance)
        remaining_balance -= principal_payment

        schedule.append({
            'payment_number': payment_num,
            'payment_amount': round(monthly_payment, 2),
            'principal_payment': round(principal_payment, 2),
            'interest_payment': round(interest_payment, 2),
            'remaining_balance': round(max(0, remaining_balance), 2)
        })

    return schedule
