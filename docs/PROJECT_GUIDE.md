# Pathway — Complete Project Handbook

> **Who this is for:** a new teammate, a reviewer, or anyone who has to understand
> this repository from zero. It explains both the non-technical purpose and the
> technical implementation, file by file and flow by flow.
>
> **What it covers:** the problem, users, stack, architecture, database, backend,
> security, CRUD, workflow, analytics/ETL, frontend, configuration, Docker, demo
> data, migrations, tests, troubleshooting, and how to explain the project in a
> viva or interview.
>
> **Verified against:** the code in this working tree on **2026-10-22**.
> **Paths in this document are relative to the repository root** unless stated
> otherwise.

---

## Table of contents

1. [The project in one page](#1-the-project-in-one-page)
2. [Problem, users, and vocabulary](#2-problem-users-and-vocabulary)
3. [Tech stack: what each piece is used for](#3-tech-stack-what-each-piece-is-used-for)
4. [Architecture](#4-architecture)
5. [End-to-end flows](#5-end-to-end-flows)
6. [Data model](#6-data-model)
7. [Repository structure](#7-repository-structure)
8. [Backend deep dive](#8-backend-deep-dive)
9. [Security and access control](#9-security-and-access-control)
10. [Application workflow (state machine)](#10-application-workflow-state-machine)
11. [CRUD and API reference](#11-crud-and-api-reference)
12. [Analytics and ETL](#12-analytics-and-etl)
13. [Frontend](#13-frontend)
14. [Configuration](#14-configuration)
15. [Running the project](#15-running-the-project)
16. [Demo data](#16-demo-data)
17. [Database migrations](#17-database-migrations)
18. [Testing and CI](#18-testing-and-ci)
19. [Error handling and troubleshooting](#19-error-handling-and-troubleshooting)
20. [Walkthroughs](#20-walkthroughs)
21. [How to explain this project to anyone](#21-how-to-explain-this-project-to-anyone)
22. [Limitations and a sensible roadmap](#22-limitations-and-a-sensible-roadmap)
23. [Cheat sheet](#23-cheat-sheet)
24. [Appendix: exact queries, payloads, and phases](#24-appendix-exact-queries-payloads-and-phases)

---

## 1. The project in one page

### 1.1 The non-technical explanation

A college **Training and Placement Cell** has to answer questions like:

- Which students have applied to which companies?
- Who reached the interview stage? Who got an offer? Who accepted it?
- Which departments are converting applications into offers?
- Which companies hire the most from us?
- What is the median package this season?

Most cells start with spreadsheets. A spreadsheet cannot enforce a workflow, cannot
tell you who changed a status and when, and cannot give a live dashboard without
manual copy-paste.

**Pathway** is one shared workspace that does all of it:

1. A placement officer records **students**, **companies**, **job openings**, and
   **applications**.
2. The system enforces rules: the student must meet the CGPA cut-off, the deadline
   must not have passed, the same student cannot apply twice to the same job, and
   an application can only move through legal statuses.
3. Every status change is stored in an audit trail.
4. A separate analytics service reads the same database and produces a live
   dashboard: KPIs, a recruitment funnel, department outcomes, top recruiters, and
   monthly volume.
5. An **ADMIN** can create and edit; a **VIEWER** can only read and export.

In one sentence:

> **Pathway is a placement-cell tracker with a guarded hiring workflow and a live
> analytics dashboard, backed by one MySQL database.**

### 1.2 The technical explanation

The repository contains three cooperating pieces:

| Piece | What it is | Port | Responsibility |
| --- | --- | --- | --- |
| **Spring Boot API** | Java 17 / Spring Boot 3.5 application | `8080` | Authentication, authorization, CRUD, validation, the status state machine, audit history, serving the SPA, and proxying analytics requests |
| **Flask + Pandas service** | Python 3.12 micro-service | `5000` (internal) | Read-only analytics: query MySQL, clean data with pandas, aggregate, return chart-ready JSON and CSV |
| **MySQL** | Relational database | `3306` (internal) | Single source of truth; schema owned by Flyway migrations |

The browser loads a vanilla HTML/CSS/JavaScript SPA from Spring Boot. The SPA calls
Spring Boot for everything. For the dashboard, Spring Boot calls Flask, Flask reads
MySQL, pandas computes the numbers, and the JSON travels back through Spring to
Chart.js in the browser.

**The core design choice:** writes go through Spring Boot; analytics is a
**read-only sidecar** over the same database. There is no second copy of the data
and no duplicated business rules.

### 1.3 What the project deliberately is not

- Not a public job board. Accounts are created by an admin; self-registration is
  disabled.
- Not a resume/ATS system. There are no file uploads, emails, or calendar
  integrations.
- Not a multi-tenant SaaS. Every admin sees the same placement cell data.
- Not a data warehouse. The "ETL" runs on demand, in memory, over a small dataset.
- Not a React/Vue application. The frontend is deliberately dependency-light and
  has no build step.
- Not a hosted demo. The repository ships as a Docker Compose stack.

### 1.4 Key facts

| Fact | Value |
| --- | --- |
| Project/brand name | **Pathway** |
| Repository name | `Job_Application_Management_System` |
| Maven artifact | `placement-analytics` (`com.tcet`) |
| Backend | Java 17, Spring Boot 3.5.3 |
| Frontend | HTML, CSS, vanilla JS, Chart.js 4.5.1 (self-hosted) |
| Analytics | Python 3.12, Flask, pandas, PyMySQL, Gunicorn |
| Database | MySQL 8.4, Flyway V1–V4 |
| Auth | JWT bearer tokens, BCrypt passwords, `ADMIN` / `VIEWER` roles |
| Tests | 30 Spring tests + 10 Python tests = **40** |
| Demo data | 24 students, 15 companies, 22 jobs, 86 applications |
| Demo funnel | `86 → 55 → 34 → 20 → 9` |
| Demo placement rate | `29.2%` |
| Default UI | <http://localhost:8080> |

---

## 2. Problem, users, and vocabulary

### 2.1 The placement-cell story

Imagine the placement season at a college.

- **Students** have a roll number, department, CGPA, graduation year, and contact
  details.
- **Companies** visit or post roles. Each company has a name, industry, location,
  and website.
- **Job openings** belong to a company. Each opening has a title, type (full-time,
  internship, part-time, contract), package, minimum CGPA, posted date, and an
  application deadline.
- **Applications** connect one student to one job opening. An application has a
  date, a status, optionally an interview date, optionally an offer package, and
  notes.

The placement officer needs to move an application through the real-world process:

```text
Applied → Shortlisted → Interview → Offered → Accepted
```

At any stage before acceptance, it can be **Rejected** or **Withdrawn**.
`Accepted`, `Rejected`, and `Withdrawn` are final.

Two things make this more than a CRUD app:

1. **Rules.** You cannot apply after the deadline, below the CGPA cut-off, or twice
   to the same job. You cannot jump from `Applied` straight to `Offered`. You
   cannot move a closed application. Interview dates and offer packages only make
   sense at certain stages.
2. **Analytics.** The placement cell wants to see conversion, not just rows. The
   system has to answer “how many reached each stage?” even after a candidate is
   rejected later.

### 2.2 Roles

| Role | Who | Can do |
| --- | --- | --- |
| **ADMIN** | Placement officer / staff | Everything: read, create, update, delete, change status, create users, export |
| **VIEWER** | Faculty, student, auditor | Read-only: browse records, dashboard, and CSV export |
| **Public** | Anyone | Login page, static SPA assets, health check |

The backend is the source of truth for permissions. The frontend hides buttons for
viewers, but even if someone calls the API directly, Spring Security rejects
writes with `403`.

### 2.3 Glossary

Read this once; the rest of the document assumes it.

| Term | Plain-English meaning |
| --- | --- |
| **API** | A contract that lets one program ask another for data or actions. Here it is HTTP + JSON. |
| **REST** | A style of API where URLs represent resources (`/api/students`) and HTTP methods represent actions. |
| **Endpoint** | One URL + method, e.g. `POST /api/applications`. |
| **JSON** | Text format for structured data: `{"name": "Asha", "cgpa": 8.4}`. |
| **CRUD** | Create, Read, Update, Delete — the four basic data operations. |
| **HTTP method** | `GET` (read), `POST` (create), `PUT` (replace/update), `PATCH` (partial update), `DELETE`. |
| **HTTP status** | A number describing the result: `200` OK, `201` created, `400` bad input, `401` not signed in, `403` forbidden, `404` not found, `409` conflict, `500` server error. |
| **SPA** | Single-Page Application: one HTML page whose JavaScript swaps sections instead of loading new pages. |
| **JWT** | JSON Web Token: a signed digital ID badge. The server can verify it without storing a session. |
| **Bearer token** | The token is sent as `Authorization: Bearer <token>`. |
| **BCrypt** | A one-way password hashing algorithm. The database stores a hash, never the plain password. |
| **Role** | A permission level. Here `ADMIN` or `VIEWER`. |
| **Stateless** | The server does not keep a login session in memory; every request proves identity with the token. |
| **ORM** | Object-Relational Mapping: Java objects ↔ database rows. Hibernate does this. |
| **JPA** | Java Persistence API: the standard annotations/interfaces used by Hibernate. |
| **Entity** | A Java class mapped to a database table (`Student`, `Company`, …). |
| **Repository** | A Spring Data interface that generates database queries for an entity. |
| **Service** | A class that holds business rules and coordinates repositories. |
| **Controller** | A class that receives HTTP requests and returns responses. |
| **DTO** | Data Transfer Object: a small record used for request/response bodies instead of exposing entities. |
| **Dependency injection** | Spring creates objects and passes their dependencies in, instead of the class constructing them. |
| **Bean Validation** | Annotation-based input checks such as `@NotBlank`, `@Email`, `@DecimalMin`. |
| **Flyway** | A migration tool that versions and applies SQL schema changes. |
| **Migration** | A numbered SQL file (`V1__...sql`) that changes the schema. |
| **Seed** | Development data inserted automatically so the app is not empty. |
| **Actuator** | Spring Boot’s built-in operational endpoints, e.g. `/actuator/health`. |
| **Health check** | A small endpoint that reports whether a service is alive; Docker uses it. |
| **Docker image** | A packaged filesystem + start command for a service. |
| **Container** | A running instance of an image. |
| **Docker Compose** | A YAML file that runs multiple containers as one stack. |
| **Volume** | Persistent Docker storage; here it holds MySQL data. |
| **ETL** | Extract, Transform, Load. Here: read MySQL, clean/aggregate with pandas, serve JSON/CSV. |
| **DataFrame** | pandas’ in-memory table, like a spreadsheet with typed columns. |
| **KPI** | Key Performance Indicator: a headline number such as placement rate. |
| **Funnel** | Counts of how many applications reached each progressive stage. |
| **Median** | The middle value when numbers are sorted; less distorted by outliers than an average. |
| **LPA** | Lakhs Per Annum — the salary/package unit used in Indian placements. |
| **CGPA** | Cumulative Grade Point Average, on a 0–10 scale here. |
| **CORS** | Browser rule about cross-origin requests. Not needed here because everything is same-origin. |
| **CSRF** | Cross-Site Request Forgery. Disabled here because auth uses bearer tokens, not cookies. |
| **H2** | In-memory Java database used only by tests. |
| **Gunicorn** | A production WSGI server that runs the Flask app. |
| **Proxy** | Spring forwards `/api/analytics/*` to Flask so the browser only talks to one origin. |
| **`sessionStorage`** | Browser storage that lasts for the tab session; used for the JWT. |
| **Chart.js** | JavaScript charting library used for the dashboard. |

---

## 3. Tech stack: what each piece is used for

### 3.1 The stack at a glance

| Layer | Technology | Version | Used for | Main location |
| --- | --- | --- | --- | --- |
| Language | Java | 17 | Main backend | `RestApi/src/main/java` |
| Framework | Spring Boot | 3.5.3 | App bootstrap, web server, dependency injection | `pom.xml` |
| Web | Spring Web MVC | (Boot-managed) | REST controllers, static file serving | `controller/` |
| Persistence | Spring Data JPA + Hibernate | (Boot-managed) | Entities, repositories, SQL generation | `model/`, `repositories/` |
| Security | Spring Security | (Boot-managed) | Filter chain, roles, password encoding | `security/` |
| Tokens | JJWT | 0.12.6 | Create and verify JWTs | `security/JwtService.java` |
| Validation | Jakarta Bean Validation | (Boot-managed) | Field-level request checks | DTOs and entities |
| Boilerplate | Lombok | (Boot-managed) | Getters/setters/constructors | Entities and services |
| Ops | Spring Actuator | (Boot-managed) | `/actuator/health` | `application.properties` |
| Database | MySQL | 8.4 | Persistent source of truth | Docker Compose / local MySQL |
| Migrations | Flyway | (Boot-managed) | Versioned schema changes V1–V4 | `db/migration/` |
| Test DB | H2 | (Boot-managed) | In-memory MySQL-mode DB for Java tests | `application-test.properties` |
| Build | Maven Wrapper | 3.9.x (downloads 3.9.16; Docker build uses 3.9.9) | Compile, test, package | `mvnw`, `pom.xml` |
| Analytics language | Python | 3.12 | Data cleaning and aggregation | `python_etl/` |
| Analytics web | Flask | 3.x | Internal HTTP endpoints | `analytics_app.py` |
| Data analysis | pandas | 2.x | DataFrames, cleaning, grouping, median | `analytics.py` |
| MySQL driver | PyMySQL | 1.x | Python → MySQL connection | `analytics_app.py` |
| Analytics server | Gunicorn | 22/23.x | Production WSGI server in Docker | `python_etl/Dockerfile` |
| Frontend | HTML + CSS + JavaScript | — | SPA, no framework, no build step | `src/main/resources/static/` |
| Charts | Chart.js | 4.5.1 | Doughnut, bar, line charts | `static/vendor/` |
| Fonts | DM Sans, Manrope | self-hosted | UI typography, no CDN | `static/fonts/` |
| Packaging | Docker | — | Reproducible images | `Dockerfile`, `python_etl/Dockerfile` |
| Orchestration | Docker Compose | — | MySQL + Flask + Spring together | `docker-compose.yml` |
| CI | GitHub Actions | — | Run Java + Python tests | `.github/workflows/ci.yml` |

### 3.2 Why each choice was made

| Decision | Reason | Trade-off |
| --- | --- | --- |
| **Spring Boot for writes** | Mature security, validation, JPA, transactions, and a single deployable JAR. | More ceremony than a small Flask app. |
| **Separate Flask + pandas for analytics** | pandas expresses cleaning/grouping/median in a few readable lines; it also demonstrates a polyglot read/write split. | One more process, one more network hop, two languages to maintain. |
| **MySQL as the only source of truth** | No data duplication, no sync problem. Analytics always reflects live writes. | Analytics load lands on the same database. |
| **Flyway for schema** | The schema is reproducible from an empty database; changes are reviewable SQL. | Every schema change needs a new migration file. |
| **`ddl-auto=validate`** | Hibernate checks that entities match the migrated schema; drift fails at startup instead of corrupting data. | You must keep entities and migrations in sync manually. |
| **JWT instead of server sessions** | Stateless, simple for a single-page app, no session store. | No built-in revocation or refresh; token theft is a risk. |
| **Vanilla JS SPA** | No Node/npm build chain; easy to read and demo; self-hosted assets work offline. | No component framework; DOM code is manual. |
| **Chart.js** | Small, familiar, works with plain `<canvas>`; no framework integration needed. | Self-hosted file must be updated manually. |
| **Two roles only** | Matches the academic scope and keeps the permission matrix simple. | No per-department or per-student scoping. |
| **History table** | Audit trail and history-aware funnel, which a current-status column cannot provide. | More writes and more data to query. |
| **Deterministic demo seeder** | A fresh clone looks alive immediately; tests/demos are repeatable. | Seed data must be understood as fake and must never reach production. |
| **Docker Compose** | Reproduces MySQL + analytics + API with health checks. | Not a production orchestrator; no TLS, backups, or secrets manager. |

### 3.3 What runs where

```text
Your machine
├── browser  ──────────────────────────────►  http://localhost:8080
│
└── Docker Compose network (or local processes)
    ├── app (Spring Boot)          :8080  ← published to the host
    ├── analytics (Flask/Gunicorn) :5000  ← internal only
    └── db (MySQL)                 :3306  ← internal only
```

Only the Spring Boot port is published by Compose. The Flask service and MySQL are
reachable only inside the Compose network. In local (non-Docker) development, all
three are on `localhost`.

---

## 4. Architecture

### 4.1 Context diagram (who talks to what)

```text
                         ┌───────────────────────────────────┐
                         │             People                │
                         │  ADMIN: read + write              │
                         │  VIEWER: read + export            │
                         └────────────────┬──────────────────┘
                                          │ HTTPS/HTTP + browser
                                          ▼
              ┌─────────────────────────────────────────────────────┐
              │                Spring Boot app :8080                │
              │                                                     │
              │  Static SPA            REST API                     │
              │  index.html            /auth/*                      │
              │  app.js                /api/students                 │
              │  styles.css            /api/companies                │
              │  Chart.js              /api/jobs                     │
              │  fonts                 /api/applications             │
              │                        /api/meta                     │
              │                        /api/analytics/*              │
              │                        /actuator/health              │
              │                                                     │
              │  JWT filter · roles · validation · workflow         │
              └───────────────┬──────────────────────┬──────────────┘
                              │ JDBC/Hibernate       │ HTTP (RestClient)
                              ▼                      ▼
                    ┌──────────────────┐    ┌────────────────────────┐
                    │   MySQL 8.4      │◄───│  Flask + pandas :5000  │
                    │   source of      │    │  read-only analytics   │
                    │   truth          │    │  JSON + CSV            │
                    │   Flyway schema  │    │  PyMySQL               │
                    └──────────────────┘    └────────────────────────┘
```

### 4.2 Container/component diagram

```text
┌─────────────────────────────────────────────────────────────────────────┐
│ Browser (SPA)                                                           │
│  login overlay · sidebar · tables · inline forms · Chart.js dashboard   │
│  state stored in JS + sessionStorage (JWT and username/role)            │
└───────────────────────────────┬─────────────────────────────────────────┘
                                │ fetch() with Bearer JWT
                                ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ Spring Boot                                                             │
│                                                                         │
│  security/          controller/          service/                       │
│  ├ JwtFilter        ├ AuthController     ├ ApplicationService           │
│  ├ JwtService       ├ StudentController  ├ JobOpeningService            │
│  ├ UserDetailsSvc   ├ CompanyController  └ UserService                  │
│  └ SecurityConfig   ├ JobOpeningCtrl                                    │
│                     ├ JobApplicationCtrl repositories/                  │
│  config/            ├ MetaController     ├ StudentRepository            │
│  ├ AdminSeeder      └ AnalyticsController├ CompanyRepository             │
│  ├ DemoDataSeeder                        ├ JobOpeningRepository         │
│  └ AnalyticsClientConfig                 ├ JobApplicationRepository     │
│                                          ├ StatusHistoryRepository      │
│  web/                                    └ UserRepository               │
│  ├ ApiError                                                             │
│  └ ApiExceptionHandler          model/ (entities + status enum)         │
│                                                                         │
│  static/ (SPA)                  db/migration/ (Flyway V1–V4)            │
└───────────────┬──────────────────────────────────────┬──────────────────┘
                │ JPA/JDBC                             │ RestClient
                ▼                                      ▼
        ┌──────────────────┐                  ┌──────────────────────────┐
        │ MySQL            │                  │ Flask                    │
        │ students         │                  │  /health                 │
        │ companies        │                  │  /dashboard              │
        │ job_openings     │                  │  /export.csv             │
        │ applications     │                  │                          │
        │ status_history   │                  │ pandas: clean → filter → │
        │ users            │                  │ rank → aggregate → JSON  │
        └──────────────────┘                  └──────────────────────────┘
```

### 4.3 The two paths

**Write path (Spring Boot only):**

```text
Browser → JWT filter → controller → Bean Validation → service rules
        → repository → Hibernate → MySQL → response DTO/entity → browser
```

**Analytics path (read-only sidecar):**

```text
Browser → Spring /api/analytics/dashboard → Flask /dashboard
        → PyMySQL SELECTs → pandas → JSON → Spring → browser → Chart.js
```

The analytics service never writes. It does not own the schema, does not run
migrations, and has no Spring Security. It is kept internal.

### 4.4 Why proxy analytics through Spring?

- **One origin.** The browser only talks to `localhost:8080`, so there is no CORS
  configuration and no second public attack surface.
- **One authentication gate.** `/api/analytics/*` is protected by the same JWT and
  role rules as the rest of the API.
- **One URL space.** The SPA does not need to know that Flask exists.
- **CSV stays authenticated.** The browser downloads the CSV with a bearer token;
  Spring forwards it and sets the download filename.

### 4.5 Trust boundaries

| Boundary | Trust level | What protects it |
| --- | --- | --- |
| Browser → Spring | Untrusted input | JWT, role checks, Bean Validation, service rules, SQL constraints |
| Spring → Flask | Internal network in Compose | Not authenticated itself; must never be published publicly |
| Flask → MySQL | Internal network | Database credentials from environment |
| Spring → MySQL | Internal network | Database credentials from environment |
| Public internet → deployment | Out of scope in repo | A reverse proxy should terminate TLS in front of `8080` |

### 4.6 Deployment diagram (Docker Compose)

```text
Host machine
│
│  localhost:8080
▼
┌─────────────────────────────────────────────────────────────────────┐
│ Docker Compose network                                              │
│                                                                     │
│  ┌──────────────────────┐      ┌──────────────────────────────┐     │
│  │ app                  │      │ analytics                    │     │
│  │ Spring Boot :8080    │─────►│ Flask + Gunicorn :5000       │     │
│  │ health: /actuator    │      │ health: /health (DB-aware)   │     │
│  └──────────┬───────────┘      └──────────────┬───────────────┘     │
│             │ JDBC                            │ PyMySQL             │
│             ▼                                 ▼                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ db: MySQL 8.4 :3306                                         │   │
│  │ volume: mysql_data                                          │   │
│  │ health: mysqladmin ping                                     │   │
│  └─────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

`app` waits for both `db` and `analytics` to become healthy. The analytics service
waits for `db`. Only `app` publishes a host port.

---

## 5. End-to-end flows

This section follows the actual code. If you understand these seven flows, you
understand almost the whole repository.

### 5.1 Login flow

```text
Browser                    Spring Boot                         MySQL
   │                           │                                 │
   │ POST /auth/login          │                                 │
   │ {username,password}       │                                 │
   ├──────────────────────────►│                                 │
   │                           │ AuthenticationManager           │
   │                           │   → CustomUserDetailsService    │
   │                           │   → UserRepository.findByUsername
   │                           │────────────────────────────────►│
   │                           │◄────────────────────────────────│
   │                           │ BCrypt.matches(raw, hash)       │
   │                           │ JwtService.generateToken(user)  │
   │◄──────────────────────────┤ {token, tokenType, expiresIn,   │
   │                           │  username, role}                │
   │ sessionStorage.setItem    │                                 │
   │ load /api/meta + data     │                                 │
```

Step by step:

1. The SPA sends `POST /auth/login` with `{"username": "...", "password": "..."}`.
2. `/auth/login` is public in `SecurityConfig`.
3. `AuthController` asks Spring’s `AuthenticationManager` to authenticate.
4. `AuthenticationManager` uses `CustomUserDetailsService`, which loads the user by
   username through `UserRepository`.
5. Spring Security compares the raw password with the BCrypt hash using the
   configured `BCryptPasswordEncoder`.
6. On success, `JwtService` creates a signed token whose subject is the username,
   with issued-at and expiry claims.
7. The response contains `token`, `tokenType` (`Bearer`), `expiresIn` (seconds),
   `username`, and `role`.
8. The SPA stores the token and a small user object in `sessionStorage`.
9. The SPA loads `/api/meta`, then all four tables, then the dashboard.

Failure: bad credentials produce `401` with the standard `ApiError` shape and
message `Invalid username or password.`

### 5.2 Authenticated CRUD request

Example: `GET /api/students`.

```text
1. Browser sends Authorization: Bearer <token>.
2. JwtAuthenticationFilter reads the header.
3. It extracts the username from the signed token.
4. CustomUserDetailsService loads the current user and role from MySQL.
5. JwtService verifies signature + expiry + subject.
6. SecurityContext is populated with the user and authorities.
7. SecurityConfig checks:
      GET /api/**  → ADMIN or VIEWER allowed
      other /api/** → ADMIN only
8. StudentController.all() calls StudentRepository.findAll().
9. Hibernate issues SELECT ... FROM students.
10. Spring serializes the result to JSON.
11. Browser renders the table.
```

Important detail: the JWT does **not** contain the role. The role is loaded from the
database on every request. So if an admin changes a user’s role, the change takes
effect on the next request even though the token is still valid.

### 5.3 Create an application (the most rule-heavy write)

`POST /api/applications`

```text
1. Bean Validation checks:
   studentId, jobId, appliedDate, status are present;
   offerPackageLpa >= 0; notes <= 500 characters.
2. ApplicationService.requireStudent / requireJob load the rows.
   Missing row -> 400 "Select an existing student/job opening".
3. ApplicationStatus.fromLabel parses the status (case-insensitive).
   Unknown -> 400 "Invalid status. Use: ...".
4. validateEligibility:
   - appliedDate after job.deadline          -> 409
   - student.cgpa < job.minCgpa              -> 409
5. validateDetails:
   - interviewDate only from Shortlisted onward
   - offerPackage only for Offered/Accepted
6. Duplicate check on (student_id, job_id)  -> 409
7. Save the application; @PrePersist sets updatedAt.
8. Insert application_status_history:
   status = initial status, changedBy = JWT username, note = "Application created".
9. Return 201 + ApplicationResponse with student/job/company display names.
```

Note that the **create** endpoint accepts any initial status (including `Accepted`,
`Rejected`, or `Withdrawn`). This is deliberate: it lets the placement cell import
historical records. The transition rules apply to later changes. The demo seeder
uses repository writes rather than the API, but follows the same idea: a record can
start at its current stage with a matching history path.

### 5.4 Change a status (state machine + audit)

`PATCH /api/applications/{id}/status` with `{"status": "Shortlisted"}`

```text
1. Parse the requested status.
2. Load the application (with student, job, company via EntityGraph).
3. Parse the current status.
4. ApplicationStatus.canTransitionTo(next):
      same status always allowed;
      otherwise next must be in the allowed set.
   Illegal -> 409 with the allowed next statuses in the message.
5. validateDetails(nextStatus, existing interviewDate, existing offerPackage).
6. Set the new label, save.
7. If the status actually changed, insert a history row:
   changedBy = JWT username, changedAt = now, note = "Status updated".
8. Return the updated ApplicationResponse.
```

The frontend reads the allowed transitions from `GET /api/meta`, so the status
dropdown on an edit form only offers the current status plus legal next statuses.

### 5.5 Dashboard flow (the analytics path)

```text
Browser                    Spring Boot                   Flask                 MySQL
   │ GET /api/analytics/      │                            │                     │
   │ dashboard?department=…   │                            │                     │
   ├─────────────────────────►│ RestClient                 │                     │
   │                          ├───────────────────────────►│ /dashboard          │
   │                          │                            │ load_data()         │
   │                          │                            ├────────────────────►│
   │                          │                            │◄────────────────────┤
   │                          │                            │ build_dashboard()   │
   │                          │                            │ (pandas)            │
   │                          │◄───────────────────────────┤ JSON                │
   │◄─────────────────────────┤ JSON                       │                     │
   │ update KPI cards,        │                            │                     │
   │ pipeline bars, 5 charts  │                            │                     │
```

1. The user changes a dashboard filter (department, graduation year, status) or
   clicks a chart segment.
2. `app.js` builds a query string and calls `/api/analytics/dashboard`.
3. Spring’s `AnalyticsController.dashboard` forwards only non-blank filters using
   `RestClient`.
4. Flask parses `graduationYear`; a non-numeric value returns `400`.
5. `load_data()` opens one PyMySQL connection and runs two queries:
   the student/application join and the full status history.
6. `build_dashboard()` cleans the rows with pandas, applies filters, computes
   furthest stages, aggregates KPIs/charts, and returns a dict.
7. Flask serializes it to JSON.
8. Spring returns it unchanged to the browser.
9. `app.js` updates six metric cards, the pipeline bars, and five Chart.js charts.

There is **no cache**: every request recomputes from MySQL. This keeps the demo
current and is acceptable for a small dataset.

### 5.6 CSV export flow

```text
Browser → GET /api/analytics/export.csv (Bearer token)
        → Spring AnalyticsController.exportCsv
        → Flask /export.csv
        → load_data() → clean_records() → DataFrame.to_csv(index=False)
        → bytes back to Spring
        → Content-Type: text/csv
          Content-Disposition: attachment; filename=applications.csv
        → browser creates a Blob and downloads it
```

The export is the **cleaned, deduplicated application table**, not a report and not
the current dashboard filter selection. It has one row per application.

### 5.7 Error flow

```text
Controller/Service throws
  ├── ResponseStatusException        → ApiExceptionHandler.handleResponseStatus
  ├── MethodArgumentNotValidException→ 400 VALIDATION_FAILED + fieldErrors
  ├── HttpMessageNotReadableException→ 400 MALFORMED_REQUEST
  ├── AuthenticationException        → 401 UNAUTHORIZED
  ├── AccessDeniedException          → 403 FORBIDDEN
  ├── DataIntegrityViolationException→ 409 DATA_CONFLICT
  └── anything else                  → 500 INTERNAL_ERROR
```

Security-filter failures (no token, bad token, viewer writing) happen before the
controller. `SecurityConfig` writes the same `ApiError` JSON directly through its
`authenticationEntryPoint` and `accessDeniedHandler`.

The SPA’s `request()` helper reads `message` (or `detail`/`error`) and shows it in
the top notice. On `401` it clears the session and shows the login overlay.

### 5.8 The whole system on one page

```text
                    ┌──────────────┐
                    │   Browser    │
                    │  SPA + Chart │
                    └──────┬───────┘
                           │ JWT
                           ▼
┌──────────────────────────────────────────────────┐
│ Spring Boot                                      │
│  auth ──► security ──► controllers ──► services  │
│                                      │           │
│                                      ▼           │
│                                 repositories     │
│                                      │           │
│  static SPA ◄────────────────────────┘           │
│  analytics proxy ──────────────┐                 │
└────────────────────────────────┼─────────────────┘
                                 │ HTTP
                                 ▼
                        ┌──────────────────┐
                        │ Flask + pandas   │
                        │ clean/aggregate  │
                        └────────┬─────────┘
                                 │ SQL
                                 ▼
                        ┌──────────────────┐
                        │ MySQL            │
                        │ 6 tables         │
                        └──────────────────┘
```

---

## 6. Data model

### 6.1 Entity-relationship diagram

```text
┌──────────────────┐          ┌──────────────────┐
│ students         │          │ companies        │
│──────────────────│          │──────────────────│
│ id (PK)          │          │ id (PK)          │
│ roll_no (UQ)     │          │ company_name (UQ)│
│ full_name        │          │ industry         │
│ email            │          │ location         │
│ department       │          │ website          │
│ cgpa             │          └────────┬─────────┘
│ graduation_year  │                   │ 1
└────────┬─────────┘                   │
         │ 1                           │ *
         │                    ┌────────▼─────────┐
         │                    │ job_openings     │
         │                    │──────────────────│
         │                    │ id (PK)          │
         │                    │ company_id (FK)  │
         │                    │ job_title        │
         │                    │ job_type         │
         │                    │ package_lpa      │
         │                    │ min_cgpa         │
         │                    │ posted_date      │
         │                    │ deadline         │
         │                    └────────┬─────────┘
         │ *                           │ 1
         ▼                             │
┌──────────────────────────────────────▼─────────┐
│ applications                                   │
│────────────────────────────────────────────────│
│ id (PK)                                        │
│ student_id (FK → students.id)                  │
│ job_id (FK → job_openings.id)                  │
│ applied_date                                   │
│ status                                         │
│ interview_date                                 │
│ offer_package_lpa                              │
│ notes                                          │
│ updated_at                                     │
│ UNIQUE (student_id, job_id)                    │
└───────────────────────┬────────────────────────┘
                        │ 1
                        │ *
              ┌─────────▼──────────────────────┐
              │ application_status_history     │
              │────────────────────────────────│
              │ id (PK)                        │
              │ application_id (FK, CASCADE)   │
              │ status                         │
              │ changed_at                     │
              │ changed_by                     │
              │ note                           │
              └────────────────────────────────┘

┌──────────────────┐
│ users            │   (standalone; not linked by FK)
│──────────────────│
│ id (PK)          │
│ username (UQ)    │
│ password (hash)  │
│ role             │
└──────────────────┘
```

### 6.2 Tables in detail

#### `students`

| Column | Type | Constraints | Meaning |
| --- | --- | --- | --- |
| `id` | BIGINT | PK, auto-increment | Internal ID |
| `roll_no` | VARCHAR(30) | NOT NULL, UNIQUE | College roll number |
| `full_name` | VARCHAR(120) | NOT NULL | Student name |
| `email` | VARCHAR(150) | NULL, `@Email` in Java | Contact |
| `department` | VARCHAR(80) | NOT NULL | e.g. `Information Technology` |
| `cgpa` | DOUBLE | NULL, check 0–10 | Used for eligibility |
| `graduation_year` | INT | NULL, check 2000–2100 | Used for dashboard filters |

#### `companies`

| Column | Type | Constraints | Meaning |
| --- | --- | --- | --- |
| `id` | BIGINT | PK | Internal ID |
| `company_name` | VARCHAR(140) | NOT NULL, UNIQUE | Employer name |
| `industry` | VARCHAR(100) | NULL | Software, Fintech, … |
| `location` | VARCHAR(120) | NULL | City or Remote |
| `website` | VARCHAR(220) | NULL; Java pattern allows empty or `http(s)://...` | Company site |

#### `job_openings`

| Column | Type | Constraints | Meaning |
| --- | --- | --- | --- |
| `id` | BIGINT | PK | Internal ID |
| `company_id` | BIGINT | NOT NULL, FK → `companies.id` | Who is hiring |
| `job_title` | VARCHAR(140) | NOT NULL | Role name |
| `job_type` | VARCHAR(40) | NOT NULL | Full-time / Internship / Part-time / Contract |
| `package_lpa` | DOUBLE | NULL, check ≥ 0 | Offered package |
| `min_cgpa` | DOUBLE | NULL, check 0–10 | Eligibility cut-off |
| `posted_date` | DATE | NULL | When posted |
| `deadline` | DATE | NULL | Last date to apply |

#### `applications`

| Column | Type | Constraints | Meaning |
| --- | --- | --- | --- |
| `id` | BIGINT | PK | Internal ID; UI shows `APP-0001` |
| `student_id` | BIGINT | NOT NULL, FK → `students.id` | Applicant |
| `job_id` | BIGINT | NOT NULL, FK → `job_openings.id` | Opening |
| `applied_date` | DATE | NOT NULL | Date of application |
| `status` | VARCHAR(30) | NOT NULL, default `Applied`, check in 7 labels | Current state |
| `interview_date` | DATE | NULL | Only meaningful from `Shortlisted` onward |
| `offer_package_lpa` | DOUBLE | NULL, check ≥ 0 | Only for `Offered` / `Accepted` |
| `notes` | VARCHAR(500) | NULL | Free text |
| `updated_at` | DATETIME | NULL | Set by JPA `@PrePersist`/`@PreUpdate` |

Unique key `uk_student_job (student_id, job_id)` prevents duplicate applications.

#### `application_status_history`

| Column | Type | Constraints | Meaning |
| --- | --- | --- | --- |
| `id` | BIGINT | PK | Internal ID |
| `application_id` | BIGINT | NOT NULL, FK → `applications.id` **ON DELETE CASCADE** | Which application |
| `status` | VARCHAR(30) | NOT NULL | Status at that moment |
| `changed_at` | DATETIME | NOT NULL | When |
| `changed_by` | VARCHAR(50) | NULL | Username, `placement.cell`, or `system` |
| `note` | VARCHAR(255) | NULL | Why/how |

Index `idx_history_application (application_id, changed_at)`.

#### `users`

| Column | Type | Constraints | Meaning |
| --- | --- | --- | --- |
| `id` | BIGINT | PK | Internal ID |
| `username` | VARCHAR(50) | NOT NULL, UNIQUE | Login name |
| `password` | VARCHAR(255) | NOT NULL | BCrypt hash, never serialized (`@JsonIgnore`) |
| `role` | VARCHAR(20) | NOT NULL | `ADMIN` or `VIEWER` |

### 6.3 Indexes and check constraints

From **V2**:

| Index | Why |
| --- | --- |
| `applications (status, applied_date)` | Status/date queries |
| `applications (applied_date)` | Monthly trend |
| `students (department, graduation_year)` | Dashboard filters |
| `job_openings (deadline)` | Deadline lookups |

From **V3** (database-level sanity):

- `students.cgpa` is null or 0–10.
- `students.graduation_year` is null or 2000–2100.
- `job_openings.package_lpa` is null or ≥ 0.
- `job_openings.min_cgpa` is null or 0–10.
- `applications.status` is one of the seven exact labels.
- `applications.offer_package_lpa` is null or ≥ 0.

From **V4**:

- `application_status_history (application_id, changed_at)` index.
- `ON DELETE CASCADE` from history to application.

### 6.4 Delete behavior

| Delete | What happens |
| --- | --- |
| Application | Allowed. Its history rows are deleted automatically by the FK cascade. |
| Student with applications | Blocked by the foreign key. Spring converts the database error to `409 DATA_CONFLICT`. |
| Company with job openings | Blocked by the foreign key. |
| Job opening with applications | Blocked by the foreign key. |
| User | No FK references, so deletion is possible at the DB level (there is no delete-user API). |

The UI cannot delete parent records with children; it shows the generic
`This change conflicts with existing records...` message. Delete children first.

### 6.5 Why a separate history table?

The `applications.status` column answers “where is it now?”. The history table
answers:

- Who changed it?
- When?
- What was the previous state?
- Did it ever reach a stage even if it was later rejected?

The last question is why the dashboard funnel is **history-aware**. A candidate who
went `Applied → Shortlisted → Interview → Rejected` still counts as having reached
the interview stage. Without history, the funnel would silently lose that progress.

History is written on:

| Event | History row |
| --- | --- |
| Create application | Initial status, note `Application created` |
| Edit application and change status | New status, note `Application edited` |
| PATCH status | New status, note `Status updated` |
| Demo seeding | Full status path for new seed rows, changedBy `placement.cell` or `admin`, descriptive notes |
| V4 migration backfill | Current status, changedBy `system`, note `Backfilled from current status` |

`GET /api/applications/{id}/history` returns the rows oldest-first as
`ApplicationHistoryResponse` records. The applications table exposes the same data
in a read-only **History** dialog for both roles; the endpoint is intended for audit
review, not for analytics writes. History rows are never updated or deleted
individually; deleting the application cascades them away.

### 6.6 Data-type and time notes

- Dates use `LocalDate` and serialize as `YYYY-MM-DD`.
- Timestamps use `LocalDateTime`; Jackson writes them as ISO-like strings because
  `write-dates-as-timestamps=false`.
- `updated_at`, `changed_at` are set with `LocalDateTime.now()` — the JVM’s local
  clock, not explicitly UTC. The JDBC URL sets `serverTimezone=UTC` for MySQL
  session behavior. If exact cross-time-zone audit times matter, normalize this.
- Status is stored as a human-readable label (`Applied`, `Shortlisted`, …), not a
  numeric enum. The Java enum maps labels case-insensitively.
- `changed_by` is a **string snapshot**, not a foreign key to `users`. Deleting or
  renaming a user does not rewrite history.

---

## 7. Repository structure

### 7.1 Full tree with explanations

```text
Job_Application_Management_System/
│
├── README.md                          # Project overview, quick start, API, analytics
├── PROJECT_GUIDE.md                   # This handbook
├── README_sample.md                   # Unrelated sample README (SkillShift); not used
├── .gitignore                         # Build, Python, IDE, local secrets
├── .gitattributes
│
├── .github/
│   ├── workflows/
│   │   └── ci.yml                     # GitHub Actions: Java tests + Python tests
│   └── modernize/java-upgrade/        # Editor/automation hooks for Java upgrades
│       └── hooks/scripts/recordToolUse.{sh,ps1}
│
├── .vscode/
│   └── settings.json                  # Java null-analysis setting
│
├── docs/
│   ├── assets/logo.svg                # Pathway logo used by the README
│   └── implementation-plan.md         # Phase record, tags, verification, deviations
│
└── RestApi/                           # The application (despite the name, it contains all)
    │
    ├── pom.xml                        # Maven dependencies + Spring Boot parent
    ├── mvnw / mvnw.cmd                # Maven wrapper scripts
    ├── .mvn/wrapper/maven-wrapper.properties
    ├── Dockerfile                     # Multi-stage Spring Boot image
    ├── .dockerignore
    ├── docker-compose.yml             # db + analytics + app
    ├── .env.example                   # Compose environment template
    ├── .gitignore / .gitattributes
    │
    ├── sql/
    │   └── schema.sql                 # Only creates the empty database; Flyway owns tables
    │
    ├── python_etl/                    # Read-only analytics service
    │   ├── analytics.py               # pandas cleaning, dedupe, filters, KPIs, charts
    │   ├── analytics_app.py           # MySQL queries + Flask endpoints
    │   ├── test_analytics.py          # 10 Python unit/HTTP tests
    │   ├── requirements.txt           # pandas, Flask, PyMySQL, Gunicorn
    │   └── Dockerfile                 # Python 3.12-slim + Gunicorn image
    │
    └── src/
        ├── main/
        │   ├── java/com/tcet/RestApi/
        │   │   ├── RestApiApplication.java       # main() / @SpringBootApplication
        │   │   │
        │   │   ├── config/
        │   │   │   ├── AdminSeeder.java          # Seeds admin/viewer if users table empty
        │   │   │   ├── AnalyticsClientConfig.java# RestClient bean → Flask base URL
        │   │   │   └── DemoDataSeeder.java       # Deterministic demo dataset (profile demo)
        │   │   │
        │   │   ├── controller/
        │   │   │   ├── AnalyticsController.java  # /api/analytics summary, dashboard, CSV proxy
        │   │   │   ├── AuthController.java       # /auth/login, /auth/register
        │   │   │   ├── CompanyController.java    # /api/companies CRUD
        │   │   │   ├── JobApplicationController.java # /api/applications CRUD + status + history
        │   │   │   ├── JobOpeningController.java # /api/jobs CRUD
        │   │   │   ├── MetaController.java       # /api/meta: statuses, job types, transitions
        │   │   │   └── StudentController.java    # /api/students CRUD
        │   │   │
        │   │   ├── dto/
        │   │   │   ├── ApplicationHistoryResponse.java # one audit-history row
        │   │   │   ├── ApplicationRequest.java   # create/update application body
        │   │   │   ├── ApplicationResponse.java  # application response with display names
        │   │   │   ├── AuthResponseDTO.java      # login response
        │   │   │   ├── JobOpeningRequest.java    # create/update job body
        │   │   │   ├── JobOpeningResponse.java   # job response with company name
        │   │   │   ├── LoginRequestDTO.java      # username + password
        │   │   │   ├── RegisterRequestDTO.java   # username + password + role
        │   │   │   └── UserResponseDTO.java      # safe user response (no password)
        │   │   │
        │   │   ├── model/
        │   │   │   ├── ApplicationStatus.java    # enum + transition graph + label parsing
        │   │   │   ├── ApplicationStatusHistory.java # history entity
        │   │   │   ├── Company.java              # companies entity
        │   │   │   ├── JobApplication.java       # applications entity
        │   │   │   ├── JobOpening.java           # job_openings entity
        │   │   │   ├── Student.java              # students entity
        │   │   │   └── User.java                 # users entity
        │   │   │
        │   │   ├── repositories/
        │   │   │   ├── ApplicationStatusHistoryRepository.java
        │   │   │   ├── CompanyRepository.java
        │   │   │   ├── JobApplicationRepository.java
        │   │   │   ├── JobOpeningRepository.java
        │   │   │   ├── StudentRepository.java
        │   │   │   └── UserRepository.java
        │   │   │
        │   │   ├── security/
        │   │   │   ├── CustomUserDetailsService.java # DB user → Spring UserDetails
        │   │   │   ├── JwtAuthenticationFilter.java  # reads Bearer token, sets context
        │   │   │   ├── JwtService.java               # sign/parse/validate JWT
        │   │   │   └── SecurityConfig.java           # route rules, BCrypt, 401/403 JSON
        │   │   │
        │   │   ├── service/
        │   │   │   ├── ApplicationService.java       # workflow, eligibility, history, DTOs
        │   │   │   ├── JobOpeningService.java        # job CRUD + company resolution
        │   │   │   └── UserService.java              # register user, hash password, role
        │   │   │
        │   │   └── web/
        │   │       ├── ApiError.java                 # one error record
        │   │       └── ApiExceptionHandler.java      # global @RestControllerAdvice
        │   │
        │   └── resources/
        │       ├── application.properties            # ports, DB, JPA, Flyway, JWT, defaults
        │       ├── db/migration/
        │       │   ├── V1__baseline_schema.sql       # 5 core tables
        │       │   ├── V2__add_dashboard_indexes.sql # indexes
        │       │   ├── V3__add_data_constraints.sql  # check constraints
        │       │   └── V4__application_status_history.sql # history + backfill
        │       └── static/                           # the SPA
        │           ├── index.html                    # login overlay, 5 pages, history dialog
        │           ├── app.js                        # all UI logic, API calls, charts, history
        │           ├── styles.css                    # main layout/components
        │           ├── dashboard.css                 # dashboard, skip link, focus, motion
        │           ├── fonts.css                     # @font-face declarations
        │           ├── fonts/
        │           │   ├── dm-sans.woff2
        │           │   └── manrope.woff2
        │           └── vendor/
        │               ├── chart.umd.min.js          # Chart.js 4.5.1
        │               └── chartjs-LICENSE.md
        │
        └── test/
            ├── java/com/tcet/RestApi/
            │   ├── RestApiApplicationTests.java      # context loads
            │   ├── controller/
            │   │   ├── AnalyticsControllerTest.java  # proxy filters + CSV
            │   │   ├── JobApplicationControllerTest.java # workflow/validation/history
            │   │   └── StudentControllerTest.java    # student CRUD/errors
            │   └── security/
            │       └── SecurityTest.java             # auth/role matrix
            └── resources/
                └── application-test.properties       # H2, Flyway off, test JWT
```

### 7.2 Folder responsibilities

| Folder | Responsibility | Change here when… |
| --- | --- | --- |
| `controller/` | HTTP surface: paths, request params, status codes | Adding/changing an endpoint |
| `service/` | Business rules and transactions | Changing workflow, eligibility, or multi-step operations |
| `repositories/` | Database access | Adding a query or lookup |
| `model/` | Table mappings + the status enum | Changing columns/entities or transition rules |
| `dto/` | Request/response shapes | Changing the API contract |
| `security/` | JWT + authorization | Changing roles, token behavior, public routes |
| `config/` | Startup seeding and the Flask client | Adding a startup task or external service |
| `web/` | Global error handling | Adding a new error mapping |
| `resources/db/migration/` | Schema history | Changing the database (new `V5__*.sql`) |
| `resources/static/` | The whole frontend | UI/UX changes |
| `python_etl/` | Analytics cleaning/aggregation/HTTP | Dashboard metrics or chart data changes |
| `src/test/` | Automated backend tests | Adding coverage |
| `.github/workflows/` | CI | Changing the test pipeline |

### 7.3 Files you will touch most

| Task | Files |
| --- | --- |
| Add a field to a table | new migration, entity, DTO, frontend form/table, tests |
| Add an endpoint | controller, service (if rules), repository, test |
| Change a workflow rule | `ApplicationStatus.java`, `ApplicationService.java`, tests, `/api/meta` consumer |
| Change a dashboard number | `python_etl/analytics.py`, Python tests, possibly `app.js` |
| Change a chart | `app.js` and `dashboard.css` |
| Change auth | `security/`, `AuthController`, `app.js` login handling |
| Change deployment | `Dockerfile`, `python_etl/Dockerfile`, `docker-compose.yml`, `.env.example` |

### 7.4 Suggested reading order (about 45 minutes)

1. `README.md` (this repo) — the product story.
2. `RestApi/src/main/resources/db/migration/*.sql` — the data model.
3. `model/ApplicationStatus.java` — the business workflow in one file.
4. `service/ApplicationService.java` — the rules in action.
5. `controller/JobApplicationController.java` — the HTTP surface.
6. `security/SecurityConfig.java` + `JwtAuthenticationFilter.java` — access control.
7. `python_etl/analytics.py` — the analytics brain.
8. `python_etl/analytics_app.py` — SQL + HTTP.
9. `static/app.js` — the entire frontend.
10. The test files — executable documentation of expected behavior.

---

## 8. Backend deep dive

### 8.1 Layering

```text
HTTP request
   │
   ▼
Controller        @RestController, paths, @Valid, status codes
   │
   ▼
Service           business rules, transactions, orchestration
   │
   ▼
Repository        Spring Data JPA interfaces + @Query/@EntityGraph
   │
   ▼
Entity            @Entity classes mapped to tables
   │
   ▼
Hibernate         generates SQL, manages persistence context
   │
   ▼
MySQL
```

Not every feature uses every layer:

- **Students** and **companies** controllers talk directly to repositories. They
  are simple CRUD with no cross-entity rules.
- **Job openings** use `JobOpeningService` because they must resolve a company and
  return a DTO with the company name.
- **Applications** use `ApplicationService` because they carry the workflow,
  eligibility, duplicate, and history rules.
- **Auth/users** use `UserService` for hashing and role normalization.

This mixed style is a documented deviation in `docs/implementation-plan.md`:
extracting student/company services would add churn without changing behavior.

### 8.2 What happens inside a request

```text
1. Servlet container (Tomcat, embedded)
2. Security filter chain
   ├── CORS (same-origin defaults)
   ├── CSRF disabled
   ├── JwtAuthenticationFilter
   └── authorization rules
3. DispatcherServlet → handler mapping → controller method
4. Argument resolution: @RequestBody + Jackson + @Valid
5. Controller → service → repository
6. Hibernate flush/commit (service @Transactional)
7. Return value → Jackson → JSON
8. Exception? → @RestControllerAdvice
9. Security exception? → entry point / denied handler
```

### 8.3 Controllers

| Controller | Base path | Endpoints | Notes |
| --- | --- | --- | --- |
| `AuthController` | `/auth` | `POST /login`, `POST /register` | Login public; register `@PreAuthorize("hasRole('ADMIN')")` |
| `StudentController` | `/api/students` | `GET`, `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}` | Direct repository; duplicate roll check |
| `CompanyController` | `/api/companies` | `GET`, `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}` | Direct repository; duplicate name check (case-insensitive) |
| `JobOpeningController` | `/api/jobs` | `GET`, `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}` | Optional `?companyId=`; returns DTO |
| `JobApplicationController` | `/api/applications` | `GET`, `GET /{id}`, `GET /{id}/history`, `POST`, `PUT /{id}`, `PATCH /{id}/status`, `DELETE /{id}` | Optional `?studentId=`; service owns rules |
| `MetaController` | `/api/meta` | `GET` | Statuses, job types, allowed transitions |
| `AnalyticsController` | `/api/analytics` | `GET /summary`, `GET /dashboard`, `GET /export.csv` | Summary is JPA; dashboard/CSV proxy to Flask |

Controller conventions:

- `@RestController` = `@Controller` + JSON response body.
- `@RequestMapping` sets the base path.
- `@GetMapping`/`@PostMapping`/… bind HTTP methods.
- `@RequestBody` deserializes JSON.
- `@Valid` triggers Bean Validation.
- `@PathVariable` reads `/…/{id}`.
- `@RequestParam(required = false)` reads query parameters.
- `@ResponseStatus(HttpStatus.CREATED)` / `NO_CONTENT` sets success codes.
- `ResponseStatusException` is the standard way to return a specific HTTP error.

### 8.4 Services

#### `ApplicationService`

The most important class in the backend.

| Method | Responsibility |
| --- | --- |
| `findAll(studentId)` | Load applications with student/job/company via `@EntityGraph`; map to DTOs |
| `findOne(id)` | Load one detailed application or 404 |
| `findHistory(id)` | Verify the application exists, then return its history oldest-first as DTOs |
| `create(request)` | Validate refs, eligibility, details, duplicate; save; write history |
| `update(id, request)` | Validate transition, eligibility, details, duplicate; save; write history only if status changed |
| `updateStatus(id, status)` | Validate transition + details; save; write history if changed |
| `delete(id)` | Delete application (history cascades) |
| `validateEligibility` | Deadline + CGPA |
| `validateDetails` | Interview date / offer package stage rules |
| `validateTransition` | Uses `ApplicationStatus.canTransitionTo` |
| `recordHistory` | Inserts `changedBy` from `SecurityContext` |
| `toResponse` | Maps entity → `ApplicationResponse` with display names |

`@Transactional(readOnly = true)` is on the class; write methods override with
`@Transactional`. This keeps read queries efficient and writes atomic.

#### `JobOpeningService`

- Resolves `companyId` to a `Company` or returns 400.
- Maps entity to `JobOpeningResponse` including `companyName`.
- Uses `findDetailedById`/`findAllDetailed` with `@EntityGraph(attributePaths = "company")`
  so the lazy company is loaded before mapping.

#### `UserService`

- Trims the username, rejects duplicates case-insensitively.
- BCrypt-hashes the password.
- Normalizes role to `ADMIN`/`VIEWER`; blank defaults to `VIEWER`.
- Returns the saved `User`.

### 8.5 Repositories

| Repository | Key methods |
| --- | --- |
| `StudentRepository` | `existsByRollNo`, `existsByRollNoAndIdNot` + inherited CRUD |
| `CompanyRepository` | `existsByCompanyNameIgnoreCase`, `existsByCompanyNameIgnoreCaseAndIdNot` |
| `JobOpeningRepository` | `findAllDetailed`, `findByCompanyId`, `findDetailedById` (all with company `@EntityGraph`) |
| `JobApplicationRepository` | `findAllDetailed`, `findByStudentId`, `findDetailedById` (student/job/company graph), `countByStatusIgnoreCase`, duplicate checks |
| `ApplicationStatusHistoryRepository` | `findByApplication_IdOrderByChangedAtAscIdAsc` |
| `UserRepository` | `findByUsername`, `existsByUsernameIgnoreCase` |

Why `@EntityGraph`? `spring.jpa.open-in-view=false` means lazy loading after the
transaction would fail. The graph tells Hibernate to fetch the associations in the
same query, avoiding `LazyInitializationException` and N+1 selects.

### 8.6 Entities

| Entity | Table | Notable annotations |
| --- | --- | --- |
| `Student` | `students` | `@Entity`, `@Table`, Lombok getters/setters, `@NotBlank`, `@Email`, `@DecimalMin/Max` |
| `Company` | `companies` | unique `company_name`, website `@Pattern` |
| `JobOpening` | `job_openings` | `@ManyToOne Company`, package/min CGPA bounds |
| `JobApplication` | `applications` | `@ManyToOne Student`, `@ManyToOne JobOpening`, unique constraint, `@PrePersist/@PreUpdate` timestamp |
| `ApplicationStatusHistory` | `application_status_history` | `@ManyToOne JobApplication`, status string, changed fields |
| `User` | `users` | `@JsonIgnore` password, unique username |
| `ApplicationStatus` | — (enum) | labels + transition map + `fromLabel` |

`ApplicationStatus` is the workflow’s single source of truth:

```java
APPLIED      → {SHORTLISTED, REJECTED, WITHDRAWN}
SHORTLISTED  → {INTERVIEW, REJECTED, WITHDRAWN}
INTERVIEW    → {OFFERED, REJECTED, WITHDRAWN}
OFFERED      → {ACCEPTED, REJECTED, WITHDRAWN}
ACCEPTED     → {}
REJECTED     → {}
WITHDRAWN    → {}
```

`canTransitionTo(next)` returns `true` when `next == current` as well, so a PATCH or
edit that keeps the same status is allowed.

### 8.7 DTOs

| DTO | Direction | Fields |
| --- | --- | --- |
| `LoginRequestDTO` | in | username, password |
| `RegisterRequestDTO` | in | username (3–50), password (8–72), role (`ADMIN`/`VIEWER`) |
| `AuthResponseDTO` | out | token, tokenType, expiresIn, username, role |
| `UserResponseDTO` | out | id, username, role |
| `JobOpeningRequest` | in | companyId, jobTitle, jobType, packageLpa, minCgpa, postedDate, deadline |
| `JobOpeningResponse` | out | id, companyId, companyName, jobTitle, jobType, packageLpa, minCgpa, postedDate, deadline |
| `ApplicationRequest` | in | studentId, jobId, appliedDate, status, interviewDate, offerPackageLpa, notes |
| `ApplicationResponse` | out | id, studentId, studentName, jobId, jobTitle, companyId, companyName, appliedDate, status, interviewDate, offerPackageLpa, notes, updatedAt |
| `ApplicationHistoryResponse` | out | id, applicationId, status, changedAt, changedBy, note |

Why DTOs for jobs/applications? They avoid exposing lazy JPA proxies and flatten the
display names the UI needs (`studentName`, `companyName`) into one response. The
history DTO also keeps the audit response independent of the JPA entity graph.

Students and companies are returned as entities directly because they have no
relationships and no sensitive fields.

### 8.8 Global exception handling

`web/ApiExceptionHandler.java` is a `@RestControllerAdvice`. It maps:

| Exception | HTTP | Code |
| --- | --- | --- |
| `ResponseStatusException` | its status | `NOT_FOUND`, `CONFLICT`, `BAD_REQUEST`, … |
| `MethodArgumentNotValidException` | 400 | `VALIDATION_FAILED` + field errors |
| `HttpMessageNotReadableException` | 400 | `MALFORMED_REQUEST` |
| `AuthenticationException` | 401 | `UNAUTHORIZED` |
| `AccessDeniedException` | 403 | `FORBIDDEN` |
| `DataIntegrityViolationException` | 409 | `DATA_CONFLICT` |
| any other `Exception` | 500 | `INTERNAL_ERROR` |

`ApiError` fields: `timestamp`, `status`, `code`, `message`, `path`, `fieldErrors`.

### 8.9 Transactions, lazy loading, and N+1

- Service read methods run in read-only transactions.
- Write methods are transactional; the application row and its history row commit
  together. If history insert fails, the status change rolls back.
- `open-in-view=false` prevents accidental lazy loads in the view layer.
- `@EntityGraph` fetches the associations needed for DTO mapping.
- The application list query loads student, job, and job.company; the job list query
  loads company.

### 8.10 Annotation cheat sheet

| Annotation | Meaning here |
| --- | --- |
| `@SpringBootApplication` | Enables component scan, auto-configuration, and configuration |
| `@RestController` | JSON controller |
| `@RequestMapping` | Base URL for a controller |
| `@GetMapping`, `@PostMapping`, `@PutMapping`, `@PatchMapping`, `@DeleteMapping` | HTTP method bindings |
| `@RequestBody` | Parse JSON body |
| `@PathVariable` | Read `{id}` from the URL |
| `@RequestParam` | Read `?key=value` |
| `@Valid` | Run Bean Validation |
| `@ResponseStatus` | Set success status code |
| `@Service` | Business-layer Spring bean |
| `@Repository` (implicit) | Spring Data repository |
| `@Transactional` | Wrap method in a DB transaction |
| `@Entity`, `@Table` | Map class to table |
| `@Id`, `@GeneratedValue` | Primary key + auto-increment |
| `@Column`, `@JoinColumn` | Column/foreign-key mapping |
| `@ManyToOne` | Many rows → one parent |
| `@EntityGraph` | Fetch associations eagerly for a query |
| `@PrePersist`, `@PreUpdate` | Lifecycle callbacks (timestamp) |
| `@NotBlank`, `@NotNull`, `@Email`, `@Size`, `@DecimalMin`, `@DecimalMax`, `@Pattern` | Validation |
| `@RestControllerAdvice`, `@ExceptionHandler` | Global error mapping |
| `@Profile` | Activate a bean only for a Spring profile |
| `@ConditionalOnProperty` | Activate a bean only when a property matches |
| `@PreAuthorize` | Method-level role check |
| `@Value` | Inject a property/environment value |
| `@Configuration`, `@Bean` | Define a Spring bean |
| `@RequiredArgsConstructor` | Lombok generates a constructor for final fields (constructor injection) |

---

## 9. Security and access control

### 9.1 Permission matrix

| Action | Public | VIEWER | ADMIN |
| --- | --- | --- | --- |
| Open the SPA (`/`, `index.html`, CSS, JS, fonts, Chart.js) | ✅ | ✅ | ✅ |
| `POST /auth/login` | ✅ | ✅ | ✅ |
| `GET /actuator/health` | ✅ | ✅ | ✅ |
| `GET /api/**` (students, companies, jobs, applications, meta, analytics, CSV) | ❌ 401 | ✅ | ✅ |
| `POST` / `PUT` / `PATCH` / `DELETE` on `/api/**` | ❌ 401 | ❌ 403 | ✅ |
| `POST /auth/register` | ❌ 401 | ❌ 403 | ✅ |
| Dashboard and CSV export | ❌ 401 | ✅ | ✅ |

The frontend also hides write controls for viewers, but the backend is the real
enforcement point. A viewer who edits `sessionStorage` to claim `ADMIN` will see the
buttons but get `403` from every write.

### 9.2 JWT in plain English

A JWT is like a signed wristband:

- The server creates it after a correct username/password.
- It contains the username and an expiry time.
- It is signed with a secret key, so the client cannot alter it undetected.
- The client sends it on every request in the `Authorization` header.
- The server verifies the signature and expiry, loads the user, and decides.

Token anatomy:

```text
header.payload.signature
  │       │        │
  │       │        └── HMAC-SHA signature using JWT_SECRET
  │       └────────── { "sub": "admin", "iat": ..., "exp": ... }
  └────────────────── { "alg": "HMAC-SHA (variant chosen from key length)" }
```

JJWT picks an HMAC-SHA variant (HS256/HS384/HS512) based on the secret length, so
the exact `alg` header depends on your `JWT_SECRET`.

Notes:

- The **role is not in the token**. It is loaded from `users.role` on each request.
- The secret must be at least 32 bytes; `JwtService` refuses to start otherwise.
- Default lifetime is `3,600,000 ms` = **1 hour**.
- There is no refresh token and no revocation list. Logout only deletes the browser
  copy; the token remains valid until it expires.
- `sessionStorage` is per-tab and is cleared when the tab closes. It is readable by
  JavaScript, so XSS would be dangerous; the SPA escapes rendered values and
  validates URLs, but there is no Content-Security-Policy.

### 9.3 Login and registration

**Login** (`POST /auth/login`, public):

```json
{"username": "admin", "password": "ChangeMe123!"}
```

Response `200`:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "username": "admin",
  "role": "ADMIN"
}
```

**Register** (`POST /auth/register`, admin only):

```json
{"username": "placement_officer", "password": "a-strong-password", "role": "ADMIN"}
```

Rules:

- username 3–50 characters, unique (case-insensitive check)
- password 8–72 characters, BCrypt-hashed
- role must be `ADMIN` or `VIEWER`; blank defaults to `VIEWER`
- returns `201` with `{id, username, role}` — never the password

### 9.4 Security filter chain

`SecurityConfig` configures:

| Setting | Value | Why |
| --- | --- | --- |
| CSRF | disabled | Bearer tokens, no cookies |
| CORS | defaults (no cross-origin mappings) | SPA is same-origin behind Spring |
| Session | `STATELESS` | JWT, no server session |
| Form login | disabled | SPA uses JSON login |
| HTTP Basic | disabled | JWT only |
| Logout | disabled | Handled client-side |
| Password encoder | BCrypt | Standard slow hash |

Public paths:

```text
/  /index.html  /styles.css  /dashboard.css  /fonts.css  /app.js
/vendor/**  /fonts/**  /favicon.ico  /error
/auth/login
/actuator/health  /actuator/health/**
```

Authorization order:

1. `GET /api/**` → `ADMIN` or `VIEWER`
2. any other `/api/**` → `ADMIN`
3. anything else → authenticated

`/auth/register` is not under `/api`, so it falls into “anything else →
authenticated”, and then `@PreAuthorize("hasRole('ADMIN')")` adds the admin check.

### 9.5 401 vs 403

| Situation | Status | Message |
| --- | --- | --- |
| No token on a protected route | `401` | `Sign in to continue.` |
| Expired/invalid token on a protected route | `401` | `Sign in to continue.` |
| Wrong username/password on login | `401` | `Invalid username or password.` |
| Authenticated viewer tries a write | `403` | `You do not have permission to perform this action.` |
| Viewer tries to register a user | `403` | same |

Both security failures are written as the same `ApiError` JSON shape by
`SecurityConfig`, so the SPA can handle them uniformly.

### 9.6 Seeded accounts

`AdminSeeder` runs on startup for every profile except `test`:

- If the `users` table already has **any** row, it does nothing. This means it will
  not top up a missing viewer on an existing database.
- Otherwise it creates the admin and viewer if both username and password are
  non-blank.
- Passwords are BCrypt-hashed.
- It logs a warning telling you to change the defaults.

Defaults (development only):

| Username | Password | Role |
| --- | --- | --- |
| `admin` | `ChangeMe123!` | ADMIN |
| `viewer` | `viewer123` | VIEWER |

Override with `ADMIN_USERNAME`, `ADMIN_PASSWORD`, `VIEWER_USERNAME`,
`VIEWER_PASSWORD` before exposing the app.

### 9.7 Security limitations and production checklist

Current limitations:

- No refresh tokens, token revocation, or “log out everywhere”.
- No rate limiting or account lockout on login.
- No password reset or email verification.
- No per-user data ownership; every admin sees everything.
- No audit of read access or failed logins.
- No Content-Security-Policy or security headers configured in Spring.
- The Compose stack uses the MySQL **root** account for both app and analytics.
- Demo credentials and `SPRING_PROFILES_ACTIVE=demo` must not be used in production.

Before a real deployment:

1. Change `JWT_SECRET`, `DB_PASSWORD`, and both seeded passwords.
2. Remove the demo profile or set `APP_DEMO_SEED=false`.
3. Put TLS and a reverse proxy in front of `8080`.
4. Create a least-privilege MySQL user; do not use root.
5. Back up the MySQL volume.
6. Add rate limiting, refresh/revocation, and security headers if the app is public.
7. Keep Flask and MySQL off the public network.

---

## 10. Application workflow (state machine)

### 10.1 Diagram

```text
                         ┌────────────┐
                         │  Applied   │
                         └─────┬──────┘
                               │ Shortlist
                               ▼
                         ┌────────────┐
                         │Shortlisted │
                         └─────┬──────┘
                               │ Interview
                               ▼
                         ┌────────────┐
                         │ Interview  │
                         └─────┬──────┘
                               │ Offer
                               ▼
                         ┌────────────┐
                         │  Offered   │
                         └─────┬──────┘
                               │ Accept
                               ▼
                         ┌────────────┐
                         │  Accepted  │  (terminal)
                         └────────────┘

At every non-terminal stage:
   Applied / Shortlisted / Interview / Offered
        ├──► Rejected   (terminal)
        └──► Withdrawn  (terminal)

Accepted, Rejected, and Withdrawn cannot move anywhere.
```

### 10.2 Transition table

| Current status | Allowed next statuses |
| --- | --- |
| `Applied` | `Shortlisted`, `Rejected`, `Withdrawn` |
| `Shortlisted` | `Interview`, `Rejected`, `Withdrawn` |
| `Interview` | `Offered`, `Rejected`, `Withdrawn` |
| `Offered` | `Accepted`, `Rejected`, `Withdrawn` |
| `Accepted` | none |
| `Rejected` | none |
| `Withdrawn` | none |

The same status is always allowed (`canTransitionTo` returns true for `this == next`),
so re-saving without a change is harmless and does not add a history row.

### 10.3 Rules and where they live

| Rule | Enforced in | Error |
| --- | --- | --- |
| Required fields | Bean Validation + DB `NOT NULL` | 400 `VALIDATION_FAILED` |
| CGPA 0–10 | Bean Validation + DB check | 400 / 409 |
| Graduation year 2000–2100 | DB check only | 409 `DATA_CONFLICT` |
| Unique roll number | Controller check + DB unique | 409 `CONFLICT` |
| Unique company name | Controller check (case-insensitive) + DB unique | 409 `CONFLICT` |
| One application per student/job | Service check + DB unique | 409 `CONFLICT` |
| Deadline not passed | `ApplicationService` | 409 `CONFLICT` |
| CGPA ≥ job minimum | `ApplicationService` | 409 `CONFLICT` |
| Legal status transition | `ApplicationStatus` + service | 409 `CONFLICT` |
| Interview date only from `Shortlisted` onward | `ApplicationService` | 400 |
| Offer package only for `Offered`/`Accepted` | `ApplicationService` | 400 |
| Parent delete blocked | DB foreign keys | 409 `DATA_CONFLICT` |
| Role permissions | `SecurityConfig` + `@PreAuthorize` | 401/403 |

The database rules are the last line of defence. Even if a client bypasses the UI
and calls the API directly, the service checks run. Even if the service has a gap,
the DB constraints catch the worst cases.

### 10.4 Create vs transition semantics

**Create (`POST /api/applications`) accepts any initial status.** This is on
purpose:

- A placement cell may be importing records that are already at `Interview` or
  `Accepted`.
- Historical imports can create records at their current stage instead of replaying
  every transition. The demo seeder writes its applications and histories through
  repositories, but follows the same “current stage plus history” idea.
- The detail rules still apply: if you create with status `Applied`, you cannot
  attach an interview date or offer package.

**Update (`PUT`)** can change the status only along a legal transition. It also
re-checks eligibility for the (possibly new) student/job/date and details for the
new status.

**PATCH status** only changes the status; it validates the transition and the
details already stored on the record.

### 10.5 What the frontend does

`GET /api/meta` returns a `transitions` map. When editing an existing application,
`statusChoices(current)` returns `[current, ...allowedNext]`, so the dropdown cannot
offer an illegal jump. When creating a new application, the form offers all
statuses, because historical import is allowed.

The UI cannot prevent a crafted API call; the server remains authoritative.

### 10.6 Known sharp edge: terminal transitions with details

This is current behavior worth knowing before you demo or extend the workflow.

`ApplicationService.updateStatus` validates the **stored** `interviewDate` and
`offerPackageLpa` against the **new** status:

```java
validateDetails(nextStatus, item.getInterviewDate(), item.getOfferPackageLpa());
```

`Rejected` and `Withdrawn` are not in `INTERVIEW_STAGES` or `OFFER_STAGES`. So:

- `Interview → Rejected` on an application that already has an interview date
  returns `400` (“Interview date is only valid from Shortlisted onward”).
- `Offered → Rejected` on an application that already has an offer package
  returns `400` (“Offer package is only valid for Offered or Accepted applications”).
- Editing (`PUT`) such a record has the same problem because the form resubmits the
  stored date/package.

This looks unintended. A sensible fix is to clear details when entering a terminal
state, or to validate only values being *newly supplied*. Until then, clear the
interview date/package before rejecting, or reject before setting those fields.
Document this in the team’s issue tracker; do not assume the workflow diagram is
fully achievable through every path.

### 10.7 History semantics

- Every create writes one row.
- Every status change writes one row.
- Editing other fields without changing status writes no history row.
- PATCHing the same status writes no history row.
- `changedBy` comes from the authenticated username; demo seeding uses
  `placement.cell`/`admin`, and migration backfill uses `system`.
- History rows are never updated or deleted individually. Deleting the application
  cascades them away.

The history table is what powers the **history-aware funnel**, explained in
[section 12](#12-analytics-and-etl).

---

## 11. CRUD and API reference

### 11.1 Conventions

- Base URL in local development: `http://localhost:8080`.
- All data endpoints except `/auth/login` and `/actuator/health` require
  `Authorization: Bearer <token>`.
- Request and response bodies are JSON, except CSV export.
- Dates are `YYYY-MM-DD`.
- `POST` returns `201` with the created record.
- `DELETE` returns `204` with no body.
- `GET` list endpoints return a JSON array; there is no pagination.
- Missing records return `404` with a message like `Student not found`.
- IDs are database-generated numbers; the UI formats applications as `APP-0001`.

### 11.2 Students

**Purpose:** the people who apply.

Entity fields:

| Field | Type | Required | Validation |
| --- | --- | --- | --- |
| `rollNo` | string | yes | not blank, unique |
| `fullName` | string | yes | not blank |
| `email` | string | no | valid email if present |
| `department` | string | yes | not blank |
| `cgpa` | number | no | 0–10 |
| `graduationYear` | integer | no | DB check 2000–2100 |

Endpoints:

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/students` | List all students |
| `GET` | `/api/students/{id}` | One student |
| `POST` | `/api/students` | Create |
| `PUT` | `/api/students/{id}` | Update |
| `DELETE` | `/api/students/{id}` | Delete |

Create example:

```bash
curl -X POST http://localhost:8080/api/students \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "rollNo": "IT001",
    "fullName": "Asha Rao",
    "email": "asha.rao@example.com",
    "department": "Information Technology",
    "cgpa": 8.4,
    "graduationYear": 2027
  }'
```

Response `201`:

```json
{
  "id": 1,
  "rollNo": "IT001",
  "fullName": "Asha Rao",
  "email": "asha.rao@example.com",
  "department": "Information Technology",
  "cgpa": 8.4,
  "graduationYear": 2027
}
```

Rules:

- Duplicate roll number → `409 Roll number already exists`.
- `PUT` replaces all fields; omitted optional fields become `null`.
- Delete is blocked if the student has applications.
- There is no server-side search or pagination; the UI filters the loaded array.

### 11.3 Companies

**Purpose:** employers that post jobs.

| Field | Type | Required | Validation |
| --- | --- | --- | --- |
| `companyName` | string | yes | not blank, unique (case-insensitive check) |
| `industry` | string | no | — |
| `location` | string | no | — |
| `website` | string | no | empty or starts with `http://` / `https://` |

Endpoints: `GET /api/companies`, `GET /api/companies/{id}`, `POST /api/companies`,
`PUT /api/companies/{id}`, `DELETE /api/companies/{id}`.

Create example:

```json
{
  "companyName": "Nimbus Systems",
  "industry": "Software",
  "location": "Bengaluru",
  "website": "https://nimbus.example.com"
}
```

Rules:

- Duplicate name → `409 Company name already exists`.
- Delete is blocked if the company has job openings.
- The UI only linkifies websites that parse as `http:` or `https:` (`safeUrl`).

### 11.4 Job openings

**Purpose:** the roles students apply to.

| Field | Type | Required | Validation |
| --- | --- | --- | --- |
| `companyId` | number | yes | must reference an existing company |
| `jobTitle` | string | yes | not blank |
| `jobType` | string | yes | not blank; UI offers `Full-time`, `Internship`, `Part-time`, `Contract` |
| `packageLpa` | number | no | ≥ 0 |
| `minCgpa` | number | no | 0–10 |
| `postedDate` | date | no | — |
| `deadline` | date | no | — |

Endpoints:

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/jobs` | List all; optional `?companyId=` |
| `GET` | `/api/jobs/{id}` | One job |
| `POST` | `/api/jobs` | Create |
| `PUT` | `/api/jobs/{id}` | Update |
| `DELETE` | `/api/jobs/{id}` | Delete |

Create example:

```json
{
  "companyId": 1,
  "jobTitle": "Java Developer",
  "jobType": "Full-time",
  "packageLpa": 8.5,
  "minCgpa": 7.0,
  "postedDate": "2026-10-01",
  "deadline": "2026-12-31"
}
```

Response `201`:

```json
{
  "id": 1,
  "companyId": 1,
  "companyName": "Nimbus Systems",
  "jobTitle": "Java Developer",
  "jobType": "Full-time",
  "packageLpa": 8.5,
  "minCgpa": 7.0,
  "postedDate": "2026-10-01",
  "deadline": "2026-12-31"
}
```

Rules:

- Missing company → `400 Select an existing company`.
- Delete is blocked if applications exist.
- `jobType` is a free string at the API level; the UI’s list comes from `/api/meta`.
- There is no check that `postedDate <= deadline`.

### 11.5 Applications

**Purpose:** the link between a student and a job, plus the hiring state.

| Field | Type | Required | Validation |
| --- | --- | --- | --- |
| `studentId` | number | yes | existing student |
| `jobId` | number | yes | existing job |
| `appliedDate` | date | yes | must not be after the job deadline |
| `status` | string | yes | one of the seven labels, case-insensitive |
| `interviewDate` | date | no | only allowed for `Shortlisted`, `Interview`, `Offered`, `Accepted` |
| `offerPackageLpa` | number | no | ≥ 0; only for `Offered`, `Accepted` |
| `notes` | string | no | max 500 chars |

Endpoints:

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/applications` | List all; optional `?studentId=` |
| `GET` | `/api/applications/{id}` | One application |
| `GET` | `/api/applications/{id}/history` | Ordered audit history |
| `POST` | `/api/applications` | Create |
| `PUT` | `/api/applications/{id}` | Update (including a legal status change) |
| `PATCH` | `/api/applications/{id}/status` | Change status |
| `DELETE` | `/api/applications/{id}` | Delete (history cascades) |

Create example:

```json
{
  "studentId": 1,
  "jobId": 1,
  "appliedDate": "2026-10-22",
  "status": "Applied",
  "notes": "Referred by the placement cell"
}
```

Response `201`:

```json
{
  "id": 1,
  "studentId": 1,
  "studentName": "Asha Rao",
  "jobId": 1,
  "jobTitle": "Java Developer",
  "companyId": 1,
  "companyName": "Nimbus Systems",
  "appliedDate": "2026-10-22",
  "status": "Applied",
  "interviewDate": null,
  "offerPackageLpa": null,
  "notes": "Referred by the placement cell",
  "updatedAt": "2026-10-22T15:00:00"
}
```

Status change:

```bash
curl -X PATCH http://localhost:8080/api/applications/1/status \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status": "Shortlisted"}'
```

Rules:

- Duplicate `(studentId, jobId)` → `409 This student already applied for this job`.
- CGPA below the job minimum → `409 Student CGPA X is below the job minimum of Y`.
- Applied date after the deadline → `409 The application deadline has passed`.
- Illegal transition → `409 Cannot move from X to Y. Allowed next: ...`.
- Terminal status → `409 An application in X is closed and cannot change status.`
- Interview/offer detail violations → `400`.
- Missing student/job → `400` with a “Select an existing …” message.
- `PUT` requires all required fields; optional fields omitted become `null`.
- `PATCH` with an empty body → `400` with `fieldErrors.status`.

History response:

```json
[
  {
    "id": 1,
    "applicationId": 1,
    "status": "Applied",
    "changedAt": "2026-08-03T12:20:00",
    "changedBy": "placement.cell",
    "note": "Application received"
  },
  {
    "id": 2,
    "applicationId": 1,
    "status": "Shortlisted",
    "changedAt": "2026-08-09T12:38:00",
    "changedBy": "placement.cell",
    "note": "Shortlisted after resume screening"
  }
]
```

History is ordered by `changedAt`, then `id`. A missing application returns `404`;
an application with no rows (possible only with manual database work) returns `[]`.
Both roles can read it because it is a `GET /api/**` route.

### 11.6 Auth endpoints

| Method | Path | Role | Description |
| --- | --- | --- | --- |
| `POST` | `/auth/login` | public | Returns token, type, expiry, username, role |
| `POST` | `/auth/register` | ADMIN | Creates a user |

### 11.7 Meta endpoint

`GET /api/meta` returns the vocabulary the UI needs:

```json
{
  "statuses": ["Applied", "Shortlisted", "Interview", "Offered", "Accepted", "Rejected", "Withdrawn"],
  "jobTypes": ["Full-time", "Internship", "Part-time", "Contract"],
  "transitions": {
    "Applied": ["Rejected", "Shortlisted", "Withdrawn"],
    "Shortlisted": ["Interview", "Rejected", "Withdrawn"],
    "Interview": ["Offered", "Rejected", "Withdrawn"],
    "Offered": ["Accepted", "Rejected", "Withdrawn"],
    "Accepted": [],
    "Rejected": [],
    "Withdrawn": []
  }
}
```

The transition values are sorted alphabetically, not in pipeline order. The UI uses
them to limit the edit dropdown.

### 11.8 Analytics endpoints

| Method | Path | Source | History-aware? | Notes |
| --- | --- | --- | --- | --- |
| `GET` | `/api/analytics/summary` | Spring Data JPA | ❌ | Current entity/status counts |
| `GET` | `/api/analytics/dashboard` | Flask/pandas via proxy | ✅ | Filtered dashboard payload |
| `GET` | `/api/analytics/export.csv` | Flask/pandas via proxy | n/a | Cleaned CSV, ignores filters |

`GET /api/analytics/summary` returns:

```json
{
  "students": 24,
  "companies": 15,
  "jobOpenings": 22,
  "applications": 86,
  "interviews": 9,
  "offers": 16,
  "accepted": 9,
  "rejected": 11
}
```

Those are the current demo values; `interviews` counts **current** `Interview` rows,
not everyone who ever reached an interview. `offers` counts current `Offered` plus
`Accepted` applications. The SPA uses the Flask dashboard payload for its cards, so
`/summary` is available to API consumers but not used by the UI.

### 11.9 Complete endpoint table

| Method | Path | Auth | Description |
| --- | --- | --- | --- |
| `POST` | `/auth/login` | public | Login |
| `POST` | `/auth/register` | ADMIN | Create user |
| `GET` | `/api/meta` | any authenticated | Statuses, job types, transitions |
| `GET` | `/api/students` | any authenticated | List students |
| `GET` | `/api/students/{id}` | any authenticated | Get student |
| `POST` | `/api/students` | ADMIN | Create student |
| `PUT` | `/api/students/{id}` | ADMIN | Update student |
| `DELETE` | `/api/students/{id}` | ADMIN | Delete student |
| `GET` | `/api/companies` | any authenticated | List companies |
| `GET` | `/api/companies/{id}` | any authenticated | Get company |
| `POST` | `/api/companies` | ADMIN | Create company |
| `PUT` | `/api/companies/{id}` | ADMIN | Update company |
| `DELETE` | `/api/companies/{id}` | ADMIN | Delete company |
| `GET` | `/api/jobs` | any authenticated | List jobs; optional `?companyId=` |
| `GET` | `/api/jobs/{id}` | any authenticated | Get job |
| `POST` | `/api/jobs` | ADMIN | Create job |
| `PUT` | `/api/jobs/{id}` | ADMIN | Update job |
| `DELETE` | `/api/jobs/{id}` | ADMIN | Delete job |
| `GET` | `/api/applications` | any authenticated | List applications; optional `?studentId=` |
| `GET` | `/api/applications/{id}` | any authenticated | Get application |
| `GET` | `/api/applications/{id}/history` | any authenticated | Ordered status history |
| `POST` | `/api/applications` | ADMIN | Create application |
| `PUT` | `/api/applications/{id}` | ADMIN | Update application |
| `PATCH` | `/api/applications/{id}/status` | ADMIN | Change status |
| `DELETE` | `/api/applications/{id}` | ADMIN | Delete application |
| `GET` | `/api/analytics/summary` | any authenticated | JPA entity/status counts |
| `GET` | `/api/analytics/dashboard` | any authenticated | Filtered, history-aware dashboard |
| `GET` | `/api/analytics/export.csv` | any authenticated | CSV download |
| `GET` | `/actuator/health` | public | Health |
| `GET` | `:5000/health` | internal | Flask DB-aware health |
| `GET` | `:5000/dashboard` | internal | Raw dashboard JSON |
| `GET` | `:5000/export.csv` | internal | Raw CSV |

### 11.10 Error envelope

Every API failure (including security failures) uses:

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

Validation failures add field messages:

```json
{
  "timestamp": "2026-10-22T15:00:00Z",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Please correct the highlighted fields.",
  "path": "/api/applications",
  "fieldErrors": {
    "studentId": "Select an existing student",
    "appliedDate": "Applied date is required"
  }
}
```

### 11.11 HTTP status codes used

| Code | Meaning in this project |
| --- | --- |
| `200` | Successful read/update/PATCH |
| `201` | Created (student, company, job, application, user) |
| `204` | Deleted |
| `400` | Validation error, malformed JSON, bad status, bad reference, detail rule |
| `401` | Missing/expired token, bad login |
| `403` | Authenticated but wrong role |
| `404` | Record not found |
| `409` | Duplicate, eligibility failure, illegal transition, DB constraint |
| `500` | Unexpected error, downstream analytics failure |
| `503` | Flask health says database unavailable (internal) |

---

## 12. Analytics and ETL

### 12.1 What “ETL” means in this repository

The folder is called `python_etl/`, but this is **not** a scheduled warehouse
pipeline. There is no Airflow, no cron, and no separate analytics database. It is a
**request-time, read-only ETL**:

| Stage | Here |
| --- | --- |
| **Extract** | Run two `SELECT` queries against MySQL through PyMySQL |
| **Transform** | Clean, deduplicate, filter, rank, and aggregate with pandas |
| **Load** | Return JSON to Spring/the browser, or CSV to a download |

It runs fresh on every `/dashboard` or `/export.csv` request. The dataset is small,
so recomputation is simpler and always current. The trade-off is repeated reads and
no cache.

### 12.2 Pipeline

```text
MySQL 8.4
  │
  │  APPLICATIONS_QUERY
  │    students LEFT JOIN applications
  │      LEFT JOIN job_openings
  │      LEFT JOIN companies
  │  HISTORY_QUERY
  │    application_status_history
  ▼
PyMySQL (one connection, DictCursor)
  │
  ▼
list[dict] records + list[dict] history
  │
  ▼
pandas.DataFrame
  │  clean_records()
  │    select columns · coerce types · trim text
  │    normalize status · normalize department
  │    dedupe applications by id
  │    dedupe students by id
  ▼
applications frame + students frame
  │  build_dashboard(...)
  │    apply filters
  │    attach furthest stage from history
  │    group / count / median
  ▼
Python dict
  │
  ├── Flask jsonify → Spring proxy → browser → Chart.js
  └── DataFrame.to_csv → Spring proxy → browser download
```

### 12.3 Extract: the exact SQL

`analytics_app.py` defines:

```sql
SELECT
    a.id AS application_id,
    a.applied_date,
    a.status,
    a.offer_package_lpa,
    s.id AS student_id,
    s.department,
    s.graduation_year,
    j.job_type,
    c.company_name
FROM students s
LEFT JOIN applications a ON a.student_id = s.id
LEFT JOIN job_openings j ON j.id = a.job_id
LEFT JOIN companies c ON c.id = j.company_id
```

Why this shape?

- It starts from **students**, so a student with zero applications still appears
  once, with `application_id = NULL`. That is how the student total and
  placement-rate denominator include unplaced students.
- `LEFT JOIN` (not `INNER JOIN`) is essential; an inner join would silently drop
  students who have not applied.
- It flattens the display fields the dashboard needs (`department`,
  `graduation_year`, `job_type`, `company_name`) into one row per application.
- There is **no `WHERE` clause**. Filtering happens later in pandas. This keeps the
  query simple and lets one load serve all filter combinations.

The history query:

```sql
SELECT application_id, status, changed_at
FROM application_status_history
ORDER BY application_id, changed_at, id
```

Both queries run on the same PyMySQL connection, which is closed in a `finally`
block. Timeouts: connect 5s, read 10s, write 10s. `DictCursor` returns rows as
dictionaries, which `pd.DataFrame.from_records` consumes directly.

### 12.4 Transform: cleaning rules

`clean_records(records)` returns two DataFrames: applications and students.

#### Step 1 — select a stable schema

It keeps only:

```text
application_id, applied_date, status, student_id, department,
graduation_year, job_type, company_name, offer_package_lpa
```

Any missing column is added as `pd.NA`, so the code does not crash on partial data.

#### Step 2 — coerce types

| Column | Rule |
| --- | --- |
| `application_id` | numeric, nullable `Int64` |
| `applied_date` | `pd.to_datetime`, invalid → `NaT` |
| `graduation_year` | numeric, nullable `Int64` |
| `offer_package_lpa` | numeric, invalid → `NaN` |

Bad values become null rather than breaking the dashboard.

#### Step 3 — normalize text

For `department`, `company_name`, `job_type`:

1. cast to string
2. `str.strip()`
3. collapse repeated whitespace with `\s+` → one space
4. empty string → `"Not specified"`

Department naming:

- Four characters or fewer → uppercase (`it` → `IT`, `cse` → `CSE`)
- Longer → title case (`information technology` → `Information Technology`)

This makes filter matching predictable and makes chart labels readable.

#### Step 4 — normalize status

- Match case-insensitively (`" offered "` → `Offered`).
- The allowed set is the seven labels plus `Unknown`.
- Anything unrecognized becomes `Unknown`.

`Unknown` appears in the status distribution with a count (possibly zero) instead
of vanishing.

#### Step 5 — deduplicate

| Frame | Key | Behavior |
| --- | --- | --- |
| Applications | `application_id` | Drop rows with null ID, keep first duplicate |
| Students | `student_id` | Drop rows with null ID, keep first row per student |

The database already enforces one row per application and one row per student, so
this is defensive. It also protects the CSV from accidental duplicate joins.

### 12.5 Filter semantics

`build_dashboard(records, department, graduation_year, status, history)`

| Filter | Applied to applications? | Applied to students? | Consequence |
| --- | --- | --- | --- |
| `department` | ✅ case-insensitive | ✅ case-insensitive | Both numerator and denominator follow the selection |
| `graduationYear` | ✅ exact | ✅ exact | Same |
| `status` | ✅ case-insensitive | ❌ | Cards/funnel narrow, but the placement-rate denominator stays the whole department/year cohort |

That last row is important. If you filter to `status=Offered`, the **students** KPI
and placement rate still describe all students in the selected department/year, not
only students with an offer. This is intentional: otherwise the rate would be
meaningless (for a single status, the denominator would collapse).

Filter dropdown options come from the **unfiltered** student frame:

- `filters.departments` = sorted unique department names
- `filters.graduationYears` = sorted unique integer years

So the options do not disappear as you narrow the dashboard.

Invalid `graduationYear` in Flask (`?graduationYear=twenty`) returns `400` with
`{"error": "graduationYear must be a whole number"}`. The SPA only sends numbers
from a select, so users do not normally hit this.

### 12.6 The history-aware stage (the clever part)

A current status alone is not enough for a funnel. Consider an application that
went `Applied → Shortlisted → Interview → Rejected`. Its current status is
`Rejected`, but it did reach the interview stage. A funnel built only from current
statuses would undercount.

The code:

1. Builds `stage_map`: `application_id → set of every status in history`.
2. Adds the current status to that set.
3. Maps each status to a rank:

```python
PROGRESSION = ["Applied", "Shortlisted", "Interview", "Offered", "Accepted"]
PROGRESSION_RANK = {"Applied": 1, "Shortlisted": 2, "Interview": 3, "Offered": 4, "Accepted": 5}
```

4. `reached_rank = max(1, max(rank(status) for status in current + history))`.

- `Rejected` and `Withdrawn` are not in the progression, so they contribute rank 0.
- The `max(1, …)` guarantees every application counts as at least `Applied`.
- Unknown statuses also contribute 0.

Then:

- **Funnel** uses `reached_rank >= stage_rank`.
- **Interviews KPI** counts `reached_rank >= 3`.
- **Offers KPI** counts `reached_rank >= 4`.

By contrast:

- **Status distribution** uses the **current** status.
- `departmentStats.offers`, `companyOffers`, and the package metrics use the
  **current** status (`Offered` or `Accepted`), not history.

That mixed design is deliberate: “how far did they get?” uses history; “where are
they now?” uses current status.

### 12.7 Aggregations and KPI definitions

| Output | Definition | Notes |
| --- | --- | --- |
| `summary.students` | Distinct `student_id` in filtered students | Includes students with no applications |
| `summary.applications` | Row count of filtered applications | After dedupe |
| `summary.interviews` | Applications with `reached_rank >= 3` | History-aware |
| `summary.offers` | Applications with `reached_rank >= 4` | History-aware |
| `summary.accepted` | Applications whose **current** status is `Accepted` | Application count, not distinct students |
| `summary.companies` | Distinct `company_name` in filtered applications | Only companies present in the selection |
| `placementRate` | Distinct accepted students ÷ distinct filtered students × 100, 1 decimal | Status filter does not change the denominator |
| `medianPackage` | Median `offer_package_lpa` among current `Offered`/`Accepted` | `0.0` if none |
| `highestPackage` | Max of the same values | `0.0` if none |
| `topRecruiter` | First company in `companyOffers` | `null` if none |
| `funnel` | For each of the five stages, count `reached_rank >= rank` | Always five entries |
| `statusDistribution` | Counts for all 8 labels (7 + `Unknown`) | Missing labels get 0 |
| `departmentStats` | Group by department: applications, current offers, distinct students | Sorted by applications desc, then name |
| `companyOffers` | Current `Offered`/`Accepted` grouped by company, top 8 | Sorted by count desc |
| `monthlyApplications` | Applications with a valid date, grouped by `YYYY-MM` | Sorted by month |
| `filters` | Available departments and graduation years | From all students, unfiltered |
| `selectedFilters` | Echo of the requested filters | Empty string / null when absent |

Why “accepted” and “placement rate” differ:

- `summary.accepted` counts **applications** currently `Accepted`.
- `placementRate` counts **distinct students** with an accepted application.
- A student could theoretically accept more than one offer; the funnel counts
  applications, the rate counts people.

Why median matters: one very high package can distort an average. The median is the
middle offer and is more representative for a placement report.

### 12.8 Example dashboard payload

Illustrative, not an exact demo snapshot:

```json
{
  "filters": {
    "departments": ["AI & Data Science", "Civil", "Computer Engineering", "Electronics", "Information Technology", "Mechanical"],
    "graduationYears": [2026, 2027, 2028]
  },
  "selectedFilters": {
    "department": "Information Technology",
    "graduationYear": 2027,
    "status": ""
  },
  "summary": {
    "students": 4,
    "applications": 6,
    "interviews": 4,
    "offers": 3,
    "accepted": 1,
    "companies": 3
  },
  "placementRate": 25.0,
  "medianPackage": 8.5,
  "highestPackage": 12.0,
  "topRecruiter": "Acme",
  "funnel": [
    {"stage": "Applied", "count": 6},
    {"stage": "Shortlisted", "count": 5},
    {"stage": "Interview", "count": 4},
    {"stage": "Offered", "count": 3},
    {"stage": "Accepted", "count": 1}
  ],
  "statusDistribution": [
    {"status": "Applied", "count": 1},
    {"status": "Shortlisted", "count": 1},
    {"status": "Interview", "count": 1},
    {"status": "Offered", "count": 2},
    {"status": "Accepted", "count": 1},
    {"status": "Rejected", "count": 0},
    {"status": "Withdrawn", "count": 0},
    {"status": "Unknown", "count": 0}
  ],
  "departmentStats": [
    {"department": "Information Technology", "applications": 6, "offers": 3, "students": 4}
  ],
  "companyOffers": [
    {"company": "Acme", "offers": 2},
    {"company": "Beta", "offers": 1}
  ],
  "monthlyApplications": [
    {"month": "2026-08", "applications": 2},
    {"month": "2026-09", "applications": 4}
  ]
}
```

### 12.9 Chart mapping

| UI element | Payload field | Chart type | Click behavior |
| --- | --- | --- | --- |
| Application status | `statusDistribution` | Doughnut | Click a slice → toggle `status` filter |
| Application funnel | `funnel` | Horizontal bar | None |
| Department outcomes | `departmentStats` | Grouped bar (applications + offers) | Click a department → toggle `department` filter |
| Offers by company | `companyOffers` | Horizontal bar | None |
| Applications over time | `monthlyApplications` | Line | None |
| KPI cards | `summary`, `placementRate`, `medianPackage` | Text | None |
| Pipeline bars | `statusDistribution` | CSS bars | None |

Chart.js is configured with `responsive: true`, `maintainAspectRatio: false`,
integer ticks, and legends (bottom for doughnut, top for others). Existing chart
instances are updated in place rather than recreated, which avoids canvas leaks.

### 12.10 Worked example: why history matters

Four applications, all students in the same department/year:

| App | Current status | History | Reached rank | Counts in funnel |
| --- | --- | --- | --- | --- |
| A1 | Rejected | Applied → Shortlisted → Interview | 3 | Applied, Shortlisted, Interview |
| A2 | Interview | Applied → Shortlisted → Interview | 3 | Applied, Shortlisted, Interview |
| A3 | Offered | Applied → Shortlisted → Interview → Offered | 4 | Applied, Shortlisted, Interview, Offered |
| A4 | Accepted | … → Offered → Accepted | 5 | all five |

Results:

```text
funnel:              Applied 4, Shortlisted 4, Interview 4, Offered 2, Accepted 1
statusDistribution:  Rejected 1, Interview 1, Offered 1, Accepted 1
summary.interviews:  4
summary.offers:      2
summary.accepted:    1
placementRate:       1 / 4 = 25.0%
medianPackage:       median of A3 + A4 packages
```

Now imagine A1 is a legacy record whose history contains only `Rejected` (for
example, a row created by the V4 backfill). Its reached rank becomes 1, and the
funnel becomes:

```text
Applied 4, Shortlisted 3, Interview 3, Offered 2, Accepted 1
```

The current status is still `Rejected` either way; only the funnel changes. This is
the key difference between “current status” and “furthest stage reached”.

### 12.11 Demo dataset numbers

On a fresh demo database the verified dashboard shows:

```text
funnel: 86 → 55 → 34 → 20 → 9
placement rate: 29.2%
```

How to read that:

- 86 applications exist.
- 55 applications reached at least `Shortlisted`.
- 34 reached at least `Interview`.
- 20 reached at least `Offered`.
- 9 applications are currently `Accepted`, held by 7 distinct students:
  `7 / 24 = 29.2%`.

The funnel counts **applications**; the placement rate counts **students**. They do
not have to match, and they don’t.

The seeder now writes a **full history** for every newly seeded application,
including terminal ones. A `Rejected` record that reached `Interview` has rows for
`Applied`, `Shortlisted`, `Interview`, and `Rejected`, so it contributes to every
stage it reached. On an upgraded older database, legacy applications may still have
only one backfilled history row; those records count only as far as that row
indicates.

### 12.12 CSV export

`GET /api/analytics/export.csv` (or Flask `:5000/export.csv`) returns:

- Columns: `application_id, applied_date, status, student_id, department,
  graduation_year, job_type, company_name, offer_package_lpa`
- One row per deduplicated application
- Normalized text/status/dates/numbers from `clean_records`
- `index=False`, so no pandas index column
- `Content-Type: text/csv`
- `Content-Disposition: attachment; filename=applications.csv`
- **No dashboard filters applied** — it is the full cleaned table

With the 86-row demo dataset, the file has 87 lines (header + 86 rows). Nulls
appear as empty cells.

### 12.13 Performance characteristics

- Each dashboard request runs two full-table reads and a pandas pipeline.
- There is no cache and no materialized view.
- The SQL has no `WHERE`; filters are applied in pandas. The V2 indexes help other
  queries/joins but do not make the dashboard filters run in SQL.
- History is loaded in full, not only for the selected applications. The verified
  demo produces 219 history rows for 86 applications.
- Gunicorn runs 2 workers × 4 threads, timeout 30s.
- For a few hundred rows this is effectively instant. For a real placement season
  (thousands of applications), move filtering/aggregation into SQL or a warehouse,
  add a TTL cache, and paginate the list endpoints.

### 12.14 Failure modes

| Failure | Flask behavior | Through Spring |
| --- | --- | --- |
| MySQL down | `/health` → `503 {"status":"unavailable"}`; `/dashboard`/`/export.csv` → `503 {"error": ...}` | Spring’s generic handler turns the downstream error into `500 INTERNAL_ERROR` for dashboard/CSV |
| Invalid `graduationYear` | `400 {"error":"graduationYear must be a whole number"}` | Spring’s `Integer` binding fails first; the proxy returns a generic `500` |
| Empty database | Dashboard returns zeros and empty arrays, not an error | Works |
| Missing history table | PyMySQL error → `503` | `500` through the proxy |
| Flask down | — | Dashboard/CSV fail; `/api/analytics/summary` still works because it is JPA-only |

The Compose health check makes `app` wait for `analytics` to be healthy, so a cold
stack does not start the API until Flask can reach MySQL. Flask’s health check does
not require the tables to exist; Flyway creates them when Spring starts.

### 12.15 Analytics gotchas to remember

1. `/api/analytics/summary` is **not** history-aware; the dashboard is.
2. CSV export ignores dashboard filters.
3. The `status` filter does not change the student count or placement-rate
   denominator.
4. `departmentStats.offers`, `companyOffers`, and package metrics use **current**
   status, not reached status.
5. Seed history and migrated history are different: the current seeder writes a
   full path, while the V4 backfill writes one row for pre-existing applications.
   The funnel can only use history that exists.
6. `Unknown` is a real status bucket in the distribution.
7. Department codes of ≤4 characters are uppercased.
8. `summary.companies` is companies in the filtered applications, not all companies.
9. Students are deduplicated by `student_id`; a student with inconsistent
   department/year values keeps the first row.
10. The analytics service reads directly from MySQL; it is not using Spring’s
    repositories or business rules.

---

## 13. Frontend

### 13.1 What the user sees

A single HTML page with a login overlay and five workspace pages:

| Page | Hash | What it shows |
| --- | --- | --- |
| **Overview** | `#dashboard` | Welcome banner, six KPI cards, pipeline bars, quick actions, filters, five charts |
| **Applications** | `#applications` | Application tracker table, search, status filter, add/edit/delete, **History** dialog |
| **Students** | `#students` | Student table, search, add/edit/delete |
| **Job openings** | `#jobs` | Job table, search, add/edit/delete |
| **Companies** | `#companies` | Company table, search, add/edit/delete |

The sidebar switches pages. The top bar shows the signed-in user, a refresh button,
and a sign-out button. A notice strip shows success/error messages for five seconds.
The footer names the stack.

The **History** button on an application row opens a read-only native `<dialog>`
showing every recorded status transition. It is available to viewers as well as
admins.

### 13.2 State and bootstrap

`app.js` keeps a small in-memory `state`:

```js
const state = {
  students: [],
  companies: [],
  jobs: [],
  applications: [],
  meta: { statuses: [], jobTypes: [], transitions: {} },
  editing: null
};
```

Browser storage:

| Key | Contents |
| --- | --- |
| `pathway.token` | Raw JWT |
| `pathway.user` | `{"username": "...", "role": "ADMIN"}` |

Both live in `sessionStorage`, not `localStorage`.

Bootstrap sequence:

```text
bootstrap()
  ├── applyRole()                  show/hide write controls
  ├── no token? → showLogin()      stop
  ├── hideLogin()
  ├── loadMeta()                   GET /api/meta
  ├── refreshAll()                 GET students/companies/jobs/applications
  │     └── loadDashboard()        GET /api/analytics/dashboard
  └── applyInitialRoute()          honor #hash, default dashboard
```

The login overlay is shown by default (`hidden` class removed) when there is no
token. It is a styled `div`/`form`, not a native `<dialog>`, and there is no focus
trap; the username field is focused when it opens.

### 13.3 The request helper

`request(path, options)`:

1. Adds `Content-Type: application/json`.
2. Adds `Authorization: Bearer <token>` if a token exists.
3. Calls `fetch("/api" + path)`.
4. On `401`: clears the session, shows the login overlay, throws “Please sign in to
   continue.”
5. On `204`: returns `null`.
6. Otherwise reads text, tries JSON, and returns it.
7. On a non-OK response, throws a message from `body.detail`, `body.message`,
   `body.error`, or a generic status message.

Login itself uses a raw `fetch("/auth/login")`, because it is outside `/api` and
does not yet have a token. The CSV export also uses raw `fetch` so it can handle a
blob.

### 13.4 Loading and rendering

`refreshAll()` fetches the four lists in parallel with `Promise.all`:

```text
GET /api/students
GET /api/companies
GET /api/jobs
GET /api/applications
```

Then it renders each table and reloads the dashboard. The refresh button does the
same thing.

Tables are rendered with template strings and `innerHTML`. Every dynamic value is
passed through `esc()` (HTML-escape) before interpolation. Company websites are
also passed through `safeUrl()`, which only accepts `http:`/`https:`.

Client-side behavior:

- Search boxes filter the already-loaded arrays by a lowercase substring match.
- The applications page has an additional status `<select>`.
- Counts show `N record(s)`.
- Empty tables render an empty-state row.
- Application IDs display as `APP-0001`.
- Job and application tables show display names even though the underlying API
  responses include both IDs and names.
- Every application row has a `History` button; clicking it fetches
  `GET /api/applications/{id}/history` and lists changes oldest-first.

### 13.5 Forms and CRUD

Forms are inline panels, not modals. `showForm(type, item)` builds the fields from a
configuration object:

| Form | Fields |
| --- | --- |
| Student | rollNo, fullName, email, department, cgpa, graduationYear |
| Company | companyName, industry, location, website |
| Job | companyId (select), jobTitle, jobType (select), packageLpa, minCgpa, postedDate, deadline |
| Application | studentId (select), jobId (select), appliedDate, status (select), interviewDate, offerPackageLpa, notes |

Details:

- `appliedDate` and `postedDate` default to today.
- Status defaults to `Applied` on create.
- On edit, the status choices are `[current, ...allowedNext]` from `/api/meta`.
- On create, all statuses are offered (historical import).
- Required fields get the HTML `required` attribute.
- Number inputs get `min`/`max`/`step` hints; the server still validates.
- `saveForm` converts empty numeric strings to `null`, otherwise `Number(...)`.
- Create uses `POST`, edit uses `PUT`, then `refreshAll()` and a success notice.
- Delete uses `confirm()` and `DELETE`, then `refreshAll()`.
- If the API rejects a save, the error message appears in the notice strip.
- Viewers cannot open forms: the add buttons are hidden and `showForm` refuses with
  “Your account is read-only.”

One small numeric quirk: `if (!data.cgpa) data.cgpa = null` treats a CGPA of `0` as
empty. The same pattern applies to `packageLpa` and `offerPackageLpa`. This is
unlikely in practice but worth knowing.

### 13.6 Dashboard behavior in the browser

`loadDashboard()`:

1. Builds `URLSearchParams` from the three filter selects.
2. Calls `/api/analytics/dashboard` through the request helper.
3. Uses a monotonically increasing `dashboardRequestId` to ignore stale responses
   when the user changes filters quickly.
4. Populates the filter dropdowns from `data.filters`.
5. Updates six cards:
   - applications
   - interviews
   - offers
   - students
   - placement rate (`%`)
   - median package (`LPA` or `—`)
6. Renders the pipeline bars from `statusDistribution`.
7. Calls `renderDashboardCharts(data)`.
8. Updates the “Updated …” timestamp.

Chart click handlers:

- Doughnut slice → set/toggle the status filter.
- Department bar → set/toggle the department filter.

Filter changes, clear-filters, and chart clicks all call `loadDashboard()` again.
The year filter is only changed via its select.

`export-csv` fetches `/api/analytics/export.csv` with the bearer token, turns the
response into a Blob, creates a temporary `<a download="applications.csv">`, clicks
it, and revokes the object URL.

### 13.7 Role behavior in the UI

`applyRole()`:

- Shows `username · ROLE` in the top bar.
- Shows/hides the sign-out button.
- Hides the four add buttons for non-admins.
- Adds a `read-only` class to `<body>`.
- `actionButtons()` still renders the application `History` button, then renders
  “Read only” instead of Edit/Delete for viewers.

This is convenience only. The API enforces the same rules, so tampering with
`sessionStorage` does not grant access.

### 13.8 Routing

The SPA uses **hash routing**:

```text
http://localhost:8080/#dashboard
http://localhost:8080/#applications
http://localhost:8080/#students
http://localhost:8080/#jobs
http://localhost:8080/#companies
```

`nav(page)` hides all `.page` sections, shows the target, updates the active nav
button and `aria-current`, sets the page title and document title, and updates the
hash. `hashchange` handles back/forward. Unknown hashes fall back to the dashboard.
Hash routing means the server only ever serves `index.html`; no server-side route
fallback is needed.

### 13.9 Accessibility and security details

Accessibility:

- “Skip to main content” link.
- Semantic `<aside>`, `<main>`, `<section>`, `<table>`, `<footer>`.
- Login overlay has `role="dialog"`, `aria-modal="true"`, and a labelled title.
- Navigation buttons use `aria-current="page"`.
- Tables have `aria-label`.
- Visible `:focus-visible` states.
- `@media (prefers-reduced-motion: reduce)` disables animations/transitions.
- Notice strip uses `role="status"` and `aria-live="polite"`.
- Chart canvases have `aria-label`s.
- The history view is a native modal `<dialog>` with a labelled title and a live
  region for its list. Browsers provide Escape-to-close and focus containment; the
  code also has a fallback for browsers without `showModal()`.

Security:

- `esc()` HTML-escapes interpolated text.
- `safeUrl()` only allows `http:`/`https:` links.
- The token is not placed in a cookie, so CSRF is not a concern.
- `sessionStorage` is still readable by injected JavaScript; there is no CSP.
- The login overlay is not a full focus trap; keyboard users can tab behind it.
- No loading skeletons; the dashboard shows text placeholders while loading.

### 13.10 Frontend limitations

- No framework, no components, no build step — all DOM updates are manual strings.
- No pagination; `refreshAll()` loads every row.
- No server-side search/sort; filtering is client-side.
- No optimistic updates; every save refetches all lists.
- No token refresh; after an hour the next request returns `401`.
- No offline mode.
- No file uploads, resumes, emails, or calendar integration.
- History has no pagination, filtering, export, or deep link of its own; it always
  loads the full timeline for one application.
- The application edit form can resubmit stale interview/offer details; see the
  workflow sharp edge in [section 10.6](#106-known-sharp-edge-terminal-transitions-with-details).

---

## 14. Configuration

### 14.1 Environment variables

The backend reads environment variables; `application.properties` supplies defaults.

| Variable | Default | Used by | Purpose |
| --- | --- | --- | --- |
| `DB_URL` | `jdbc:mysql://localhost:3306/placement_analytics?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC` | Spring | JDBC URL |
| `DB_USERNAME` | `root` | Spring | DB user |
| `DB_PASSWORD` | empty | Spring | DB password |
| `JWT_SECRET` | `local-dev-secret-key-change-me-32bytes!!` | Spring | HMAC signing key, must be ≥ 32 bytes |
| `JWT_EXPIRATION_MS` | `3600000` | Spring | Token lifetime in ms |
| `ADMIN_USERNAME` | `admin` | Spring | Seeded admin username |
| `ADMIN_PASSWORD` | `ChangeMe123!` | Spring | Seeded admin password |
| `VIEWER_USERNAME` | `viewer` | Spring | Seeded viewer username |
| `VIEWER_PASSWORD` | `viewer123` | Spring | Seeded viewer password |
| `ANALYTICS_SERVICE_URL` | `http://localhost:5000` | Spring | Flask base URL for the proxy |
| `SPRING_PROFILES_ACTIVE` | none (Compose defaults to `demo`) | Spring | Active profile(s) |
| `APP_DEMO_SEED` | `true` in the demo profile | Spring | Set `false` to skip demo seeding |
| `DB_HOST` | `localhost` | Flask | MySQL host |
| `DB_PORT` | `3306` | Flask | MySQL port |
| `DB_NAME` | `placement_analytics` | Flask | MySQL database |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / empty | Flask | MySQL credentials |
| `PORT` | `5000` | Flask | Flask dev-server port |
| `LOG_LEVEL` | `INFO` | Flask | Python logging level |

Compose also uses `DB_PASSWORD`, `JWT_SECRET`, `JWT_EXPIRATION_MS`,
`ADMIN_USERNAME`, `ADMIN_PASSWORD`, `VIEWER_USERNAME`, `VIEWER_PASSWORD`,
`SPRING_PROFILES_ACTIVE`, and `APP_DEMO_SEED` from `.env` for interpolation.

### 14.2 Profiles

| Profile | What changes |
| --- | --- |
| **default** (no profile) | Normal app, Flyway on, `AdminSeeder` seeds accounts if the users table is empty, no demo data |
| **demo** | Same plus `DemoDataSeeder` (unless `APP_DEMO_SEED=false`) |
| **test** | H2 in-memory MySQL mode, Flyway off, `ddl-auto=create-drop`, `AdminSeeder` excluded, analytics URL pointed at `127.0.0.1:1` |

The `demo` profile is activated in `docker-compose.yml` by default:
`SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-demo}` and
`APP_DEMO_SEED=${APP_DEMO_SEED:-true}`.

### 14.3 Precedence

Spring resolves values in this order (simplified):

```text
environment variables / command line
        ↓
application.properties
        ↓
hard-coded defaults in @Value / property placeholders
```

Compose reads `.env` for `${...}` substitution and passes the resulting values as
container environment variables. `.env` is **not** copied into the image and is
git-ignored. The Flask container gets `DB_HOST=db`, `DB_PORT=3306`, etc. directly
from `docker-compose.yml`.

Spring relaxed binding maps `ANALYTICS_SERVICE_URL` → `analytics.service.url`,
which `AnalyticsClientConfig` injects.

### 14.4 Secrets

- Generate `JWT_SECRET` with `openssl rand -base64 48`.
- The app refuses to start if the secret is shorter than 32 bytes.
- Change `DB_PASSWORD` and both seeded account passwords before any shared use.
- Never commit `.env`; it is in `.gitignore`.
- The demo defaults are development-only and are printed in the README on purpose.

### 14.5 Important properties in `application.properties`

```properties
server.port=8080
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
spring.jackson.serialization.write-dates-as-timestamps=false
server.error.include-message=never
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=1
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=never
```

What they mean:

- `ddl-auto=validate`: Hibernate checks the schema but never creates/alters it.
- `open-in-view=false`: no lazy loading after the transaction; forces explicit
  fetching in repositories.
- `write-dates-as-timestamps=false`: dates serialize as ISO strings, not numbers.
- `include-message=never`: Spring’s default error page does not leak messages; the
  custom handler owns error text.
- `baseline-on-migrate=true` + `baseline-version=1`: an existing non-empty database
  without Flyway history is baselined at V1, then V2–V4 are applied.
- Actuator only exposes `health` and `info`, and health details are hidden.

---

## 15. Running the project

### 15.1 Docker path (recommended)

Prerequisites: Docker + Docker Compose.

```bash
cd RestApi
cp .env.example .env
# Edit .env: DB_PASSWORD, JWT_SECRET, ADMIN_PASSWORD, VIEWER_PASSWORD
docker compose up --build
```

Then open <http://localhost:8080>.

What Compose starts:

| Service | Image/build | Port | Health check |
| --- | --- | --- | --- |
| `db` | `mysql:8.4` | internal 3306 | `mysqladmin ping` every 5s, 20 retries, 30s start period |
| `analytics` | `python_etl/Dockerfile` | internal 5000 | Python `urlopen('/health')` every 10s |
| `app` | root `Dockerfile` | host **8080** | `wget /actuator/health` every 15s, 75s start period |

Startup order: `db` healthy → `analytics` healthy → `app`. The app waits for both.

Verify:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

Useful commands:

```bash
docker compose ps
docker compose logs -f app
docker compose logs -f analytics
docker compose logs -f db
docker compose down
```

Inspect MySQL (the port is not published):

```bash
docker compose exec db mysql -u root -p placement_analytics
```

Reset everything (**deletes local data**):

```bash
docker compose down -v
docker compose up --build
```

Only use `-v` when you intend to wipe the MySQL volume.

### 15.2 Local path without Docker

Prerequisites:

- JDK 17
- MySQL 8 running locally
- Python 3.12

The app creates the `placement_analytics` database on first connection if the user
has permission (`createDatabaseIfNotExist=true`).

Terminal 1 — analytics service:

```bash
cd RestApi
python3 -m venv .venv
source .venv/bin/activate
pip install -r python_etl/requirements.txt
python python_etl/analytics_app.py
# Flask dev server on http://localhost:5000
```

Terminal 2 — Spring Boot:

```bash
cd RestApi
export JWT_SECRET="$(openssl rand -base64 48)"
SPRING_PROFILES_ACTIVE=demo \
ANALYTICS_SERVICE_URL=http://localhost:5000 \
./mvnw spring-boot:run
```

Flyway applies V1–V4 on startup. With `SPRING_PROFILES_ACTIVE=demo` the demo data is
seeded. Omit the profile to run with only your own records; `AdminSeeder` still
creates the admin/viewer if the users table is empty.

Order note: analytics can start before the schema exists, but dashboard requests
will fail until Spring has run Flyway. In practice, start MySQL, then Spring (to
create/migrate), then Flask, or simply start both and wait for Spring to finish.

### 15.3 Smoke test

```bash
# 1. Health
curl -s http://localhost:8080/actuator/health

# 2. Login and capture the token (jq optional)
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"ChangeMe123!"}' | jq -r .token)

# 3. Read students
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/students | head

# 4. Dashboard
curl -s -H "Authorization: Bearer $TOKEN" \
  'http://localhost:8080/api/analytics/dashboard' | head

# 5. CSV (headers only)
curl -s -D - -o /dev/null -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/analytics/export.csv
```

Without `jq`, copy the token from the JSON manually.

### 15.4 Common commands

| Task | Command (from repo root unless noted) |
| --- | --- |
| Run Java tests | `cd RestApi && ./mvnw test` |
| Run Java verify/package | `cd RestApi && ./mvnw -B verify` |
| Run Python tests | `cd RestApi && python3 -m unittest discover -s python_etl -v` |
| Run Spring locally | `cd RestApi && ./mvnw spring-boot:run` |
| Run Flask locally | `cd RestApi && python python_etl/analytics_app.py` |
| Build images | `cd RestApi && docker compose build` |
| Start stack | `cd RestApi && docker compose up` |
| Rebuild + start | `cd RestApi && docker compose up --build` |
| Stop stack | `cd RestApi && docker compose down` |
| Wipe data | `cd RestApi && docker compose down -v` |
| Tail app logs | `cd RestApi && docker compose logs -f app` |

### 15.5 Ports summary

| Port | Process | Exposed? |
| --- | --- | --- |
| `8080` | Spring Boot + SPA | Yes, host |
| `5000` | Flask/Gunicorn | No, Compose internal |
| `3306` | MySQL | No, Compose internal |
| `3000`/`5173` | — | Not used (no frontend dev server) |

---

## 16. Demo data

### 16.1 Demo accounts

| Role | Username | Password |
| --- | --- | --- |
| ADMIN | `admin` | `ChangeMe123!` |
| VIEWER | `viewer` | `viewer123` |

Development defaults only. Override via environment variables before sharing.

### 16.2 What the seeder creates

`DemoDataSeeder` is active only with the `demo` profile and `app.demo.seed=true`
(the default in that profile). On a fresh database the verified targets are:

| Entity | Target | Composition |
| --- | --- | --- |
| Students | 24 | 24 total; existing rows are preserved |
| Companies | 15 | 14 base companies + `Atlas Manufacturing` for edge cases |
| Job openings | 22 | 18 base jobs + 4 edge-case jobs at Atlas |
| Applications | 86 | 80 base applications (existing + filler) + 6 scripted scenarios |

The six scenarios are included in the 86 target. They also produce full histories:
the verified fresh run has 219 history rows. The original pre-demo dataset was 11
students / 6 companies / 8 jobs / 37 applications; the seeder tops up, never
replaces, those rows.

### 16.3 How it works

```text
run()
  ├── Random(20261022L)             fixed seed
  ├── seedStudents(random)          top up to 24
  ├── seedCompanies(random)         top up to 14, then Atlas is added if needed
  ├── seedBaseJobs(random)          top up to 18
  ├── seedEdgeJobs()                add 4 Atlas openings (guarded by title)
  ├── seedScenarioApplications()    add 6 scripted applications + full histories
  └── seedFillerApplications()      top up applications to 86
```

Student seeding:

- Roll numbers `SEED100`, `SEED101`, …
- Names from fixed first/last-name arrays.
- Emails `demo100@placement.local`, …; `SEED105` deliberately has no email.
- Departments cycle through six departments; `SEED117` has an untidy
  `"  information   technology "` value for pandas to normalise.
- CGPA 6.4–9.6 (rounded to 2 decimals); `SEED111` deliberately has a null CGPA.
- Graduation year 2026–2028.
- Skips a roll number that already exists.

Company seeding:

- 14 names from the fixed list (`Nimbus Systems`, `Vertex Labs`, …).
- Random industry/location; website `https://demo.example.com/<slug>`.
- Skips an existing name (case-insensitive).
- `Atlas Manufacturing` is reused or created for the edge jobs.

Base job seeding:

- Picks a random existing company.
- Titles cycle through 12 roles; types cycle through all four so every type exists.
- Package 4.0–18.0 LPA; minimum CGPA 6.5–8.5.
- Posted 20–163 days ago; deadline 20–169 days in the future.

Edge jobs (all under Atlas Manufacturing):

| Title | Deadline | Purpose |
| --- | --- | --- |
| Backend Engineer | 30 days ago | Expired; used by the rejected-after-interview scenario |
| Data Analyst | 10 days ago | Expired |
| QA Engineer | today | Closing today |
| Support Engineer | 60 days ahead | Newly posted; intended to have no applicants |

Scripted scenarios:

| Marker in notes | Status path | Purpose |
| --- | --- | --- |
| `Demo scenario: rejected after interview` | Applied → Shortlisted → Interview → Rejected | Shows a terminal record that still reached Interview |
| `Demo scenario: withdrawn after offer` | Applied → Shortlisted → Interview → Offered → Withdrawn | Shows withdrawal after an offer |
| `Demo scenario: accepted end-to-end` | Applied → Shortlisted → Interview → Offered → Accepted | Complete happy path |
| `Demo scenario: stuck at applied` | Applied | Stays at the start |
| `Demo scenario: offered pending decision` | Applied → Shortlisted → Interview → Offered | Open offer |
| `Demo scenario: interview scheduled` | Applied → Shortlisted → Interview | Upcoming interview |

Each scenario is detected by its marker in `applications.notes`, so reruns do not
duplicate it. The scripted scenario uses `placement.cell` as `changedBy` and writes
one history row per transition with a descriptive note.

Filler applications:

- Random student/job pairs, but only open, non-Atlas jobs and students with a CGPA.
- Skips CGPA below the job minimum and duplicate `(student, job)` pairs.
- Dates stay within the job’s posted/deadline window.
- Builds a weighted path such as `Applied → Shortlisted → Interview → Offered` or
  `Applied → Shortlisted → Rejected`.
- Sets an interview date for paths that reach Interview and an offer package for
  paths that reach Offered/Accepted.
- Writes the **full path** to history with `changedBy` alternating between
  `placement.cell` and `admin`, realistic transition notes, and timestamps.
- Application notes cycle through “Campus placement drive”, “Applied through the
  placement portal”, “Referred by the placement cell”, and “On-campus recruitment”.

The status weights for filler applications are approximately: Applied 28%,
Shortlisted 18%, Interview 14%, Offered 12%, Accepted 8%, Rejected 15%,
Withdrawn 5%. These are targets; random draws and eligibility can shift the final
distribution.

### 16.4 “Deterministic” and “idempotent” — what exactly?

- **Deterministic:** the random generator has a fixed seed (`20261022`), so names,
  CGPAs, company choices, and package values repeat on a fresh database. Dates are
  relative to `LocalDate.now()`, so the actual dates (and monthly chart) shift with
  the day you run it.
- **Insert-only:** it never updates or deletes existing rows.
- **Top-up:** base counts are computed as `target - current count`; scenario
  applications are guarded by their notes; edge jobs are guarded by company/title.
  Running the same seed twice leaves its own rows unchanged.
- **Existing databases:** if the database already has the V4-era baseline of
  24 students / 14 companies / 18 jobs / 80 applications, the seeder adds the six
  scenarios and the four Atlas jobs (which also create Atlas as the 15th company),
  then tops applications up to 86. It does not rewrite the old applications or
  their histories. If existing data already exceeds a target, it inserts nothing
  for that entity.
- **Possible undershoot:** company-name collisions or an inability to find 86
  valid, unique, eligible pairs can leave counts short; warnings are logged and the
  seeder does not relax uniqueness or eligibility.
- If it cannot find enough filler pairs within `needed × 200` attempts, it logs a
  warning and stops short.
- The seeder does **not** create the admin/viewer accounts; `AdminSeeder` does that
  separately.

### 16.5 How to read the demo funnel

On a fresh database, the verified dashboard is:

```text
funnel: 86 → 55 → 34 → 20 → 9
placement rate: 29.2%
```

- 86 applications exist.
- 55 reached at least `Shortlisted`.
- 34 reached at least `Interview`.
- 20 reached at least `Offered`.
- 9 applications are currently `Accepted`, held by 7 distinct students:
  `7 / 24 = 29.2%`.

The funnel counts **applications**; the placement rate counts **students**. They do
not have to match.

The current seeder writes a full path for every new application, including terminal
ones. A rejected-after-interview record therefore contributes to Applied,
Shortlisted, Interview, and Rejected (the terminal stage itself is not one of the
five funnel stages). On an upgraded older database, legacy records may have only a
single V4 backfill row, so their funnel contribution is lower. That is a data
migration artifact, not an analytics bug.

### 16.6 Disabling or resetting

Disable demo data:

```bash
SPRING_PROFILES_ACTIVE= # or omit
APP_DEMO_SEED=false
```

Or keep the profile but set `APP_DEMO_SEED=false`.

Reset the Compose database:

```bash
cd RestApi
docker compose down -v   # deletes the mysql_data volume
docker compose up --build
```

Never run the demo profile in production; the accounts and data are public
knowledge.

---

## 17. Database migrations

### 17.1 What each migration does

| Version | File | Contents |
| --- | --- | --- |
| **V1** | `V1__baseline_schema.sql` | Creates `students`, `companies`, `job_openings`, `applications`, `users`; primary keys, unique keys, foreign keys; InnoDB/utf8mb4 |
| **V2** | `V2__add_dashboard_indexes.sql` | Adds four indexes for status/date, applied date, department/year, deadline |
| **V3** | `V3__add_data_constraints.sql` | Adds CHECK constraints for CGPA, graduation year, package, min CGPA, status labels, offer package |
| **V4** | `V4__application_status_history.sql` | Creates history table + index + cascade FK; backfills one row per existing application |

V1 mirrors the original Hibernate-managed schema so an existing installation can be
baselined at version 1. V2–V4 are additive.

The V4 backfill:

```sql
INSERT INTO application_status_history
    (application_id, status, changed_at, changed_by, note)
SELECT a.id, a.status, COALESCE(a.updated_at, NOW()), 'system',
       'Backfilled from current status'
FROM applications a
WHERE NOT EXISTS (
    SELECT 1 FROM application_status_history h
    WHERE h.application_id = a.id
);
```

It gives every pre-existing application a starting history row so the
history-aware funnel has something to read.

### 17.2 Migration policy

1. **Never edit a migration that has already run.** Flyway records a checksum; an
   edit causes a validation failure on existing databases.
2. Add the next numbered file, e.g. `V5__add_interview_feedback.sql`.
3. Keep migrations additive where possible. For a destructive change, plan a
   backup/rollback.
4. Update the JPA entity in the same change; `ddl-auto=validate` will fail at
   startup if they drift.
5. Test against a fresh database and, if relevant, a copy of an existing one.
6. The schema belongs to Flyway; the analytics service only reads it.

### 17.3 How Flyway runs

- On Spring Boot startup, Flyway scans `classpath:db/migration`.
- It creates/uses `flyway_schema_history` to track applied versions and checksums.
- `baseline-on-migrate=true` and `baseline-version=1` mean:
  - Empty database: apply V1 → V4.
  - Non-empty database with no Flyway history: mark it as baseline V1, then apply
    V2 → V4 (assuming the existing tables match V1).
- `spring.jpa.hibernate.ddl-auto=validate` then verifies that entities match the
  resulting schema.

### 17.4 Manual database creation helper

`RestApi/sql/schema.sql` contains only:

```sql
CREATE DATABASE IF NOT EXISTS placement_analytics;
```

It does **not** create tables. Flyway owns the schema. Use it only if you need to
create the empty database manually (for example, a DB user without
`CREATE DATABASE` permission).

### 17.5 Adding a migration — checklist

```text
1. Create RestApi/src/main/resources/db/migration/V5__short_description.sql
2. Write additive SQL (ALTER TABLE ... ADD COLUMN, CREATE TABLE, CREATE INDEX).
3. Update the matching entity/DTO/frontend.
4. Add/adjust tests.
5. Run ./mvnw test and the Python tests.
6. Start against a fresh DB and an existing DB copy.
7. Update README / this guide if the schema is user-visible.
```

---

## 18. Testing and CI

### 18.1 Test suites at a glance

| Suite | Count | Command | Database |
| --- | --- | --- | --- |
| Spring/JUnit | 30 | `cd RestApi && ./mvnw test` | H2 in-memory, MySQL mode |
| Python/unittest | 10 | `cd RestApi && python3 -m unittest discover -s python_etl -v` | Mocked data / Flask test client |
| **Total** | **40** | — | — |

### 18.2 Java tests

| Test class | Tests | What it covers |
| --- | --- | --- |
| `RestApiApplicationTests` | 1 | Spring context loads |
| `StudentControllerTest` | 6 | Create, duplicate roll on create/update, 404, validation field errors, delete |
| `JobApplicationControllerTest` | 11 | Create + status change, duplicate create, duplicate on update, unknown status, empty status, illegal jump, CGPA failure, deadline failure, history rows, history ordering, history 404 |
| `SecurityTest` | 9 | Anonymous read/write 401, viewer read, viewer write 403, viewer register 403, login token usable, bad password, landing page public, health public |
| `AnalyticsControllerTest` | 3 | Forwards dashboard filters, omits blank filters, proxies CSV (spins a small local HTTP server) |

The test profile (`application-test.properties`):

```properties
spring.datasource.url=jdbc:h2:mem:placement_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1
spring.jpa.hibernate.ddl-auto=create-drop
spring.flyway.enabled=false
analytics.service.url=http://127.0.0.1:1
jwt.secret=test-secret-key-with-at-least-32-bytes!!
```

Key points:

- H2 is in MySQL compatibility mode, but it is **not** MySQL.
- Flyway is disabled; Hibernate creates the schema from entities.
- `analytics.service.url` points to an unreachable port by default; tests that need
  analytics start their own local server and override the property.
- `@WithMockUser(username = "admin", roles = "ADMIN")` is used for controller tests.
- Test methods are `@Transactional`, so each rolls back.

### 18.3 Python tests

`python_etl/test_analytics.py` covers:

1. Cleaning, trimming, status normalization, deduplication.
2. Department/year filters and dashboard metrics.
3. Students without applications stay in the student KPI.
4. History-aware funnel and placement rate.
5. HTTP `/dashboard` with filters (mocked `load_data`).
6. Invalid `graduationYear` → 400.
7. CSV export content type/headers/body.
8. Health endpoint queries the DB and returns ok.
9. Health endpoint returns 503 on a DB error.
10. Empty dataset returns zeroed chart series.

The tests mock `analytics_app.load_data` or `_connect`; they do not require a live
MySQL.

### 18.4 CI

`.github/workflows/ci.yml` runs on pushes to `main` and every pull request:

```text
backend job
  checkout
  setup Java 17 (Temurin) + Maven cache
  cd RestApi && ./mvnw -B verify

analytics job
  checkout
  setup Python 3.12
  pip install -r RestApi/python_etl/requirements.txt
  python -m unittest discover -s RestApi/python_etl -v
```

`verify` compiles, runs tests, and packages the JAR.

### 18.5 What is not covered

- No tests for `CompanyController` or `JobOpeningController`.
- No frontend/UI/browser tests.
- No test that runs the real Flyway migrations against MySQL.
- No Docker/Compose integration test in CI.
- No test of the analytics SQL against a real database (only mocked rows).
- No test for delete cascades or parent-delete conflicts.
- No test for token expiry or tampered signatures.
- No coverage threshold or static analysis gate.

If you add behavior, add at least one test at the layer where the rule lives. For a
workflow rule, a MockMvc test is the most valuable.

### 18.6 Running the tests before a PR

```bash
cd RestApi
./mvnw -B verify
python3 -m unittest discover -s python_etl -v
```

Both must pass. The CI runs the same commands.

---

## 19. Error handling and troubleshooting

### 19.1 Error code reference

| HTTP | `code` | Typical cause |
| --- | --- | --- |
| `400` | `VALIDATION_FAILED` | Missing/invalid field; inspect `fieldErrors` |
| `400` | `BAD_REQUEST` | Unknown status, missing reference, detail rule violation |
| `400` | `MALFORMED_REQUEST` | Invalid JSON body |
| `401` | `UNAUTHORIZED` | No token, expired token, bad login |
| `403` | `FORBIDDEN` | Authenticated but not `ADMIN` |
| `404` | `NOT_FOUND` | Student/company/job/application ID does not exist |
| `409` | `CONFLICT` | Duplicate, CGPA below minimum, deadline passed, illegal transition |
| `409` | `DATA_CONFLICT` | Database constraint (FK delete, check violation) |
| `500` | `INTERNAL_ERROR` | Unexpected exception or analytics proxy failure |
| `503` | — | Flask health / internal analytics failure (internal endpoint) |

### 19.2 Common symptoms and fixes

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| Browser shows the login overlay repeatedly | Token expired (1 hour) or invalid | Sign in again; check `JWT_SECRET` did not change between restarts |
| `401 Sign in to continue.` | No/expired bearer token | Log in; verify `Authorization: Bearer …` |
| `403 You do not have permission…` | Logged in as `VIEWER`, trying to write | Use an `ADMIN` account |
| `409 Roll number already exists` | Duplicate student | Use a unique roll number |
| `409 Company name already exists` | Duplicate company (case-insensitive) | Use a unique name |
| `409 This student already applied for this job` | Unique `(student_id, job_id)` | Edit the existing application |
| `409 Student CGPA … below …` | Eligibility rule | Lower the job’s `minCgpa` or use an eligible student |
| `409 The application deadline has passed` | `appliedDate > deadline` | Fix the date or deadline |
| `409 Cannot move from X to Y` | Illegal workflow jump | Follow the transition table; use the UI’s allowed options |
| `409 An application in X is closed…` | Trying to move a terminal record | Create a new application if needed |
| `400 Interview date is only valid…` | Interview date on `Applied`/`Rejected`/`Withdrawn` | Clear the date or use a stage where it is allowed |
| `400 Offer package is only valid…` | Package outside `Offered`/`Accepted` | Clear the package or change status |
| `409 DATA_CONFLICT` on delete | Parent still has children | Delete applications/jobs first |
| Dashboard says “Analytics unavailable” | Flask down, MySQL down, or wrong `ANALYTICS_SERVICE_URL` | `docker compose ps`, `docker compose logs analytics`, `curl :5000/health` |
| Dashboard cards are zero | No applications, or all filters exclude everything | Clear filters; load demo profile |
| CSV is not filtered | Expected: export ignores dashboard filters | Filter the CSV in a spreadsheet if needed |
| Viewer sees no Add/Edit buttons | Expected read-only UI | Use an admin account |
| App fails to start: `JWT secret must be at least 32 bytes` | Short `JWT_SECRET` | `openssl rand -base64 48` |
| App fails: `Schema-validation: missing column/table` | Entity changed without a migration, or migration not applied | Add `V5__*.sql`, never edit old migrations |
| Flyway checksum error | An applied migration was edited | Restore the file; use `flyway repair` only with care |
| `Port 8080 already in use` | Another process/container | Stop it or change the port mapping |
| `Communications link failure` / MySQL connection refused | DB not running or wrong host/port | Start MySQL; check `DB_URL`/`DB_HOST` |
| `Public Key Retrieval is not allowed` (local MySQL) | MySQL 8 auth over non-TLS | Add `allowPublicKeyRetrieval=true` to the JDBC URL for local dev |
| `createDatabaseIfNotExist` fails | DB user lacks permission | Create the database manually with `sql/schema.sql` |
| Demo data is missing | Profile not `demo`, or `APP_DEMO_SEED=false` | Set `SPRING_PROFILES_ACTIVE=demo` and `APP_DEMO_SEED=true` |
| Demo data looks different on another day | Dates are relative to `LocalDate.now()` | Expected; counts/values are seeded, dates shift |
| `ModuleNotFoundError: analytics` | Running from the wrong directory | Run `python python_etl/analytics_app.py` from `RestApi/` |
| Flask health `503` | It cannot connect to MySQL | Check `DB_HOST`, `DB_PORT`, `DB_NAME`, credentials |
| Compose `app` never becomes healthy | Analytics/DB health failed, or app startup error | `docker compose logs app analytics db` |
| Browser shows old JS/CSS | Cached assets | Hard refresh (Ctrl/Cmd+Shift+R) |
| CORS error | Someone opened Flask directly or used another origin | Use the SPA through `8080`; do not call Flask cross-origin |

### 19.3 Debug commands

```bash
# Is the API healthy?
curl -s http://localhost:8080/actuator/health

# Is Flask healthy? (inside Compose)
docker compose exec analytics python -c \
  "from urllib.request import urlopen; print(urlopen('http://127.0.0.1:5000/health').read())"

# What is the app doing?
docker compose logs -f --tail=100 app

# What is the analytics service doing?
docker compose logs -f --tail=100 analytics

# Is MySQL up?
docker compose exec db mysqladmin ping -h 127.0.0.1 -u root -p

# Inspect tables directly
docker compose exec db mysql -u root -p placement_analytics -e "SHOW TABLES;"
docker compose exec db mysql -u root -p placement_analytics -e "SELECT id,status FROM applications LIMIT 5;"
docker compose exec db mysql -u root -p placement_analytics -e "SELECT * FROM application_status_history ORDER BY id DESC LIMIT 5;"

# Which process owns 8080?
ss -ltnp | grep 8080
```

For a logged-in request, print the HTTP status and body:

```bash
curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/students
```

---

## 20. Walkthroughs

### 20.1 Full admin journey with curl

Start from a running stack with demo data or a fresh database.

```bash
BASE=http://localhost:8080

# 1. Login
TOKEN=$(curl -s -X POST $BASE/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"ChangeMe123!"}' | jq -r .token)

# 2. Create a company
COMPANY_ID=$(curl -s -X POST $BASE/api/companies \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"companyName":"Acme Testing","industry":"Software","location":"Pune","website":"https://acme.example"}' \
  | jq -r .id)

# 3. Create a student
STUDENT_ID=$(curl -s -X POST $BASE/api/students \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"rollNo":"IT900","fullName":"Asha Rao","email":"asha@example.com","department":"Information Technology","cgpa":8.4,"graduationYear":2027}' \
  | jq -r .id)

# 4. Create a job opening
JOB_ID=$(curl -s -X POST $BASE/api/jobs \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"companyId\":$COMPANY_ID,\"jobTitle\":\"Java Developer\",\"jobType\":\"Full-time\",\"packageLpa\":8.5,\"minCgpa\":7.0,\"postedDate\":\"2026-10-01\",\"deadline\":\"2026-12-31\"}" \
  | jq -r .id)

# 5. Create an application
APP_ID=$(curl -s -X POST $BASE/api/applications \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"studentId\":$STUDENT_ID,\"jobId\":$JOB_ID,\"appliedDate\":\"2026-10-22\",\"status\":\"Applied\",\"notes\":\"Walkthrough\"}" \
  | jq -r .id)

# 6. Move it along the workflow
curl -s -X PATCH $BASE/api/applications/$APP_ID/status \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"status":"Shortlisted"}'

curl -s -X PATCH $BASE/api/applications/$APP_ID/status \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"status":"Interview"}'

curl -s -X PATCH $BASE/api/applications/$APP_ID/status \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"status":"Offered"}'

curl -s -X PATCH $BASE/api/applications/$APP_ID/status \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"status":"Accepted"}'

# 7. Add an offer package if desired (full PUT; status must remain legal)
curl -s -X PUT $BASE/api/applications/$APP_ID \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"studentId\":$STUDENT_ID,\"jobId\":$JOB_ID,\"appliedDate\":\"2026-10-22\",\"status\":\"Accepted\",\"offerPackageLpa\":8.5,\"notes\":\"Walkthrough\"}"

# 8. See the dashboard
curl -s -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/analytics/dashboard?department=Information%20Technology&graduationYear=2027" | jq .summary

# 9. Export the cleaned CSV
curl -s -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/analytics/export.csv" -o applications.csv
wc -l applications.csv
```

What this demonstrates:

- IDs flow from one create to the next (`companyId`, `studentId`, `jobId`).
- The status moves through the legal path.
- `PUT` can add an offer package only while the status is `Accepted`/`Offered`.
- The dashboard reflects the new application after the next request.
- The CSV contains the cleaned application row.

If you try an illegal jump:

```bash
curl -i -X PATCH $BASE/api/applications/$APP_ID/status \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"status":"Applied"}'
# 409 An application in Accepted is closed and cannot change status.
```

### 20.2 Viewer journey

```bash
BASE=http://localhost:8080

VIEWER_TOKEN=$(curl -s -X POST $BASE/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"viewer","password":"viewer123"}' | jq -r .token)

# Read: allowed
curl -i -H "Authorization: Bearer $VIEWER_TOKEN" $BASE/api/students
# 200

# Write: forbidden
curl -i -X POST $BASE/api/students \
  -H "Authorization: Bearer $VIEWER_TOKEN" -H 'Content-Type: application/json' \
  -d '{"rollNo":"X1","fullName":"X","department":"IT"}'
# 403

# Register: forbidden
curl -i -X POST $BASE/auth/register \
  -H "Authorization: Bearer $VIEWER_TOKEN" -H 'Content-Type: application/json' \
  -d '{"username":"newuser","password":"password123","role":"VIEWER"}'
# 403
```

In the UI, a viewer sees the tables, dashboard, and export button, but no add/edit/
delete controls.

### 20.3 Simulating an analytics outage

```bash
# Stop only the analytics service
docker compose stop analytics

# Dashboard/CSV fail (through Spring, usually 500)
curl -i -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/analytics/dashboard"

# JPA-only summary still works
curl -i -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/analytics/summary"

# Restart
docker compose start analytics
```

This is a useful demo of why the dashboard and summary endpoints have different
reliability characteristics.

### 20.4 Adding a feature safely (worked checklist)

Example: add `interviewFeedback` to applications.

```text
1. Migration
   RestApi/src/main/resources/db/migration/V5__add_interview_feedback.sql
   ALTER TABLE applications ADD COLUMN interview_feedback VARCHAR(1000) NULL;

2. Entity
   model/JobApplication.java
   @Column(name="interview_feedback", length=1000)
   private String interviewFeedback;

3. DTOs
   dto/ApplicationRequest.java  → add String interviewFeedback
   dto/ApplicationResponse.java → add String interviewFeedback

4. Service
   ApplicationService.applyRequest / toResponse → map the field.

5. Frontend
   static/app.js → add the field to the application form and table.
   static/index.html → add a table header if needed.

6. Analytics (only if it should appear in the dashboard)
   python_etl/analytics_app.py → add to SELECT
   python_etl/analytics.py → add to RECORD_COLUMNS and cleaning
   python_etl/test_analytics.py → add coverage

7. Tests
   JobApplicationControllerTest → create/update with the field.
   Python tests if query/cleaning changed.

8. Run
   ./mvnw -B verify
   python3 -m unittest discover -s python_etl -v

9. Docs
   README / API table / this guide.

Rules:
- Never edit V1–V4.
- Keep the migration additive.
- `ddl-auto=validate` will fail if entity and migration disagree.
```

---

## 21. How to explain this project to anyone

### 21.1 The 30-second version (non-technical)

> Pathway is a placement-cell workspace. It replaces spreadsheets with one system
> that tracks students, companies, jobs, and applications, enforces the hiring
> rules, and keeps an audit trail of every status change. A live dashboard shows
> placement rate, interview and offer conversion, department outcomes, and top
> recruiters. Admins can edit; viewers can only read and export.

### 21.2 The 2-minute version (technical)

> The backend is Spring Boot 3.5 on Java 17. It owns authentication, CRUD,
> validation, and the application state machine. MySQL is the only source of truth,
> and Flyway owns the schema. The API is stateless: a JWT bearer token is verified
> by a filter, and the user’s role is loaded from the database on every request.
> Roles are `ADMIN` (write) and `VIEWER` (read). All errors use one JSON envelope.
>
> The frontend is a vanilla JS SPA served by Spring; no Node build. Chart.js is
> self-hosted.
>
> Analytics is a separate read-only Flask + pandas service. Spring proxies
> `/api/analytics/*` to it. Flask queries MySQL directly, cleans and deduplicates
> the rows in pandas, computes KPIs and chart series, and returns JSON or CSV. The
> funnel is history-aware: it reads `application_status_history` and uses each
> application’s furthest stage, so a candidate rejected after an interview still
> counts as having reached the interview stage.
>
> The status workflow is `Applied → Shortlisted → Interview → Offered → Accepted`,
> with `Rejected`/`Withdrawn` available before acceptance and terminal at the end.
> Every change is written to an audit table with the acting user and timestamp.
>
> Docker Compose runs MySQL, Flask, and Spring with health checks. GitHub Actions
> runs 30 Java tests and 10 Python tests.

### 21.3 Ten things to remember

1. **MySQL is the single source of truth.**
2. **Spring Boot owns writes, auth, and workflow.**
3. **Flask + pandas is a read-only analytics sidecar.**
4. **The browser only talks to Spring (no CORS).**
5. **Flyway owns the schema; Hibernate only validates it.**
6. **`ApplicationStatus` is the transition graph.**
7. **History makes the funnel history-aware.**
8. **`students LEFT JOIN applications` keeps unplaced students in the denominator.**
9. **ADMIN writes, VIEWER reads; the server enforces it.**
10. **The demo seed is deterministic, insert-only, and for development only.**

### 21.4 Viva / interview questions with short answers

**1. What problem does this solve?**
A placement cell needs a single, rule-enforcing tracker plus live analytics instead
of disconnected spreadsheets.

**2. Why two backends instead of one?**
Spring Boot is strong for secured CRUD and workflow; pandas is concise for tabular
cleaning and aggregation. The split also demonstrates a read/write separation. The
cost is an extra service and network hop.

**3. How does authentication work?**
`POST /auth/login` verifies BCrypt credentials, signs a JWT with the username and
expiry. Every request sends `Authorization: Bearer`. A filter verifies the token,
loads the user/role from MySQL, and populates the security context.

**4. Why JWT instead of a server session?**
It is stateless and fits the SPA; no session store is needed. The trade-off is no
built-in revocation/refresh.

**5. Where is the business logic?**
In `ApplicationService` and the `ApplicationStatus` enum, backed by database
constraints. Controllers only handle HTTP; repositories only handle data access.

**6. How do you prevent an illegal status jump?**
`ApplicationStatus.canTransitionTo` defines the graph; the service validates every
create/update/PATCH and returns `409` for a bad transition. The UI also reads
`/api/meta`, but the server is authoritative.

**7. What does “history-aware funnel” mean?**
The funnel counts each application at every stage it has ever reached, using the
history table plus the current status. A rejected-after-interview application still
counts as having reached the interview stage.

**8. Why `LEFT JOIN students`?**
To keep students with zero applications in the student count and placement-rate
denominator. An inner join would drop them.

**9. How do filters work?**
Department and graduation year filter both applications and students; status
filters only applications, so the placement-rate denominator remains the cohort.
Filtering happens in pandas, not SQL.

**10. Why Flyway and `ddl-auto=validate`?**
Flyway makes schema changes reproducible and reviewable. `validate` ensures the JPA
entities match the migrated schema at startup, so drift fails fast.

**11. How is demo data generated?**
`DemoDataSeeder` uses a fixed random seed and insert-only top-up logic to reach
24/15/22/86, including six scripted applications with full status histories and
four Atlas Manufacturing edge-case openings. It never updates or deletes existing
rows.

**12. How does CSV export work?**
Spring proxies the request to Flask; Flask loads the data, cleans/deduplicates it
with pandas, and returns `applications.csv`. It ignores dashboard filters.

**13. What are the roles?**
`ADMIN` can read/write/register; `VIEWER` can read and export. Public access is
limited to login, static assets, and health.

**14. How would you scale it?**
Paginate list endpoints, move analytics aggregation into SQL or a warehouse, add a
cache/materialized view, use a read replica, separate DB users, add refresh tokens,
and add observability. See section 22.

**15. What would you improve first?**
Fix the terminal-transition detail bug, add pagination to list and history views,
and add tests for the company/job controllers and real MySQL migrations.

### 21.5 Analogies that help non-technical listeners

| Technical thing | Analogy |
| --- | --- |
| MySQL | The official ledger. Everyone reads from the same book. |
| Spring Boot | The front desk clerk who checks your ID and whether the form is filled correctly. |
| Flask + pandas | The analyst in the back room who reads the ledger and prepares charts. |
| JWT | A signed wristband issued after you show your ID. |
| Flyway | A numbered recipe book; you never rewrite an old recipe, you add a new page. |
| Docker | A shipping container: the app and everything it needs travel together. |
| Audit history | The “changes” tab in a shared document, but permanent. |
| Funnel | A series of gates; each application is counted at every gate it passed. |

---

## 22. Limitations and a sensible roadmap

### 22.1 Known limitations (accurate as of this code)

**Correctness / behavior**

- The terminal-transition detail bug described in section 10.6: rejecting an
  application that already has an interview date or offer package returns `400`.
- `/api/analytics/summary` counts current statuses, not history; the UI’s cards use
  the Flask payload, so the two can disagree.
- The CSV export ignores dashboard filters.
- The `status` dashboard filter does not affect the student/placement-rate
  denominator.
- Existing databases seeded by the old seeder (or upgraded through V4) may have
  only a single history row per application, so their funnel undercounts stages.
  The current seeder writes full paths only for new rows.
- `departmentStats.offers` and `companyOffers` use current status, while the funnel
  uses reached status; the dashboard mixes both semantics.
- `AdminSeeder` does nothing if **any** user exists, so it will not add a missing
  viewer to an existing database.
- `DemoDataSeeder` company top-up can undershoot if generated names collide with
  existing names; it logs the final counts but does not relax uniqueness.
- `graduationYear` has no Bean Validation range; an out-of-range value reaches the
  DB check and becomes `409 DATA_CONFLICT` rather than a friendly field error.
- `JobOpening` has no check that `postedDate <= deadline`.
- `jobType` is a free string at the API level, despite the UI list.
- Timestamps use `LocalDateTime.now()` (server local time), while the JDBC session
  is UTC; audit times are not explicitly normalized.
- History has no pagination, actor/status filtering, export, or deep link; the
  dialog always loads the entire timeline for one application.
- No pagination or server-side search; every list call returns the full table.
- No optimistic concurrency control; two admins can overwrite each other.
- No bulk import; the create-any-status behavior is the only import path.
- No soft delete; deletes are real and cascade history for applications.

**Security / operations**

- No refresh token, revocation, or server-side logout.
- Token in `sessionStorage`; no CSP; XSS would expose it.
- No rate limiting, lockout, password reset, or email verification.
- No security headers configured.
- Compose uses MySQL `root` for both services.
- Flask has no authentication and must stay internal.
- No TLS in the repository; terminate at a reverse proxy.
- No backups, migrations rollback automation, or observability stack.
- Demo credentials are public.

**Frontend / UX**

- No loading skeletons; some actions wait on full refreshes.
- No focus trap in the login dialog.
- No sorting controls; tables render in repository order.
- No pagination; large datasets will make the DOM heavy.
- Job/application table headers say “Company ID”/“Student ID”/“Job ID” while the
  cells show names (cosmetic mismatch).
- Dashboard uses mixed current/reached semantics, which can confuse viewers unless
  explained.
- No mobile-specific table design beyond responsive CSS.

**Testing**

- No tests for company/job controllers, frontend, real MySQL/Flyway, Docker, or
  token expiry/tampering.
- Analytics tests mock the database; the SQL itself is not integration-tested.

**Project / docs**

- No project license file is included.
- `README_sample.md` is an unrelated sample (SkillShift), which can confuse a new
  reader; it is not part of this application.
- The root README is the single project README; keep it in sync with the code.

### 22.2 What to fix first (one-day plan)

1. Fix terminal transitions: clear interview date/package when entering
   `Rejected`/`Withdrawn`, or validate only newly supplied values.
2. Add tests for that workflow and for company/job CRUD.
3. Harden the existing history feature: add pagination, actor/status filters, and a
   deep link; return focus to the triggering row when the dialog closes.
4. Decide and document the semantics of `summary` vs `dashboard`, and make the CSV
   filter-aware (or label it clearly as “all data”).
5. Add pagination to list endpoints or at least a `limit`/`offset`.
6. Add a separate least-privilege MySQL user for the app and analytics.

### 22.3 A scaled architecture (if this became real)

```text
                    ┌──────────────┐
                    │  Browser     │
                    └──────┬───────┘
                           │ HTTPS
                    ┌──────▼───────┐
                    │ Reverse proxy│  TLS, rate limit, headers
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │ Spring Boot  │  pagination, caching, audit API
                    └──┬────────┬──┘
                       │        │
             ┌─────────▼──┐  ┌──▼─────────────┐
             │ MySQL      │  │ Analytics API  │
             │ primary    │  │ (Flask/FastAPI)│
             └─────┬──────┘  └──────┬─────────┘
                   │                │
             ┌─────▼──────┐  ┌──────▼─────────┐
             │ Read       │  │ Warehouse /    │
             │ replica    │  │ materialized   │
             └────────────┘  │ views          │
                             └────────────────┘
```

Realistic improvements, in order:

1. **Pagination + server-side filtering** for students/applications.
2. **SQL-side aggregation** or a nightly ETL into a small warehouse; stop loading
   full tables per request.
3. **Cache** dashboard payloads for 30–60 seconds or precompute after writes.
4. **Read replica** for analytics so dashboard traffic does not touch the write DB.
5. **Separate DB users** and network policies.
6. **Refresh tokens/revocation**, rate limiting, security headers, TLS.
7. **Audit UI enhancements**, bulk import, notifications.
8. **Observability**: structured logs, metrics, tracing, error reporting.
9. **CI/CD**: Docker build, migration check against MySQL, deployment pipeline.
10. **Data governance**: backups, retention, PII handling, access reviews.

---

## 23. Cheat sheet

### 23.1 URLs and ports

| What | URL |
| --- | --- |
| App / SPA | <http://localhost:8080> |
| Health | <http://localhost:8080/actuator/health> |
| Login | `POST http://localhost:8080/auth/login` |
| API base | `http://localhost:8080/api` |
| Dashboard | `http://localhost:8080/api/analytics/dashboard` |
| CSV | `http://localhost:8080/api/analytics/export.csv` |
| Flask health (internal) | `http://localhost:5000/health` |
| Flask dashboard (internal) | `http://localhost:5000/dashboard` |
| MySQL (internal) | `db:3306` |

### 23.2 Default credentials and demo numbers

| Item | Value |
| --- | --- |
| Admin | `admin` / `ChangeMe123!` |
| Viewer | `viewer` / `viewer123` |
| Students | 24 |
| Companies | 15 |
| Jobs | 22 |
| Applications | 86 |
| Demo funnel | `86 → 55 → 34 → 20 → 9` |
| Demo placement rate | `29.2%` |

### 23.3 Status vocabulary

```text
Applied, Shortlisted, Interview, Offered, Accepted, Rejected, Withdrawn
```

Terminal: `Accepted`, `Rejected`, `Withdrawn`.

### 23.4 Table names

```text
students
companies
job_openings
applications
application_status_history
users
flyway_schema_history   (created by Flyway)
```

### 23.5 Key files

| If you want to understand… | Open |
| --- | --- |
| The workflow | `RestApi/src/main/java/com/tcet/RestApi/model/ApplicationStatus.java` |
| The rules | `RestApi/src/main/java/com/tcet/RestApi/service/ApplicationService.java` |
| Auth | `RestApi/src/main/java/com/tcet/RestApi/security/SecurityConfig.java` |
| Schema | `RestApi/src/main/resources/db/migration/V1..V4` |
| Analytics math | `RestApi/python_etl/analytics.py` |
| Analytics SQL/HTTP | `RestApi/python_etl/analytics_app.py` |
| The whole UI | `RestApi/src/main/resources/static/app.js` |
| Deployment | `RestApi/docker-compose.yml`, `RestApi/Dockerfile`, `RestApi/python_etl/Dockerfile` |
| Tests | `RestApi/src/test/`, `RestApi/python_etl/test_analytics.py` |

### 23.6 Commands

```bash
# Docker
cd RestApi && cp .env.example .env
cd RestApi && docker compose up --build
cd RestApi && docker compose down
cd RestApi && docker compose down -v          # deletes data
cd RestApi && docker compose logs -f app

# Local
cd RestApi && python3 -m venv .venv && source .venv/bin/activate
pip install -r python_etl/requirements.txt
python python_etl/analytics_app.py
SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run

# Tests
cd RestApi && ./mvnw -B verify
cd RestApi && python3 -m unittest discover -s python_etl -v

# Health
curl http://localhost:8080/actuator/health
```

---

## 24. Appendix: exact queries, payloads, and phases

### 24.1 Exact analytics queries

```sql
-- Applications + student + job + company, including students with no applications
SELECT
    a.id AS application_id,
    a.applied_date,
    a.status,
    a.offer_package_lpa,
    s.id AS student_id,
    s.department,
    s.graduation_year,
    j.job_type,
    c.company_name
FROM students s
LEFT JOIN applications a ON a.student_id = s.id
LEFT JOIN job_openings j ON j.id = a.job_id
LEFT JOIN companies c ON c.id = j.company_id;

-- Full status history for the history-aware funnel
SELECT application_id, status, changed_at
FROM application_status_history
ORDER BY application_id, changed_at, id;
```

### 24.2 Login and CRUD request examples

Login:

```http
POST /auth/login
Content-Type: application/json

{"username":"admin","password":"ChangeMe123!"}
```

Create student:

```http
POST /api/students
Authorization: Bearer <token>
Content-Type: application/json

{"rollNo":"IT001","fullName":"Asha Rao","email":"asha@example.com","department":"Information Technology","cgpa":8.4,"graduationYear":2027}
```

Create job:

```http
POST /api/jobs
Authorization: Bearer <token>
Content-Type: application/json

{"companyId":1,"jobTitle":"Java Developer","jobType":"Full-time","packageLpa":8.5,"minCgpa":7.0,"postedDate":"2026-10-01","deadline":"2026-12-31"}
```

Create application:

```http
POST /api/applications
Authorization: Bearer <token>
Content-Type: application/json

{"studentId":1,"jobId":1,"appliedDate":"2026-10-22","status":"Applied","notes":"Referred"}
```

Change status:

```http
PATCH /api/applications/1/status
Authorization: Bearer <token>
Content-Type: application/json

{"status":"Shortlisted"}
```

### 24.3 Dashboard JSON shape

The exact keys are:

```text
filters.departments[]
filters.graduationYears[]
selectedFilters.department
selectedFilters.graduationYear
selectedFilters.status
summary.students
summary.applications
summary.interviews
summary.offers
summary.accepted
summary.companies
placementRate
medianPackage
highestPackage
topRecruiter
funnel[].stage
funnel[].count
statusDistribution[].status
statusDistribution[].count
departmentStats[].department
departmentStats[].applications
departmentStats[].offers
departmentStats[].students
companyOffers[].company
companyOffers[].offers
monthlyApplications[].month
monthlyApplications[].applications
```

### 24.4 Implementation phases (from `docs/implementation-plan.md`)

| Phase | Outcome | Tag |
| --- | --- | --- |
| 0 | Git baseline, `.gitignore`, `.gitattributes` | `phase-0` |
| 1 | Consistent JSON errors, duplicate conflicts, URL validation | `phase-1-errors` |
| 2 | H2 Java tests, Python tests, GitHub Actions | `phase-2-tests` |
| 3 | JWT login, roles, admin/viewer seeding, login UI | `phase-3-auth` |
| 4 | Flyway V1–V4, indexes/constraints, history, DTOs | `phase-4-persistence` |
| 5 | Transitions/history, meta API, eligibility, funnel/KPIs, CSV, demo seeder | `phase-5-analytics` |
| 6 | Self-hosted fonts, hash routing, accessibility, formatted assets | `phase-6-frontend` |
| 7 | Actuator, Docker caching/health, Compose env, Gunicorn, docs | `phase-7-ops` |

Verification performed for this handbook against the current working tree:

| Check | Result |
| --- | --- |
| Java tests | 30 pass |
| Python tests | 10 pass |
| Anonymous `GET /api/students` | 401 JSON |
| Viewer write | 403 |
| Health without token | 200 `{"status":"UP"}` |
| Demo seed first run | 24 / 15 / 22 / 86 |
| Demo seed later runs | Counts unchanged, originals preserved |
| Dashboard | funnel `86 → 55 → 34 → 20 → 9`, rate `29.2%` |
| CSV | `text/csv`, `applications.csv`, 87 lines |
| Compose | all services healthy; login/dashboard/export verified |
| Static assets | fonts, `fonts.css`, `app.js`, `index.html` return 200 |

The checked-in `docs/implementation-plan.md` now carries a historical banner but
still records an **earlier** baseline: 28 Java tests, a 24/14/18/80 demo seed, the
`80 → 45 → 32 → 17 → 7` funnel, and a `20.8%` placement rate. Treat those numbers
as historical. The table above and the rest of this handbook reflect the current
working tree, which adds the history feature, two Java tests, six scripted
scenarios, four edge jobs, and full seeded histories.

### 24.5 Known deviations recorded by the team

- Student/company controllers use repositories directly; no service extraction.
- No Flask TTL cache; every request recomputes.
- No custom Actuator indicator for Flask health (would make the whole app unhealthy
  and trigger restarts); Flask has its own health check.
- No full modal focus trap or loading skeletons.
- Configuration uses env vars + `demo`/`test` profiles rather than separate
  `application-dev.properties` / `application-docker.properties`.
- The root `README.md` is the single project README; the former
  `RestApi/README.md` was consolidated into it.

### 24.6 How to keep this document accurate

When you change code, update the matching section:

| Change | Update |
| --- | --- |
| Schema | §6, §17 |
| Endpoint/validation | §11 |
| Workflow | §10 |
| Analytics metric | §12 |
| UI page/behavior | §13 |
| Env var/profile | §14 |
| Test count/coverage | §18 |
| Docker/deployment | §4.6, §15 |
| Demo data | §16 |

The most common documentation drift is the test count and the exact dashboard
payload. Verify both after a change.

---

*End of handbook. If you are reading this before an exam or demo, start with
sections 1, 4, 10, and 12 — they give you the story, the architecture, the rules,
and the analytics.*
