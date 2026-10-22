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

PROGRESSION = ["Applied", "Shortlisted", "Interview", "Offered", "Accepted"]
PROGRESSION_RANK = {status: index for index, status in enumerate(PROGRESSION, start=1)}

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
    frame["application_id"] = pd.to_numeric(frame["application_id"], errors="coerce").astype("Int64")

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


def _stage_lookup(history: list[dict] | None) -> dict[int, set[str]]:
    """Map application id to every status it has ever held."""
    stages: dict[int, set[str]] = {}
    for row in history or []:
        try:
            application_id = int(row.get("application_id"))
        except (TypeError, ValueError):
            continue
        status = row.get("status")
        if status is None or (isinstance(status, float) and pd.isna(status)):
            continue
        stages.setdefault(application_id, set()).add(str(status))
    return stages


def _attach_reached_rank(applications: pd.DataFrame, stage_map: dict[int, set[str]]) -> pd.DataFrame:
    applications = applications.copy()
    if applications.empty:
        applications["reached_rank"] = pd.Series(dtype="int64")
        return applications

    def reached(row) -> int:
        statuses = {str(row["status"])} | stage_map.get(int(row["application_id"]), set())
        # Every recorded application has at least reached the Applied stage, even when
        # its history only contains a terminal status such as Rejected or Withdrawn.
        return max(1, max((PROGRESSION_RANK.get(status, 0) for status in statuses), default=0))

    applications["reached_rank"] = applications.apply(reached, axis=1)
    return applications


def build_dashboard(
    records: list[dict],
    department: str | None = None,
    graduation_year: int | None = None,
    status: str | None = None,
    history: list[dict] | None = None,
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

    filtered_applications = _attach_reached_rank(filtered_applications, _stage_lookup(history))

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

    reached = filtered_applications["reached_rank"]
    funnel = [
        {
            "stage": stage,
            "count": int((reached >= rank).sum()),
        }
        for stage, rank in PROGRESSION_RANK.items()
    ]

    status_series = filtered_applications["status"]
    accepted_students = int(
        filtered_applications.loc[status_series == "Accepted", "student_id"].nunique()
    )
    student_count = int(filtered_students["student_id"].nunique())

    package_rows = offer_rows["offer_package_lpa"].dropna()
    median_package = round(float(package_rows.median()), 2) if not package_rows.empty else 0.0
    highest_package = round(float(package_rows.max()), 2) if not package_rows.empty else 0.0

    return {
        "filters": {"departments": departments, "graduationYears": graduation_years},
        "selectedFilters": {
            "department": department or "",
            "graduationYear": graduation_year,
            "status": status or "",
        },
        "summary": {
            "students": student_count,
            "applications": int(len(filtered_applications)),
            "interviews": int((reached >= PROGRESSION_RANK["Interview"]).sum()),
            "offers": int((reached >= PROGRESSION_RANK["Offered"]).sum()),
            "accepted": int(status_series.eq("Accepted").sum()),
            "companies": int(filtered_applications["company_name"].nunique()),
        },
        "placementRate": round(accepted_students / student_count * 100, 1) if student_count else 0.0,
        "medianPackage": median_package,
        "highestPackage": highest_package,
        "topRecruiter": company_offers[0]["company"] if company_offers else None,
        "funnel": funnel,
        "statusDistribution": status_distribution,
        "departmentStats": department_rows,
        "companyOffers": company_offers,
        "monthlyApplications": monthly_applications,
    }
