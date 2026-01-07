
# BANKING FUNCTIONAL SPECIFICATION

## INDEX

1. Purpose and Scope
2. People and Stakeholders
3. Product Context
4. User Stories / Use Cases
5. Business Rules
6. Functional Requirements
7. Non-Functional Requirements (NFR)
8. Metrics and KPIs
9. Acceptance Criteria
10. Risks and Assumptions
11. Glossary

## 1. Purpose and Scope

The purpose of the Banking module is to manage users' bank accounts, the transactions produced in those accounts, and their categorization to facilitate personal financial analysis. This module will allow users to import transactions from CSV files or via API, view their account balances, and categorize expenses and income for better financial tracking.

## 2. People and Stakeholders

- End users: People who want to manage their bank accounts and personal financial transactions.
- Product Owner: Responsible for defining the functionalities and priorities of the Banking module.
- Developers: Team in charge of implementing and maintaining the Banking module.

## 3. Product Context

The Banking module is an integral part of the "finance-app" personal finance application. It integrates with other modules to provide a complete view of the user's financial situation. It uses a database (e.g., PostgreSQL) to store information and communicates with other services through mechanisms such as RESTful APIs or asynchronous messaging (e.g., RabbitMQ).

The main data flow includes importing transactions from CSV files provided by users or via API, securely storing this information in the database, and automatically or manually categorizing transactions to facilitate financial analysis.

## 4. User Stories / Use Cases

- US-001: As a user, I want to create bank accounts with an associated currency.
- US-002: As a user, I want each bank account to have a type (regular, interest-bearing, savings).
- US-003: As a user, I want to import transactions from a CSV file to record my bank movements.
- US-004: As a user, I want to categorize my transactions automatically (e.g., with AI) to analyze expenses and income.
- US-005: As a user, I want to see the current balance of all my bank accounts.
- US-006: As a user, I want to filter my transactions by date, category, and bank account.
- US-007: As a user, I want to receive notifications about important or unusual transactions in my bank accounts.
- US-008: As a user, I want my bank data to be secure and only accessible to me.
- US-009: As a user, I want to see spending in a period by category.
- US-010: As a user, I want to create custom tags for my transactions and filter by them (e.g., "vacation", "work").

## 5. Business Rules

- BR-001: Each bank account must have a unique identifier, alias, type, and associated currency.
- BR-002: Imported CSV files must be validated to ensure they meet the expected format.
- BR-003: Transactions must be categorized automatically based on predefined rules or machine learning.
- BR-004: Users can only access and modify their own accounts and transactions.
- BR-005: Transactions must have a status (pending, confirmed, failed) to reflect their processing.
- BR-006: Transactions can have multiple associated tags to facilitate classification.
- BR-007: Each user belongs to a tenant, and data must be isolated between tenants.
- BR-008: Transaction categories must be hierarchical, allowing subcategories.
- BR-009: Internal transfers between accounts of the same user do not affect the user's total balance and are not considered expenses or income.
- BR-010: The base currency is EUR; it must be converted to other currencies according to the exchange rate on the transaction date.

## 6. Functional Requirements

Each FR implements one or more US and complies with the indicated BRs.

- FR-001 (Account CRUD): The system allows creating, reading, updating, and deleting bank accounts.
Implements: US-001, US-002 · Complies: BR-001, BR-004, BR-007
- FR-002 (CSV Import): The system allows importing transactions from CSV files.
Implements: US-003 · Complies: BR-002, BR-004, BR-005, BR-007
- FR-003 (Automatic Categorization): The system automatically categorizes imported transactions and allows manual adjustment.
Implements: US-004 · Complies: BR-003, BR-008, BR-004
- FR-004 (Account and Aggregate Balance): The system allows viewing the current balance of bank accounts and the user's aggregate balance.
Implements: US-005 · Complies: BR-009, BR-010
- FR-005 (Transaction Filtering): The system allows filtering transactions by date, category, and bank account.
Implements: US-006 · Complies: BR-008, BR-004
- FR-006 (Notifications): The system sends notifications about important or unusual transactions, according to user configuration.
Implements: US-007 · Complies: BR-004, BR-007
- FR-007 (Data Security): The system ensures that users' bank data is protected and only accessible to them.
Implements: US-008 · Complies: BR-004, BR-007
- FR-008 (Custom Tags): The system allows creating and managing custom tags and filtering by them.
Implements: US-010 · Complies: BR-006, BR-004, BR-007
- FR-009 (Multi-tenant): The system supports multi-tenant, ensuring data isolation between different tenants.
Implements: — · Complies: BR-007

## 7. Non-Functional Requirements (NFR)

- Security: Authentication and authorization must be managed through Keycloak.
- Automatic database backup every 24 hours.
- Privacy: Sensitive data must be encrypted at rest and in transit. Merchant and IBAN data must be anonymized.

## 8. Metrics and KPIs

- Average CSV import time.
- Percentage of transactions correctly categorized automatically.
- Number of monthly active users using the Banking module.
- Average latency of bank account balance queries.

## 9. Acceptance Criteria

- AC-001 (Account CRUD): Users can create, read, update, and delete bank accounts without errors.
Validates: FR-001 · Reference: US-001, US-002
- AC-002 (Import and Visualization): Users can import transactions from CSV files and see them reflected in their accounts.
Validates: FR-002 · Reference: US-003
- AC-003 (Custom Tags): Users can add and manage custom tags for their transactions.
Validates: FR-008 · Reference: US-010
- AC-004 (Access Security): Users' bank data is protected and only accessible to them.
Validates: FR-007 · Reference: US-008

## 10. Risks and Assumptions

- Risk of non-compliance with data protection regulations (GDPR).
- Assumption that users have basic knowledge of personal finance and banking management.

## 11. Glossary

- Bank account: Represents a user's financial account.
- Transaction: Financial movement associated with a bank account.
- Category: Classification assigned to a transaction to facilitate financial analysis.
- Tag: Custom label that a user can assign to a transaction for classification.
- Multi-tenant: Architecture that allows multiple users (tenants) to share the same application while keeping their data isolated.
- CSV: Comma-separated values file format used to import transactions.
