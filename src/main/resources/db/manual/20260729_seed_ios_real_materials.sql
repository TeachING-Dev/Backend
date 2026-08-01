-- Manual MySQL seed: 6 real, user-picked iOS materials (replacing the earlier 6 placeholder
-- iOS materials removed by 20260729_delete_seed_material_analysis_test_data.sql).
--
-- URLs were provided by the user and verified via WebFetch to be real, loading pages.
-- platform_type avoids BLOG/CAFE/PDF because those icon files 403 in the S3 icon bucket
-- (teaching-app-static-2026/icons/) — the Tistory post below uses WEB instead of BLOG so
-- its icon actually renders.
--
-- Applies to every non-deleted user's existing 'iOS 개발' folder (does not create folders —
-- they must already exist). Idempotent: guarded by NOT EXISTS, safe to re-run.
--
-- Run manually with a MySQL-compatible client (USE `teaching-db`; first). Check DB_HOST
-- in .env before running against a real environment.

SET SQL_SAFE_UPDATES = 0;

START TRANSACTION;

-- =====================================================
-- 1. materials 생성 (분석 완료 상태로 바로 생성)
-- =====================================================

INSERT INTO materials (
    user_id, folder_id, title, original_url, analysis_title,
    ai_status, difficulty, platform_type, created_at, updated_at
)
SELECT
    f.user_id, f.id,
    material_data.title, material_data.original_url, material_data.analysis_title,
    'COMPLETED', material_data.difficulty, material_data.platform_type, NOW(), NOW()
FROM folders f
JOIN users u ON u.id = f.user_id
CROSS JOIN (
    SELECT
        'iOS 앱 개발 시작하기 - Xcode 사용해보기' AS title,
        'https://velog.io/@sun02/iOS-%EC%95%B1-%EA%B0%9C%EB%B0%9C-%EC%8B%9C%EC%9E%91%ED%95%98%EA%B8%B0-1-Xcode-%EC%82%AC%EC%9A%A9%ED%95%B4%EB%B3%B4%EA%B8%B0' AS original_url,
        'Xcode 프로젝트 생성과 기본 사용법' AS analysis_title,
        1 AS difficulty,
        'VELOG' AS platform_type

    UNION ALL

    SELECT
        'iOS 앱 개발을 위한 개발환경 구축',
        'https://velog.io/@frozenxnow/iOS-%EC%95%B1-%EA%B0%9C%EB%B0%9C%EC%9D%84-%EC%9C%84%ED%95%9C-%EA%B0%9C%EB%B0%9C%ED%99%98%EA%B2%BD-%EA%B5%AC%EC%B6%95',
        'Xcode 설치와 Swift 학습 준비',
        1,
        'VELOG'

    UNION ALL

    SELECT
        'iOS 개발 처음이세요? 초보를 위한 기초개념 정복',
        'https://youtu.be/UKknl2yxQr4?si=PUtjBTJEAiJM0Mu2',
        'iOS 개발 초보자를 위한 기초 개념',
        1,
        'YOUTUBE'

    UNION ALL

    SELECT
        '매일 iOS 앱개발 뽀개기 - 결제테스트 쉽게하기',
        'https://www.youtube.com/watch?v=t65c_ABM_TQ&list=PLgOlaPUIbynq9CDCkKT_6aCkWX0HnwCBq&index=4',
        '인앱결제(IAP) 테스트 방법',
        2,
        'YOUTUBE'

    UNION ALL

    SELECT
        'iOS 왕기초! UIViewController의 생명주기 살펴보기',
        'https://www.youtube.com/watch?v=6PzoacRPW_U',
        'UIViewController 생명주기 이해하기',
        1,
        'YOUTUBE'

    UNION ALL

    SELECT
        'iOS 앱 제작: 완벽한 가이드',
        'https://st7ve.tistory.com/entry/iOS-%EC%95%B1-%EC%A0%9C%EC%9E%91-%EC%99%84%EB%B2%BD%ED%95%9C-%EA%B0%80%EC%9D%B4%EB%93%9C',
        'iOS 앱 개발 전체 과정 가이드',
        2,
        'WEB'
) material_data
WHERE u.deleted_at IS NULL
  AND f.name = 'iOS 개발'
  AND f.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM materials existing_material
      WHERE existing_material.user_id = u.id
        AND existing_material.folder_id = f.id
        AND existing_material.title = material_data.title
  );

-- =====================================================
-- 2. material_analysis 생성
-- =====================================================

INSERT INTO material_analysis (
    material_id, summary, detail_analysis, prompt_version, is_user_edited, created_at, updated_at
)
SELECT
    m.id, d.summary, d.detail_analysis, 'gpt-4o-mini-v1', false, NOW(), NOW()
FROM materials m
JOIN folders f ON f.id = m.folder_id
JOIN (
    SELECT 'iOS 앱 개발 시작하기 - Xcode 사용해보기' AS title,
        'Xcode를 처음 사용해 iOS 앱 프로젝트를 생성하고, Navigator·Toolbar·Inspector 등 기본 화면 구성을 익히는 입문 글입니다.' AS summary,
        '새 프로젝트 생성 과정과 Xcode의 주요 영역(Navigator, Toolbar, Inspector)의 역할을 설명하고, AppDelegate와 ViewController의 기본 개념을 소개하며 시뮬레이터로 앱을 실행해보는 과정까지 다룹니다.' AS detail_analysis
    UNION ALL
    SELECT 'iOS 앱 개발을 위한 개발환경 구축',
        'iOS 앱 개발을 시작하기 전에 필요한 Xcode 설치 방법과 Playground를 활용한 기초 학습 준비 과정을 정리한 글입니다.',
        'Mac App Store를 통한 Xcode 설치 절차와, Swift 문법을 가볍게 실습해볼 수 있는 Playground 생성 방법을 소개하며, 프로그래밍 경험이 있는 사람이 iOS 개발에 입문할 때 필요한 준비 과정을 안내합니다.'
    UNION ALL
    SELECT 'iOS 개발 처음이세요? 초보를 위한 기초개념 정복',
        'iOS 개발을 처음 시작하는 사람들을 위해 꼭 알아야 할 기초 개념들을 정리한 강의입니다.',
        'iOS 앱의 기본 구조와 개발에 필요한 핵심 용어, 그리고 처음 개발을 시작할 때 흔히 겪는 어려움과 학습 순서를 짚어주는 내용을 다룹니다.'
    UNION ALL
    SELECT '매일 iOS 앱개발 뽀개기 - 결제테스트 쉽게하기',
        'StoreKit 기반 인앱결제(In-App Purchase)를 개발 단계에서 쉽고 빠르게 테스트하는 방법을 다루는 영상입니다.',
        '실제 결제 없이 인앱결제 흐름을 검증할 수 있는 StoreKit 테스트 환경 구성 방법과, 흔히 겪는 결제 테스트 오류 상황에 대한 대응 방법을 설명합니다.'
    UNION ALL
    SELECT 'iOS 왕기초! UIViewController의 생명주기 살펴보기',
        'UIViewController가 생성되고 화면에 나타났다가 사라지기까지의 생명주기(Lifecycle)를 단계별로 설명하는 강의입니다.',
        'viewDidLoad, viewWillAppear, viewDidAppear, viewWillDisappear, viewDidDisappear 등 각 생명주기 메서드가 호출되는 시점과 역할을 설명하고, 어떤 시점에 어떤 작업을 해야 하는지 실제 예시로 보여줍니다.'
    UNION ALL
    SELECT 'iOS 앱 제작: 완벽한 가이드',
        '기획부터 배포까지 iOS 앱 제작의 전체 과정을 단계별로 정리한 가이드입니다.',
        'Mac, Xcode, Swift 같은 필수 도구 준비부터 UI/UX 디자인, 개발, 테스트, App Store 배포와 배포 이후 유지보수까지 앱 제작의 전체 라이프사이클을 설명하며, 일반적인 앱은 개발에 2~3개월 정도 소요된다고 안내합니다.'
) d ON d.title = m.title
WHERE f.name = 'iOS 개발'
  AND m.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM material_analysis ma WHERE ma.material_id = m.id
  );

-- =====================================================
-- 3. material_highlights 생성 (MAIN + CAUTION)
-- =====================================================

INSERT INTO material_highlights (
    material_analysis_id, highlight_text, highlight_type, start_position, end_position, created_at
)
SELECT ma.id, d.main_text, 'MAIN', 0, 60, NOW()
FROM material_analysis ma
JOIN materials m ON m.id = ma.material_id
JOIN folders f ON f.id = m.folder_id
JOIN (
    SELECT 'iOS 앱 개발 시작하기 - Xcode 사용해보기' AS title, 'Xcode의 Navigator, Toolbar, Inspector 영역 구조를 이해하면 프로젝트 파일을 훨씬 빠르게 탐색할 수 있다.' AS main_text
    UNION ALL SELECT 'iOS 앱 개발을 위한 개발환경 구축', 'Playground를 활용하면 별도 프로젝트 생성 없이 Swift 문법을 빠르게 실험해볼 수 있다.'
    UNION ALL SELECT 'iOS 개발 처음이세요? 초보를 위한 기초개념 정복', '기초 개념을 순서대로 익히면 이후 심화 학습에서 시행착오를 크게 줄일 수 있다.'
    UNION ALL SELECT '매일 iOS 앱개발 뽀개기 - 결제테스트 쉽게하기', 'StoreKit 테스트 환경을 활용하면 실제 결제 없이도 인앱결제 로직을 안전하게 검증할 수 있다.'
    UNION ALL SELECT 'iOS 왕기초! UIViewController의 생명주기 살펴보기', 'viewDidLoad는 딱 한 번만 호출되므로, 화면이 다시 보일 때마다 갱신이 필요한 데이터는 viewWillAppear에서 처리해야 한다.'
    UNION ALL SELECT 'iOS 앱 제작: 완벽한 가이드', '기획 단계에서 앱의 핵심 기능을 명확히 정의해두면 이후 개발 범위가 불필요하게 늘어나는 것을 막을 수 있다.'
) d ON d.title = m.title
WHERE f.name = 'iOS 개발'
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
JOIN (
    SELECT 'iOS 앱 개발 시작하기 - Xcode 사용해보기' AS title, 'AppDelegate와 ViewController의 역할을 혼동하면 이후 화면 전환 로직을 이해하기 어려워지므로 개념을 명확히 구분해야 한다.' AS caution_text
    UNION ALL SELECT 'iOS 앱 개발을 위한 개발환경 구축', 'Xcode 버전과 macOS 버전 호환성을 확인하지 않으면 설치 후 실행이 안 될 수 있으므로 미리 확인해야 한다.'
    UNION ALL SELECT 'iOS 개발 처음이세요? 초보를 위한 기초개념 정복', '기초를 건너뛰고 바로 실전 프로젝트부터 시작하면 오히려 학습 속도가 느려질 수 있으므로 주의해야 한다.'
    UNION ALL SELECT '매일 iOS 앱개발 뽀개기 - 결제테스트 쉽게하기', '샌드박스 계정 설정을 제대로 하지 않으면 결제 테스트 중 예상치 못한 오류가 발생할 수 있으므로 주의해야 한다.'
    UNION ALL SELECT 'iOS 왕기초! UIViewController의 생명주기 살펴보기', '생명주기 메서드 호출 시점을 착각하면 아직 준비되지 않은 뷰에 접근해 크래시가 발생할 수 있으므로 주의해야 한다.'
    UNION ALL SELECT 'iOS 앱 제작: 완벽한 가이드', '테스트를 충분히 거치지 않고 배포하면 심사 반려나 사용자 이탈로 이어질 수 있으므로 배포 전 QA 과정을 반드시 거쳐야 한다.'
) d ON d.title = m.title
WHERE f.name = 'iOS 개발'
  AND NOT EXISTS (
      SELECT 1 FROM material_highlights h WHERE h.material_analysis_id = ma.id AND h.highlight_type = 'CAUTION'
  );

-- =====================================================
-- 4. tag 생성
-- =====================================================

INSERT INTO tag (name)
SELECT t.name FROM (
    SELECT 'iOS' AS name UNION ALL SELECT 'Xcode' UNION ALL SELECT '개발환경'
    UNION ALL SELECT '입문' UNION ALL SELECT '기초개념' UNION ALL SELECT 'Swift'
    UNION ALL SELECT 'IAP' UNION ALL SELECT '결제' UNION ALL SELECT 'StoreKit'
    UNION ALL SELECT 'UIViewController' UNION ALL SELECT '생명주기'
    UNION ALL SELECT '앱배포' UNION ALL SELECT '디자인'
) t
WHERE NOT EXISTS (SELECT 1 FROM tag WHERE tag.name = t.name);

-- =====================================================
-- 5. material_tag 연결 (자료마다 대표 태그 1개 + 보조 태그 3개)
-- =====================================================

INSERT INTO material_tag (material_id, tag_id, is_representative, created_at, updated_at)
SELECT m.id, t.id, d.is_representative, NOW(), NOW()
FROM materials m
JOIN folders f ON f.id = m.folder_id
JOIN (
    SELECT 'iOS 앱 개발 시작하기 - Xcode 사용해보기' AS title, 'Xcode' AS tag_name, TRUE AS is_representative
    UNION ALL SELECT 'iOS 앱 개발 시작하기 - Xcode 사용해보기', 'iOS', FALSE
    UNION ALL SELECT 'iOS 앱 개발 시작하기 - Xcode 사용해보기', '입문', FALSE
    UNION ALL SELECT 'iOS 앱 개발 시작하기 - Xcode 사용해보기', '개발환경', FALSE
    UNION ALL SELECT 'iOS 앱 개발을 위한 개발환경 구축', '개발환경', TRUE
    UNION ALL SELECT 'iOS 앱 개발을 위한 개발환경 구축', 'iOS', FALSE
    UNION ALL SELECT 'iOS 앱 개발을 위한 개발환경 구축', 'Xcode', FALSE
    UNION ALL SELECT 'iOS 앱 개발을 위한 개발환경 구축', '입문', FALSE
    UNION ALL SELECT 'iOS 개발 처음이세요? 초보를 위한 기초개념 정복', '입문', TRUE
    UNION ALL SELECT 'iOS 개발 처음이세요? 초보를 위한 기초개념 정복', 'iOS', FALSE
    UNION ALL SELECT 'iOS 개발 처음이세요? 초보를 위한 기초개념 정복', '기초개념', FALSE
    UNION ALL SELECT 'iOS 개발 처음이세요? 초보를 위한 기초개념 정복', 'Swift', FALSE
    UNION ALL SELECT '매일 iOS 앱개발 뽀개기 - 결제테스트 쉽게하기', 'IAP', TRUE
    UNION ALL SELECT '매일 iOS 앱개발 뽀개기 - 결제테스트 쉽게하기', 'iOS', FALSE
    UNION ALL SELECT '매일 iOS 앱개발 뽀개기 - 결제테스트 쉽게하기', '결제', FALSE
    UNION ALL SELECT '매일 iOS 앱개발 뽀개기 - 결제테스트 쉽게하기', 'StoreKit', FALSE
    UNION ALL SELECT 'iOS 왕기초! UIViewController의 생명주기 살펴보기', 'UIViewController', TRUE
    UNION ALL SELECT 'iOS 왕기초! UIViewController의 생명주기 살펴보기', 'iOS', FALSE
    UNION ALL SELECT 'iOS 왕기초! UIViewController의 생명주기 살펴보기', '생명주기', FALSE
    UNION ALL SELECT 'iOS 왕기초! UIViewController의 생명주기 살펴보기', '기초개념', FALSE
    UNION ALL SELECT 'iOS 앱 제작: 완벽한 가이드', '앱배포', TRUE
    UNION ALL SELECT 'iOS 앱 제작: 완벽한 가이드', 'iOS', FALSE
    UNION ALL SELECT 'iOS 앱 제작: 완벽한 가이드', '디자인', FALSE
    UNION ALL SELECT 'iOS 앱 제작: 완벽한 가이드', 'Xcode', FALSE
) d ON d.title = m.title
JOIN tag t ON t.name = d.tag_name
WHERE f.name = 'iOS 개발'
  AND m.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM material_tag mt WHERE mt.material_id = m.id AND mt.tag_id = t.id
  );

-- =====================================================
-- 6. 폴더 item_count 재계산
-- =====================================================

UPDATE folders f
SET f.item_count = (
        SELECT COUNT(*) FROM materials m WHERE m.folder_id = f.id
    ),
    f.updated_at = NOW()
WHERE f.name = 'iOS 개발';

COMMIT;

SET SQL_SAFE_UPDATES = 1;

-- =====================================================
-- 검증
-- =====================================================

SELECT
    u.nickname, f.name AS folder_name, m.id AS material_id, m.title, m.platform_type,
    m.original_url, m.ai_status,
    (SELECT COUNT(*) FROM material_highlights h WHERE h.material_analysis_id =
        (SELECT id FROM material_analysis ma WHERE ma.material_id = m.id)) AS highlight_count,
    (SELECT GROUP_CONCAT(t.name ORDER BY mt.is_representative DESC SEPARATOR ', ')
       FROM material_tag mt JOIN tag t ON t.id = mt.tag_id WHERE mt.material_id = m.id) AS tags
FROM materials m
JOIN folders f ON f.id = m.folder_id
JOIN users u ON u.id = f.user_id
WHERE f.name = 'iOS 개발'
  AND m.deleted_at IS NULL
ORDER BY u.nickname, m.id;
