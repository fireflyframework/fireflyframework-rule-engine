#!/usr/bin/env python3
"""
Firefly Rule Engine Interactive Utilities

This module provides utilities for interactive execution of compiled rules,
including user input collection with type conversion and error handling.

Copyright 2025 Firefly Software Solutions Inc
Licensed under the Apache License, Version 2.0
"""

import sys
from typing import Any, Optional, Union


def get_user_input(prompt: str, input_type: str = "text", required: bool = False) -> Optional[Any]:
    """
    Helper function to get user input with type conversion and error handling.
    
    Args:
        prompt (str): The prompt to display to the user
        input_type (str): Expected type - 'text', 'number', 'boolean'
        required (bool): Whether the input is required
    
    Returns:
        The parsed input value or None if not provided and not required
    
    Raises:
        SystemExit: If user cancels with Ctrl+C
    """
    try:
        value = input(prompt).strip()
        if not value:
            if required:
                print(f"❌ This field is required.")
                return get_user_input(prompt, input_type, required)
            return None
        
        # Type conversion
        if input_type == "number":
            try:
                return float(value) if '.' in value else int(value)
            except ValueError:
                print(f"❌ Invalid number format: {value}")
                return get_user_input(prompt, input_type, required)
        elif input_type == "boolean":
            return value.lower() in ['true', '1', 'yes', 'y']
        else:  # text
            return value
            
    except KeyboardInterrupt:
        print("\n\n❌ Execution cancelled by user.")
        sys.exit(1)
    except Exception as e:
        print(f"❌ Error reading input: {e}")
        return get_user_input(prompt, input_type, required)


def collect_inputs(input_definitions: dict) -> dict:
    """
    Collect multiple inputs based on definitions.
    
    Args:
        input_definitions (dict): Dictionary mapping input names to their types
        
    Returns:
        dict: Dictionary with collected input values
    """
    context = {}
    
    print("📝 Please provide input values:")
    for input_name, input_type in input_definitions.items():
        value = get_user_input(f"{input_name} ({input_type}): ", input_type)
        if value is not None:
            context[input_name] = value
    
    return context


def configure_constants_interactively(constants_need_config: list) -> dict:
    """
    Interactively configure constants that need values.
    
    Args:
        constants_need_config (list): List of constant names that need configuration
        
    Returns:
        dict: Dictionary with configured constant values
    """
    constants_values = {}
    
    if not constants_need_config:
        return constants_values
    
    print("⚠️  WARNING: The following constants are not configured:")
    for const in constants_need_config:
        print(f"   - {const}")
    print("\nPlease configure these constants in the database or update the code manually.")
    print("You can still run the rule, but it may not work correctly.\n")
    
    # Interactive constant configuration
    configure = input("Would you like to configure constants interactively? (y/n): ").lower().strip()
    if configure == 'y' or configure == 'yes':
        for const in constants_need_config:
            value = input(f"Enter value for {const}: ").strip()
            try:
                # Try to parse as number first
                if '.' in value:
                    constants_values[const] = float(value)
                else:
                    constants_values[const] = int(value)
            except ValueError:
                # If not a number, treat as string
                if value.lower() in ['true', 'false']:
                    constants_values[const] = value.lower() == 'true'
                else:
                    constants_values[const] = value
        print("\n✅ Constants configured!\n")
    
    return constants_values


def print_firefly_header(rule_name: str, description: str = None, version: str = None):
    """
    Print the standard Firefly Rule Engine header.
    
    Args:
        rule_name (str): Name of the rule
        description (str, optional): Rule description
        version (str, optional): Rule version
    """
    print("\n" + "="*80)
    print("🔥 FIREFLY RULE ENGINE - COMPILED PYTHON RULE")
    print("="*80)
    print(f"Rule: {rule_name}")
    if description:
        print(f"Description: {description}")
    if version:
        print(f"Version: {version}")
    print("Copyright 2025 Firefly Software Solutions Inc")
    print("Licensed under Apache 2.0 | Made with ❤️")
    print("="*80)


def print_execution_results(result: dict):
    """
    Print execution results in a formatted way.
    
    Args:
        result (dict): The execution results to display
    """
    import json
    
    print("✅ Rule executed successfully!")
    print("\n📊 RESULTS:")
    print(json.dumps(result, indent=2, default=str))


def print_firefly_footer():
    """Print the standard Firefly Rule Engine footer."""
    print("\n" + "="*80)
    print("🎉 Execution completed successfully!")
    print("Thank you for using Firefly Rule Engine ❤️")
    print("="*80)


def execute_rule_interactively(rule_function, rule_name: str, description: str = None, 
                             version: str = None, input_definitions: dict = None,
                             constants_need_config: list = None):
    """
    Execute a rule function interactively with full user interface.
    
    Args:
        rule_function: The compiled rule function to execute
        rule_name (str): Name of the rule
        description (str, optional): Rule description  
        version (str, optional): Rule version
        input_definitions (dict, optional): Input definitions {name: type}
        constants_need_config (list, optional): List of constants needing configuration
    """
    import traceback
    
    # Print header
    print_firefly_header(rule_name, description, version)
    
    # Configure constants if needed
    if constants_need_config:
        constants_values = configure_constants_interactively(constants_need_config)
        # Apply configured constants to global constants dict
        if 'constants' in globals():
            globals()['constants'].update(constants_values)
    
    # Collect inputs
    context = {}
    if input_definitions:
        context = collect_inputs(input_definitions)
    else:
        print("ℹ️  No input variables required for this rule.")
    
    # Execute rule
    print("\n🚀 Executing rule...")
    print("-" * 40)
    
    try:
        result = rule_function(context)
        print_execution_results(result)
    except Exception as e:
        print(f"❌ Error executing rule: {e}")
        traceback.print_exc()
        sys.exit(1)
    
    # Print footer
    print_firefly_footer()
