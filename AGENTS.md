# Tracci Backend Agent Instructions

## Project

This is the **business backend** for Tracci / 趣溪 — travel planning, expense tracking, travel log, collaboration, and sharing.

Core business modules are implemented. Do not rewrite User/Auth or other stable modules unless required by the current task.

| Module | Status |
|---|---|
| Auth (email + WeChat mini + iOS WeChat, account deletion) | ✅ |
| SMS login | ⏸ reserved (Service only) |
| User profile | ✅ |
| Trip + itinerary + weather + collaboration + public share | ✅ |
| Trip stage overview | ✅ |
| Location POI search | ✅ |
| Expense + AA settlement | ✅ |
| Journal (incl. featured photos) + file upload (COS) | ✅ |
| Feedback | ✅ |
| Summary / stats (incl. analysis) / export-assets | ✅ |
| Place / Saved Place（想去的地点） | ✅ |
| Utils exchange-rate API | ❌ |
| Export record / `trip_exports` | ❌ |
| Membership / entitlements | 🧭 planned |

## Tech Stack

- Java 21, Spring Boot 3.3.x, PostgreSQL 17, Redis, Tencent COS + CI, Amap
- Do not introduce MQ by default; evaluate when there is a clear async/decoupling need
- Do not introduce DDD/CQRS/event sourcing without a concrete pain point

## Architecture

Simple layered architecture:

- controller → service → repository/mapper
- dto.request / dto.response, entity (DB only)
- common, config, integration, module/{domain}

Rules:

- Controller must not directly access database.
- Entity must not be returned as API response.
- API response must use `ApiResponse<T>`.
- Paginated response must use `PageResponse<T>`.
- Current user ID must come from `LoginUserContext`.
- Never trust userId from frontend request body/query.
- Trip-scoped data must use member-aware access (`TripAccessService`).

## API Rules

- All APIs start with `/api/v1`
- Create: POST, Update: PUT, Delete: DELETE, Detail: GET /{id}, List: GET with pagination
- Public (no auth): health/info, Swagger, auth register/login/refresh, password reset, selected public share paths
- SMS auth endpoints exist in Service but are not exposed on Controller yet

## Database Rules

Every business table must contain: id, created_at, updated_at, deleted_at (logical deletion).

- Money: BigDecimal / DECIMAL(12,2)
- Exchange rate: BigDecimal / DECIMAL(16,8)
- Schema migrations: Flyway, `src/main/resources/db/migration/`

## Implementation Rules

Before editing: inspect package structure, reuse exceptions and auth.

After coding: run `./gradlew test`, report changed files and test results.

## Build & Run

```bash
./gradlew bootRun   # port 8080, profile dev
./gradlew test      # H2 PostgreSQL compatibility mode, Redis stub
./gradlew bootJar
```