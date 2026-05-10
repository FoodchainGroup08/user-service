# User Service

Handles authentication and user management for the FoodChain platform. Provides JWT-based login, registration, token refresh, password reset, Google OAuth2 sign-in, and role-based user administration.

---

## Table of Contents

- [Tech Stack](#tech-stack)
- [Running the Service](#running-the-service)
  - [Standalone with Docker](#standalone-with-docker)
  - [Local (Maven)](#local-maven)
- [Environment Variables](#environment-variables)
- [API Base URL](#api-base-url)
- [Authentication](#authentication)
- [Roles](#roles)
- [Auth Endpoints](#auth-endpoints)
  - [Register](#post-authregister)
  - [Login](#post-authlogin)
  - [Google Sign-In](#post-authgoogle)
  - [Refresh Token](#post-authrefresh)
  - [Logout](#post-authlogout)
  - [Forgot Password](#post-authforgot-password)
  - [Reset Password](#post-authreset-password)
  - [Get Current User](#get-authme)
- [User Endpoints](#user-endpoints)
  - [Get Current User](#get-usersme)
  - [List All Users](#get-users)
  - [Get User by ID](#get-usersid)
  - [Update User](#patch-usersid)
- [Error Responses](#error-responses)
- [Password Rules](#password-rules)
- [Swagger UI](#swagger-ui)

---

## Tech Stack

- **Java 17** / Spring Boot 3.2.3
- **Spring Security** — JWT filter chain, stateless sessions
- **Spring Data JPA** — MySQL 8 persistence
- **Spring Data Redis** — refresh token storage and token blacklist
- **Spring Cloud** — Eureka service discovery, Config Server (optional in standalone mode)
- **jjwt 0.12.6** — JWT generation and validation
- **Springdoc OpenAPI 2.3.0** — Swagger UI

---

## Running the Service

### Standalone with Docker

Spins up MySQL, Redis, and the service — no Eureka or Config Server required.

**Step 1 — build the JAR:**
```bash
cd user-service
mvn package -Dmaven.test.skip=true
```

**Step 2 — start the stack:**
```bash
docker compose -f docker-compose.dev.yml up --build
```

| URL | Description |
|-----|-------------|
| `http://localhost:8081/api/swagger-ui/index.html` | Swagger UI |
| `http://localhost:8081/api/actuator/health` | Health check |

Ports exposed on the host:

| Service | Host Port | Container Port |
|---------|-----------|----------------|
| user-service | 8081 | 8081 |
| MySQL | 3307 | 3306 |
| Redis | 6380 | 6379 |

**To stop:**
```bash
docker compose -f docker-compose.dev.yml down
```

**To stop and wipe data volumes:**
```bash
docker compose -f docker-compose.dev.yml down -v
```

---

### Local (Maven)

Requires MySQL and Redis already running locally.

```bash
cd user-service
mvn spring-boot:run
```

Or with overrides:
```bash
mvn spring-boot:run \
  -Dspring-boot.run.arguments="--DB_HOST=localhost --DB_NAME=user_db --DB_USERNAME=root --DB_PASSWORD=secret"
```

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | `localhost` | MySQL host |
| `DB_NAME` | `user_db` | MySQL database name |
| `DB_USERNAME` | `root` | MySQL username |
| `DB_PASSWORD` | `devpassword` | MySQL password |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | _(empty)_ | Redis password |
| `JWT_SECRET` | _(required, 32+ chars)_ | HMAC-SHA secret for signing JWTs |
| `JWT_ACCESS_EXPIRY_MS` | `900000` | Access token lifetime (ms) — default 15 min |
| `JWT_REFRESH_EXPIRY_MS` | `604800000` | Refresh token lifetime (ms) — default 7 days |
| `FRONTEND_URL` | `http://localhost:5173` | Used in password-reset email links |
| `OAUTH2_ENABLED` | `false` | Enable server-side Google OAuth2 flow |

---

## API Base URL

```
http://localhost:8081/api
```

All paths below are relative to this base.

---

## Authentication

Protected endpoints require a `Bearer` token in the `Authorization` header:

```
Authorization: Bearer <access_token>
```

The access token is returned by `/auth/login`, `/auth/register`, `/auth/google`, and `/auth/refresh`.

---

## Roles

| Role | Description |
|------|-------------|
| `CUSTOMER` | Default role. Can view and manage their own profile. |
| `KITCHEN_STAFF` | Kitchen operations staff. Must be assigned a branch. |
| `BRANCH_MANAGER` | Manages a specific branch. Must be assigned a branch. |
| `HEAD_OFFICE_ADMIN` | Full administrative access across all users and branches. |

---

## Auth Endpoints

### `POST /auth/register`

Creates a new user account. Returns the created user's profile.

**Request body:**

```json
{
  "name": "John Doe",
  "email": "john@example.com",
  "password": "Secure@123!",
  "role": "CUSTOMER",
  "branchId": null
}
```

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `name` | string | No | Full name |
| `email` | string | Yes | Must be a valid email address |
| `password` | string | Yes | See [Password Rules](#password-rules) |
| `role` | string | No | Defaults to `CUSTOMER`. One of: `CUSTOMER`, `KITCHEN_STAFF`, `BRANCH_MANAGER`, `HEAD_OFFICE_ADMIN` |
| `branchId` | UUID | No | Required for `BRANCH_MANAGER` and `KITCHEN_STAFF`. Leave `null` for `CUSTOMER` |

**Response `201 Created`:**

```json
{
  "id": "7264-58d3-...",
  "name": "John Doe",
  "email": "john@example.com",
  "role": "CUSTOMER",
  "branchId": null,
  "isActive": true,
  "createdAt": "2026-05-10T04:30:00Z"
}
```

**Error responses:**

| Code | Reason |
|------|--------|
| `400` | Validation failed (invalid email, weak password, email already registered) |

---

### `POST /auth/login`

Authenticates a user and returns a token pair. This endpoint is intercepted by the JWT filter — it does not reach the controller method.

**Request body:**

```json
{
  "email": "john@example.com",
  "password": "Secure@123!"
}
```

**Response `200 OK`:**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": "7264-58d3-...",
    "name": "John Doe",
    "email": "john@example.com",
    "role": "CUSTOMER",
    "branchId": null,
    "isActive": true,
    "createdAt": "2026-05-10T04:30:00Z"
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `accessToken` | string | Short-lived JWT. Include in `Authorization: Bearer <token>` header |
| `refreshToken` | string | Long-lived token. Use with `/auth/refresh` to get a new pair |
| `tokenType` | string | Always `"Bearer"` |
| `expiresIn` | number | Access token lifetime in **seconds** (default 900 = 15 min) |
| `user` | object | The authenticated user's profile |

**Error responses:**

| Code | Reason |
|------|--------|
| `401` | Invalid email or password |

---

### `POST /auth/google`

Authenticates using a Google ID token obtained from Google Identity Services on the client side. Creates an account automatically if the email is not yet registered.

**Request body:**

```json
{
  "credential": "eyJhbGciOiJSUzI1NiIsImtpZCI6Ii4uLiJ9..."
}
```

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `credential` | string | Yes | JWT ID token from `window.google.accounts.id.initialize` callback |

**Response `200 OK`:** Same shape as [login response](#post-authlogin).

**Error responses:**

| Code | Reason |
|------|--------|
| `400` | Invalid or expired Google credential |

---

### `POST /auth/refresh`

Exchanges a valid refresh token for a new access token and refresh token. The old refresh token is deleted (rotation).

**Request body:**

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**Response `200 OK`:** Same shape as [login response](#post-authlogin) with a new token pair.

**Error responses:**

| Code | Reason |
|------|--------|
| `400` | Refresh token is missing or blank |
| `401` | Refresh token is expired, invalid, or already rotated |
| `404` | The user associated with the token no longer exists |

---

### `POST /auth/logout`

Invalidates the access token (adds the JTI to the blacklist) and deletes the refresh token from Redis.

Both the `Authorization` header and the request body are optional — provide what you have.

**Headers (optional):**
```
Authorization: Bearer <access_token>
```

**Request body (optional):**

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**Response `204 No Content`** — no body.

---

### `POST /auth/forgot-password`

Sends a one-time password-reset link to the given email address. Always returns `200` regardless of whether the email is registered (prevents user enumeration).

**Request body:**

```json
{
  "email": "john@example.com"
}
```

**Response `200 OK`:**

```json
{
  "message": "If that email is registered, a reset link has been sent."
}
```

---

### `POST /auth/reset-password`

Sets a new password using the one-time token from the reset link.

**Request body:**

```json
{
  "token": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "newPassword": "NewSecure@456!"
}
```

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `token` | string | Yes | One-time token received via email |
| `newPassword` | string | Yes | Must satisfy [Password Rules](#password-rules) |

**Response `200 OK`:**

```json
{
  "message": "Password updated successfully."
}
```

**Error responses:**

| Code | Reason |
|------|--------|
| `400` | Token is invalid, expired, or new password is too weak |

---

### `GET /auth/me`

Returns the currently authenticated user's profile. Used for session rehydration on page load.

**Headers:**
```
Authorization: Bearer <access_token>
```

**Response `200 OK`:**

```json
{
  "id": "7264-58d3-...",
  "name": "John Doe",
  "email": "john@example.com",
  "role": "CUSTOMER",
  "branchId": null,
  "isActive": true,
  "createdAt": "2026-05-10T04:30:00Z"
}
```

**Error responses:**

| Code | Reason |
|------|--------|
| `401` | Missing or invalid token |

---

## User Endpoints

### `GET /users/me`

Returns the profile of the currently authenticated user.

**Headers:**
```
Authorization: Bearer <access_token>
```

**Response `200 OK`:** See [UserResponse](#get-authme) shape above.

**Error responses:**

| Code | Reason |
|------|--------|
| `401` | Missing or invalid token |

---

### `GET /users`

Returns all users. **Requires `HEAD_OFFICE_ADMIN` role.**

**Headers:**
```
Authorization: Bearer <access_token>
```

**Response `200 OK`:**

```json
[
  {
    "id": "7264-58d3-...",
    "name": "John Doe",
    "email": "john@example.com",
    "role": "CUSTOMER",
    "branchId": null,
    "isActive": true,
    "createdAt": "2026-05-10T04:30:00Z"
  }
]
```

**Error responses:**

| Code | Reason |
|------|--------|
| `401` | Missing or invalid token |
| `403` | Authenticated but not `HEAD_OFFICE_ADMIN` |

---

### `GET /users/{id}`

Returns a single user's profile by UUID. **Requires `HEAD_OFFICE_ADMIN` or `BRANCH_MANAGER` role.**

**Headers:**
```
Authorization: Bearer <access_token>
```

**Path parameter:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | The user's unique identifier |

**Response `200 OK`:** See [UserResponse](#get-authme) shape above.

**Error responses:**

| Code | Reason |
|------|--------|
| `401` | Missing or invalid token |
| `403` | Insufficient role |
| `404` | User not found |

---

### `PATCH /users/{id}`

Partially updates a user. All fields are optional — only provided fields are changed. **Requires `HEAD_OFFICE_ADMIN` or `BRANCH_MANAGER` role.**

**Headers:**
```
Authorization: Bearer <access_token>
```

**Path parameter:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | The user's unique identifier |

**Request body** (all fields optional):

```json
{
  "role": "BRANCH_MANAGER",
  "branchId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "isActive": true
}
```

| Field | Type | Notes |
|-------|------|-------|
| `role` | string | One of: `CUSTOMER`, `KITCHEN_STAFF`, `BRANCH_MANAGER`, `HEAD_OFFICE_ADMIN` |
| `branchId` | UUID \| null | Set to `null` to remove branch association |
| `isActive` | boolean | Set to `false` to deactivate the account |

**Response `200 OK`:** Updated user profile (see [UserResponse](#get-authme) shape).

**Error responses:**

| Code | Reason |
|------|--------|
| `401` | Missing or invalid token |
| `403` | Insufficient role |
| `404` | User not found |

---

## Error Responses

All errors return a consistent JSON body:

```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Authentication required — provide a valid Bearer token",
  "path": "/api/users",
  "timestamp": "2026-05-10T04:41:48Z"
}
```

For validation errors (`400`), a `fields` map is also included:

```json
{
  "status": 400,
  "error": "Validation Failed",
  "message": "One or more fields are invalid",
  "path": "/api/auth/register",
  "timestamp": "2026-05-10T04:41:48Z",
  "fields": {
    "password": "Password must be at least 8 characters and include at least one uppercase letter, one lowercase letter, one digit, and one special character (@#$!%*?&-_+=)",
    "email": "Invalid email format"
  }
}
```

| Status | Meaning |
|--------|---------|
| `400` | Bad request — validation failed or duplicate email |
| `401` | Unauthenticated — missing, expired, or invalid token |
| `403` | Forbidden — authenticated but insufficient role |
| `404` | Resource not found |
| `500` | Unexpected server error |

---

## Password Rules

Passwords must satisfy all of the following:

| Rule | Requirement |
|------|-------------|
| Minimum length | 8 characters |
| Uppercase letter | At least one (`A–Z`) |
| Lowercase letter | At least one (`a–z`) |
| Digit | At least one (`0–9`) |
| Special character | At least one from `@ # $ ! % * ? & - _ + =` |

**Valid example:** `Secure@123!`

**Invalid examples:**

| Password | Reason |
|----------|--------|
| `password` | No uppercase, digit, or special character |
| `Password1` | No special character |
| `SECURE@1` | No lowercase letter |
| `Secure@!` | No digit |
| `Se@1` | Too short |

---

## Swagger UI

Interactive API explorer available at:

```
http://localhost:8081/api/swagger-ui/index.html
```

To test protected endpoints:
1. Call `POST /auth/login` or `POST /auth/register` using **Try it out**
2. Copy the `accessToken` from the response
3. Click the **Authorize** button (top right)
4. Paste the token (without the `Bearer ` prefix) and click **Authorize**
5. All subsequent requests will include the token automatically
