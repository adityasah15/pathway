"""HTTP API that serves pandas-cleaned dashboard analytics from MySQL."""

from __future__ import annotations

import logging
import os

import pymysql
from flask import Flask, jsonify, request
from pymysql.cursors import DictCursor

from analytics import build_dashboard

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


def load_records() -> list[dict]:
    connection = pymysql.connect(
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
    try:
        with connection.cursor() as cursor:
            cursor.execute(APPLICATIONS_QUERY)
            return list(cursor.fetchall())
    finally:
        connection.close()


@app.get("/health")
def health():
    return jsonify({"status": "ok"})


@app.get("/dashboard")
def dashboard():
    year_value = request.args.get("graduationYear", "").strip()
    try:
        graduation_year = int(year_value) if year_value else None
    except ValueError:
        return jsonify({"error": "graduationYear must be a whole number"}), 400

    try:
        records = load_records()
        result = build_dashboard(
            records,
            department=request.args.get("department") or None,
            graduation_year=graduation_year,
            status=request.args.get("status") or None,
        )
        return jsonify(result)
    except pymysql.MySQLError:
        logger.exception("Could not load dashboard records from MySQL")
        return jsonify({"error": "Dashboard data is temporarily unavailable"}), 503


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.getenv("PORT", "5000")))
