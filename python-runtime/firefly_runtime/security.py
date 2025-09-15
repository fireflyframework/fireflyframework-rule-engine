#!/usr/bin/env python3
"""
Security functions for the Firefly Rule Engine Python Runtime

This module provides encryption, decryption, and data masking functions.
"""

import hashlib
import base64
import secrets
from typing import Any, Optional, Union
from cryptography.fernet import Fernet
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC


# Global encryption key (should be configured externally in production)
_encryption_key: Optional[bytes] = None


def configure_encryption_key(key: Union[str, bytes]) -> None:
    """
    Configure the global encryption key.

    Args:
        key: Encryption key (string or bytes)
    """
    global _encryption_key

    if isinstance(key, str):
        key = key.encode('utf-8')

    # Derive a proper Fernet key from the provided key
    kdf = PBKDF2HMAC(
        algorithm=hashes.SHA256(),
        length=32,
        salt=b'firefly_salt',  # In production, use a random salt
        iterations=100000,
    )
    _encryption_key = base64.urlsafe_b64encode(kdf.derive(key))


def firefly_encrypt(data: str, key: Optional[str] = None) -> str:
    """
    Encrypt data using Fernet symmetric encryption.

    Args:
        data: Data to encrypt
        key: Optional encryption key (uses global key if not provided)

    Returns:
        Encrypted data as base64 string
    """
    if key:
        configure_encryption_key(key)

    if not _encryption_key:
        # Generate a default key if none is configured
        configure_encryption_key("default_firefly_key_2025")

    try:
        fernet = Fernet(_encryption_key)
        encrypted_data = fernet.encrypt(data.encode('utf-8'))
        return base64.urlsafe_b64encode(encrypted_data).decode('utf-8')
    except Exception as e:
        raise ValueError(f"Encryption failed: {str(e)}")


def firefly_decrypt(encrypted_data: str, key: Optional[str] = None) -> str:
    """
    Decrypt data using Fernet symmetric encryption.

    Args:
        encrypted_data: Encrypted data as base64 string
        key: Optional encryption key (uses global key if not provided)

    Returns:
        Decrypted data as string
    """
    if key:
        configure_encryption_key(key)

    if not _encryption_key:
        # Generate a default key if none is configured
        configure_encryption_key("default_firefly_key_2025")

    try:
        fernet = Fernet(_encryption_key)
        decoded_data = base64.urlsafe_b64decode(encrypted_data.encode('utf-8'))
        decrypted_data = fernet.decrypt(decoded_data)
        return decrypted_data.decode('utf-8')
    except Exception as e:
        raise ValueError(f"Decryption failed: {str(e)}")


def firefly_mask_data(data: str, mask_type: str = 'partial', mask_char: str = '*') -> str:
    """
    Mask sensitive data for logging or display.

    Args:
        data: Data to mask
        mask_type: Type of masking ('partial', 'full', 'email', 'ssn', 'credit_card')
        mask_char: Character to use for masking

    Returns:
        Masked data string
    """
    if not data:
        return data

    if mask_type == 'full':
        return mask_char * len(data)

    elif mask_type == 'partial':
        if len(data) <= 4:
            return mask_char * len(data)
        return data[:2] + mask_char * (len(data) - 4) + data[-2:]

    elif mask_type == 'email':
        if '@' in data:
            local, domain = data.split('@', 1)
            if len(local) <= 2:
                masked_local = mask_char * len(local)
            else:
                masked_local = local[0] + mask_char * (len(local) - 2) + local[-1]
            return f"{masked_local}@{domain}"
        return firefly_mask_data(data, 'partial', mask_char)

    elif mask_type == 'ssn':
        # Mask SSN format: XXX-XX-1234
        clean_ssn = data.replace('-', '').replace(' ', '')
        if len(clean_ssn) == 9:
            return f"{mask_char * 3}-{mask_char * 2}-{clean_ssn[-4:]}"
        return firefly_mask_data(data, 'partial', mask_char)

    elif mask_type == 'credit_card':
        # Mask credit card: XXXX-XXXX-XXXX-1234
        clean_cc = data.replace('-', '').replace(' ', '')
        if len(clean_cc) >= 12:
            masked = mask_char * (len(clean_cc) - 4) + clean_cc[-4:]
            # Format with dashes every 4 digits
            return '-'.join([masked[i:i+4] for i in range(0, len(masked), 4)])
        return firefly_mask_data(data, 'partial', mask_char)

    else:
        # Default to partial masking
        return firefly_mask_data(data, 'partial', mask_char)


def generate_secure_token(length: int = 32) -> str:
    """
    Generate a cryptographically secure random token.

    Args:
        length: Length of the token in bytes

    Returns:
        Secure token as hex string
    """
    return secrets.token_hex(length)


def hash_data(data: str, algorithm: str = 'sha256') -> str:
    """
    Hash data using the specified algorithm.

    Args:
        data: Data to hash
        algorithm: Hash algorithm ('sha256', 'sha512', 'md5')

    Returns:
        Hashed data as hex string
    """
    data_bytes = data.encode('utf-8')

    if algorithm == 'sha256':
        return hashlib.sha256(data_bytes).hexdigest()
    elif algorithm == 'sha512':
        return hashlib.sha512(data_bytes).hexdigest()
    elif algorithm == 'md5':
        return hashlib.md5(data_bytes).hexdigest()
    else:
        raise ValueError(f"Unsupported hash algorithm: {algorithm}")


def verify_hash(data: str, hash_value: str, algorithm: str = 'sha256') -> bool:
    """
    Verify data against a hash value.

    Args:
        data: Original data
        hash_value: Hash to verify against
        algorithm: Hash algorithm used

    Returns:
        True if hash matches, False otherwise
    """
    computed_hash = hash_data(data, algorithm)
    return computed_hash == hash_value
