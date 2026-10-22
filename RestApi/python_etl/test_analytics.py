import unittest
from unittest.mock import patch

import pymysql
from analytics import build_dashboard, clean_records
from analytics_app import app


class DashboardAnalyticsTests(unittest.TestCase):
    def setUp(self):
        self.records = [
            {
                "application_id": 1,
                "applied_date": "2026-08-10",
                "status": " offered ",
                "student_id": 10,
                "department": " information technology ",
                "graduation_year": 2027,
                "job_type": "Full-time",
                "company_name": "Example Co",
            },
            {
                "application_id": 1,
                "applied_date": "2026-08-10",
                "status": "offered",
                "student_id": 10,
                "department": "Information Technology",
                "graduation_year": 2027,
                "job_type": "Full-time",
                "company_name": "Example Co",
            },
            {
                "application_id": 2,
                "applied_date": "2026-09-02",
                "status": "Interview",
                "student_id": 11,
                "department": "Computer Engineering",
                "graduation_year": 2026,
                "job_type": "Internship",
                "company_name": "Another Co",
            },
            {
                "application_id": None,
                "student_id": 12,
                "department": "IT",
                "graduation_year": 2027,
            },
        ]

    def test_cleaning_deduplicates_and_standardizes(self):
        applications, students = clean_records(self.records)

        self.assertEqual(len(applications), 2)
        self.assertEqual(students["student_id"].nunique(), 3)
        self.assertEqual(applications.iloc[0]["status"], "Offered")
        self.assertEqual(applications.iloc[0]["department"], "Information Technology")

    def test_filters_and_dashboard_metrics(self):
        report = build_dashboard(
            self.records,
            department="INFORMATION TECHNOLOGY",
            graduation_year=2027,
        )

        self.assertEqual(report["summary"]["students"], 1)
        self.assertEqual(report["summary"]["applications"], 1)
        self.assertEqual(report["summary"]["offers"], 1)
        self.assertEqual(report["companyOffers"], [{"company": "Example Co", "offers": 1}])
        self.assertEqual(
            report["monthlyApplications"],
            [{"month": "2026-08", "applications": 1}],
        )

    def test_students_without_applications_are_kept_in_student_kpi(self):
        report = build_dashboard(self.records)

        self.assertEqual(report["summary"]["students"], 3)
        self.assertEqual(report["summary"]["applications"], 2)

    def test_status_history_is_used_for_the_funnel_and_placement_rate(self):
        records = [
            {
                "application_id": 1,
                "applied_date": "2026-01-10",
                "status": "Offered",
                "student_id": 10,
                "department": "IT",
                "graduation_year": 2027,
                "job_type": "Full-time",
                "company_name": "Acme",
                "offer_package_lpa": 8.0,
            },
            {
                "application_id": 2,
                "applied_date": "2026-02-10",
                "status": "Accepted",
                "student_id": 11,
                "department": "IT",
                "graduation_year": 2027,
                "job_type": "Full-time",
                "company_name": "Acme",
                "offer_package_lpa": 12.0,
            },
            {
                "application_id": 3,
                "applied_date": "2026-03-10",
                "status": "Rejected",
                "student_id": 12,
                "department": "IT",
                "graduation_year": 2027,
                "job_type": "Full-time",
                "company_name": "Beta",
                "offer_package_lpa": None,
            },
        ]
        history = [
            {"application_id": 3, "status": "Applied"},
            {"application_id": 3, "status": "Shortlisted"},
            {"application_id": 3, "status": "Interview"},
        ]

        report = build_dashboard(records, history=history)

        self.assertEqual(
            report["funnel"],
            [
                {"stage": "Applied", "count": 3},
                {"stage": "Shortlisted", "count": 3},
                {"stage": "Interview", "count": 3},
                {"stage": "Offered", "count": 2},
                {"stage": "Accepted", "count": 1},
            ],
        )
        self.assertEqual(report["summary"]["interviews"], 3)
        self.assertEqual(report["summary"]["offers"], 2)
        self.assertEqual(report["summary"]["accepted"], 1)
        self.assertEqual(report["placementRate"], 33.3)
        self.assertEqual(report["medianPackage"], 10.0)
        self.assertEqual(report["highestPackage"], 12.0)
        self.assertEqual(report["topRecruiter"], "Acme")

    def test_http_endpoint_returns_filtered_dashboard(self):
        with patch("analytics_app.load_data", return_value=(self.records, [])):
            response = app.test_client().get(
                "/dashboard?department=Information%20Technology&graduationYear=2027"
            )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json["summary"]["applications"], 1)
        self.assertEqual(response.json["selectedFilters"]["graduationYear"], 2027)

    def test_http_endpoint_rejects_invalid_graduation_year(self):
        response = app.test_client().get("/dashboard?graduationYear=twenty")

        self.assertEqual(response.status_code, 400)

    def test_http_endpoint_returns_csv_export(self):
        with patch("analytics_app.load_data", return_value=(self.records, [])):
            response = app.test_client().get("/export.csv")

        self.assertEqual(response.status_code, 200)
        self.assertIn("text/csv", response.content_type)
        self.assertEqual(
            response.headers["Content-Disposition"],
            "attachment; filename=applications.csv",
        )
        body = response.get_data(as_text=True)
        self.assertIn("application_id", body)
        self.assertIn("Offered", body)

    def test_health_endpoint_checks_the_database(self):
        with patch("analytics_app._connect") as connect:
            response = app.test_client().get("/health")

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json, {"status": "ok"})
        connect.return_value.cursor.return_value.__enter__.return_value.execute.assert_called_once_with(
            "SELECT 1"
        )

    def test_health_endpoint_reports_an_unavailable_database(self):
        with patch("analytics_app._connect", side_effect=pymysql.MySQLError("down")), patch(
            "analytics_app.logger"
        ):
            response = app.test_client().get("/health")

        self.assertEqual(response.status_code, 503)
        self.assertEqual(response.json, {"status": "unavailable"})

    def test_empty_dataset_returns_empty_chart_series(self):
        report = build_dashboard([])

        self.assertEqual(report["summary"]["applications"], 0)
        self.assertTrue(all(item["count"] == 0 for item in report["statusDistribution"]))
        self.assertEqual(report["funnel"], [
            {"stage": "Applied", "count": 0},
            {"stage": "Shortlisted", "count": 0},
            {"stage": "Interview", "count": 0},
            {"stage": "Offered", "count": 0},
            {"stage": "Accepted", "count": 0},
        ])
        self.assertEqual(report["departmentStats"], [])
        self.assertEqual(report["companyOffers"], [])


if __name__ == "__main__":
    unittest.main()
