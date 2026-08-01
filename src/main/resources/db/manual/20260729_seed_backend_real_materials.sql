-- Manual MySQL seed: 6 real, user-picked backend materials (replacing the earlier 6
-- placeholder backend materials removed by 20260729_delete_seed_material_analysis_test_data.sql).
--
-- URLs were provided by the user and verified via WebFetch to be real, loading pages.
-- The Tistory post uses platform_type WEB instead of BLOG, because blog-icon.svg 403s in
-- the S3 icon bucket (teaching-app-static-2026/icons/) while web-icon.svg works.
--
-- Applies to every non-deleted user's existing '백엔드 개발' folder (does not create
-- folders — they must already exist). Idempotent: guarded by NOT EXISTS, safe to re-run.
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
        '백엔드 개발자가 되기 위해 필요한 지식 및 기술들' AS title,
        'https://velog.io/@2000sdh/%EB%B0%B1%EC%97%94%EB%93%9C-%EA%B0%9C%EB%B0%9C%EC%9E%90%EA%B0%80-%EB%90%98%EA%B8%B0-%EC%9C%84%ED%95%B4-%ED%95%84%EC%9A%94%ED%95%9C-%EC%A7%80%EC%8B%9D-%EB%B0%8F-%EA%B8%B0%EC%88%A0%EB%93%A4' AS original_url,
        '백엔드 개발자 필수 지식 로드맵' AS analysis_title,
        1 AS difficulty,
        'VELOG' AS platform_type

    UNION ALL

    SELECT
        '백엔드 개발에서의 트러블슈팅',
        'https://velog.io/@rosin23/%EB%B0%B1%EC%97%94%EB%93%9C-%EA%B0%9C%EB%B0%9C%EC%97%90%EC%84%9C%EC%9D%98-%ED%8A%B8%EB%9F%AC%EB%B8%94%EC%8A%88%ED%8C%85-%EB%8B%A8%EA%B3%84%EB%B3%84-%EC%A0%91%EA%B7%BC',
        '백엔드 트러블슈팅 6단계 접근법',
        2,
        'VELOG'

    UNION ALL

    SELECT
        '[트러블 슈팅] 백엔드개발자가 프론트엔드와 협업하기',
        'https://wanglan.tistory.com/entry/%ED%8A%B8%EB%9F%AC%EB%B8%94-%EC%8A%88%ED%8C%85%EB%B0%B1%EC%97%94%EB%93%9C%EA%B0%9C%EB%B0%9C%EC%9E%90%EA%B0%80-%ED%94%84%EB%A1%A0%ED%8A%B8%EC%97%94%EB%93%9C%EC%99%80-%ED%98%91%EC%97%85%ED%95%98%EA%B8%B0',
        '프론트엔드 협업 트러블슈팅 사례',
        2,
        'WEB'

    UNION ALL

    SELECT
        '백엔드 개발 (Backend web development) - A to Z',
        'https://www.youtube.com/watch?v=yY5zUp1J-iI',
        '백엔드 개발 전체 흐름 개괄',
        1,
        'YOUTUBE'

    UNION ALL

    SELECT
        '한시간만에 Node.js 백엔드 기초 끝내기 (ft. API 구축)',
        'https://www.youtube.com/watch?v=Tt_tKhhhJqY',
        'Node.js 백엔드 기초와 API 구축',
        1,
        'YOUTUBE'

    UNION ALL

    SELECT
        '백엔드 개발구조 - 백엔드입문 01화',
        'https://www.youtube.com/watch?v=CpERNqY0VPM',
        '백엔드 개발 구조 입문',
        1,
        'YOUTUBE'
) material_data
WHERE u.deleted_at IS NULL
  AND f.name = '백엔드 개발'
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
    SELECT '백엔드 개발자가 되기 위해 필요한 지식 및 기술들' AS title,
        'REST API, 웹 프레임워크, 서버 기본 지식, 네트워크, 데이터베이스 등 백엔드 개발자가 갖춰야 할 핵심 지식 7가지를 정리한 글입니다.' AS summary,
        'Spring Boot와 MySQL을 중심으로 학습 방향을 제시하면서, AWS 같은 클라우드 서비스 지식과 프론트엔드 기초 이해의 필요성도 함께 강조하는 입문자용 학습 로드맵입니다.' AS detail_analysis
    UNION ALL
    SELECT '백엔드 개발에서의 트러블슈팅',
        '백엔드 시스템에서 문제가 발생했을 때 이를 해결해나가는 체계적인 트러블슈팅 절차를 정리한 글입니다.',
        '문제 정의 및 증상 식별, 정보 수집, 가설 설정, 가설 검증, 해결책 적용, 문서화 및 사후 분석까지 6단계로 이어지는 접근법을 소개하며, 서버 로그·APM 모니터링·네트워크 분석 같은 도구를 활용해 데이터베이스·API·서버 연결 문제를 진단하는 방법을 설명합니다.'
    UNION ALL
    SELECT '[트러블 슈팅] 백엔드개발자가 프론트엔드와 협업하기',
        '백엔드 개발자가 AI 활용 "바이브 코딩"으로 Next.js 프론트엔드를 직접 개발하며 겪은 협업 경험과 교훈을 정리한 글입니다.',
        '게시판 프로젝트에서 API 응답에 게시글 ID를 빠뜨려 프론트엔드에서 404 에러가 발생한 사례를 통해, 개발 전 와이어프레임 회의와 API 명세 작성이 왜 중요한지를 실제 트러블슈팅 경험으로 보여줍니다.'
    UNION ALL
    SELECT '백엔드 개발 (Backend web development) - A to Z',
        '백엔드 웹 개발의 전체 흐름을 처음부터 끝까지(A to Z) 개괄적으로 설명하는 영상입니다.',
        '서버, 데이터베이스, API 등 백엔드 개발을 구성하는 주요 요소들이 서로 어떻게 연결되는지 전체 그림을 보여주며, 백엔드 개발을 처음 접하는 사람이 전반적인 흐름을 파악하는 데 도움을 주는 내용입니다.'
    UNION ALL
    SELECT '한시간만에 Node.js 백엔드 기초 끝내기 (ft. API 구축)',
        'Node.js를 이용해 백엔드 기초 개념과 API 구축 방법을 한 시간 분량으로 압축해서 설명하는 강의입니다.',
        'Node.js 환경에서 서버를 구성하고 API 엔드포인트를 만드는 기본 과정을 빠르게 훑어보며, 백엔드 개발을 처음 시작하는 사람이 짧은 시간 안에 핵심 흐름을 파악할 수 있도록 구성되어 있습니다.'
    UNION ALL
    SELECT '백엔드 개발구조 - 백엔드입문 01화',
        '백엔드 개발의 전체 구조를 소개하는 입문 시리즈의 첫 번째 영상입니다.',
        '클라이언트-서버-데이터베이스로 이어지는 백엔드의 기본 구조와 각 구성 요소의 역할을 설명하며, 백엔드 개발을 처음 시작하는 사람이 전체 그림을 그릴 수 있도록 돕는 입문 시리즈의 시작점입니다.'
) d ON d.title = m.title
WHERE f.name = '백엔드 개발'
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
    SELECT '백엔드 개발자가 되기 위해 필요한 지식 및 기술들' AS title, '백엔드 개발자는 서버·네트워크·데이터베이스뿐 아니라 프론트엔드 기초 지식까지 폭넓게 이해하고 있어야 협업이 수월해진다.' AS main_text
    UNION ALL SELECT '백엔드 개발에서의 트러블슈팅', '가설을 세우고 검증하는 단계를 거치지 않고 바로 코드를 수정하면 같은 문제가 재발할 가능성이 높다.'
    UNION ALL SELECT '[트러블 슈팅] 백엔드개발자가 프론트엔드와 협업하기', '개발 시작 전 프론트엔드와 와이어프레임을 함께 검토하고 API 명세를 문서화하면 이후 발생할 수 있는 연동 오류를 크게 줄일 수 있다.'
    UNION ALL SELECT '백엔드 개발 (Backend web development) - A to Z', '백엔드 개발의 전체 흐름을 먼저 파악하면 이후 특정 기술을 학습할 때 그 기술이 전체 시스템에서 어떤 역할을 하는지 이해하기 쉬워진다.'
    UNION ALL SELECT '한시간만에 Node.js 백엔드 기초 끝내기 (ft. API 구축)', 'Node.js로 간단한 API를 직접 만들어보면 요청과 응답이 오가는 백엔드의 기본 동작 흐름을 빠르게 체감할 수 있다.'
    UNION ALL SELECT '백엔드 개발구조 - 백엔드입문 01화', '클라이언트-서버-데이터베이스 구조를 먼저 이해하면 이후 배우는 개별 기술들이 어느 위치에서 동작하는지 훨씬 명확하게 파악할 수 있다.'
) d ON d.title = m.title
WHERE f.name = '백엔드 개발'
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
    SELECT '백엔드 개발자가 되기 위해 필요한 지식 및 기술들' AS title, '특정 프레임워크 사용법만 익히고 네트워크·서버 기본기를 소홀히 하면 실무에서 발생하는 문제의 원인을 파악하기 어려울 수 있다.' AS caution_text
    UNION ALL SELECT '백엔드 개발에서의 트러블슈팅', '장애 해결 후 문서화와 사후 분석을 생략하면 동일한 원인의 장애가 반복될 수 있으므로 반드시 기록을 남겨야 한다.'
    UNION ALL SELECT '[트러블 슈팅] 백엔드개발자가 프론트엔드와 협업하기', 'API 응답에 프론트엔드가 실제로 필요로 하는 필드(예: 게시글 ID)가 빠지면 화면에서 예기치 않은 404 에러로 이어질 수 있으므로 응답 설계 시 프론트엔드 요구사항을 꼼꼼히 확인해야 한다.'
    UNION ALL SELECT '백엔드 개발 (Backend web development) - A to Z', '개별 기술 학습에만 집중하고 전체 아키텍처 흐름을 이해하지 않으면, 배운 지식들을 실제 프로젝트에 연결시키기 어려울 수 있다.'
    UNION ALL SELECT '한시간만에 Node.js 백엔드 기초 끝내기 (ft. API 구축)', '짧은 시간에 압축된 강의이다 보니 각 개념의 세부 원리까지는 다루지 않으므로, 기초를 익힌 후에는 별도로 깊이 있는 학습이 필요하다.'
    UNION ALL SELECT '백엔드 개발구조 - 백엔드입문 01화', '구조에 대한 이해 없이 특정 프레임워크 사용법부터 익히면, 왜 이런 코드를 작성하는지 맥락을 이해하지 못한 채 암기 위주로 학습하게 될 수 있다.'
) d ON d.title = m.title
WHERE f.name = '백엔드 개발'
  AND NOT EXISTS (
      SELECT 1 FROM material_highlights h WHERE h.material_analysis_id = ma.id AND h.highlight_type = 'CAUTION'
  );

-- =====================================================
-- 4. tag 생성
-- =====================================================

INSERT INTO tag (name)
SELECT t.name FROM (
    SELECT '백엔드' AS name UNION ALL SELECT '입문' UNION ALL SELECT 'Spring Boot'
    UNION ALL SELECT '로드맵' UNION ALL SELECT '트러블슈팅' UNION ALL SELECT '모니터링'
    UNION ALL SELECT '장애대응' UNION ALL SELECT '협업' UNION ALL SELECT 'API설계'
    UNION ALL SELECT '백엔드입문' UNION ALL SELECT '아키텍처' UNION ALL SELECT 'Node.js'
    UNION ALL SELECT 'API구축' UNION ALL SELECT '서버구조'
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
    SELECT '백엔드 개발자가 되기 위해 필요한 지식 및 기술들' AS title, '로드맵' AS tag_name, TRUE AS is_representative
    UNION ALL SELECT '백엔드 개발자가 되기 위해 필요한 지식 및 기술들', '백엔드', FALSE
    UNION ALL SELECT '백엔드 개발자가 되기 위해 필요한 지식 및 기술들', 'Spring Boot', FALSE
    UNION ALL SELECT '백엔드 개발자가 되기 위해 필요한 지식 및 기술들', '입문', FALSE
    UNION ALL SELECT '백엔드 개발에서의 트러블슈팅', '트러블슈팅', TRUE
    UNION ALL SELECT '백엔드 개발에서의 트러블슈팅', '백엔드', FALSE
    UNION ALL SELECT '백엔드 개발에서의 트러블슈팅', '모니터링', FALSE
    UNION ALL SELECT '백엔드 개발에서의 트러블슈팅', '장애대응', FALSE
    UNION ALL SELECT '[트러블 슈팅] 백엔드개발자가 프론트엔드와 협업하기', '협업', TRUE
    UNION ALL SELECT '[트러블 슈팅] 백엔드개발자가 프론트엔드와 협업하기', '백엔드', FALSE
    UNION ALL SELECT '[트러블 슈팅] 백엔드개발자가 프론트엔드와 협업하기', 'API설계', FALSE
    UNION ALL SELECT '[트러블 슈팅] 백엔드개발자가 프론트엔드와 협업하기', '트러블슈팅', FALSE
    UNION ALL SELECT '백엔드 개발 (Backend web development) - A to Z', '백엔드입문', TRUE
    UNION ALL SELECT '백엔드 개발 (Backend web development) - A to Z', '백엔드', FALSE
    UNION ALL SELECT '백엔드 개발 (Backend web development) - A to Z', '아키텍처', FALSE
    UNION ALL SELECT '백엔드 개발 (Backend web development) - A to Z', '입문', FALSE
    UNION ALL SELECT '한시간만에 Node.js 백엔드 기초 끝내기 (ft. API 구축)', 'Node.js', TRUE
    UNION ALL SELECT '한시간만에 Node.js 백엔드 기초 끝내기 (ft. API 구축)', '백엔드', FALSE
    UNION ALL SELECT '한시간만에 Node.js 백엔드 기초 끝내기 (ft. API 구축)', 'API구축', FALSE
    UNION ALL SELECT '한시간만에 Node.js 백엔드 기초 끝내기 (ft. API 구축)', '입문', FALSE
    UNION ALL SELECT '백엔드 개발구조 - 백엔드입문 01화', '백엔드입문', TRUE
    UNION ALL SELECT '백엔드 개발구조 - 백엔드입문 01화', '백엔드', FALSE
    UNION ALL SELECT '백엔드 개발구조 - 백엔드입문 01화', '아키텍처', FALSE
    UNION ALL SELECT '백엔드 개발구조 - 백엔드입문 01화', '서버구조', FALSE
) d ON d.title = m.title
JOIN tag t ON t.name = d.tag_name
WHERE f.name = '백엔드 개발'
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
WHERE f.name = '백엔드 개발';

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
WHERE f.name = '백엔드 개발'
  AND m.deleted_at IS NULL
ORDER BY u.nickname, m.id;
