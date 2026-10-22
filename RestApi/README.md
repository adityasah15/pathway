# Job Application Management System with Placement Analytics

Mini-project for approved IP topic **#57: Job Application Management System**. A Spring Boot REST API
with JWT authentication, MySQL storage managed by Flyway, and a Python/Pandas analytics service that
powers an interactive placement dashboard.

## Architecture

```text
Browser (vanilla SPA)
  │  /auth/login (public)
  │  /api/**      (Bearer JWT)
  ▼
Spring Boot :8080 ──────────────► MySQL :3306
  │  Flyway migrations              ▲
  │                                 │ SQL
  └─ /api/analytics/* ─► Flask + Pandas :5000
```

- **Spring Boot** owns the domain data, authentication, validation, status transitions, CSV proxy, and
  serves the SPA.
- **Flask/Pandas** reads MySQL directly, cleans and aggregates the data, and returns chart-ready JSON.
- **MySQL** is the single source of truth. Flyway migrations create and evolve the schema.

## Features

- JWT authentication with two roles: `ADMIN` (full CRUD) and `VIEWER` (read-only).
- Student, company, job-opening, and application CRUD.
- One application per student/job pair, CGPA eligibility, and deadline checks.
- Enforced application status workflow with a full status-history table.
- Dashboard KPIs: applications, interviews reached, offers, students, placement rate, median package.
- Application funnel, status distribution, department outcomes, top recruiters, and monthly volume.
- Dashboard filters by department, graduation year, and status, plus CSV export.
- Demo dataset seeding that tops the database up to 24 students / 15 companies / 22 jobs / 86 applications, including scripted histories and edge-case openings.
- Structured JSON errors, Flyway migrations, Actuator health, Docker health checks, and CI.

## Tech stack

| Layer | Technology |
| --- | --- |
| API | Java 17, Spring Boot 3, Spring Security, Spring Data JPA |
| Database | MySQL 8.4, Flyway |
| Analytics | Python 3.12, Flask, Pandas, PyMySQL |
| Frontend | HTML, CSS, vanilla JavaScript, Chart.js |
| Operations | Docker, Docker Compose, GitHub Actions |

## Quick start with Docker

```bash
cd RestApi
cp .env.example .env
# Edit .env: DB_PASSWORD, JWT_SECRET, ADMIN_PASSWORD, VIEWER_PASSWORD
docker compose up --build
```

Open <http://localhost:8080> and sign in. The API is healthy when:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

## Run locally without Docker

Prerequisites: JDK 17, MySQL 8, Python 3.12.

1. Start MySQL. The application creates the `placement_analytics` database if it does not exist.
2. Start the analytics service:

   ```bash
   cd RestApi
   python3 -m venv .venv
   source .venv/bin/activate
   pip install -r python_etl/requirements.txt
   python python_etl/analytics_app.py        # http://localhost:5000
   ```

3. Start Spring Boot (second terminal):

   ```bash
   cd RestApi
   SPRING_PROFILES_ACTIVE=demo \
   ANALYTICS_SERVICE_URL=http://localhost:5000 \
   ./mvnw spring-boot:run
   ```

   Flyway applies the migrations on startup. Use `SPRING_PROFILES_ACTIVE=demo` to seed demo data;
   omit it to run with only your existing records.

## Demo accounts

| Username | Password | Role |
| --- | --- | --- |
| `admin` | `ChangeMe123!` | `ADMIN` |
| `viewer` | `viewer123` | `VIEWER` |

These are development defaults. Override `ADMIN_USERNAME`, `ADMIN_PASSWORD`, `VIEWER_USERNAME`, and
`VIEWER_PASSWORD` before deploying anywhere. Public registration is disabled and creating users
requires an `ADMIN` token.

## Demo data

The `demo` profile starts `DemoDataSeeder` (disable with `APP_DEMO_SEED=false`). It is insert-only and
idempotent: it tops the database up to the target counts and never deletes or rewrites existing rows.

| Entity | Target |
| --- | --- |
| Students | 24 |
| Companies | 15 |
| Job openings | 22 |
| Applications | 86 |

Six applications carry scripted, multi-step histories: rejected after interview, withdrawn after offer,
accepted end-to-end, stuck at `Applied`, offered pending a decision, and an upcoming interview. Four
edge-case openings live under **Atlas Manufacturing** — two expired, one closing today, and one just
posted with no applicants. The seed also contains a null email, a null CGPA, and a whitespace/case
department for the pandas cleaning path.

Existing records, including the original 37 applications, are preserved. A second startup with the
same profile leaves the counts unchanged. On an older 24/14/18/80 database the seeder adds the
scripted records and edge-case openings without rewriting existing rows.

## Configuration

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | `jdbc:mysql://localhost:3306/placement_analytics?...` | JDBC connection URL |
| `DB_USERNAME` | `root` | Database user |
| `DB_PASSWORD` | empty | Database password |
| `JWT_SECRET` | dev-only fallback | HMAC signing key; must be at least 32 bytes |
| `JWT_EXPIRATION_MS` | `3600000` | Token lifetime in milliseconds |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | `admin` / `ChangeMe123!` | Seeded admin account |
| `VIEWER_USERNAME` / `VIEWER_PASSWORD` | `viewer` / `viewer123` | Seeded viewer account |
| `ANALYTICS_SERVICE_URL` | `http://localhost:5000` | Flask service used by the CSV proxy |
| `SPRING_PROFILES_ACTIVE` | none | Set to `demo` to enable demo seeding |
| `APP_DEMO_SEED` | `true` in the demo profile | Set to `false` to skip seeding |

Generate a secret with `openssl rand -base64 48`.

## API

All `/api/**` routes require `Authorization: Bearer <token>`. `GET` is allowed for both roles;
`POST`, `PUT`, and `DELETE` require `ADMIN`.

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/auth/login` | Public login, returns token, role, and expiry |
| `POST` | `/auth/register` | Create a user (`ADMIN` only) |
| `GET` | `/api/meta` | Statuses, job types, and allowed status transitions |
| `GET` | `/api/students` | List students |
| `GET` | `/api/students/{id}` | Get one student |
| `POST` | `/api/students` | Create a student |
| `PUT` | `/api/students/{id}` | Update a student |
| `DELETE` | `/api/students/{id}` | Delete a student |
| `GET` / `POST` / `PUT` / `DELETE` | `/api/companies[/{id}]` | Company CRUD |
| `GET` / `POST` / `PUT` / `DELETE` | `/api/jobs[/{id}]` | Job-opening CRUD |
| `GET` | `/api/applications?studentId=` | List applications, optionally by student |
| `GET` | `/api/applications/{id}` | Get one application |
| `GET` | `/api/applications/{id}/history` | Ordered status history for one application |
| `POST` | `/api/applications` | Create an application |
| `PUT` | `/api/applications/{id}` | Update an application |
| `PATCH` | `/api/applications/{id}/status` | Move an application to a new status |
| `DELETE` | `/api/applications/{id}` | Delete an application |
| `GET` | `/api/analytics/summary` | Live entity counts |
| `GET` | `/api/analytics/dashboard` | Filtered dashboard payload |
| `GET` | `/api/analytics/export.csv` | Cleaned application CSV download |
| `GET` | `/actuator/health` | Public health check |

Dashboard filters: `?department=Information%20Technology&graduationYear=2027&status=Offered`.

Errors use one shape:

```json
{
  "timestamp": "2026-10-22T15:00:00Z",
  "status": 409,
  "code": "CONFLICT",
  "message": "This student already applied for this job",
  "path": "/api/applications",
  "fieldErrors": {}
}
```

## Application status workflow

```text
Applied ──► Shortlisted ──► Interview ──► Offered ──► Accepted
   │             │              │            │
   └─────────────┴──────────────┴────────────┴──────► Rejected
   └─────────────┴──────────────┴────────────┴──────► Withdrawn
```

`Accepted`, `Rejected`, and `Withdrawn` are terminal. Status changes are validated server-side and
written to `application_status_history` with the acting user and timestamp. The frontend reads the
allowed transitions from `/api/meta`, so closed applications cannot be moved in the UI.

Interview dates are only accepted from `Shortlisted` onward, and offer packages only for `Offered` or
`Accepted` applications.

## Database migrations

Schema changes live in `src/main/resources/db/migration` and are applied by Flyway. Never edit a
migration that has already run; add the next `V5__*.sql` file instead. Existing databases are
baselined at `V1`, so the migrations are additive for current installations.

The analytics service queries the same tables but does not own the schema.

## Tests and CI

```bash
cd RestApi
./mvnw test
python3 -m unittest discover -s python_etl -v
```

- Spring tests run against in-memory H2 in MySQL mode; Flyway is disabled for the test profile.
- Python tests cover cleaning, filters, history-aware funnel/KPIs, HTTP errors, and CSV export.
- GitHub Actions runs both suites on every push and pull request.

## Project structure

```text
RestApi/
├── src/main/java/com/tcet/RestApi/   # controllers, services, repositories, security, config
├── src/main/resources/
│   ├── db/migration/                 # Flyway SQL
│   └── static/                       # SPA, self-hosted fonts, Chart.js
├── src/test/                         # Spring tests
├── python_etl/                       # Flask + Pandas analytics and tests
├── Dockerfile
├── docker-compose.yml
└── .env.example
```

## Notes and limitations

- Tokens are stored in `sessionStorage`; there is no refresh-token flow or revocation list.
- List endpoints return full tables; there is no pagination yet.
- The analytics service runs two Gunicorn workers and holds no query cache.
- The demo seed data is deterministic and for development only.
