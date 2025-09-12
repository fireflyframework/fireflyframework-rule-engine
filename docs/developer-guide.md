# Developer Guide

This guide provides comprehensive instructions for setting up, developing, testing, and deploying the Firefly Rule Engine.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Development Environment Setup](#development-environment-setup)
- [Building the Project](#building-the-project)
- [Running the Application](#running-the-application)
- [Testing](#testing)
- [Database Setup](#database-setup)
- [IDE Configuration](#ide-configuration)
- [Debugging](#debugging)
- [Contributing](#contributing)

## Prerequisites

### Required Software

| Software | Version | Purpose |
|----------|---------|---------|
| **Java** | 21+ | Runtime and compilation |
| **Maven** | 3.8+ | Build and dependency management |
| **PostgreSQL** | 12+ | Database for constants storage |
| **Docker** | 20+ | Containerization (optional) |
| **Git** | 2.30+ | Version control |

### Recommended Tools

- **IntelliJ IDEA** or **Eclipse** - IDE with Spring Boot support
- **Postman** or **curl** - API testing
- **DBeaver** or **pgAdmin** - Database management
- **Docker Compose** - Multi-container orchestration

## Development Environment Setup

### 1. Clone the Repository

```bash
git clone https://github.com/firefly-oss/common-platform-rule-engine.git
cd common-platform-rule-engine
```

### 2. Set Up PostgreSQL Database

The application uses PostgreSQL with R2DBC for reactive database access. The database stores system constants and YAML DSL rule definitions.

#### Database Schema Overview

The application includes two main tables:
- **`constants`** - System constants used in rule evaluation (e.g., MIN_CREDIT_SCORE, MAX_LOAN_AMOUNT)
- **`rule_definitions`** - Stored YAML DSL rule definitions with versioning and metadata

Database migrations are automatically applied on startup using Flyway.

#### Option A: Local PostgreSQL Installation

```bash
# Install PostgreSQL (Ubuntu/Debian)
sudo apt update
sudo apt install postgresql postgresql-contrib

# Create database and user
sudo -u postgres psql
CREATE DATABASE firefly_rules;
CREATE USER firefly_user WITH PASSWORD 'firefly_password';
GRANT ALL PRIVILEGES ON DATABASE firefly_rules TO firefly_user;
\q
```

#### Option B: Docker PostgreSQL

```bash
# Run PostgreSQL in Docker
docker run --name firefly-postgres \
  -e POSTGRES_DB=firefly_rules \
  -e POSTGRES_USER=firefly_user \
  -e POSTGRES_PASSWORD=firefly_password \
  -p 5432:5432 \
  -d postgres:15
```

### 3. Configure Environment Variables

Create a `.env` file in the project root:

```bash
# Database Configuration
DB_HOST=localhost
DB_PORT=5432
DB_NAME=firefly_rules
DB_USERNAME=firefly_user
DB_PASSWORD=firefly_password
DB_SSL_MODE=disable

# Application Configuration
SERVER_PORT=8080
SERVER_ADDRESS=localhost

# Logging Configuration
LOGGING_LEVEL_ROOT=INFO
LOGGING_LEVEL_FIREFLY=DEBUG
```

### 4. Load Environment Variables

```bash
# Linux/macOS
export $(cat .env | xargs)

# Windows (PowerShell)
Get-Content .env | ForEach-Object {
    $name, $value = $_.split('=')
    Set-Content env:\$name $value
}
```

## Building the Project

### 1. Clean and Compile

```bash
# Clean previous builds
mvn clean

# Compile all modules
mvn compile

# Package without running tests
mvn package -DskipTests

# Full build with tests
mvn clean install
```

### 2. Module-Specific Builds

```bash
# Build specific module
cd common-platform-rule-engine-core
mvn clean install

# Build with specific profile
mvn clean install -Pdev
```

### 3. Build Verification

```bash
# Verify build artifacts
ls -la common-platform-rule-engine-web/target/
# Should see: common-platform-rule-engine-web-1.0.0-SNAPSHOT.jar
```

## Running the Application

### 1. Development Mode

```bash
# Run with Maven (auto-reload enabled)
cd common-platform-rule-engine-web
mvn spring-boot:run

# Run with specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Run with JVM arguments
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx1024m -Xdebug"
```

### 2. Production Mode

```bash
# Build JAR
mvn clean package

# Run JAR
java -jar common-platform-rule-engine-web/target/common-platform-rule-engine-web-1.0.0-SNAPSHOT.jar

# Run with specific profile
java -jar -Dspring.profiles.active=prod common-platform-rule-engine-web-1.0.0-SNAPSHOT.jar
```

### 3. Docker Mode

```bash
# Build Docker image
docker build -t firefly-rule-engine .

# Run container
docker run -p 8080:8080 \
  -e DB_HOST=host.docker.internal \
  -e DB_USERNAME=firefly_user \
  -e DB_PASSWORD=firefly_password \
  firefly-rule-engine
```

### 4. Verify Application Startup

```bash
# Check health endpoint
curl http://localhost:8080/actuator/health

# Check API documentation
open http://localhost:8080/swagger-ui.html
```

## Testing

### 1. Unit Tests

```bash
# Run all unit tests
mvn test

# Run tests for specific module
cd common-platform-rule-engine-core
mvn test

# Run specific test class
mvn test -Dtest=RulesEvaluationEngineTest

# Run specific test method
mvn test -Dtest=RulesEvaluationEngineTest#testEvaluateSimplifiedDSL
```

### 2. Integration Tests

```bash
# Run integration tests
mvn verify

# Run with test database
mvn verify -Dspring.profiles.active=test

# Generate test coverage report
mvn jacoco:report
open target/site/jacoco/index.html
```

### 3. Test Categories

| Test Type | Location | Purpose |
|-----------|----------|---------|
| Unit Tests | `src/test/java/**/*Test.java` | Test individual components |
| Integration Tests | `src/test/java/**/*IntegrationTest.java` | Test component interactions |
| API Tests | `src/test/java/**/controllers/*Test.java` | Test REST endpoints |

### 4. Test Data Management

```bash
# Reset test database
mvn flyway:clean flyway:migrate -Dspring.profiles.active=test

# Load test data
mvn exec:java -Dexec.mainClass="com.firefly.rules.TestDataLoader"
```

## Database Setup

### 1. Database Migrations

```bash
# Run migrations manually
mvn flyway:migrate

# Check migration status
mvn flyway:info

# Clean database (development only)
mvn flyway:clean
```

### 2. Migration Files

Location: `common-platform-rule-engine-models/src/main/resources/db/migration/`

```
V1__Create_constants_table.sql       # System constants storage
V2__Create_rule_definitions_table.sql # YAML DSL rule definitions storage
```

#### V1__Create_constants_table.sql
Creates the `constants` table for storing system constants used in rule evaluation.

#### V2__Create_rule_definitions_table.sql
Creates the `rule_definitions` table for storing YAML DSL rule definitions with:
- Unique code-based identification
- Version tracking and metadata
- Active/inactive status management
- Full YAML content storage
- Audit fields (created_by, updated_by, timestamps)

### 3. Test Database Setup

```yaml
# application-test.yaml
spring:
  r2dbc:
    url: r2dbc:h2:mem:///testdb
  flyway:
    url: jdbc:h2:mem:testdb
```

## Working with YAML DSL Storage

### 1. Development Workflow

#### Creating Rule Definitions

```bash
# 1. Create and validate YAML DSL
curl -X POST http://localhost:8080/api/v1/rules/definitions/validate \
  -H "Content-Type: text/plain" \
  -d 'name: "Credit Scoring"
description: "Basic credit assessment"

inputs:
  - creditScore
  - annualIncome

when:
  - creditScore at_least MIN_CREDIT_SCORE
  - annualIncome at_least MIN_ANNUAL_INCOME

then:
  - set is_eligible to true
  - set approval_tier to "STANDARD"

else:
  - set is_eligible to false
  - set approval_tier to "DECLINED"

output:
  is_eligible: boolean
  approval_tier: text'

# 2. Store validated rule definition
curl -X POST http://localhost:8080/api/v1/rules/definitions \
  -H "Content-Type: application/json" \
  -d '{
    "code": "credit_scoring_v1",
    "name": "Credit Scoring Rule v1",
    "description": "Basic credit scoring for loan applications",
    "yamlContent": "...",
    "version": "1.0",
    "isActive": true,
    "tags": "credit,scoring,loan"
  }'
```

#### Testing Stored Rules

```bash
# Evaluate by code
curl -X POST http://localhost:8080/api/v1/rules/evaluate/by-code \
  -H "Content-Type: application/json" \
  -d '{
    "ruleDefinitionCode": "credit_scoring_v1",
    "inputData": {
      "creditScore": 720,
      "annualIncome": 75000
    }
  }'

# Evaluate by ID
curl -X POST http://localhost:8080/api/v1/rules/evaluate/by-id \
  -H "Content-Type: application/json" \
  -d '{
    "ruleDefinitionId": "123e4567-e89b-12d3-a456-426614174000",
    "inputData": {
      "creditScore": 720,
      "annualIncome": 75000
    }
  }'
```

### 2. Best Practices

#### Rule Definition Management
- Use semantic versioning for rule versions (e.g., "1.0.0", "1.1.0")
- Include descriptive tags for easy categorization and search
- Always validate YAML DSL before storing
- Use meaningful codes that reflect the rule's purpose
- Set `isActive: false` for deprecated rules instead of deleting

#### Development Tips
- Test rules with various input scenarios before deployment
- Use the validation endpoint during development to catch issues early
- Leverage filtering and pagination for large rule sets
- Monitor rule evaluation performance and optimize as needed

## IDE Configuration

### IntelliJ IDEA Setup

1. **Import Project**
   - File → Open → Select `pom.xml`
   - Import as Maven project

2. **Configure JDK**
   - File → Project Structure → Project → SDK: Java 21

3. **Enable Annotation Processing**
   - Settings → Build → Compiler → Annotation Processors
   - Enable annotation processing

4. **Install Plugins**
   - Lombok Plugin
   - Spring Boot Plugin
   - YAML/Ansible Support

5. **Run Configurations**
   ```
   Main Class: com.firefly.rules.web.RuleEngineApplication
   VM Options: -Dspring.profiles.active=dev
   Environment Variables: DB_HOST=localhost;DB_USERNAME=firefly_user
   ```

### Eclipse Setup

1. **Import Maven Project**
   - File → Import → Existing Maven Projects

2. **Configure Build Path**
   - Right-click project → Properties → Java Build Path
   - Add JRE 21

3. **Enable Lombok**
   - Download lombok.jar
   - Run: `java -jar lombok.jar`
   - Point to Eclipse installation

## Debugging

### 1. Application Debugging

```bash
# Enable debug mode
export JAVA_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
mvn spring-boot:run

# Or with JAR
java -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005 \
  -jar common-platform-rule-engine-web-1.0.0-SNAPSHOT.jar
```

### 2. Database Debugging

```bash
# Enable SQL logging
export LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_R2DBC=DEBUG

# Monitor database connections
export LOGGING_LEVEL_IO_R2DBC_POOL=DEBUG
```

### 3. Rule Evaluation Debugging

```yaml
# Enable detailed rule logging
logging:
  level:
    com.firefly.rules.core.dsl.evaluation: DEBUG
    com.firefly.rules.core.utils.JsonLogger: TRACE
```

### 4. Common Issues

| Issue | Symptom | Solution |
|-------|---------|----------|
| Database Connection | `Connection refused` | Check PostgreSQL is running |
| Port Conflict | `Port 8080 already in use` | Change `SERVER_PORT` or kill process |
| Memory Issues | `OutOfMemoryError` | Increase heap size: `-Xmx2g` |
| Lombok Issues | `Cannot resolve symbol` | Install Lombok plugin |

## Contributing

### 1. Code Style

```bash
# Format code with Maven
mvn spotless:apply

# Check code style
mvn spotless:check
```

### 2. Commit Guidelines

```bash
# Commit message format
git commit -m "feat(core): add support for nested conditions

- Implement nested condition evaluation
- Add tests for complex rule scenarios
- Update documentation

Closes #123"
```

### 3. Pull Request Process

1. Create feature branch: `git checkout -b feature/new-feature`
2. Make changes and add tests
3. Run full test suite: `mvn clean verify`
4. Update documentation if needed
5. Submit pull request with description

### 4. Code Quality Gates

- All tests must pass
- Code coverage > 80%
- No SonarQube critical issues
- Documentation updated
- Changelog entry added

This developer guide provides everything needed to start contributing to the Firefly Rule Engine project.
