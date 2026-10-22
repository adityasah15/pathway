-- Baseline schema. Mirrors the original Hibernate-managed database so existing
-- installations can be baselined at version 1, and fresh installs start here.
CREATE TABLE IF NOT EXISTS students (
    id BIGINT NOT NULL AUTO_INCREMENT,
    roll_no VARCHAR(30) NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(150) DEFAULT NULL,
    department VARCHAR(80) NOT NULL,
    cgpa DOUBLE DEFAULT NULL,
    graduation_year INT DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY roll_no (roll_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS companies (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_name VARCHAR(140) NOT NULL,
    industry VARCHAR(100) DEFAULT NULL,
    location VARCHAR(120) DEFAULT NULL,
    website VARCHAR(220) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY company_name (company_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS job_openings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    job_title VARCHAR(140) NOT NULL,
    job_type VARCHAR(40) NOT NULL,
    package_lpa DOUBLE DEFAULT NULL,
    min_cgpa DOUBLE DEFAULT NULL,
    posted_date DATE DEFAULT NULL,
    deadline DATE DEFAULT NULL,
    PRIMARY KEY (id),
    KEY fk_job_company (company_id),
    CONSTRAINT fk_job_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS applications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    job_id BIGINT NOT NULL,
    applied_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'Applied',
    interview_date DATE DEFAULT NULL,
    offer_package_lpa DOUBLE DEFAULT NULL,
    notes VARCHAR(500) DEFAULT NULL,
    updated_at DATETIME DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_student_job (student_id, job_id),
    KEY fk_app_job (job_id),
    CONSTRAINT fk_app_job FOREIGN KEY (job_id) REFERENCES job_openings (id),
    CONSTRAINT fk_app_student FOREIGN KEY (student_id) REFERENCES students (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
