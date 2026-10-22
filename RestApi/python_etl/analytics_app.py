"""HTTP API that serves pandas-cleaned dashboard analytics from MySQL."""

from __future__ import annotations

import logging
import os

import pymysql
from flask import Flask, Response, jsonify, request
from pymysql.cursors import DictCursor

from analytics import build_dashboard, clean_records

app = Flask(__name__)
logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"))
logger = logging.getLogger(__name__)

APPLICATIONS_QUERY = """
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
"""

HISTORY_QUERY = """
SELECT application_id, status, changed_at
FROM application_status_history
ORDER BY application_id, changed_at, id
"""


def _connect():
    return pymysql.connect(
        host=os.getenv("DB_HOST", "localhost"),
        port=int(os.getenv("DB_PORT", "3306")),
        user=os.getenv("DB_USERNAME", "root"),
        password=os.getenv("DB_PASSWORD", ""),
        database=os.getenv("DB_NAME", "placement_analytics"),
        cursorclass=DictCursor,
        connect_timeout=5,
        read_timeout=10,
        write_timeout=10,
    )


def load_data() -> tuple[list[dict], list[dict]]:
    """Load applications and their status history in one connection."""
    connection = _connect()
    try:
        with connection.cursor() as cursor:
            cursor.execute(APPLICATIONS_QUERY)
            records = list(cursor.fetchall())
            cursor.execute(HISTORY_QUERY)
            history = list(cursor.fetchall())
        return records, history
    finally:
        connection.close()


def load_records() -> list[dict]:
    """Backwards-compatible helper for callers that only need applications."""
    return load_data()[0]


@app.get("/health")
def health():
    try:
        connection = _connect()
        try:
            with connection.cursor() as cursor:
                cursor.execute("SELECT 1")
        finally:
            connection.close()
        return jsonify({"status": "ok"})
    except pymysql.MySQLError:
        logger.exception("Health check could not reach MySQL")
        return jsonify({"status": "unavailable"}), 503


@app.get("/dashboard")
def dashboard():
    year_value = request.args.get("graduationYear", "").strip()
    try:
        graduation_year = int(year_value) if year_value else None
    except ValueError:
        return jsonify({"error": "graduationYear must be a whole number"}), 400

    try:
        records, history = load_data()
        result = build_dashboard(
            records,
            department=request.args.get("department") or None,
            graduation_year=graduation_year,
            status=request.args.get("status") or None,
            history=history,
        )
        return jsonify(result)
    except pymysql.MySQLError:
        logger.exception("Could not load dashboard records from MySQL")
        return jsonify({"error": "Dashboard data is temporarily unavailable"}), 503


@app.get("/export.csv")
def export_csv():
    try:
        records, _ = load_data()
        applications, _ = clean_records(records)
        payload = applications.to_csv(index=False)
        return Response(
            payload,
            mimetype="text/csv",
            headers={"Content-Disposition": "attachment; filename=applications.csv"},
        )
    except pymysql.MySQLError:
        logger.exception("Could not export application records from MySQL")
        return jsonify({"error": "Export data is temporarily unavailable"}), 503


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.getenv("PORT", "5000")))
