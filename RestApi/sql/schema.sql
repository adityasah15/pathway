-- Schema is owned by Flyway. Migrations live in
-- RestApi/src/main/resources/db/migration and are applied automatically on startup.
--
-- The application creates the database itself because the JDBC URL includes
-- createDatabaseIfNotExist=true. Use this file only if you need to create the
-- empty database manually:
CREATE DATABASE IF NOT EXISTS placement_analytics;
