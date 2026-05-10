# user-service

Spring Boot service for **authentication**, **user profiles**, and **admin user management**. It issues JWT access and refresh tokens, integrates with MySQL for accounts, Redis for sessions/token metadata, and Kafka for async work (for example email notifications).

## Port and base URL

| Item | Value |
|------|--------|
| **Local `application.yml`** | **`server.port: 8081`** — often overridden because **branch-service** also defaults to 8081 |
| **Docker Compose** (`foodchain-deployment`) | Maps host **`${USER_SERVICE_HOST_PORT:-8086}` → container `8086`** (`SERVER_PORT=8086`) |
| **Context path** | **`/api`** |

Example direct URLs: `http://localhost:8086/api/v1/auth/login` (when running on 8086).

### Via API Gateway

Use **`http://localhost:8080`**. Routes include:

| Gateway prefix | Purpose |
|----------------|---------|
| `/api/v1/auth/**` | Register, login, refresh, password reset, verify — **except** `GET /api/v1/auth/me` which requires a token |
| `/api/v1/users/**` | Profile and admin user listing (role-gated) |
| `/api/v1/admin/users/**` | Admin-focused user management |

The gateway validates JWTs and forwards identity headers (**`X-User-Id`**, **`X-User-Role`**, **`X-User-BranchId`** when applicable). **Downstream services often rely on these headers** when the raw `Authorization` header is not forwarded.

---

## Security behaviour (high level)

- **JWT:** HS256; secret must match **api-gateway** (`jwt.secret`).
- **Calling through the gateway:** Filters can build a synthetic Spring Security context from **`X-User-Id`** + **`X-User-Role`** when no Bearer token is present on the wire, so `@PreAuthorize` continues to work.
- **Role claims:** Access-token role claims are normalized to Spring **`ROLE_*`** authorities so names like **`ROLE_HEAD_OFFICE_ADMIN`** align with `@PreAuthorize("hasRole('HEAD_OFFICE_ADMIN')")`.

---

## Main REST groups

### `/v1/auth`

Registration, login, token refresh, password flows, email verification. See Swagger tag **Auth** for status codes (for example **403** if email not verified, or CORS misconfiguration).

### `/v1/users`

- **`GET /v1/users/me`** — current user (Bearer required).
- **`GET /v1/users`** — list users (**HEAD_OFFICE_ADMIN**).
- **`GET /v1/users/{id}`** — **HEAD_OFFICE_ADMIN** or **BRANCH_MANAGER**.

### `/v1/admin/users`

Admin operations (listing/managing accounts). **`X-User-Role`** is preferred when present (gateway); otherwise the role is taken from the JWT-derived **`Authentication`**. Allowed admin role strings include **`HEAD_OFFICE_ADMIN`**, **`OFFICE_ADMIN`**, **`Admin`**.

---

## Dependencies (runtime)

- **MySQL** — user data (`user_db` in Docker stack).
- **Redis** — caching / session-related usage per configuration.
- **Kafka** — producers for notifications (topics configured via app properties).
- **Eureka** — service discovery (`optional:configserver` + Eureka client).

---

## Configuration highlights

| Area | Notes |
|------|--------|
| Config Server | `optional:configserver:http://localhost:8888` |
| CORS | Configurable for gateway Swagger origins (`APP_CORS_ALLOWED_ORIGINS` / `app.cors.allowed-origins`) |
| OAuth2 | Optional Google OAuth — commented in `application.yml`; enable with env flags when needed |

---

## Running locally

```bash
cd user-service
./mvnw spring-boot:run
```

Set **`SERVER_PORT`** if **8081** clashes with branch-service. With the full stack, start infrastructure (MySQL, Redis, Kafka) or use **`foodchain-deployment`** Compose.

**Swagger (direct):** `http://localhost:<port>/api/swagger-ui.html`
