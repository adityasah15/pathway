-- Data sanity rules that should have been enforced by the database from the start.
ALTER TABLE students
    ADD CONSTRAINT chk_students_cgpa CHECK (cgpa IS NULL OR (cgpa >= 0 AND cgpa <= 10)),
    ADD CONSTRAINT chk_students_graduation_year CHECK (graduation_year IS NULL OR (graduation_year BETWEEN 2000 AND 2100));

ALTER TABLE job_openings
    ADD CONSTRAINT chk_job_openings_package CHECK (package_lpa IS NULL OR package_lpa >= 0),
    ADD CONSTRAINT chk_job_openings_min_cgpa CHECK (min_cgpa IS NULL OR (min_cgpa >= 0 AND min_cgpa <= 10));

ALTER TABLE applications
    ADD CONSTRAINT chk_applications_status CHECK (status IN ('Applied', 'Shortlisted', 'Interview', 'Offered', 'Accepted', 'Rejected', 'Withdrawn')),
    ADD CONSTRAINT chk_applications_offer_package CHECK (offer_package_lpa IS NULL OR offer_package_lpa >= 0);
