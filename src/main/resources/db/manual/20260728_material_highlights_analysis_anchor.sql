-- Manual MySQL schema alignment for MaterialHighlight analysis-based anchoring.
-- This project currently does not use Flyway/Liquibase and dev/prod use hibernate.ddl-auto=update.
-- Apply this file manually after backing up the target database. It is written to be safe on
-- databases that have already removed the legacy material_chunk_id column.
--
-- Run this as a full script in a MySQL-compatible client that supports stored procedures and
-- the DELIMITER client directive, such as the MySQL CLI or IntelliJ Database Console.
-- DELIMITER is a client directive, not MySQL server SQL, so do not treat this as an automatic
-- JDBC/Flyway/Liquibase migration file.

DELIMITER //

DROP PROCEDURE IF EXISTS migrate_material_highlights_analysis_anchor//

CREATE PROCEDURE migrate_material_highlights_analysis_anchor()
BEGIN
    DECLARE v_analysis_column_count INT DEFAULT 0;
    DECLARE v_chunk_column_count INT DEFAULT 0;
    DECLARE v_invalid_analysis_count INT DEFAULT 0;
    DECLARE v_chunk_fk_name VARCHAR(128);
    DECLARE v_chunk_fk_count INT DEFAULT 0;
    DECLARE v_analysis_fk_count INT DEFAULT 0;
    DECLARE v_duplicate_analysis_material_count INT DEFAULT 0;

    SELECT COUNT(*)
      INTO v_analysis_column_count
      FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'material_highlights'
       AND COLUMN_NAME = 'material_analysis_id';

    IF v_analysis_column_count = 0 THEN
        ALTER TABLE material_highlights
            ADD COLUMN material_analysis_id BIGINT NULL;
    END IF;

    SELECT COUNT(*)
      INTO v_chunk_column_count
      FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'material_highlights'
       AND COLUMN_NAME = 'material_chunk_id';

    SELECT COUNT(*), MIN(CONSTRAINT_NAME)
      INTO v_chunk_fk_count, v_chunk_fk_name
      FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'material_highlights'
       AND COLUMN_NAME = 'material_chunk_id'
       AND REFERENCED_TABLE_NAME IS NOT NULL;

    IF v_chunk_fk_count > 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'material_highlights.material_chunk_id has multiple foreign keys';
    END IF;

    IF v_chunk_column_count > 0 THEN
        SELECT COUNT(*)
          INTO v_duplicate_analysis_material_count
          FROM (
              SELECT mc.material_id
                FROM material_highlights mh
                JOIN material_chunks mc ON mh.material_chunk_id = mc.id
                JOIN material_analysis ma ON ma.material_id = mc.material_id
               WHERE mh.material_analysis_id IS NULL
                  OR mh.material_analysis_id = 0
                  OR NOT EXISTS (
                      SELECT 1
                        FROM material_analysis valid_ma
                       WHERE valid_ma.id = mh.material_analysis_id
                  )
               GROUP BY mc.material_id
              HAVING COUNT(DISTINCT ma.id) > 1
          ) duplicate_analysis_materials;

        IF v_duplicate_analysis_material_count > 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'material_analysis has duplicate rows for material_id used by material_highlights migration';
        END IF;

        UPDATE material_highlights mh
        JOIN material_chunks mc ON mh.material_chunk_id = mc.id
        JOIN material_analysis ma ON ma.material_id = mc.material_id
           SET mh.material_analysis_id = ma.id
         WHERE mh.material_analysis_id IS NULL
            OR mh.material_analysis_id = 0
            OR NOT EXISTS (
                SELECT 1
                  FROM material_analysis valid_ma
                 WHERE valid_ma.id = mh.material_analysis_id
            );
    END IF;

    SELECT COUNT(*)
      INTO v_invalid_analysis_count
      FROM material_highlights mh
     WHERE mh.material_analysis_id IS NULL
        OR mh.material_analysis_id = 0
        OR NOT EXISTS (
            SELECT 1
              FROM material_analysis ma
             WHERE ma.id = mh.material_analysis_id
        );

    IF v_invalid_analysis_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'material_highlights contains rows that cannot be mapped to material_analysis_id';
    END IF;

    ALTER TABLE material_highlights
        MODIFY COLUMN material_analysis_id BIGINT NOT NULL;

    IF v_chunk_fk_name IS NOT NULL THEN
        SET @drop_chunk_fk_sql = CONCAT(
            'ALTER TABLE material_highlights DROP FOREIGN KEY `',
            REPLACE(v_chunk_fk_name, '`', '``'),
            '`'
        );
        PREPARE drop_chunk_fk_stmt FROM @drop_chunk_fk_sql;
        EXECUTE drop_chunk_fk_stmt;
        DEALLOCATE PREPARE drop_chunk_fk_stmt;
    END IF;

    IF v_chunk_column_count > 0 THEN
        ALTER TABLE material_highlights
            DROP COLUMN material_chunk_id;
    END IF;

    SELECT COUNT(*)
      INTO v_analysis_fk_count
      FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'material_highlights'
       AND COLUMN_NAME = 'material_analysis_id'
       AND REFERENCED_TABLE_NAME = 'material_analysis'
       AND REFERENCED_COLUMN_NAME = 'id';

    IF v_analysis_fk_count = 0 THEN
        ALTER TABLE material_highlights
            ADD CONSTRAINT fk_material_highlights_material_analysis
            FOREIGN KEY (material_analysis_id)
            REFERENCES material_analysis(id);
    END IF;
END//

CALL migrate_material_highlights_analysis_anchor()//

DROP PROCEDURE IF EXISTS migrate_material_highlights_analysis_anchor//

DELIMITER ;

-- Post-apply verification.
SHOW COLUMNS FROM material_highlights;

SELECT
    CONSTRAINT_NAME,
    COLUMN_NAME,
    REFERENCED_TABLE_NAME,
    REFERENCED_COLUMN_NAME
FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'material_highlights'
  AND REFERENCED_TABLE_NAME IS NOT NULL;
