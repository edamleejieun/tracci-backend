-- ============================================================
-- V20: 已发布地点列表（探索 Tab）查询索引
-- GET /api/v1/places 按 ACTIVE + place_scope + city_name 精确过滤，
-- 并 JOIN locale=zh-CN 的 PUBLISHED 内容版本。
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_places_explore_scope_city
    ON places (place_scope, city_name, id)
    WHERE deleted_at IS NULL AND status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_content_versions_published_locale_at
    ON place_content_versions (place_id, locale, published_at DESC)
    WHERE status = 'PUBLISHED' AND deleted_at IS NULL;
