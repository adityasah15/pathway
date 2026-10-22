# Job Application Management System with Placement Analytics

Spring Boot + MySQL + Python/Pandas mini-project for job application tracking and placement analytics.
It includes JWT authentication with `ADMIN`/`VIEWER` roles, application status rules and history,
dashboard KPIs and charts, CSV export, Flyway migrations, tests, CI, and Docker Compose.

## Quick start

```bash
cd RestApi
cp .env.example .env     # change DB_PASSWORD, JWT_SECRET, and demo passwords
docker compose up --build
```

Open <http://localhost:8080>. Development demo accounts are `admin` / `ChangeMe123!` and
`viewer` / `viewer123`; change them in `.env` before using this anywhere real.

Without Docker, start the Flask analytics service and Spring Boot in separate terminals:

```bash
cd RestApi
python3 -m venv .venv
source .venv/bin/activate
pip install -r python_etl/requirements.txt
python python_etl/analytics_app.py        # terminal 1, http://localhost:5000
```

```bash
cd RestApi
SPRING_PROFILES_ACTIVE=demo ANALYTICS_SERVICE_URL=http://localhost:5000 ./mvnw spring-boot:run
```

The demo profile tops the database up to 24 students, 14 companies, 18 jobs, and 80 applications.
It is insert-only and idempotent.

## Tests

```bash
cd RestApi
./mvnw test
python3 -m unittest discover -s python_etl -v
```

## Documentation

- [RestApi/README.md](RestApi/README.md) — full setup, configuration, API reference, auth model, and
  application status workflow.
- [docs/implementation-plan.md](docs/implementation-plan.md) — phase-by-phase record, tags, commits,
  verification results, and known deviations.
