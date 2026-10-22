# Job Application Management System with Placement Analytics

Mini-project for approved IP topic **#57: Job Application Management System**. It combines a Java Spring Boot CRUD application, MySQL storage, and a Python/Pandas analytics service powering the interactive website dashboard.

## Features
- Student CRUD
- Company CRUD
- Job-opening CRUD
- Application CRUD and status updates
- Duplicate application protection (one student per job opening)
- Website summary cards and interactive application analytics dashboard
- Python/Pandas data cleaning, filtering, and analytics
- Docker Compose orchestration for the Spring Boot app, analytics service, and MySQL

## Requirements
- JDK 17 or later
- Maven 3.6+ (or included Maven wrapper)
- MySQL 8.x
- Python 3.12+ and pip if running the analytics service outside Docker

## Run without Docker
1. Start MySQL and create the `placement_analytics` database. Hibernate creates or updates the application tables on startup.
2. Install the Python service dependencies:

   ```powershell
   python -m pip install -r python_etl/requirements.txt
   ```

3. In a terminal, configure MySQL access and start the analytics service:

   ```powershell
   $env:DB_HOST = "localhost"
   $env:DB_PORT = "3306"
   $env:DB_NAME = "placement_analytics"
   $env:DB_USERNAME = "root"
   $env:DB_PASSWORD = "your-local-mysql-password"
   python python_etl/analytics_app.py
   ```

4. In a second terminal in the `RestApi` folder, run `mvnw.cmd spring-boot:run` on Windows or `./mvnw spring-boot:run` on macOS/Linux. Use the same `DB_USERNAME` and `DB_PASSWORD` values for Spring Boot.
5. Open http://localhost:8080.

For the simplest setup, use Docker Compose below; it starts all three services and wires their database connection automatically.

## Run with Docker
From the `RestApi` folder, start the application and MySQL with Docker Compose:

```bash
docker compose up --build
```

Open http://localhost:8080 when the containers are running. Compose starts the Spring Boot website/API, a Python analytics service that cleans and aggregates live MySQL data with pandas, and MySQL. The overview dashboard includes live KPI cards, a clickable status doughnut chart, department applications/offers, offers by company, monthly applications, and department/year/status filters. Selecting a status slice or department bar filters the dashboard. Chart.js is served locally by the application, so the dashboard does not depend on a public chart CDN.

The default database password (`placement_dev_password`) is for local development only. To override it, set `DB_PASSWORD` in your environment before running Compose; use a strong secret and do not commit it. To stop the containers, press Ctrl+C and run `docker compose down`. To also permanently remove the database volume and its data, run `docker compose down -v`.

## API endpoints
| Method | Endpoint | Action |
|---|---|---|
| GET/POST | `/api/students` | List/create students |
| GET/PUT/DELETE | `/api/students/{id}` | Read/update/delete student |
| GET/POST | `/api/companies` | List/create companies |
| GET/PUT/DELETE | `/api/companies/{id}` | Read/update/delete company |
| GET/POST | `/api/jobs` | List/create job openings |
| GET/PUT/DELETE | `/api/jobs/{id}` | Read/update/delete job opening |
| GET/POST | `/api/applications` | List/create applications |
| GET/PUT/DELETE | `/api/applications/{id}` | Read/update/delete application |
| PATCH | `/api/applications/{id}/status` | Update status only |
| GET | `/api/analytics/summary` | Summary counts for website cards |
| GET | `/api/analytics/dashboard` | Pandas-backed dashboard data; accepts `department`, `graduationYear`, and `status` filters |

Valid statuses: `Applied`, `Shortlisted`, `Interview`, `Offered`, `Accepted`, `Rejected`, `Withdrawn`.

## Python/Pandas analytics
The Dockerized analytics service reads the current MySQL records when the dashboard is requested. Pandas normalizes department and status labels, handles missing values, removes duplicate application IDs, and applies dashboard filters before returning KPI/chart data to Spring Boot. No CSV export or separate report refresh is needed for the website dashboard.

To run the Python analytics service directly instead of using Docker, install its dependencies and configure it to connect to your MySQL instance:

```powershell
python -m pip install -r python_etl/requirements.txt
$env:DB_HOST = "localhost"
$env:DB_PORT = "3306"
$env:DB_NAME = "placement_analytics"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "your-local-mysql-password"
python python_etl/analytics_app.py
```

The interactive website charts use locally hosted Chart.js; Python/Pandas cleans, filters, and aggregates their live data.

### PS syllabus mapping
- Data preprocessing: Pandas duplicate removal, date conversion, missing-value handling and status standardization.
- ETL: live MySQL extract → Pandas cleaning, normalization, filtering, and aggregation.
- Data warehousing: application facts are combined with student, company/job, and date dimensions for dashboard analysis.
- KPI reporting: applications, applicants, interview progression, offers, and acceptance.
- Interactive visualization: website charts, filters, and cross-filtering.
- Advanced analysis: company, department and job-type comparisons. Forecasting is optional and should only be used with sufficient historical data.

## Important notes
- The website dashboard reads live MySQL data through the Dockerized analytics service.
- The API is configured for open classroom demonstration. Do not deploy it with real personal data without proper authentication, authorization and access controls.
- Use synthetic or anonymized student records in screenshots and presentations.
- Interview progression calculated from the current status is only an approximation. For exact conversion over time, add an application-status-history table in a future iteration.
