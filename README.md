<p align="center">
  <img src="docs/assets/logo.svg" width="72" height="72" alt="Pathway logo">
</p>

<h1 align="center">Pathway</h1>

<p align="center">
  <strong>Job Application Management System with Placement Analytics.</strong><br>
  Track every application, enforce the recruitment workflow, and turn placement data into decisions — in one workspace.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/tests-38%20passing-10b981?style=flat-square" alt="38 tests passing">
  &nbsp;
  <a href="#placement-analytics"><img src="https://img.shields.io/badge/analytics-5%20dashboard%20views-6558D9?style=flat-square" alt="5 dashboard views"></a>
  &nbsp;
  <a href="#api"><img src="https://img.shields.io/badge/API-reference-6558D9?style=flat-square" alt="API reference"></a>
  &nbsp;
  <img src="https://img.shields.io/badge/demo%20seed-24%C2%B714%C2%B718%C2%B780-0ea5e9?style=flat-square" alt="Demo seed: 24 students, 14 companies, 18 jobs, 80 applications">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java%2017-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java 17">
  &nbsp;
  <img src="https://img.shields.io/badge/Spring%20Boot%203.5-E0234E?style=flat-square&logo=springboot&logoColor=white" alt="Spring Boot 3.5">
  &nbsp;
  <img src="https://img.shields.io/badge/MySQL%208.4-4169E1?style=flat-square&logo=mysql&logoColor=white" alt="MySQL 8.4">
  &nbsp;
  <img src="https://img.shields.io/badge/Flyway-CC0200?style=flat-square&logo=flyway&logoColor=white" alt="Flyway">
  &nbsp;
  <img src="https://img.shields.io/badge/Python%203.12-3776AB?style=flat-square&logo=python&logoColor=white" alt="Python 3.12">
  &nbsp;
  <img src="https://img.shields.io/badge/Flask-000000?style=flat-square&logo=flask&logoColor=white" alt="Flask">
  &nbsp;
  <img src="https://img.shields.io/badge/Pandas-150458?style=flat-square&logo=pandas&logoColor=white" alt="Pandas">
  &nbsp;
  <img src="https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white" alt="Docker">
  &nbsp;
  <img src="https://img.shields.io/badge/GitHub%20Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white" alt="GitHub Actions">
</p>

<p align="center">
  <a href="#what-is-pathway">Overview</a> ·
  <a href="#features">Features</a> ·
  <a href="#placement-analytics">Analytics</a> ·
  <a href="#architecture">Architecture</a> ·
  <a href="#application-status-workflow">Workflow</a> ·
  <a href="#getting-started">Getting started</a> ·
  <a href="#demo-data">Demo data</a> ·
  <a href="#api">API</a> ·
  <a href="#documentation">Documentation</a>
</p>

---

## What is Pathway?

Pathway is a placement workspace for a college training and placement cell. It keeps students, companies, job openings, applications, and full status history in one system — then reads the same live MySQL data through a separate Python/Pandas analytics service to power an interactive placement dashboard.

Two roles use it: **`ADMIN`** manages records and moves applications through the hiring workflow; **`VIEWER`** gets read-only access to the tracker, dashboard, and CSV export.

**How an application moves**

1. **Record** — add a student, company, and opening, then file an application. The API checks the deadline, the student's CGPA against the job minimum, and that the student hasn't already applied to the same job.
2. **Progress** — move through `Applied → Shortlisted → Interview → Offered → Accepted`, or close the application as `Rejected` or `Withdrawn`. Illegal transitions are rejected server-side with a `409`.
3. **Audit** — every status change is written to `application_status_history` with the acting user, timestamp, and note.
4. **Analyze** — Pandas normalizes and deduplicates the records, then builds the KPIs, funnel, department outcomes, and charts. The funnel is history-aware: an application rejected after an interview still counts as having reached that stage.

## Features

**Placement workspace**

- **Four connected records** — students, companies, job openings, and applications, each with create, edit, delete, and searchable table views.
- **Eligibility at the door** — CGPA minimums, application deadlines, and a unique student/job pair are enforced before an application is saved.
- **One record, full context** — status, interview date, offer package, and notes live in a single record; every transition is retained in `application_status_history`.

**Guarded workflow**

- **Server-side state machine** — only legal next statuses are accepted; the UI reads the allowed transitions from `/api/meta` so closed applications cannot be moved.
- **Stage-aware details** — interview dates are only valid from `Shortlisted` onward, and offer packages only for `Offered` and `Accepted`.
- **Audit trail** — every transition records who changed it, when, and a note describing the change.

**Placement analytics**

- **Six live KPIs** — applications, students, interviews reached, offers, placement rate, and median package.
- **Five dashboard views** — status distribution, application funnel, department outcomes, offers by company, and monthly application volume.
- **Explore and export** — filter by department, graduation year, and status; click chart segments to filter; download a cleaned CSV.
- **History-aware funnel** — progress is not lost when a candidate is later rejected or withdraws.

**Access and operations**

- **Two roles** — `ADMIN` writes; `VIEWER` reads. Public self-registration is disabled and creating users requires an admin token.
- **JWT security** — BCrypt passwords, signed tokens, stateless sessions, and consistent JSON `401`/`403` responses.
- **One error shape** — validation, conflict, and not-found errors all return the same `ApiError` envelope.
- **Demo-ready** — deterministic, insert-only seed data and a Docker Compose stack with health checks for every service.

## Placement analytics

The dashboard is not a static report. On every request, Flask reads MySQL directly, loads the records into pandas, cleans and aggregates them, and returns chart-ready JSON. Spring Boot proxies the result so the browser only talks to one authenticated origin.

```text
MySQL 8.4
   │  students LEFT JOIN applications → job_openings → companies
   │  application_status_history
   ▼
Flask :5000 · PyMySQL
   │  rows → pandas DataFrames
   ▼
pandas cleaning
   │  trim text · normalize statuses · dedupe · coerce dates and numbers
   ▼
aggregation
   │  KPIs · funnel · status distribution · departments · companies · monthly volume
   ▼
JSON  ──►  Spring Boot /api/analytics/*  ──►  Chart.js dashboard
```

Implementation: [`analytics.py`](RestApi/python_etl/analytics.py) for cleaning and aggregation, and [`analytics_app.py`](RestApi/python_etl/analytics_app.py) for the MySQL queries and Flask endpoints.

**What the dashboard computes**

| KPI card          | Definition                                                                                              |
| ----------------- | ------------------------------------------------------------------------------------------------------- |
| Applications      | Filtered application rows after deduplication                                                           |
| Reached interview | Applications whose furthest stage is `Interview`, `Offered`, or `Accepted`                              |
| Offers received   | Applications whose furthest stage is `Offered` or `Accepted`                                            |
| Students          | Distinct students matching the department/year filters, including students with no applications         |
| Placement rate    | Accepted students ÷ all students in the filtered set                                                    |
| Median package    | Median `offer_package_lpa` across applications currently `Offered` or `Accepted`                        |

**Five dashboard views**

| View                   | Chart          | What it answers                                                          |
| ---------------------- | -------------- | ------------------------------------------------------------------------ |
| Status distribution    | Doughnut       | Where are applications sitting right now?                                |
| Application funnel     | Horizontal bar | How many applications reached each stage?                                |
| Department outcomes    | Grouped bar    | Which departments apply most, and which convert to offers?               |
| Offers by company      | Bar            | Which companies extend the most offers?                                  |
| Applications over time | Line           | How is application volume trending by month?                             |

The payload also exposes `highestPackage` and `topRecruiter` for API consumers; the current UI surfaces the median package and the company chart.

**The funnel is history-aware.** A current status alone would undercount progress: an application that reached `Interview` and was later `Rejected` is still a record that reached the interview stage. The analytics service loads `application_status_history`, computes each application's furthest stage, and uses that rank for the funnel and the interviews/offers KPIs. The status distribution, by contrast, shows the current status.

**Filters and export**

- `department` and `graduationYear` filter both the student and application frames, so the placement-rate denominator follows the selection.
- `status` filters application rows.
- Clicking a doughnut slice or a department bar applies the matching filter in the UI.
- `/api/analytics/export.csv` returns the cleaned and deduplicated application table as `applications.csv`. The export is not narrowed by the current dashboard filters.

**Data-quality rules**

- Text fields are trimmed and whitespace-collapsed; department names are title-cased, while department codes of four characters or fewer stay uppercase.
- Statuses are matched case-insensitively; unknown values become `Unknown`.
- Applications are deduplicated by `application_id`; students by `student_id`.
- Dates and numeric fields are coerced; invalid values become null rather than breaking the dashboard.
- The query starts from `students` with a `LEFT JOIN`, so students without applications still count in the student total and placement-rate denominator.

**Analytics endpoints**

| Method | Path                        | Purpose                                                                 |
| ------ | --------------------------- | ----------------------------------------------------------------------- |
| `GET`  | `/api/analytics/summary`    | Current-status counts from Spring Data JPA (lightweight, not history-aware) |
| `GET`  | `/api/analytics/dashboard`  | Filtered, history-aware dashboard payload proxied from Flask            |
| `GET`  | `/api/analytics/export.csv` | Cleaned application CSV proxied from Flask                              |
| `GET`  | `:5000/dashboard`           | Internal Flask endpoint; not published by Compose                       |
| `GET`  | `:5000/export.csv`          | Internal Flask CSV endpoint                                             |
| `GET`  | `:5000/health`              | Database-aware Flask health check                                       |

Dashboard query example:

```text
/api/analytics/dashboard?department=Information%20Technology&graduationYear=2027&status=Offered
```

The dataset is small, so the dashboard recomputes on every request instead of maintaining a cache. That keeps the numbers current at the cost of repeated reads.

## Architecture

Spring Boot owns the write path and serves the SPA. MySQL is the only source of truth, Flyway owns the schema, and the Flask/Pandas service reads the same tables to build chart-ready JSON. The analytics path is deliberately read-only and separate from the write path.

```text
┌──────────────────────────────────────────────────────────┐
│                Pathway SPA · vanilla JS + Chart.js       │
│      login · dashboard · applications · students · ...   │
└──────────────────────────┬───────────────────────────────┘
                           │ HTTP · Bearer JWT
┌──────────────────────────▼───────────────────────────────┐
│                Spring Boot 3.5 API · :8080               │
│   auth · students · companies · jobs · applications      │
│   status workflow · history · analytics proxy · CSV      │
└────────────┬─────────────────────────────┬───────────────┘
             │ JDBC                        │ HTTP
      ┌──────▼───────┐              ┌──────▼──────────────┐
      │  MySQL 8.4   │◄─────────────│  Flask + Pandas     │
      │ source of    │     SQL      │  analytics · :5000  │
      │ truth        │              │  dashboard + CSV    │
      └──────────────┘              └─────────────────────┘
```

**Key decisions**

- MySQL is the single source of truth; the analytics service never writes and does not own the schema.
- Workflow policy lives in the enum and service layer, not in the browser or database triggers.
- Flyway migrations are the only way the schema changes; Hibernate validates rather than generates.
- Spring proxies dashboard and CSV requests so the SPA keeps one authenticated origin and no CORS layer is needed.
- The SPA uses no framework or build step; fonts and Chart.js are self-hosted, so the UI has no external CDN dependency.
- Demo seeding is opt-in by profile and insert-only, so it is safe to run against an existing database.

## Application status workflow

```text
Applied ──► Shortlisted ──► Interview ──► Offered ──► Accepted
   │             │              │            │
   └─────────────┴──────────────┴────────────┴──────► Rejected
   └─────────────┴──────────────┴────────────┴──────► Withdrawn
```

`Accepted`, `Rejected`, and `Withdrawn` are terminal. Status changes are validated server-side and written to `application_status_history` with the acting user, timestamp, and note. The frontend reads the allowed transitions from `/api/meta`, so closed applications cannot be moved in the UI.

Interview dates are only accepted from `Shortlisted` onward, and offer packages only for `Offered` or `Accepted` applications.

## Engineering highlights

- **Workflow as code** — `ApplicationStatus` owns the transition graph and `ApplicationService` validates every move; invalid transitions fail with `409` even if a client bypasses the UI.
- **History-aware analytics** — Flask loads `application_status_history` alongside applications, computes each record's furthest stage, and uses that for the funnel and the interviews/offers KPIs.
- **Eligibility at the boundary** — deadline and CGPA checks run in the service layer; unique keys (Flyway V1) and check constraints (V3) back them up in MySQL.
- **Migration safety** — Flyway baselines existing databases at V1 and applies V2–V4 additively; Hibernate runs with `ddl-auto=validate`, so schema drift fails fast.
- **A read/write split that fits the scope** — Spring Boot owns writes, auth, validation, and the CSV proxy; the Flask/Pandas service is read-only over the same database. No duplicated business rules.
- **Deterministic demo data** — a fixed random seed (`20261022`) and top-up logic produce the same 24/14/18/80 dataset on a fresh database; reruns never duplicate or delete rows.
- **Production-shaped Docker** — multi-stage API image, non-root runtime user, health checks for MySQL, Flask, and the API, and a Gunicorn analytics container.
- **Accessible, dependency-light SPA** — no frontend build step; self-hosted fonts and Chart.js, hash routing, a skip link, landmarks, labelled tables, visible focus states, and reduced-motion support.

## Tech stack

| Layer          | Technology                                                                 |
| -------------- | -------------------------------------------------------------------------- |
| API            | Java 17 · Spring Boot 3.5 · Spring Security · Spring Data JPA · JJWT       |
| Database       | MySQL 8.4 · Flyway (V1–V4)                                                 |
| Analytics      | Python 3.12 · Flask · Pandas · PyMySQL · Gunicorn                          |
| Frontend       | HTML · CSS · vanilla JavaScript · Chart.js (self-hosted)                   |
| Authentication | JWT bearer tokens · BCrypt · `ADMIN` / `VIEWER` roles                      |
| Operations     | Docker · Docker Compose · GitHub Actions · Spring Actuator · health checks |

## Repository layout

```text
Job_Application_Management_System/
├── RestApi/                          # Spring Boot API + SPA + analytics service
│   ├── src/main/java/com/tcet/RestApi/
│   │   ├── controller/               # auth, students, companies, jobs, applications,
│   │   │                             # meta, analytics
│   │   ├── service/                  # application workflow, job openings, users
│   │   ├── repositories/             # Spring Data JPA repositories
│   │   ├── model/                    # entities + ApplicationStatus state machine
│   │   ├── security/                 # JWT filter, JWT service, security config
│   │   ├── config/                   # admin/demo seeders, analytics client
│   │   ├── dto/                      # request and response records
│   │   └── web/                      # ApiError + global exception handler
│   ├── src/main/resources/
│   │   ├── db/migration/             # Flyway V1–V4
│   │   └── static/                   # Pathway SPA, CSS, fonts, Chart.js
│   ├── src/test/                     # Spring controller, security, context tests
│   ├── python_etl/
│   │   ├── analytics.py              # cleaning, dedupe, KPIs, funnel, chart data
│   │   ├── analytics_app.py          # MySQL queries + Flask endpoints
│   │   ├── test_analytics.py         # 10 Python tests
│   │   ├── requirements.txt
│   │   └── Dockerfile                # Gunicorn analytics image
│   ├── sql/schema.sql                # manual database creation helper
│   ├── Dockerfile                    # production API image
│   ├── docker-compose.yml            # mysql + analytics + app
│   ├── .env.example                  # Docker Compose settings template
│   └── README.md                     # full setup, config, API, workflow details
├── docs/
│   ├── assets/logo.svg               # README mark
│   └── implementation-plan.md        # phase record, tags, verification, deviations
├── .github/workflows/ci.yml          # Java tests + Python tests on push/PR
└── README.md
```

## Demo data

The repository ships with a deterministic demo profile, enabled by default in `docker-compose.yml` (`SPRING_PROFILES_ACTIVE=demo`, `APP_DEMO_SEED=true`). The profile starts `DemoDataSeeder`; set `APP_DEMO_SEED=false` to disable it.

| Role       | Username | Password       |
| ---------- | -------- | -------------- |
| `ADMIN`    | `admin`  | `ChangeMe123!` |
| `VIEWER`   | `viewer` | `viewer123`    |

These are **development defaults**. Override `ADMIN_USERNAME`, `ADMIN_PASSWORD`, `VIEWER_USERNAME`, and `VIEWER_PASSWORD` in `.env` before exposing the app anywhere.

The seeder is insert-only and idempotent: it tops the database up to the target counts and never rewrites or deletes existing rows. Existing records, including the original 37 applications, are preserved.

| Entity       | Target |
| ------------ | ------ |
| Students     | 24     |
| Companies    | 14     |
| Job openings | 18     |
| Applications | 80     |

On a fresh database the verified demo run produces an application funnel of `80 → 45 → 32 → 17 → 7` and a `20.8%` placement rate. A second startup with the same profile leaves the counts unchanged.

To reset the demo database from scratch, remove the Compose volume first — **this deletes all local data**:

```bash
cd RestApi
docker compose down -v
docker compose up --build
```

## Getting started

**Prerequisites:** Docker, or JDK 17 + MySQL 8 + Python 3.12 for the no-Docker path.

**1. Start the full stack**

```bash
cd RestApi
cp .env.example .env
# Edit .env: DB_PASSWORD, JWT_SECRET, ADMIN_PASSWORD, VIEWER_PASSWORD
docker compose up --build
```

Open <http://localhost:8080> and sign in with a demo account. The API is healthy when:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

Compose starts MySQL, the Flask/Pandas analytics service, and the Spring Boot app, each with its own health check. Only the app publishes a host port (`8080`); MySQL and Flask stay on the internal Compose network. The MySQL data lives in the `mysql_data` volume.

**2. Or run it locally without Docker**

Start MySQL first. The application creates the `placement_analytics` database on first connection when the user has permission.

Terminal 1 — analytics service on <http://localhost:5000>:

```bash
cd RestApi
python3 -m venv .venv
source .venv/bin/activate
pip install -r python_etl/requirements.txt
python python_etl/analytics_app.py
```

Terminal 2 — Spring Boot on <http://localhost:8080>:

```bash
cd RestApi
export JWT_SECRET="$(openssl rand -base64 48)"
SPRING_PROFILES_ACTIVE=demo \
ANALYTICS_SERVICE_URL=http://localhost:5000 \
./mvnw spring-boot:run
```

Flyway applies the migrations on startup. Use `SPRING_PROFILES_ACTIVE=demo` to seed demo data; omit it to run with only your existing records.

## Configuration

The backend reads environment variables; `.env.example` documents the Docker Compose values.

| Variable                              | Default                                               | Purpose                                           |
| ------------------------------------- | ----------------------------------------------------- | ------------------------------------------------- |
| `DB_URL`                              | `jdbc:mysql://localhost:3306/placement_analytics?...` | JDBC connection URL                               |
| `DB_USERNAME`                         | `root`                                                | Database user                                     |
| `DB_PASSWORD`                         | empty                                                 | Database password                                 |
| `JWT_SECRET`                          | dev-only fallback                                     | HMAC signing key; must be at least 32 bytes       |
| `JWT_EXPIRATION_MS`                   | `3600000`                                             | Token lifetime in milliseconds                    |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD`   | `admin` / `ChangeMe123!`                              | Seeded admin account                              |
| `VIEWER_USERNAME` / `VIEWER_PASSWORD` | `viewer` / `viewer123`                                | Seeded viewer account                             |
| `ANALYTICS_SERVICE_URL`               | `http://localhost:5000`                               | Flask service used by the dashboard and CSV proxy |
| `SPRING_PROFILES_ACTIVE`              | none (Compose: `demo`)                                | Set to `demo` to enable demo seeding              |
| `APP_DEMO_SEED`                       | `true` in the demo profile                            | Set to `false` to skip seeding                    |

Generate a signing key with `openssl rand -base64 48`.

## API

All `/api/**` routes require `Authorization: Bearer <token>`. `GET` is allowed for both roles; `POST`, `PUT`, `PATCH`, and `DELETE` require `ADMIN`. `POST /auth/register` also requires an admin token.

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
| `POST` | `/api/applications` | Create an application |
| `PUT` | `/api/applications/{id}` | Update an application |
| `PATCH` | `/api/applications/{id}/status` | Move an application to a new status |
| `DELETE` | `/api/applications/{id}` | Delete an application |
| `GET` | `/api/analytics/summary` | Current entity and status counts |
| `GET` | `/api/analytics/dashboard` | Filtered, history-aware dashboard payload |
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

## Database migrations

Schema changes live in `RestApi/src/main/resources/db/migration` and are applied by Flyway on startup. Never edit a migration that has already run; add the next `V5__*.sql` file instead. Existing databases are baselined at `V1`, so the migrations are additive for current installations.

The analytics service queries the same tables but does not own the schema.

## Testing and CI

```bash
cd RestApi
./mvnw test
python3 -m unittest discover -s python_etl -v
```

**38 tests passing** — 28 Spring tests and 10 Python tests.

- Spring tests run against in-memory H2 in MySQL mode; Flyway is disabled for the test profile. They cover student and application controllers, analytics proxying, security rules, and application startup.
- Python tests cover record cleaning and deduplication, dashboard filters and KPIs, history-aware funnel and placement rate, HTTP error handling, CSV export, and database-aware health checks.
- GitHub Actions runs both suites on pushes to `main` and on every pull request.

## Deployment

The repository ships as a Docker Compose stack rather than a hosted deployment. The API image is multi-stage, runs as a non-root user, and exposes an Actuator health check; the analytics container runs two Gunicorn workers with four threads and is not published outside the Compose network.

For a real deployment:

- Change every default secret, especially `JWT_SECRET`, `DB_PASSWORD`, and both seeded account passwords.
- Terminate TLS at a reverse proxy in front of port `8080`.
- Persist and back up the MySQL volume; the analytics service reads directly from the same database.
- Keep `SPRING_PROFILES_ACTIVE=demo` out of production, or set `APP_DEMO_SEED=false`.

## Documentation

| Document                                                                             | Contents                                                      |
| ------------------------------------------------------------------------------------ | ------------------------------------------------------------- |
| [`RestApi/README.md`](RestApi/README.md)                                             | Full setup, configuration, API reference, auth, and workflow  |
| [`docs/implementation-plan.md`](docs/implementation-plan.md)                         | Phases, tags, commits, verification results, known deviations |
| [`RestApi/src/main/resources/db/migration`](RestApi/src/main/resources/db/migration) | Flyway migrations V1–V4                                       |
| [`RestApi/sql/schema.sql`](RestApi/sql/schema.sql)                                   | Manual database creation helper (Flyway owns the schema)      |
| [`.github/workflows/ci.yml`](.github/workflows/ci.yml)                               | CI pipeline for the Java and Python suites                    |

## Notes and limitations

- JWTs are stored in `sessionStorage`; there is no refresh-token flow or revocation list.
- List endpoints return full tables; there is no pagination yet.
- The analytics service recomputes each dashboard request and holds no query cache. That keeps the demo current, at the cost of repeated reads.
- The CSV export returns the cleaned application table, not just the current dashboard filter selection.
- `/api/analytics/summary` counts current statuses; the dashboard payload is history-aware.
- The demo seed and default credentials are for development only. Do not use them on a public host.
- This repository does not include a hosted demo.

## Contributing

This is an academic mini-project. If you extend it, keep Flyway migrations additive and run both test suites before opening a pull request.

## License

**Personal Academic Use License**

This project was built by **Aditya Sah** and **Aniket Pandey** as a personal academic project. You may read, study, fork, and use the code for personal, educational, and non-commercial purposes, provided you keep this notice and give appropriate credit.

Commercial use, redistribution of the project as a product, or publication of substantial portions of the code requires prior written permission from the authors.

The software is provided **as is**, without warranty of any kind, express or implied. The authors are not liable for any damages arising from its use.

© 2026 Aditya Sah and Aniket Pandey. Made available for learning and academic use.
