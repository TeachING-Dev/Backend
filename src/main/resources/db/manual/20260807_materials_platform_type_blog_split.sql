-- Manual MySQL schema alignment for split blog platform types.
-- This project currently does not use Flyway/Liquibase and dev/prod use hibernate.ddl-auto=update.
-- Apply this file manually after backing up the target database.
--
-- Goal:
-- - Allow new URL analyses to store TISTORY and NAVER_BLOG in materials.platform_type.
-- - Preserve supported and legacy enum values except removed PDF: BLOG, CAFE, NOTION, VELOG, WEB, YOUTUBE.
-- - Remove PDF from the database enum after verifying there are no PDF rows.
-- - Do not backfill existing BLOG rows in this script.
--
-- Preconditions:
-- - Run the verification query first.
-- - If COLUMN_TYPE is already a VARCHAR or already includes TISTORY and NAVER_BLOG, do not run ALTER TABLE.
-- - If pdf_row_count is greater than 0, do not run ALTER TABLE until a product-approved data cleanup is complete.
--
-- Rollback note:
-- - Do not remove TISTORY or NAVER_BLOG from the enum while rows with those values exist.
-- - To roll back safely, first decide a product-approved mapping for those rows, such as BLOG,
--   and update them manually before narrowing the enum.

SHOW COLUMNS FROM materials LIKE 'platform_type';

SELECT COUNT(*) AS pdf_row_count
FROM materials
WHERE platform_type = 'PDF';

ALTER TABLE materials
    MODIFY COLUMN platform_type ENUM(
        'YOUTUBE',
        'VELOG',
        'TISTORY',
        'NAVER_BLOG',
        'BLOG',
        'CAFE',
        'NOTION',
        'WEB'
    ) NOT NULL;

-- Post-apply verification.
SHOW COLUMNS FROM materials LIKE 'platform_type';

SELECT platform_type, COUNT(*) AS row_count
FROM materials
GROUP BY platform_type
ORDER BY platform_type;
