#!/usr/bin/env python3
"""
Setup script for the Firefly Rule Engine Python Runtime Library
"""

from setuptools import setup, find_packages
import os

# Read the README file
def read_readme():
    readme_path = os.path.join(os.path.dirname(__file__), 'README.md')
    if os.path.exists(readme_path):
        with open(readme_path, 'r', encoding='utf-8') as f:
            return f.read()
    return "Firefly Rule Engine Python Runtime Library"

# Read requirements
def read_requirements():
    requirements_path = os.path.join(os.path.dirname(__file__), 'requirements.txt')
    if os.path.exists(requirements_path):
        with open(requirements_path, 'r', encoding='utf-8') as f:
            return [line.strip() for line in f if line.strip() and not line.startswith('#')]
    return []

setup(
    name="firefly-runtime",
    version="1.0.0",
    author="Firefly Software Solutions Inc",
    author_email="support@firefly-solutions.com",
    description="Python runtime library for the Firefly Rule Engine",
    long_description=read_readme(),
    long_description_content_type="text/markdown",
    url="https://github.com/firefly-oss/common-platform-rule-engine",
    packages=find_packages(),
    classifiers=[
        "Development Status :: 5 - Production/Stable",
        "Intended Audience :: Developers",
        "License :: OSI Approved :: Apache Software License",
        "Operating System :: OS Independent",
        "Programming Language :: Python :: 3",
        "Programming Language :: Python :: 3.8",
        "Programming Language :: Python :: 3.9",
        "Programming Language :: Python :: 3.10",
        "Programming Language :: Python :: 3.11",
        "Programming Language :: Python :: 3.12",
        "Topic :: Software Development :: Libraries :: Python Modules",
        "Topic :: Office/Business :: Financial",
        "Topic :: Scientific/Engineering :: Mathematics",
    ],
    python_requires=">=3.8",
    install_requires=read_requirements(),
    extras_require={
        "dev": [
            "pytest>=7.0.0",
            "pytest-cov>=4.0.0",
            "black>=23.0.0",
            "flake8>=6.0.0",
            "mypy>=1.0.0",
        ],
        "jsonpath": [
            "jsonpath-ng>=1.6.0",
        ],
    },
    entry_points={
        "console_scripts": [
            "firefly-runtime=firefly_runtime:get_version",
        ],
    },
    include_package_data=True,
    zip_safe=False,
    keywords="firefly rule-engine python runtime financial banking",
    project_urls={
        "Bug Reports": "https://github.com/firefly-oss/common-platform-rule-engine/issues",
        "Source": "https://github.com/firefly-oss/common-platform-rule-engine",
        "Documentation": "https://github.com/firefly-oss/common-platform-rule-engine/docs",
    },
)
