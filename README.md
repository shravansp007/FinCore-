# FinCore — Intelligent Banking Platform

FinCore is a full‑stack digital banking app that simulates core retail banking flows: authentication, account management, transfers, bill payments, and admin operations. It pairs a Spring Boot API with an Angular SPA and focuses on secure, auditable transactions and a modern UX.

## Project Overview

FinCore demonstrates end‑to‑end banking workflows with a clean separation of concerns, domain‑driven entities, and role‑based security. It is designed to be easy to run locally while showcasing production‑minded practices (JWT, validation, centralized errors, and audit‑friendly logs).

## Tech Stack

Backend
- Java 17
- Spring Boot 3.2
- Spring Security + JWT
- Spring Data JPA / Hibernate
- PostgreSQL
- Redis (transaction caching)
- Lombok

Frontend
- Angular 17 (standalone components)
- RxJS + Reactive Forms
- CSS3

## Key Features

- JWT authentication with role‑based access control (USER, ADMIN)
- Account management (Savings, Checking, Fixed Deposit)
- Transactions: deposit, withdraw, transfer, bill payment
- Beneficiary management (Yono‑style transfer)
- Dashboard summary and recent transactions
- Admin console for users, accounts, and transactions
- Centralized validation and error handling

## How To Run Locally

Prerequisites
- Java 17+
- Maven 3.8+
- Node.js 18+
- PostgreSQL 14+
- Redis 7+

Database and Redis
1. Create database
```sql
CREATE DATABASE banking_db;
```
2. Start Redis
```bash
redis-server
```

Backend
1. Configure env vars or `src/main/resources/application.yaml`
- `POSTGRES_*`
- `REDIS_*`
- `JWT_SECRET` (required)
- `JWT_ISSUER`
- `JWT_AUDIENCE`
2. Run API
```bash
mvn clean install
mvn spring-boot:run
```
API: `http://localhost:8080`

Frontend
1. Install and start
```bash
cd frontend
npm install
npm start
```
App: `http://localhost:4200`

## Security Highlights

- JWT validation with issuer, audience, and expiration checks
- BCrypt password hashing
- Role‑based endpoint protection
- Centralized validation and error responses
- Logs avoid sensitive data (no tokens or secrets)

Optional admin seed (local only)
- Enable in `application.yaml`: `app.admin.seed-enabled: true`
- Default: `admin@bank.com` / `Admin@123`
- Disable in production

## API Snapshot

Auth
- `POST /api/auth/register`
- `POST /api/auth/login`

Accounts
- `GET /api/accounts`
- `POST /api/accounts`
- `GET /api/accounts/{id}`
- `GET /api/accounts/number/{accountNumber}`

Transactions
- `POST /api/transactions`
- `POST /api/transactions/deposit`
- `POST /api/transactions/withdraw`
- `POST /api/transactions/bill-payment`
- `GET /api/transactions`
- `GET /api/transactions/account/{id}`
- `GET /api/transactions/account/{id}/mini-statement`
- `GET /api/transactions/reference/{reference}`

Admin (ADMIN role)
- `GET /api/admin/users`
- `GET /api/admin/accounts`
- `GET /api/admin/transactions`

## Project Structure

```
intelligent-banking-platform/
├── src/main/java/com/bank/app/
│   ├── config/          # Security, JWT, Redis configuration
│   ├── controller/      # REST API endpoints
│   ├── dto/             # Data Transfer Objects
│   ├── entity/          # JPA Entities
│   ├── exception/       # Global exception handling
│   ├── repository/      # Data repositories
│   └── service/         # Business logic
├── src/main/resources/
│   └── application.yaml # Application configuration
├── frontend/
│   └── src/app/
│       ├── core/        # Services, guards, interceptors
│       ├── features/    # Feature modules (auth, dashboard, etc.)
│       └── shared/      # Shared models and components
└── pom.xml
```

## License

MIT License
