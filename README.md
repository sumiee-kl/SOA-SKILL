# Banking Platform – API Gateway Path Rewrite (Spring Boot + Spring Cloud Gateway)

A banking **Account Service** exposes only *internal* routes at `/api/v1/internal/accounts`
on an **isolated port**. A public **API Gateway** accepts clean URLs like `/accounts/**`,
**rewrites / strips** the path, and forwards to the backend. Tests and scripts prove the
backend receives the modified path.

```
                     PUBLIC (port 8080)                         ISOLATED (127.0.0.1:8081)
 Client ──► GET /accounts/ACC1001 ──► [ API Gateway ] ──► GET /api/v1/internal/accounts/ACC1001 ──► [ Account Service ]
                                      RewritePath
            GET /account-service/api/v1/internal/accounts ──► StripPrefix=1 ──► /api/v1/internal/accounts
            GET /api/v1/internal/**   ──► 403 Forbidden (internal paths never exposed at the edge)
```

## Tasks covered

| # | Task | Where |
|---|------|-------|
| 1 | **Build target service** – Account service exposing `/api/v1/internal/accounts` on an isolated port | `account-service/` – port `8081`, bound to `127.0.0.1` (`application.yml`) |
| 2 | **Apply StripPrefix / Rewrite** – gateway routes with `RewritePath` and `StripPrefix` filters | `api-gateway/src/main/resources/application.yml` |
| 3 | **Verify forwarding** – call `http://localhost:8080/accounts` and confirm backend gets the rewritten path | `GatewayRewriteTest.java`, `scripts/verify.sh` / `verify.ps1`, backend logs + `X-Received-Path` header |

## Project structure

```
banking-gateway-rewrite/
├── pom.xml                         # parent (Spring Boot 3.3.5, Spring Cloud 2023.0.3, Java 17)
├── account-service/                # internal backend, port 8081
│   └── src/main/java/com/bank/account/
│       ├── controller/AccountController.java   # @RequestMapping("/api/v1/internal/accounts")
│       ├── service/AccountService.java         # in-memory store (seeded ACC1001, ACC1002)
│       ├── model/…                             # Account, AccountType, CreateAccountRequest
│       └── filter/ReceivedPathFilter.java      # logs + echoes path backend received (X-Received-Path)
├── api-gateway/                    # public edge, port 8080
│   ├── src/main/resources/application.yml      # ROUTES: rewrite, strip-prefix, block-internal
│   └── src/main/java/com/bank/gateway/config/
│       ├── OriginalPathHeaderFilter.java       # adds X-Original-Path before rewriting
│       └── ForwardingLogFilter.java            # logs  incoming path ==> downstream URL
├── scripts/verify.sh | verify.ps1  # end-to-end forwarding check
├── Dockerfile, docker-compose.yml  # backend port NOT published in Docker
└── .github/workflows/ci.yml        # build + tests + live e2e check on every push
```

## Gateway routes (the core of the task)

```yaml
# ROUTE 1 — RewritePath:  /accounts/**  ->  /api/v1/internal/accounts/**
- id: accounts-rewrite
  uri: ${ACCOUNT_SERVICE_URL:http://localhost:8081}
  predicates:
    - Path=/accounts,/accounts/**
  filters:
    - RewritePath=/accounts(?<segment>/?.*), /api/v1/internal/accounts$\{segment}

# ROUTE 2 — StripPrefix:  /account-service/api/v1/internal/accounts/**  ->  /api/v1/internal/accounts/**
- id: accounts-strip-prefix
  uri: ${ACCOUNT_SERVICE_URL:http://localhost:8081}
  predicates:
    - Path=/account-service/api/v1/internal/accounts,/account-service/api/v1/internal/accounts/**
  filters:
    - StripPrefix=1

# ROUTE 3 — Guard: direct internal paths are forbidden at the edge
- id: block-direct-internal
  uri: no://op
  predicates:
    - Path=/api/v1/internal/**
  filters:
    - SetStatus=403
```

> `$\{segment}` is the YAML-safe form of `${segment}` (stops Spring treating it as a property placeholder).

| Client calls (gateway :8080) | Filter | Backend receives (:8081) |
|---|---|---|
| `GET /accounts` | RewritePath | `GET /api/v1/internal/accounts` |
| `GET /accounts/ACC1001?view=summary` | RewritePath | `GET /api/v1/internal/accounts/ACC1001?view=summary` |
| `POST /accounts` | RewritePath | `POST /api/v1/internal/accounts` |
| `GET /account-service/api/v1/internal/accounts` | StripPrefix=1 | `GET /api/v1/internal/accounts` |
| `GET /api/v1/internal/accounts` | blocked | – (403, never reaches backend) |

## Run it

**Prerequisites:** Java 17+, Maven 3.9+ (or Docker).

### Option A – Maven (two terminals)

```bash
mvn clean install                         # builds both modules + runs all tests

# terminal 1
mvn -pl account-service spring-boot:run   # http://127.0.0.1:8081

# terminal 2
mvn -pl api-gateway spring-boot:run       # http://localhost:8080
```

### Option B – Docker

```bash
docker compose up --build
```
Only port **8080** is published; the account service is reachable only inside the Docker network.

## Verify forwarding

```bash
./scripts/verify.sh                 # Linux / macOS / Git Bash
.\scripts\verify.ps1                # Windows PowerShell
```

Expected:
```
PASS  GET  /accounts                 -> rewrite  (status=200, backend received=/api/v1/internal/accounts)
PASS  GET  /accounts/ACC1001         -> rewrite  (status=200, backend received=/api/v1/internal/accounts/ACC1001)
PASS  POST /accounts                 -> rewrite  (status=201, backend received=/api/v1/internal/accounts)
PASS  GET  /account-service/api/v1/internal/accounts -> StripPrefix  (status=200, backend received=/api/v1/internal/accounts)
PASS  GET  /api/v1/internal/accounts -> blocked at edge  (status=403, backend received=n/a)
Passed: 5  Failed: 0
```

Manual check with curl:
```bash
curl -i http://localhost:8080/accounts
# HTTP/1.1 200 OK
# X-Received-Path: /api/v1/internal/accounts     <-- path the backend actually got
# X-Gateway: banking-api-gateway
```

Three independent pieces of evidence that the rewrite happened:

1. **Response header** `X-Received-Path` – set by the backend from the request URI it received.
2. **Gateway log** (`ForwardingLogFilter`):
   ```
   [accounts-rewrite] GET /accounts/ACC1001  ==>  http://localhost:8081/api/v1/internal/accounts/ACC1001
   ```
3. **Backend log** (`ReceivedPathFilter`):
   ```
   Backend received GET /api/v1/internal/accounts/ACC1001 | X-Forwarded-Prefix=null | X-Gateway-Route=accounts-rewrite | X-Original-Path=/accounts/ACC1001
   ```

Route table is also visible at `http://localhost:8080/actuator/gateway/routes`.

### Automated tests

```bash
mvn test
```
- `account-service` → `AccountControllerTest`: internal endpoints work, `/accounts` is **not** served by the backend directly.
- `api-gateway` → `GatewayRewriteTest`: starts a stub backend that records the exact path/headers it received and asserts rewrite, query-string preservation, POST, StripPrefix, 403 guard and 404 for unknown routes.

## Push to GitHub

```bash
cd banking-gateway-rewrite
git init
git add .
git commit -m "Account service + Spring Cloud Gateway with RewritePath/StripPrefix and forwarding verification"
git branch -M main
git remote add origin https://github.com/<your-username>/banking-gateway-rewrite.git
git push -u origin main
```
GitHub Actions (`.github/workflows/ci.yml`) then builds, runs the tests, starts both services and runs `verify.sh`.

## Config reference

| Variable | Default | Used by |
|---|---|---|
| `ACCOUNT_SERVICE_PORT` | `8081` | account-service |
| `ACCOUNT_SERVICE_BIND` | `127.0.0.1` | account-service (isolation) |
| `GATEWAY_PORT` | `8080` | api-gateway |
| `ACCOUNT_SERVICE_URL` | `http://localhost:8081` | api-gateway route target |
