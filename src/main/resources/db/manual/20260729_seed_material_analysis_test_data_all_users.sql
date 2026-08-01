-- Manual MySQL seed data for testing the material-analysis feature end-to-end.
-- Same fixture as 20260729_seed_material_analysis_test_data.sql, but applied to
-- EVERY non-deleted user instead of just '휘윤'.
--
-- This project does not use Flyway/Liquibase (dev/prod use hibernate.ddl-auto=update),
-- so run this manually with a MySQL-compatible client (USE `teaching-db`; first) after
-- checking which DB you're connected to (see DB_HOST in .env).
--
-- For every user this creates: 2 folders ('백엔드 개발' / 'iOS 개발') -> 12 materials (6 each)
-- -> material_analysis (1:1) -> material_highlights (MAIN + CAUTION) -> tag / material_tag.
--
-- Idempotent: every INSERT is guarded by NOT EXISTS, so re-running (e.g. after new users
-- sign up) is safe and only fills in what's missing.

START TRANSACTION;

-- =====================================================
-- 1. 모든 유저에게 폴더 2개 생성
-- =====================================================

INSERT INTO folders (
    user_id,
    name,
    item_count,
    created_at,
    updated_at
)
SELECT
    u.id,
    folder_data.name,
    0,
    NOW(),
    NOW()
FROM users u
CROSS JOIN (
    SELECT '백엔드 개발' AS name
    UNION ALL
    SELECT 'iOS 개발'
) folder_data
WHERE u.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM folders f
      WHERE f.user_id = u.id
        AND f.name = folder_data.name
  );


-- =====================================================
-- 2. 백엔드 개발 자료 6개 생성
-- difficulty: 1=초급, 2=중급, 3=고급
-- =====================================================

INSERT INTO materials (
    user_id,
    folder_id,
    title,
    original_url,
    platform_type,
    ai_status,
    difficulty,
    analysis_title,
    created_at,
    updated_at
)
SELECT
    u.id,
    f.id,
    material_data.title,
    material_data.original_url,
    material_data.platform_type,
    'MANUAL_SAVED',
    material_data.difficulty,
    material_data.analysis_title,
    NOW(),
    NOW()
FROM users u
JOIN folders f
    ON f.user_id = u.id
   AND f.name = '백엔드 개발'
CROSS JOIN (
    SELECT
        'Spring Boot REST API 개발 강의' AS title,
        'https://www.youtube.com/watch?v=wfj-Z9OQpCA' AS original_url,
        'YOUTUBE' AS platform_type,
        1 AS difficulty,
        'Spring Boot REST API 기초' AS analysis_title

    UNION ALL

    SELECT
        'JPA 연관관계 매핑 정리',
        'https://velog.io/@conatuseus/%EC%97%B0%EA%B4%80%EA%B4%80%EA%B3%84-%EB%A7%A4%ED%95%91-%EA%B8%B0%EC%B4%88-2-%EC%96%91%EB%B0%A9%ED%96%A5-%EC%97%B0%EA%B4%80%EA%B4%80%EA%B3%84%EC%99%80-%EC%97%B0%EA%B4%80%EA%B4%80%EA%B3%84%EC%9D%98-%EC%A3%BC%EC%9D%B8',
        'VELOG',
        2,
        'JPA 연관관계와 지연 로딩'

    UNION ALL

    SELECT
        '백엔드 개발자 면접 질문 모음',
        'https://spartaclub.kr/blog/2024-backend-jobinterview-question',
        'CAFE',
        2,
        '백엔드 면접 핵심 개념'

    UNION ALL

    SELECT
        'Spring Boot 프로젝트 개발 문서',
        'https://velog.io/@sasha1107/%EB%85%B8%EC%85%98-%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8-%EA%B4%80%EB%A6%AC-%ED%85%9C%ED%94%8C%EB%A6%BF-%EA%B3%B5%EC%9C%A0',
        'NOTION',
        2,
        'Spring Boot 프로젝트 구조'

    UNION ALL

    SELECT
        '데이터베이스 트랜잭션 학습 자료',
        'https://hudi.blog/transaction-isolation-level/',
        'PDF',
        3,
        '트랜잭션과 동시성 제어'

    UNION ALL

    SELECT
        'Redis 캐시를 활용한 성능 개선',
        'https://velog.io/@alsdl0629/Redis-Cache%EB%A5%BC-%EC%A0%81%EC%9A%A9%ED%95%B4-%EC%84%B1%EB%8A%A5-%EA%B0%9C%EC%84%A0',
        'WEB',
        3,
        'Redis 캐시 적용 전략'
) material_data
WHERE u.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM materials existing_material
      WHERE existing_material.user_id = u.id
        AND existing_material.folder_id = f.id
        AND existing_material.title = material_data.title
  );


-- =====================================================
-- 3. iOS 개발 자료 6개 생성
-- =====================================================

INSERT INTO materials (
    user_id,
    folder_id,
    title,
    original_url,
    platform_type,
    ai_status,
    difficulty,
    analysis_title,
    created_at,
    updated_at
)
SELECT
    u.id,
    f.id,
    material_data.title,
    material_data.original_url,
    material_data.platform_type,
    'MANUAL_SAVED',
    material_data.difficulty,
    material_data.analysis_title,
    NOW(),
    NOW()
FROM users u
JOIN folders f
    ON f.user_id = u.id
   AND f.name = 'iOS 개발'
CROSS JOIN (
    SELECT
        'Swift 기초 문법 강의' AS title,
        'https://www.youtube.com/watch?v=n2lzUnWPxeE' AS original_url,
        'YOUTUBE' AS platform_type,
        1 AS difficulty,
        'Swift 기본 문법과 옵셔널' AS analysis_title

    UNION ALL

    SELECT
        'SwiftUI 화면 구성 방법',
        'https://velog.io/@mam07065/SwiftUI-%EA%B0%9C%EC%9A%94-%EB%B0%8F-%EB%B7%B0-%EB%A7%8C%EB%93%A4%EA%B8%B0Creating-and-Combining-Views',
        'VELOG',
        1,
        'SwiftUI View 구성 기초'

    UNION ALL

    SELECT
        'iOS 개발자 커뮤니티 질문 모음',
        'https://sanghyuk.dev/ios/2/',
        'CAFE',
        2,
        'iOS 개발 주요 질문 정리'

    UNION ALL

    SELECT
        'iOS 프로젝트 개발 가이드',
        'https://velog.io/@spinichi/%EA%B0%9C%EB%B0%9C%EC%9E%90%EB%A5%BC-%EC%9C%84%ED%95%9C-%ED%98%91%EC%97%85-%EA%B0%80%EC%9D%B4%EB%93%9C-%EC%A0%95%EB%A6%AC',
        'NOTION',
        2,
        'iOS 프로젝트 개발 과정'

    UNION ALL

    SELECT
        'Swift 동시성 프로그래밍 자료',
        'https://velog.io/@little_tail/Swift-Concurrency1%ED%8E%B8async-await',
        'PDF',
        3,
        'Swift Async Await와 동시성'

    UNION ALL

    SELECT
        'URLSession 네트워크 통신 구현',
        'https://developer.apple.com/documentation/foundation/urlsession',
        'WEB',
        2,
        'URLSession 기반 API 통신'
) material_data
WHERE u.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM materials existing_material
      WHERE existing_material.user_id = u.id
        AND existing_material.folder_id = f.id
        AND existing_material.title = material_data.title
  );


-- =====================================================
-- 4. 폴더 item_count를 실제 자료 개수로 갱신
-- =====================================================

UPDATE folders f
JOIN users u
    ON u.id = f.user_id
SET
    f.item_count = (
        SELECT COUNT(*)
        FROM materials m
        WHERE m.folder_id = f.id
          AND m.user_id = f.user_id
    ),
    f.updated_at = NOW()
WHERE u.deleted_at IS NULL
  AND f.name IN ('백엔드 개발', 'iOS 개발');


-- =====================================================
-- 5. 대상 자료들을 COMPLETED 상태로 전환 (분석 데이터가 실제로 붙으므로)
-- =====================================================

UPDATE materials m
JOIN folders f ON f.id = m.folder_id
JOIN users u ON u.id = f.user_id
SET m.ai_status = 'COMPLETED',
    m.updated_at = NOW()
WHERE u.deleted_at IS NULL
  AND f.name IN ('백엔드 개발', 'iOS 개발')
  AND m.deleted_at IS NULL
  AND m.ai_status = 'MANUAL_SAVED';

-- =====================================================
-- 6. material_analysis 생성 (자료 1건당 1건, 제목으로 매칭)
-- =====================================================

INSERT INTO material_analysis (
    material_id, summary, detail_analysis, prompt_version, is_user_edited, created_at, updated_at
)
SELECT
    m.id, d.summary, d.detail_analysis, 'gpt-4o-mini-v1', false, NOW(), NOW()
FROM materials m
JOIN folders f ON f.id = m.folder_id
JOIN users u ON u.id = f.user_id
JOIN (
    SELECT 'Spring Boot REST API 개발 강의' AS title,
        'Spring Boot를 이용해 REST API를 처음부터 구축하는 방법을 다루는 강의입니다. 프로젝트 초기 설정부터 컨트롤러, 서비스, 리포지토리 계층 구성까지 기본 흐름을 설명합니다.' AS summary,
        '@RestController, @RequestMapping 등 기본 어노테이션 사용법과 요청/응답 처리 방식을 소개하고, 계층형 아키텍처(Controller-Service-Repository)를 적용해 유지보수하기 쉬운 구조를 만드는 방법을 설명합니다. 예외 처리를 위한 @ExceptionHandler 활용법도 포함되어 있습니다.' AS detail_analysis
    UNION ALL
    SELECT 'JPA 연관관계 매핑 정리',
        'JPA에서 엔티티 간 연관관계(1:1, 1:N, N:1, N:M)를 매핑하는 방법과 지연 로딩/즉시 로딩의 차이를 정리한 글입니다.',
        '@ManyToOne, @OneToMany, 연관관계의 주인 개념, fetch 전략(LAZY/EAGER)에 따른 성능 차이, 그리고 N+1 문제가 발생하는 원인과 fetch join을 통한 해결 방법을 다룹니다.'
    UNION ALL
    SELECT '백엔드 개발자 면접 질문 모음',
        '실제 백엔드 개발자 채용 면접에서 자주 나오는 질문과 답변 방향을 정리한 커뮤니티 글입니다.',
        '트랜잭션, 인덱스, 캐싱, REST API 설계, 동시성 제어 등 백엔드 핵심 개념에 대한 질문과 함께, 면접관이 실제로 확인하고 싶어하는 이해도 수준을 설명합니다.'
    UNION ALL
    SELECT 'Spring Boot 프로젝트 개발 문서',
        '팀 프로젝트에서 사용한 Spring Boot 프로젝트의 폴더 구조, 코딩 컨벤션, API 설계 규칙을 정리한 문서입니다.',
        '도메인 중심 패키지 구조, 공통 응답 포맷(ApiResponse), 예외 처리 정책, Git 브랜치 전략 등 협업 시 지켜야 할 규칙들을 상세히 설명합니다.'
    UNION ALL
    SELECT '데이터베이스 트랜잭션 학습 자료',
        '데이터베이스 트랜잭션의 ACID 속성과 격리 수준(Isolation Level)에 따른 동시성 문제를 정리한 학습 자료입니다.',
        'Read Uncommitted부터 Serializable까지 각 격리 수준에서 발생할 수 있는 Dirty Read, Non-repeatable Read, Phantom Read 문제를 예시와 함께 설명하고, 락(Lock)을 이용한 동시성 제어 방식을 다룹니다.'
    UNION ALL
    SELECT 'Redis 캐시를 활용한 성능 개선',
        'Redis를 캐시 계층으로 도입해 반복 조회되는 데이터의 응답 속도를 개선한 사례를 다룬 글입니다.',
        '캐시 적중률을 높이기 위한 TTL 설정 전략, 캐시 스탬피드(Cache Stampede) 문제와 해결 방법, 그리고 캐시와 원본 데이터 간 정합성을 유지하는 방법(Cache Aside 패턴)을 설명합니다.'
    UNION ALL
    SELECT 'Swift 기초 문법 강의',
        'Swift 언어의 변수, 상수, 옵셔널, 함수 등 기본 문법을 처음 배우는 사람을 대상으로 설명하는 강의입니다.',
        '옵셔널(Optional)의 개념과 옵셔널 바인딩(if let, guard let), 강제 언래핑의 위험성, 그리고 클로저의 기본 문법을 예제와 함께 다룹니다.'
    UNION ALL
    SELECT 'SwiftUI 화면 구성 방법',
        'SwiftUI를 이용해 선언형으로 화면을 구성하는 기본 방법을 정리한 글입니다.',
        'View 프로토콜과 body 프로퍼티의 동작 방식, VStack/HStack/ZStack을 이용한 레이아웃 구성, 그리고 @State를 이용한 화면 상태 관리 방법을 설명합니다.'
    UNION ALL
    SELECT 'iOS 개발자 커뮤니티 질문 모음',
        'iOS 개발 커뮤니티에서 자주 논의되는 질문과 답변을 모아 정리한 글입니다.',
        '메모리 관리(ARC), 강한 참조 순환(Strong Reference Cycle) 문제, 그리고 앱 심사 반려 사유와 대응 방법 등 실무에서 자주 부딪히는 이슈들을 다룹니다.'
    UNION ALL
    SELECT 'iOS 프로젝트 개발 가이드',
        'iOS 팀 프로젝트에서 사용한 아키텍처 패턴과 협업 규칙을 정리한 문서입니다.',
        'MVVM 아키텍처 적용 방식, 화면 간 의존성을 줄이기 위한 Coordinator 패턴, 그리고 코드 리뷰 및 브랜치 전략에 대한 가이드를 포함합니다.'
    UNION ALL
    SELECT 'Swift 동시성 프로그래밍 자료',
        'Swift의 async/await 기반 동시성 모델과 기존 GCD 방식의 차이를 정리한 학습 자료입니다.',
        'Task, TaskGroup을 이용한 비동기 작업 관리, actor를 이용한 데이터 경합(Data Race) 방지, 그리고 async/await 도입으로 콜백 지옥을 줄이는 방법을 설명합니다.'
    UNION ALL
    SELECT 'URLSession 네트워크 통신 구현',
        'URLSession을 이용해 REST API와 통신하는 기본적인 네트워크 계층을 구현하는 방법을 다룬 글입니다.',
        'URLSessionDataTask를 이용한 GET/POST 요청 처리, Codable을 활용한 JSON 디코딩, 그리고 에러 처리와 재시도 로직을 구성하는 방법을 설명합니다.'
) d ON d.title = m.title
WHERE u.deleted_at IS NULL
  AND f.name IN ('백엔드 개발', 'iOS 개발')
  AND m.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM material_analysis ma WHERE ma.material_id = m.id
  );

-- =====================================================
-- 7. material_highlights 생성 (분석 1건당 MAIN 1개 + CAUTION 1개)
-- =====================================================

INSERT INTO material_highlights (
    material_analysis_id, highlight_text, highlight_type, start_position, end_position, created_at
)
SELECT ma.id, d.main_text, 'MAIN', 0, 60, NOW()
FROM material_analysis ma
JOIN materials m ON m.id = ma.material_id
JOIN folders f ON f.id = m.folder_id
JOIN users u ON u.id = f.user_id
JOIN (
    SELECT 'Spring Boot REST API 개발 강의' AS title, '계층형 아키텍처를 적용하면 관심사가 분리되어 유지보수가 쉬워진다.' AS main_text
    UNION ALL SELECT 'JPA 연관관계 매핑 정리', '연관관계의 주인을 명확히 정하지 않으면 예상치 못한 update 쿼리가 발생할 수 있다.'
    UNION ALL SELECT '백엔드 개발자 면접 질문 모음', '면접에서는 단순 정의보다 실제 장애 상황에서 어떻게 대응했는지를 더 중요하게 본다.'
    UNION ALL SELECT 'Spring Boot 프로젝트 개발 문서', '도메인 중심으로 패키지를 구성하면 기능 단위로 코드를 찾기 쉬워진다.'
    UNION ALL SELECT '데이터베이스 트랜잭션 학습 자료', '격리 수준이 높아질수록 동시성 문제는 줄어들지만 성능은 저하된다.'
    UNION ALL SELECT 'Redis 캐시를 활용한 성능 개선', 'Cache Aside 패턴을 사용하면 캐시 장애 시에도 원본 데이터베이스로 안전하게 폴백할 수 있다.'
    UNION ALL SELECT 'Swift 기초 문법 강의', 'guard let을 사용하면 옵셔널 값을 안전하게 언래핑하면서 코드 가독성도 높일 수 있다.'
    UNION ALL SELECT 'SwiftUI 화면 구성 방법', '@State를 사용하면 값이 변경될 때 SwiftUI가 자동으로 화면을 다시 그려준다.'
    UNION ALL SELECT 'iOS 개발자 커뮤니티 질문 모음', 'weak/unowned 참조를 적절히 사용하면 강한 참조 순환으로 인한 메모리 누수를 방지할 수 있다.'
    UNION ALL SELECT 'iOS 프로젝트 개발 가이드', 'MVVM 패턴을 적용하면 View와 비즈니스 로직을 분리해 테스트하기 쉬워진다.'
    UNION ALL SELECT 'Swift 동시성 프로그래밍 자료', 'actor를 사용하면 여러 스레드에서 동시에 접근해도 데이터 경합 없이 안전하게 상태를 관리할 수 있다.'
    UNION ALL SELECT 'URLSession 네트워크 통신 구현', 'Codable을 활용하면 JSON 응답을 별도의 파싱 코드 없이 모델 객체로 바로 변환할 수 있다.'
) d ON d.title = m.title
WHERE u.deleted_at IS NULL
  AND f.name IN ('백엔드 개발', 'iOS 개발')
  AND NOT EXISTS (
      SELECT 1 FROM material_highlights h WHERE h.material_analysis_id = ma.id AND h.highlight_type = 'MAIN'
  );

INSERT INTO material_highlights (
    material_analysis_id, highlight_text, highlight_type, start_position, end_position, created_at
)
SELECT ma.id, d.caution_text, 'CAUTION', 70, 140, NOW()
FROM material_analysis ma
JOIN materials m ON m.id = ma.material_id
JOIN folders f ON f.id = m.folder_id
JOIN users u ON u.id = f.user_id
JOIN (
    SELECT 'Spring Boot REST API 개발 강의' AS title, '컨트롤러에 비즈니스 로직을 직접 작성하면 테스트와 재사용이 어려워지므로 주의해야 한다.' AS caution_text
    UNION ALL SELECT 'JPA 연관관계 매핑 정리', '즉시 로딩(EAGER)을 남용하면 N+1 문제로 성능이 크게 저하될 수 있으므로 주의해야 한다.'
    UNION ALL SELECT '백엔드 개발자 면접 질문 모음', '암기한 정의만 나열하면 실무 이해도가 부족하다는 인상을 줄 수 있으므로 주의해야 한다.'
    UNION ALL SELECT 'Spring Boot 프로젝트 개발 문서', '공통 응답 포맷을 지키지 않으면 프론트엔드와의 API 연동에서 혼선이 생길 수 있으므로 주의해야 한다.'
    UNION ALL SELECT '데이터베이스 트랜잭션 학습 자료', 'Read Committed보다 낮은 격리 수준을 사용할 경우 Dirty Read가 발생할 수 있으므로 주의해야 한다.'
    UNION ALL SELECT 'Redis 캐시를 활용한 성능 개선', 'TTL을 너무 길게 설정하면 데이터 정합성이 깨질 수 있으므로 주의해야 한다.'
    UNION ALL SELECT 'Swift 기초 문법 강의', '느낌표(!)를 이용한 강제 언래핑은 값이 nil일 경우 런타임 크래시를 유발하므로 주의해야 한다.'
    UNION ALL SELECT 'SwiftUI 화면 구성 방법', '복잡한 화면을 하나의 View에 모두 작성하면 재사용성과 가독성이 떨어지므로 작은 단위로 분리하는 것이 좋다.'
    UNION ALL SELECT 'iOS 개발자 커뮤니티 질문 모음', '클로저 내부에서 self를 강하게 캡처하면 메모리 누수가 발생할 수 있으므로 주의해야 한다.'
    UNION ALL SELECT 'iOS 프로젝트 개발 가이드', 'ViewModel이 지나치게 커지면 결국 Massive View Model 문제로 이어질 수 있으므로 책임을 잘게 나누는 것이 좋다.'
    UNION ALL SELECT 'Swift 동시성 프로그래밍 자료', '메인 스레드에서 실행되어야 하는 UI 업데이트를 실수로 백그라운드 Task에서 처리하면 예기치 않은 동작이 발생할 수 있으므로 주의해야 한다.'
    UNION ALL SELECT 'URLSession 네트워크 통신 구현', '네트워크 에러 처리를 누락하면 오프라인 상황에서 앱이 예기치 않게 멈출 수 있으므로 주의해야 한다.'
) d ON d.title = m.title
WHERE u.deleted_at IS NULL
  AND f.name IN ('백엔드 개발', 'iOS 개발')
  AND NOT EXISTS (
      SELECT 1 FROM material_highlights h WHERE h.material_analysis_id = ma.id AND h.highlight_type = 'CAUTION'
  );

-- =====================================================
-- 8. tag 생성 (폴더별 공통 태그 + 자료별 주제 태그, 이름 중복 없이 한 번만 생성)
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

-- =====================================================
-- 9. material_tag 연결 (자료마다 주제 태그를 대표 태그로, 폴더 공통 태그를 보조 태그로)
-- =====================================================

INSERT INTO material_tag (material_id, tag_id, is_representative, created_at, updated_at)
SELECT m.id, t.id, d.is_representative, NOW(), NOW()
FROM materials m
JOIN folders f ON f.id = m.folder_id
JOIN users u ON u.id = f.user_id
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
WHERE u.deleted_at IS NULL
  AND f.name IN ('백엔드 개발', 'iOS 개발')
  AND m.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM material_tag mt WHERE mt.material_id = m.id AND mt.tag_id = t.id
  );

COMMIT;

-- =====================================================
-- 검증용 조회
-- =====================================================

SELECT
    u.nickname,
    f.name AS folder_name,
    m.id AS material_id,
    m.title,
    m.ai_status,
    ma.id AS analysis_id,
    (SELECT COUNT(*) FROM material_highlights h WHERE h.material_analysis_id = ma.id) AS highlight_count,
    (SELECT GROUP_CONCAT(t.name ORDER BY mt.is_representative DESC SEPARATOR ', ')
       FROM material_tag mt JOIN tag t ON t.id = mt.tag_id WHERE mt.material_id = m.id) AS tags
FROM materials m
JOIN folders f ON f.id = m.folder_id
JOIN users u ON u.id = f.user_id
LEFT JOIN material_analysis ma ON ma.material_id = m.id
WHERE u.deleted_at IS NULL
  AND f.name IN ('백엔드 개발', 'iOS 개발')
ORDER BY u.nickname, f.name, m.id;
