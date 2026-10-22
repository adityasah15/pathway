"""Clean placement records and build filtered dashboard data with pandas."""

from __future__ import annotations

import pandas as pd

STATUSES = [
    "Applied",
    "Shortlisted",
    "Interview",
    "Offered",
    "Accepted",
    "Rejected",
    "Withdrawn",
    "Unknown",
]

RECORD_COLUMNS = [
    "application_id",
    "applied_date",
    "status",
    "student_id",
    "department",
    "graduation_year",
    "job_type",
    "company_name",
    "offer_package_lpa",
]


def clean_records(records: list[dict]) -> tuple[pd.DataFrame, pd.DataFrame]:
    """Return deduplicated application and student frames with normalized values."""
    frame = pd.DataFrame.from_records(records)
    for column in RECORD_COLUMNS:
        if column not in frame:
            frame[column] = pd.NA

    frame = frame[RECORD_COLUMNS].copy()
    frame["application_id"] = pd.to_numeric(frame["application_id"], errors="coerce")

    for column in ["department", "company_name", "job_type"]:
        values = frame[column].astype("string").str.strip().str.replace(r"\s+", " ", regex=True)
        frame[column] = values.replace("", pd.NA).fillna("Not specified")

    frame["department"] = frame["department"].map(
        lambda value: value.upper()
        if len(value) <= 4
        else value.title()
    )

    status_names = {status.casefold(): status for status in STATUSES}
    frame["status"] = (
        frame["status"]
        .astype("string")
        .str.strip()
        .str.casefold()
        .map(status_names)
        .fillna("Unknown")
    )
    frame["applied_date"] = pd.to_datetime(frame["applied_date"], errors="coerce")
    frame["graduation_year"] = pd.to_numeric(frame["graduation_year"], errors="coerce").astype("Int64")
    frame["offer_package_lpa"] = pd.to_numeric(frame["offer_package_lpa"], errors="coerce")

    students = (
        frame[["student_id", "department", "graduation_year"]]
        .dropna(subset=["student_id"])
        .drop_duplicates(subset=["student_id"])
    )
    applications = (
        frame.dropna(subset=["application_id"])
        .drop_duplicates(subset=["application_id"], keep="first")
        .copy()
    )
    return applications, students


def build_dashboard(
    records: list[dict],
    department: str | None = None,
    graduation_year: int | None = None,
    status: str | None = None,
) -> dict:
    """Aggregate cleaned records for the website's dashboard and filter controls."""
    applications, students = clean_records(records)

    departments = sorted(students["department"].dropna().astype(str).unique().tolist())
    graduation_years = sorted(
        int(year)
        for year in students["graduation_year"].dropna().unique().tolist()
    )

    filtered_students = students
    filtered_applications = applications
    if department:
        filtered_students = filtered_students.loc[
            filtered_students["department"].str.casefold() == department.strip().casefold()
        ]
        filtered_applications = filtered_applications.loc[
            filtered_applications["department"].str.casefold() == department.strip().casefold()
        ]
    if graduation_year is not None:
        filtered_students = filtered_students.loc[
            filtered_students["graduation_year"] == graduation_year
        ]
        filtered_applications = filtered_applications.loc[
            filtered_applications["graduation_year"] == graduation_year
        ]
    if status:
        filtered_applications = filtered_applications.loc[
            filtered_applications["status"].str.casefold() == status.strip().casefold()
        ]

    status_counts = (
        filtered_applications.groupby("status").size().to_dict()
        if not filtered_applications.empty
        else {}
    )
    status_distribution = [
        {"status": label, "count": int(status_counts.get(label, 0))}
        for label in STATUSES
    ]

    department_rows = []
    for name, group in filtered_applications.groupby("department", dropna=False):
        department_rows.append(
            {
                "department": str(name),
                "applications": int(len(group)),
                "offers": int(group["status"].isin(["Offered", "Accepted"]).sum()),
                "students": int(group["student_id"].nunique()),
            }
        )
    department_rows.sort(key=lambda item: (-item["applications"], item["department"]))

    offer_rows = filtered_applications.loc[
        filtered_applications["status"].isin(["Offered", "Accepted"])
    ]
    company_rows = (
        offer_rows.groupby("company_name", dropna=False)
        .size()
        .sort_values(ascending=False)
        .head(8)
    )
    company_offers = [
        {"company": str(company), "offers": int(count)}
        for company, count in company_rows.items()
    ]

    monthly_rows = filtered_applications.dropna(subset=["applied_date"]).copy()
    if monthly_rows.empty:
        monthly_applications = []
    else:
        monthly_rows["month"] = monthly_rows["applied_date"].dt.strftime("%Y-%m")
        monthly_counts = monthly_rows.groupby("month").size().sort_index()
        monthly_applications = [
            {"month": str(month), "applications": int(count)}
            for month, count in monthly_counts.items()
        ]

    status_series = filtered_applications["status"]
    offers = status_series.isin(["Offered", "Accepted"])
    return {
        "filters": {"departments": departments, "graduationYears": graduation_years},
        "selectedFilters": {
            "department": department or "",
            "graduationYear": graduation_year,
            "status": status or "",
        },
        "summary": {
            "students": int(filtered_students["student_id"].nunique()),
            "applications": int(len(filtered_applications)),
            "interviews": int(status_series.isin(["Interview", "Offered", "Accepted"]).sum()),
            "offers": int(offers.sum()),
            "accepted": int(status_series.eq("Accepted").sum()),
            "companies": int(filtered_applications["company_name"].nunique()),
        },
        "statusDistribution": status_distribution,
        "departmentStats": department_rows,
        "companyOffers": company_offers,
        "monthlyApplications": monthly_applications,
    }
