# Implementation Plan and Record

- **Project:** Job Application Management System with Placement Analytics
- **Plan date:** 2026-10-22
- **Status:** complete through Phase 7
- **Execution:** sequential phases, one logical commit at a time, tagged at phase boundaries
- **Repository:** local only; nothing has been pushed

> **Historical record:** this plan captures the Phase 0–7 baseline. The current
> working tree adds the application-history API/UI and a 24-student / 15-company /
> 22-job / 86-application demo seed. The verification table below is the original
> baseline, not the current one; see the root `README.md` and `PROJECT_GUIDE.md`.

## Goals and constraints

1. Enforce authentication instead of leaving the JWT stack as decoration:
   `ADMIN` can write, `VIEWER` can only read.
2. Preserve the existing MySQL data. Existing schemas are baselined with Flyway; migrations are
   additive.
3. Seed at least twice the original demo volume: **24 students, 14 companies, 18 job openings, and
   80 applications** (original: 11/6/8/37). Seeding is insert-only and idempotent.
4. Use Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `build:`, `docs:`) and tag each
   completed phase.
5. Verify each phase with tests and, where possible, a live application.

## Phases

| Phase | Outcome | Tag | Representative commits |
| --- | --- | --- | --- |
| 0 | Git baseline, `.gitignore`, `.gitattributes` | `phase-0` | `66aebd5` |
| 1 | Consistent JSON errors, duplicate conflicts, URL validation | `phase-1-errors` | `340c311`, `23357f5`, `2f8bd6c` |
| 2 | H2-backed Java tests, Python tests, GitHub Actions | `phase-2-tests` | `12ff198`, `8ac9d7d` |
| 3 | JWT login, role enforcement, admin/viewer seeding, login UI | `phase-3-auth` | `9a66cc8`, `db136fe`, `8b5884b` |
| 4 | Flyway V1–V4, indexes/constraints, status-history table, package rename, JPA associations and response DTOs | `phase-4-persistence` | `663870d`, `eacb7b9`, `e017274`, `44711b0`, `6903dbb`, `9f24490` |
| 5 | Status transitions/history, metadata API, eligibility, funnel/KPIs, CSV export, demo seeder | `phase-5-analytics` | `6a7453f`, `9e9bce9`, `61f1d7a`, `ec372b7`, `fb6e267`, `2806d41` |
| 6 | Self-hosted fonts, hash routing, accessible landmarks/tables/focus states, formatted assets | `phase-6-frontend` | `ff534e6` |
| 7 | Actuator health, Docker caching and health checks, Compose env, Flask DB health, Gunicorn tuning, documentation | `phase-7-ops` | `676590b`, `08b8f4d`, documentation commit |

Phase tags are cumulative checkpoints. Phase 4's association/DTO work (`9f24490`) landed after the
first Phase 5 service commits because the status service depends on those DTOs; the tag records the
completed phase rather than a strict file boundary.

## Verification results

| Check | Result |
| --- | --- |
| `./mvnw -B test` | 28 tests pass |
| `python3 -m unittest discover -s python_etl -v` | 10 tests pass |
| Anonymous `GET /api/students` | `401` JSON error |
| `VIEWER` write request | `403` |
| `GET /actuator/health` without a token | `200 {"status":"UP"}` |
| Demo seed, first run | 24 students, 14 companies, 18 jobs, 80 applications |
| Demo seed, later runs | Counts unchanged; original rows preserved |
| Dashboard | Funnel `80 → 45 → 32 → 17 → 7`; placement rate `20.8%` |
| CSV export | `text/csv`, download filename `applications.csv`, 81 lines (header + 80 rows) |
| Docker Compose | Images build; `up --wait` reaches healthy for MySQL, analytics, and app; login/dashboard/export verified against a fresh volume |
| Fonts and SPA assets | `/fonts.css`, both `.woff2` files, `/app.js`, and `/index.html` return `200` |

## Deviations and deferred items

- **Service extraction:** jobs and applications use services and DTOs. Student and company
  controllers still operate through repositories directly; extracting those services would add
  churn without changing behavior.
- **Flask caching:** the planned TTL cache was not added. The dataset is small and every dashboard
  request still reads current data, which matters more for the demo than the saved milliseconds.
- **Dashboard dependency health:** Spring Actuator exposes its own health. A custom indicator that
  fails when Flask is down was deliberately not added, because it would make the whole app
  unhealthy and trigger container restarts. Flask has its own DB-aware health check and its own
  Compose healthcheck.
- **Frontend polish:** hash routing, local fonts, landmarks, table labels, `aria-current`, visible
  focus states, and reduced-motion support are in. A full modal focus trap and loading skeletons
  were not implemented; saving still disables the submit action and shows a notice.
- **Profiles:** configuration uses environment variables plus the `demo`/`test` profiles instead of
  separate `application-dev.properties` and `application-docker.properties` files.
- **Documentation layout:** the full README remains in `RestApi/`, with a short root README pointing
  to it and to this record.

## Rollback

Each tag is a complete checkpoint:

```bash
git show phase-5-analytics
git reset --hard phase-5-analytics   # only if you intend to discard later work
```

Flyway migrations are additive and must not be edited after they have run. To undo schema changes,
add a new migration rather than rewriting history.
