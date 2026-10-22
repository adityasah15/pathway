-- Indexes for the dashboard filters and monthly trend queries.
CREATE INDEX idx_applications_status_applied ON applications (status, applied_date);
CREATE INDEX idx_applications_applied_date ON applications (applied_date);
CREATE INDEX idx_students_department_year ON students (department, graduation_year);
CREATE INDEX idx_job_openings_deadline ON job_openings (deadline);
