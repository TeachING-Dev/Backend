-- Manual MySQL schema alignment for soft-deleted folder name reuse.
-- This project currently does not use Flyway/Liquibase and dev/prod use hibernate.ddl-auto=update.
-- Apply this file manually after backing up the target database.
--
-- Goal:
-- - Only active folders (deleted_at IS NULL) must be unique per user and name.
-- - Soft-deleted folders must not block creating a new active folder with the same name.
-- - Restoring a deleted folder with a conflicting active name must still fail.
--
-- Run this as a full script in a MySQL-compatible client that supports stored procedures and
-- the DELIMITER client directive, such as the MySQL CLI or IntelliJ Database Console.
-- DELIMITER is a client directive, not MySQL server SQL, so do not treat this as an automatic
-- JDBC/Flyway/Liquibase migration file.

DELIMITER //

DROP PROCEDURE IF EXISTS migrate_folders_active_name_unique//

CREATE PROCEDURE migrate_folders_active_name_unique()
BEGIN
    DECLARE v_active_duplicate_count INT DEFAULT 0;
    DECLARE v_active_name_column_count INT DEFAULT 0;
    DECLARE v_active_unique_exact_index_count INT DEFAULT 0;
    DECLARE v_active_unique_wrong_index_count INT DEFAULT 0;
    DECLARE v_legacy_unique_index_count INT DEFAULT 0;
    DECLARE v_legacy_unique_index_name VARCHAR(128);

    SELECT COUNT(*)
      INTO v_active_duplicate_count
      FROM (
          SELECT user_id, name
            FROM folders
           WHERE deleted_at IS NULL
           GROUP BY user_id, name
          HAVING COUNT(*) > 1
      ) active_duplicates;

    IF v_active_duplicate_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'folders contains duplicate active names for the same user';
    END IF;

    SELECT COUNT(*), MIN(INDEX_NAME)
      INTO v_legacy_unique_index_count, v_legacy_unique_index_name
      FROM (
          SELECT INDEX_NAME
            FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'folders'
             AND NON_UNIQUE = 0
           GROUP BY INDEX_NAME
          HAVING GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') IN ('user_id,name', 'name,user_id')
      ) legacy_unique_indexes;

    IF v_legacy_unique_index_count > 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'folders has multiple legacy unique indexes for user_id and name';
    END IF;

    SELECT COUNT(*)
      INTO v_active_name_column_count
      FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'folders'
       AND COLUMN_NAME = 'active_folder_name';

    IF v_active_name_column_count = 0 THEN
        ALTER TABLE folders
            ADD COLUMN active_folder_name VARCHAR(100)
            GENERATED ALWAYS AS (
                CASE
                    WHEN deleted_at IS NULL THEN name
                    ELSE NULL
                END
            ) STORED;
    END IF;

    SELECT
           COALESCE(SUM(CASE
               WHEN NON_UNIQUE = 0 AND indexed_columns = 'user_id,active_folder_name' THEN 1
               ELSE 0
           END), 0),
           COALESCE(SUM(CASE
               WHEN NOT (NON_UNIQUE = 0 AND indexed_columns = 'user_id,active_folder_name') THEN 1
               ELSE 0
           END), 0)
      INTO v_active_unique_exact_index_count, v_active_unique_wrong_index_count
      FROM (
          SELECT INDEX_NAME,
                 MIN(NON_UNIQUE) AS NON_UNIQUE,
                 GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
            FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'folders'
             AND INDEX_NAME = 'uk_folders_user_active_name'
           GROUP BY INDEX_NAME
      ) active_unique_indexes;

    IF v_active_unique_wrong_index_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'uk_folders_user_active_name exists with unexpected columns or uniqueness';
    END IF;

    IF v_active_unique_exact_index_count = 0 THEN
        ALTER TABLE folders
            ADD UNIQUE INDEX uk_folders_user_active_name (user_id, active_folder_name);
    END IF;

    SELECT COUNT(*)
      INTO v_active_unique_exact_index_count
      FROM (
          SELECT INDEX_NAME
            FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'folders'
             AND INDEX_NAME = 'uk_folders_user_active_name'
             AND NON_UNIQUE = 0
           GROUP BY INDEX_NAME
          HAVING GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') = 'user_id,active_folder_name'
      ) verified_active_unique_indexes;

    IF v_active_unique_exact_index_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'uk_folders_user_active_name is missing before dropping legacy unique index';
    END IF;

    IF v_legacy_unique_index_name IS NOT NULL THEN
        SET @drop_legacy_unique_sql = CONCAT(
            'ALTER TABLE folders DROP INDEX `',
            REPLACE(v_legacy_unique_index_name, '`', '``'),
            '`'
        );
        PREPARE drop_legacy_unique_stmt FROM @drop_legacy_unique_sql;
        EXECUTE drop_legacy_unique_stmt;
        DEALLOCATE PREPARE drop_legacy_unique_stmt;
    END IF;
END//

CALL migrate_folders_active_name_unique()//

DROP PROCEDURE IF EXISTS migrate_folders_active_name_unique//

DELIMITER ;

-- Post-apply verification.
SHOW COLUMNS FROM folders;

SELECT
    INDEX_NAME,
    NON_UNIQUE,
    GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'folders'
GROUP BY INDEX_NAME, NON_UNIQUE
ORDER BY INDEX_NAME;
