# Pathway Project Cheat Sheet

Quick revision notes for the Job Application Management System.

## 30-second overview

Pathway manages placement records and application histories. Spring Boot handles
auth, CRUD, validation, and workflow; Flask/Pandas reads MySQL for analytics, and
the browser talks only to Spring.

```text
Browser (HTML/CSS/JS + Chart.js)
              |
              v
Spring Boot API :8080 ------> MySQL 8.4
       |
       +----> Flask/Pandas analytics :5000 (internal/read-only)
```

**Key design rule:** writes go through Spring; analytics reads MySQL directly and
is proxied by Spring. Flyway owns the schema.

## Stack at a glance

| Layer | Technology | Role |
| --- | --- | --- |
| Frontend | Vanilla HTML, CSS, JavaScript; Chart.js | SPA served by Spring Boot |
| API | Java 17, Spring Boot 3.5 | Auth, role checks, CRUD, validation, workflow |
| Database | MySQL 8.4 | Source of truth |
| Migrations | Flyway | Versioned schema changes |
| Analytics | Python 3.12, Flask, pandas, PyMySQL, Gunicorn | Read-only ETL, dashboard JSON, CSV |
| Local orchestration | Docker Compose | MySQL + analytics + API |

## Main entities and tables

| Concept | Table |
| --- | --- |
| Student | `students` |
| Employer | `companies` |
| Role/opening | `job_openings` |
| Candidate's application to a job | `applications` |
| Immutable status audit trail | `application_status_history` |
| Login accounts and roles | `users` |

Relationships: one company has many openings; one student has many applications;
one opening has many applications; each application has many history entries.
Student/job pairs are unique.

## Application workflow

```text
Applied -> Shortlisted -> Interview -> Offered -> Accepted
    |           |             |          |
    +-----------+-------------+----------+--> Rejected
    +-----------+-------------+----------+--> Withdrawn
```

Allowed next statuses:

| Current | Next |
| --- | --- |
| Applied | Shortlisted, Rejected, Withdrawn |
| Shortlisted | Interview, Rejected, Withdrawn |
| Interview | Offered, Rejected, Withdrawn |
| Offered | Accepted, Rejected, Withdrawn |
| Accepted / Rejected / Withdrawn | None (terminal) |

Create may use any initial status (useful for importing existing records); later
updates must follow the transition rules. Same-status updates do not create history.
Each real status change records status, timestamp, actor, and note.

**Application checks:** student/job exist; no duplicate pair; deadline has not
passed; student meets minimum CGPA; interview date and offer package match the
application stage. The server is authoritative; UI restrictions are not security.

**Sharp edge:** service validation may reject `Rejected`/`Withdrawn` if interview
dates or offer packages remain set; clear details first or fix transition handling.

## Roles and authentication

| Role | Capabilities |
| --- | --- |
| `ADMIN` | Read and manage placement records |
| `VIEWER` | Read records, dashboard, and CSV export |

- Login: `POST /auth/login`; send the JWT in the `Authorization` header.
- JWT bearer auth, BCrypt password hashes, stateless sessions.
- Public self-registration is disabled; admin creates accounts.
- Development demo accounts only: `admin` / `ChangeMe123!`, `viewer` / `viewer123`.
- Change default credentials and secrets before sharing/deployment.
- `401` means unauthenticated/invalid token; `403` means authenticated but forbidden.

## API quick reference

Base URL: `http://localhost:8080/api`

| Method | Path | Purpose |
| --- | --- | --- |
| GET/POST | `/students` | List/create students |
| GET/PUT/DELETE | `/students/{id}` | Read/update/delete a student |
| GET/POST | `/companies` | List/create companies |
| GET/PUT/DELETE | `/companies/{id}` | Read/update/delete a company |
| GET/POST | `/jobs` | List/create job openings |
| GET/PUT/DELETE | `/jobs/{id}` | Read/update/delete an opening |
| GET/POST | `/applications` | List/create applications |
| GET | `/applications?studentId={id}` | Filter applications by student |
| GET/PUT/DELETE | `/applications/{id}` | Read/update/delete an application |
| PATCH | `/applications/{id}/status` | Change current status |
| GET | `/applications/{id}/history` | Read chronological status history |
| GET | `/meta` | Status vocabulary and allowed transitions |
| GET | `/analytics/dashboard` | Filterable placement analytics |
| GET | `/analytics/export.csv` | Download cleaned application data |

Health check: `GET /actuator/health`. API errors use a structured JSON error
envelope; validation errors are typically 400, missing records 404, conflicts 409.

## Analytics: what to remember

- Pipeline: MySQL rows -> pandas cleanup/normalization -> aggregation -> JSON/CSV.
- Funnel stages: Applied, Shortlisted, Interview, Offered, Accepted.
- Funnel and reached-stage interview/offer counts use **history**, so later
  rejection/withdrawal does not erase progress.
- Status distribution and offer/package metrics use **current status**.
- Placement rate = distinct accepted students / filtered students * 100.
- Median package uses current `Offered` and `Accepted` applications.
- Dashboard filters: department, graduation year, status.
- Views: status distribution, funnel, department outcomes, company offers, monthly
  application volume.

## Demo data and URLs

On a fresh demo-profile database, the seeder targets **24 students, 15 companies,
22 openings, and 86 applications**, including scripted histories and edge cases.
It tops up existing data; it does not replace existing records.

| Service | URL / port |
| --- | --- |
| Web app | <http://localhost:8080> |
| Spring health | <http://localhost:8080/actuator/health> |
| API | <http://localhost:8080/api> |
| Flask health (internal in Compose) | `http://analytics:5000/health` |
| Flask dashboard (internal in Compose) | `http://analytics:5000/dashboard` |
| MySQL (internal in Compose) | `db:3306` |

Compose publishes the app on host port 8080; MySQL and Flask are internal by
default.

## Run and test

Recommended Docker setup:

```bash
cd RestApi
cp .env.example .env       # set passwords and JWT secret
docker compose up --build
```

```bash
# Useful Compose commands (run from RestApi/)
docker compose ps
docker compose logs -f app
docker compose logs -f analytics
docker compose down
docker compose down -v     # DELETES the MySQL data volume
```

Tests:

```bash
cd RestApi
./mvnw test
python3 -m unittest discover -s python_etl -v
```

Local services require JDK 17, MySQL 8, and Python 3.12. Run Spring with the
`demo` profile and Flask from `RestApi/` using `python python_etl/analytics_app.py`.

## Files to know

| Concern | File or directory |
| --- | --- |
| Workflow enum | `RestApi/src/main/java/com/tcet/RestApi/model/ApplicationStatus.java` |
| Business rules | `RestApi/src/main/java/com/tcet/RestApi/service/ApplicationService.java` |
| API controllers | `RestApi/src/main/java/com/tcet/RestApi/controller/` |
| Authentication config | `RestApi/src/main/java/com/tcet/RestApi/security/SecurityConfig.java` |
| Demo seeding | `RestApi/src/main/java/com/tcet/RestApi/config/DemoDataSeeder.java` |
| UI | `RestApi/src/main/resources/static/` |
| Analytics logic | `RestApi/python_etl/analytics.py` |
| Analytics SQL/HTTP | `RestApi/python_etl/analytics_app.py` |
| Migrations | `RestApi/src/main/resources/db/migration/` |
| Java tests | `RestApi/src/test/` |
| Python tests | `RestApi/python_etl/test_analytics.py` |
| Deployment | `RestApi/docker-compose.yml`, `RestApi/Dockerfile` |

## Explain it in one breath

Pathway replaces placement spreadsheets with role-based record management,
server-enforced recruitment rules, an audit trail, and live pandas analytics.
Analytics is on-demand from the operational database, not a data warehouse.
