-- Manual MySQL cleanup: removes the 12 placeholder test materials (6 in '백엔드 개발',
-- 6 in 'iOS 개발') created by the earlier seed scripts, along with their
-- material_analysis / material_highlights / material_tag rows, so they can be replaced
-- with a smaller set of 6 real-URL materials.
--
-- Does NOT delete the folders themselves ('백엔드 개발' / 'iOS 개발') or the `tag` rows
-- (tags are shared/global — only the material_tag links are removed). Matches by
-- materials.title, so it applies regardless of which user owns the material.
--
-- Run manually with a MySQL-compatible client (USE `teaching-db`; first). Check DB_HOST
-- in .env before running against a real environment. Review the row counts from the
-- SELECT at the bottom of this file BEFORE running the DELETEs above it, if you want to
-- confirm scope first (comment out the DELETE/UPDATE statements and run only the SELECT).

SET SQL_SAFE_UPDATES = 0;

START TRANSACTION;

-- =====================================================
-- 1. material_highlights 삭제 (material_analysis 경유)
-- =====================================================

DELETE h FROM material_highlights h
JOIN material_analysis ma ON ma.id = h.material_analysis_id
JOIN materials m ON m.id = ma.material_id
JOIN folders f ON f.id = m.folder_id
WHERE f.name IN ('백엔드 개발', 'iOS 개발')
  AND m.title IN (
      'Spring Boot REST API 개발 강의', 'JPA 연관관계 매핑 정리', '백엔드 개발자 면접 질문 모음',
      'Spring Boot 프로젝트 개발 문서', '데이터베이스 트랜잭션 학습 자료', 'Redis 캐시를 활용한 성능 개선',
      'Swift 기초 문법 강의', 'SwiftUI 화면 구성 방법', 'iOS 개발자 커뮤니티 질문 모음',
      'iOS 프로젝트 개발 가이드', 'Swift 동시성 프로그래밍 자료', 'URLSession 네트워크 통신 구현'
  );

-- =====================================================
-- 2. material_analysis 삭제
-- =====================================================

DELETE ma FROM material_analysis ma
JOIN materials m ON m.id = ma.material_id
JOIN folders f ON f.id = m.folder_id
WHERE f.name IN ('백엔드 개발', 'iOS 개발')
  AND m.title IN (
      'Spring Boot REST API 개발 강의', 'JPA 연관관계 매핑 정리', '백엔드 개발자 면접 질문 모음',
      'Spring Boot 프로젝트 개발 문서', '데이터베이스 트랜잭션 학습 자료', 'Redis 캐시를 활용한 성능 개선',
      'Swift 기초 문법 강의', 'SwiftUI 화면 구성 방법', 'iOS 개발자 커뮤니티 질문 모음',
      'iOS 프로젝트 개발 가이드', 'Swift 동시성 프로그래밍 자료', 'URLSession 네트워크 통신 구현'
  );

-- =====================================================
-- 3. material_tag 삭제 (tag 테이블 자체는 유지)
-- =====================================================

DELETE mt FROM material_tag mt
JOIN materials m ON m.id = mt.material_id
JOIN folders f ON f.id = m.folder_id
WHERE f.name IN ('백엔드 개발', 'iOS 개발')
  AND m.title IN (
      'Spring Boot REST API 개발 강의', 'JPA 연관관계 매핑 정리', '백엔드 개발자 면접 질문 모음',
      'Spring Boot 프로젝트 개발 문서', '데이터베이스 트랜잭션 학습 자료', 'Redis 캐시를 활용한 성능 개선',
      'Swift 기초 문법 강의', 'SwiftUI 화면 구성 방법', 'iOS 개발자 커뮤니티 질문 모음',
      'iOS 프로젝트 개발 가이드', 'Swift 동시성 프로그래밍 자료', 'URLSession 네트워크 통신 구현'
  );

-- =====================================================
-- 4. materials 삭제
-- =====================================================

DELETE m FROM materials m
JOIN folders f ON f.id = m.folder_id
WHERE f.name IN ('백엔드 개발', 'iOS 개발')
  AND m.title IN (
      'Spring Boot REST API 개발 강의', 'JPA 연관관계 매핑 정리', '백엔드 개발자 면접 질문 모음',
      'Spring Boot 프로젝트 개발 문서', '데이터베이스 트랜잭션 학습 자료', 'Redis 캐시를 활용한 성능 개선',
      'Swift 기초 문법 강의', 'SwiftUI 화면 구성 방법', 'iOS 개발자 커뮤니티 질문 모음',
      'iOS 프로젝트 개발 가이드', 'Swift 동시성 프로그래밍 자료', 'URLSession 네트워크 통신 구현'
  );

-- =====================================================
-- 5. 폴더 item_count 재계산 (폴더 자체는 삭제하지 않음)
-- =====================================================

UPDATE folders f
SET f.item_count = (
        SELECT COUNT(*) FROM materials m WHERE m.folder_id = f.id
    ),
    f.updated_at = NOW()
WHERE f.name IN ('백엔드 개발', 'iOS 개발');

COMMIT;

SET SQL_SAFE_UPDATES = 1;

-- =====================================================
-- 검증: 남아있는 자료가 없어야 함 (0 rows 예상)
-- =====================================================

SELECT m.id, m.title, f.name AS folder_name
FROM materials m
JOIN folders f ON f.id = m.folder_id
WHERE f.name IN ('백엔드 개발', 'iOS 개발')
  AND m.title IN (
      'Spring Boot REST API 개발 강의', 'JPA 연관관계 매핑 정리', '백엔드 개발자 면접 질문 모음',
      'Spring Boot 프로젝트 개발 문서', '데이터베이스 트랜잭션 학습 자료', 'Redis 캐시를 활용한 성능 개선',
      'Swift 기초 문법 강의', 'SwiftUI 화면 구성 방법', 'iOS 개발자 커뮤니티 질문 모음',
      'iOS 프로젝트 개발 가이드', 'Swift 동시성 프로그래밍 자료', 'URLSession 네트워크 통신 구현'
  );
