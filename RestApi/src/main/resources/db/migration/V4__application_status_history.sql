CREATE TABLE IF NOT EXISTS application_status_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    changed_at DATETIME NOT NULL,
    changed_by VARCHAR(50) DEFAULT NULL,
    note VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_history_application (application_id, changed_at),
    CONSTRAINT fk_history_application FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Give every existing application a starting history entry.
INSERT INTO application_status_history (application_id, status, changed_at, changed_by, note)
SELECT a.id, a.status, COALESCE(a.updated_at, NOW()), 'system', 'Backfilled from current status'
FROM applications a
WHERE NOT EXISTS (
    SELECT 1 FROM application_status_history h WHERE h.application_id = a.id
);
