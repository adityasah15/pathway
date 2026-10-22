import unittest
from unittest.mock import patch

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

    def test_http_endpoint_returns_filtered_dashboard(self):
        with patch("analytics_app.load_records", return_value=self.records):
            response = app.test_client().get(
                "/dashboard?department=Information%20Technology&graduationYear=2027"
            )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json["summary"]["applications"], 1)
        self.assertEqual(response.json["selectedFilters"]["graduationYear"], 2027)

    def test_http_endpoint_rejects_invalid_graduation_year(self):
        response = app.test_client().get("/dashboard?graduationYear=twenty")

        self.assertEqual(response.status_code, 400)

    def test_empty_dataset_returns_empty_chart_series(self):
        report = build_dashboard([])

        self.assertEqual(report["summary"]["applications"], 0)
        self.assertTrue(all(item["count"] == 0 for item in report["statusDistribution"]))
        self.assertEqual(report["departmentStats"], [])
        self.assertEqual(report["companyOffers"], [])


if __name__ == "__main__":
    unittest.main()
