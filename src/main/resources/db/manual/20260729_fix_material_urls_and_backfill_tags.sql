-- Manual MySQL fixup for data already inserted by the earlier seed scripts:
--   1) original_url values were dummy placeholders (youtube.com/watch?v=backend-spring-api,
--      example.com/..., cafe.naver.com/test/...) that don't resolve to real pages, so
--      "원문으로 이동" doesn't open anything real. This replaces them with real, verified URLs.
--   2) Diagnostic + backfill for material_tag, in case tags aren't showing up in the app
--      because the tag/material_tag INSERT steps didn't run (or ran against a different
--      transaction) when the analysis seed script was executed.
--
-- Matches by materials.title, so it applies regardless of which user/folder the material
-- belongs to. Safe to run multiple times (UPDATE is naturally idempotent; the backfill
-- INSERT is guarded by NOT EXISTS, same as the seed scripts).
--
-- Run manually with a MySQL-compatible client (USE `teaching-db`; first). Check DB_HOST
-- in .env before running against a real environment.
--
-- The UPDATEs below filter by materials.title, not a key/indexed column, so MySQL
-- Workbench's "Safe Updates" mode (sql_safe_updates) will reject them with Error Code 1175.
-- SQL_SAFE_UPDATES is session-scoped, so turning it off here doesn't require reconnecting
-- or changing Workbench preferences; it's restored to 1 at the end of this script.

SET SQL_SAFE_UPDATES = 0;

START TRANSACTION;

-- =====================================================
-- 1. 실제로 열리는 원문 URL로 교체
-- =====================================================

UPDATE materials SET original_url = 'https://www.youtube.com/watch?v=wfj-Z9OQpCA'
WHERE title = 'Spring Boot REST API 개발 강의' AND deleted_at IS NULL;

UPDATE materials SET original_url = 'https://velog.io/@conatuseus/%EC%97%B0%EA%B4%80%EA%B4%80%EA%B3%84-%EB%A7%A4%ED%95%91-%EA%B8%B0%EC%B4%88-2-%EC%96%91%EB%B0%A9%ED%96%A5-%EC%97%B0%EA%B4%80%EA%B4%80%EA%B3%84%EC%99%80-%EC%97%B0%EA%B4%80%EA%B4%80%EA%B3%84%EC%9D%98-%EC%A3%BC%EC%9D%B8'
WHERE title = 'JPA 연관관계 매핑 정리' AND deleted_at IS NULL;

UPDATE materials SET original_url = 'https://spartaclub.kr/blog/2024-backend-jobinterview-question'
WHERE title = '백엔드 개발자 면접 질문 모음' AND deleted_at IS NULL;

UPDATE materials SET original_url = 'https://velog.io/@sasha1107/%EB%85%B8%EC%85%98-%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8-%EA%B4%80%EB%A6%AC-%ED%85%9C%ED%94%8C%EB%A6%BF-%EA%B3%B5%EC%9C%A0'
WHERE title = 'Spring Boot 프로젝트 개발 문서' AND deleted_at IS NULL;

UPDATE materials SET original_url = 'https://hudi.blog/transaction-isolation-level/'
WHERE title = '데이터베이스 트랜잭션 학습 자료' AND deleted_at IS NULL;

UPDATE materials SET original_url = 'https://velog.io/@alsdl0629/Redis-Cache%EB%A5%BC-%EC%A0%81%EC%9A%A9%ED%95%B4-%EC%84%B1%EB%8A%A5-%EA%B0%9C%EC%84%A0'
WHERE title = 'Redis 캐시를 활용한 성능 개선' AND deleted_at IS NULL;

UPDATE materials SET original_url = 'https://www.youtube.com/watch?v=n2lzUnWPxeE'
WHERE title = 'Swift 기초 문법 강의' AND deleted_at IS NULL;

UPDATE materials SET original_url = 'https://velog.io/@mam07065/SwiftUI-%EA%B0%9C%EC%9A%94-%EB%B0%8F-%EB%B7%B0-%EB%A7%8C%EB%93%A4%EA%B8%B0Creating-and-Combining-Views'
WHERE title = 'SwiftUI 화면 구성 방법' AND deleted_at IS NULL;

UPDATE materials SET original_url = 'https://sanghyuk.dev/ios/2/'
WHERE title = 'iOS 개발자 커뮤니티 질문 모음' AND deleted_at IS NULL;

UPDATE materials SET original_url = 'https://velog.io/@spinichi/%EA%B0%9C%EB%B0%9C%EC%9E%90%EB%A5%BC-%EC%9C%84%ED%95%9C-%ED%98%91%EC%97%85-%EA%B0%80%EC%9D%B4%EB%93%9C-%EC%A0%95%EB%A6%AC'
WHERE title = 'iOS 프로젝트 개발 가이드' AND deleted_at IS NULL;

UPDATE materials SET original_url = 'https://velog.io/@little_tail/Swift-Concurrency1%ED%8E%B8async-await'
WHERE title = 'Swift 동시성 프로그래밍 자료' AND deleted_at IS NULL;

UPDATE materials SET original_url = 'https://developer.apple.com/documentation/foundation/urlsession'
WHERE title = 'URLSession 네트워크 통신 구현' AND deleted_at IS NULL;

-- =====================================================
-- 2. 태그 백필 (이미 존재하면 NOT EXISTS 가드로 스킵됨)
-- =====================================================

INSERT INTO tag (name)
SELECT t.name FROM (
    SELECT '백엔드' AS name UNION ALL SELECT 'iOS'
    UNION ALL SELECT 'Spring Boot' UNION ALL SELECT 'JPA' UNION ALL SELECT '면접'
    UNION ALL SELECT '프로젝트관리' UNION ALL SELECT '트랜잭션' UNION ALL SELECT 'Redis'
    UNION ALL SELECT 'Swift' UNION ALL SELECT 'SwiftUI' UNION ALL SELECT '커뮤니티'
    UNION ALL SELECT '프로젝트가이드' UNION ALL SELECT '동시성' UNION ALL SELECT '네트워크'
    UNION ALL SELECT 'REST API' UNION ALL SELECT '입문' UNION ALL SELECT 'ORM'
    UNION ALL SELECT '연관관계' UNION ALL SELECT '커리어' UNION ALL SELECT '기술면접'
    UNION ALL SELECT '협업' UNION ALL SELECT 'DB' UNION ALL SELECT '동시성제어'
    UNION ALL SELECT '캐싱' UNION ALL SELECT '성능최적화' UNION ALL SELECT '문법'
    UNION ALL SELECT 'UI' UNION ALL SELECT '레이아웃' UNION ALL SELECT 'ARC'
    UNION ALL SELECT 'MVVM' UNION ALL SELECT 'Async/Await' UNION ALL SELECT 'URLSession'
    UNION ALL SELECT 'HTTP'
) t
WHERE NOT EXISTS (SELECT 1 FROM tag WHERE tag.name = t.name);

INSERT INTO material_tag (material_id, tag_id, is_representative, created_at, updated_at)
SELECT m.id, t.id, d.is_representative, NOW(), NOW()
FROM materials m
JOIN folders f ON f.id = m.folder_id
JOIN (
    SELECT 'Spring Boot REST API 개발 강의' AS title, 'Spring Boot' AS tag_name, TRUE AS is_representative
    UNION ALL SELECT 'Spring Boot REST API 개발 강의', '백엔드', FALSE
    UNION ALL SELECT 'Spring Boot REST API 개발 강의', 'REST API', FALSE
    UNION ALL SELECT 'Spring Boot REST API 개발 강의', '입문', FALSE
    UNION ALL SELECT 'JPA 연관관계 매핑 정리', 'JPA', TRUE
    UNION ALL SELECT 'JPA 연관관계 매핑 정리', '백엔드', FALSE
    UNION ALL SELECT 'JPA 연관관계 매핑 정리', 'ORM', FALSE
    UNION ALL SELECT 'JPA 연관관계 매핑 정리', '연관관계', FALSE
    UNION ALL SELECT '백엔드 개발자 면접 질문 모음', '면접', TRUE
    UNION ALL SELECT '백엔드 개발자 면접 질문 모음', '백엔드', FALSE
    UNION ALL SELECT '백엔드 개발자 면접 질문 모음', '커리어', FALSE
    UNION ALL SELECT '백엔드 개발자 면접 질문 모음', '기술면접', FALSE
    UNION ALL SELECT 'Spring Boot 프로젝트 개발 문서', '프로젝트관리', TRUE
    UNION ALL SELECT 'Spring Boot 프로젝트 개발 문서', '백엔드', FALSE
    UNION ALL SELECT 'Spring Boot 프로젝트 개발 문서', 'Spring Boot', FALSE
    UNION ALL SELECT 'Spring Boot 프로젝트 개발 문서', '협업', FALSE
    UNION ALL SELECT '데이터베이스 트랜잭션 학습 자료', '트랜잭션', TRUE
    UNION ALL SELECT '데이터베이스 트랜잭션 학습 자료', '백엔드', FALSE
    UNION ALL SELECT '데이터베이스 트랜잭션 학습 자료', 'DB', FALSE
    UNION ALL SELECT '데이터베이스 트랜잭션 학습 자료', '동시성제어', FALSE
    UNION ALL SELECT 'Redis 캐시를 활용한 성능 개선', 'Redis', TRUE
    UNION ALL SELECT 'Redis 캐시를 활용한 성능 개선', '백엔드', FALSE
    UNION ALL SELECT 'Redis 캐시를 활용한 성능 개선', '캐싱', FALSE
    UNION ALL SELECT 'Redis 캐시를 활용한 성능 개선', '성능최적화', FALSE
    UNION ALL SELECT 'Swift 기초 문법 강의', 'Swift', TRUE
    UNION ALL SELECT 'Swift 기초 문법 강의', 'iOS', FALSE
    UNION ALL SELECT 'Swift 기초 문법 강의', '입문', FALSE
    UNION ALL SELECT 'Swift 기초 문법 강의', '문법', FALSE
    UNION ALL SELECT 'SwiftUI 화면 구성 방법', 'SwiftUI', TRUE
    UNION ALL SELECT 'SwiftUI 화면 구성 방법', 'iOS', FALSE
    UNION ALL SELECT 'SwiftUI 화면 구성 방법', 'UI', FALSE
    UNION ALL SELECT 'SwiftUI 화면 구성 방법', '레이아웃', FALSE
    UNION ALL SELECT 'iOS 개발자 커뮤니티 질문 모음', '커뮤니티', TRUE
    UNION ALL SELECT 'iOS 개발자 커뮤니티 질문 모음', 'iOS', FALSE
    UNION ALL SELECT 'iOS 개발자 커뮤니티 질문 모음', '면접', FALSE
    UNION ALL SELECT 'iOS 개발자 커뮤니티 질문 모음', 'ARC', FALSE
    UNION ALL SELECT 'iOS 프로젝트 개발 가이드', '프로젝트가이드', TRUE
    UNION ALL SELECT 'iOS 프로젝트 개발 가이드', 'iOS', FALSE
    UNION ALL SELECT 'iOS 프로젝트 개발 가이드', '협업', FALSE
    UNION ALL SELECT 'iOS 프로젝트 개발 가이드', 'MVVM', FALSE
    UNION ALL SELECT 'Swift 동시성 프로그래밍 자료', '동시성', TRUE
    UNION ALL SELECT 'Swift 동시성 프로그래밍 자료', 'iOS', FALSE
    UNION ALL SELECT 'Swift 동시성 프로그래밍 자료', 'Swift', FALSE
    UNION ALL SELECT 'Swift 동시성 프로그래밍 자료', 'Async/Await', FALSE
    UNION ALL SELECT 'URLSession 네트워크 통신 구현', '네트워크', TRUE
    UNION ALL SELECT 'URLSession 네트워크 통신 구현', 'iOS', FALSE
    UNION ALL SELECT 'URLSession 네트워크 통신 구현', 'URLSession', FALSE
    UNION ALL SELECT 'URLSession 네트워크 통신 구현', 'HTTP', FALSE
) d ON d.title = m.title
JOIN tag t ON t.name = d.tag_name
WHERE f.name IN ('백엔드 개발', 'iOS 개발')
  AND m.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM material_tag mt WHERE mt.material_id = m.id AND mt.tag_id = t.id
  );

COMMIT;

SET SQL_SAFE_UPDATES = 1;

-- =====================================================
-- 3. 검증: 자료별 태그 개수와 실제 태그 이름 확인
--    tag_count가 0으로 나오는 자료가 있으면, 그 material_id로
--    "SELECT * FROM material_tag WHERE material_id = ?" 를 직접 찍어서
--    실제로 행이 있는지/API가 못 읽는지 구분해보세요.
-- =====================================================

SELECT
    m.id AS material_id,
    m.title,
    m.original_url,
    (SELECT COUNT(*) FROM material_tag mt WHERE mt.material_id = m.id) AS tag_count,
    (SELECT GROUP_CONCAT(t.name ORDER BY mt.is_representative DESC SEPARATOR ', ')
       FROM material_tag mt JOIN tag t ON t.id = mt.tag_id WHERE mt.material_id = m.id) AS tags
FROM materials m
JOIN folders f ON f.id = m.folder_id
WHERE f.name IN ('백엔드 개발', 'iOS 개발')
  AND m.deleted_at IS NULL
ORDER BY m.id;
