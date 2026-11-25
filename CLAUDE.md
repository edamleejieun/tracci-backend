# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build/Run/Test Commands

```bash
./gradlew bootRun     # Run dev server (port 8080, profile: dev)
./gradlew bootJar     # Package executable JAR to build/libs/
./gradlew test        # Run all tests (H2 PostgreSQL mode, Redis stub)
```

Swagger UI: `http://localhost:8080/swagger-ui.html`
Health check: `http://localhost:8080/actuator/health`

## Tech Stack

- Java 21, Spring Boot 3.3.5, Gradle 8.x (Groovy DSL, `build.gradle`)
- PostgreSQL 17 + MyBatis-Plus 3.5.7 (logical deletion via `deletedAt`; pagination dialect `POSTGRE_SQL`)
- Redis (refresh token JTI, login lockout, SMS codes, POI/weather cache)
- JWT auth via JJWT 0.12.x + spring-security-crypto (BCrypt only — no full Spring Security)
- SpringDoc OpenAPI 2.6.0 for Swagger
- Tencent COS + CI (image URLs via `FileUrlResolver` / `CiUrlBuilder`)
- Amap Web Service (POI search, trip weather)

**Architecture principle:** Business backend is a **modular monolith**. Do not introduce MQ by default; evaluate when async/decoupling needs are clear. Avoid DDD/CQRS/event sourcing unless there is a concrete need.

## Architecture: Simple Layered

```
controller → service → repository/mapper
     ↓
 dto.request / dto.response    entity (DB only)
```

Package tree under `com.tracci`:
- `common/` — `ApiResponse<T>`, `PageResponse<T>`, `ErrorCode` enum, exception hierarchy, `JwtUtil`, `LoginUserContext`, `JwtAuthenticationFilter`
- `config/` — Spring configuration properties and beans
- `integration/` — third-party adapters (SMS, WeChat, email, Tencent COS, Amap, exchange rate, tencentwsa)
- `module/<domain>/` — `auth`, `user`, `trip` (itinerary, weather, collaboration, public share), `tripoverview`, `expense`, `journal`, `file`, `location`, `feedback`, `summary`, `stats`, `export`, `place`, `placecontent`

**Key rules:**
- Controller must not access database directly or contain business logic
- Entity must never be returned as API response — always map to response DTO
- Current user ID comes from `LoginUserContext.requireUserId()`, never from request body/query params
- All user-owned data must check ownership in the service layer

## API Conventions

- All endpoints under `/api/v1`
- CREATE → `POST`, UPDATE → `PUT`, DELETE → `DELETE`, detail → `GET /{id}`, list → `GET` with pagination
- All responses wrapped in `ApiResponse<T>` (`{ "code": 0, "message": "ok", "data": ... }`)
- Paginated responses use `PageResponse<T>`
- Auth required on all endpoints except public paths in `tracci.auth.public-path-patterns`:
  - Actuator health/info, Swagger
  - `/api/v1/auth/register`, `/login`, `/wechat-mini/login`, `/wechat-app/login`, `/refresh`

## Database Rules

Every business table must include: `id` (UUID), `created_at`, `updated_at`, `deleted_at` (logical deletion, auto-managed by MyBatis-Plus). No physical deletes.

- Money → `BigDecimal` in Java, `DECIMAL(12,2)` in PostgreSQL
- Exchange rates → `BigDecimal`, `DECIMAL(16,8)` in DB; fetched via `ExchangeRateClient` (MVP: `MockExchangeRateClient` only)
- Images: `files` table + COS object metadata; journal links via `journal_photos.file_id`; URL/CI variants via `FileUrlResolver`
- Schema migrations: Flyway (`src/main/resources/db/migration/V*.sql`), applied automatically on startup

## Error Handling

`ErrorCode` enum defines all business error codes with HTTP status + business code + default message. Exception hierarchy: `AuthException`, `BizException`, `ForbiddenException`, `NotFoundException`, `ValidationException`, `ThirdPartyException` — all handled centrally by `GlobalExceptionHandler`.

Error code ranges: `0` = success, `400xx` = params/auth-code, `401xx` = token, `403xx` = permission, `404xx` = not found, `422xx` = validation, `429xx` = rate limit, `500xx` = internal, `502xx` = third-party.

## Configuration Profiles

- `application.yml` — common config (port, MyBatis-Plus, Jackson, logging, actuator, auth whitelist)
- `application-dev.yml` — local PostgreSQL (`tracci_dev`), local Redis, dev JWT secret, third-party mocks enabled
- `application-prod.yml` — all values from env vars, mocks disabled, Swagger disabled
- `src/test/resources/application-test.yml` — H2 PostgreSQL compatibility mode, Redis excluded, all mocks enabled

Sensitive values (JWT secret, cloud AK/SK, WeChat AppSecret, Amap key, DB passwords) must be injected via environment variables in production.

## Before Editing Code

1. Inspect existing package structure, naming conventions, and common classes
2. Reuse existing exception types, `ApiResponse`/`PageResponse`, and auth mechanism
3. Validate request DTOs with `@Valid` / Jakarta validation annotations
4. Add ownership checks for all user-owned resources
5. Add Flyway migration SQL for new tables
6. After coding, run `./gradlew test` and report results

When docs conflict with code, the code is authoritative.