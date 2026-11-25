-- ============================================================
-- V15: 地点内容资产（Place Content Assets）
-- 新增 place_content_versions / place_content_sources / place_media_assets
-- 支持版本化、可审核、单一发布版本的内容管理
-- ============================================================

-- 1. 地点内容版本表
CREATE TABLE IF NOT EXISTS place_content_versions (
    id                              VARCHAR(36)     PRIMARY KEY,
    place_id                        VARCHAR(36)     NOT NULL,
    version_no                      INTEGER         NOT NULL,
    locale                          VARCHAR(16)     NOT NULL DEFAULT 'zh-CN',
    introduction                    TEXT,
    highlights_json                 JSONB           NOT NULL DEFAULT '[]',
    recommended_duration_minutes    INTEGER,
    visit_tips_json                 JSONB           NOT NULL DEFAULT '[]',
    best_visit_time                 TEXT,
    reservation_tips                TEXT,
    notice                          TEXT,
    generation_type                 VARCHAR(20)     NOT NULL,
    ai_assisted                     BOOLEAN         NOT NULL DEFAULT false,
    model_provider                  VARCHAR(40),
    model_name                      VARCHAR(80),
    prompt_version                  VARCHAR(40),
    status                          VARCHAR(20)     NOT NULL,
    reviewed_by                     VARCHAR(36),
    reviewed_at                     TIMESTAMP,
    verified_at                     TIMESTAMP,
    next_review_at                  TIMESTAMP,
    published_at                    TIMESTAMP,
    archived_at                     TIMESTAMP,
    content_hash                    VARCHAR(64),
    source_digest                   VARCHAR(64),
    rollback_from_version_id        VARCHAR(36),
    created_at                      TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMP       NOT NULL DEFAULT now(),
    deleted_at                      TIMESTAMP,
    CONSTRAINT chk_content_versions_generation_type
        CHECK (generation_type IN ('MANUAL', 'AI_ASSISTED', 'IMPORT', 'ROLLBACK')),
    CONSTRAINT chk_content_versions_status
        CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'OUTDATED', 'ARCHIVED')),
    CONSTRAINT chk_content_versions_duration
        CHECK (recommended_duration_minutes IS NULL OR recommended_duration_minutes BETWEEN 1 AND 1440)
);

-- 同一 Place + locale + version_no 唯一（未删除）
CREATE UNIQUE INDEX IF NOT EXISTS uniq_content_versions_place_locale_version
    ON place_content_versions (place_id, locale, version_no)
    WHERE deleted_at IS NULL;

-- 同一 Place + locale 仅一个 PUBLISHED（核心单版本发布约束）
CREATE UNIQUE INDEX IF NOT EXISTS uniq_content_versions_published
    ON place_content_versions (place_id, locale)
    WHERE status = 'PUBLISHED' AND deleted_at IS NULL;

-- 审核到期查询
CREATE INDEX IF NOT EXISTS idx_content_versions_review_due
    ON place_content_versions (status, next_review_at)
    WHERE deleted_at IS NULL;

-- 按 Place 查询最新发布版本
CREATE INDEX IF NOT EXISTS idx_content_versions_place_status_published
    ON place_content_versions (place_id, status, published_at DESC)
    WHERE deleted_at IS NULL;


-- 2. 内容事实来源表
CREATE TABLE IF NOT EXISTS place_content_sources (
    id                  VARCHAR(36)     PRIMARY KEY,
    content_version_id  VARCHAR(36)     NOT NULL,
    fact_key            VARCHAR(80)     NOT NULL,
    fact_value          TEXT            NOT NULL,
    source_type         VARCHAR(40)     NOT NULL,
    source_title        VARCHAR(300),
    source_url          TEXT,
    retrieved_at        TIMESTAMP       NOT NULL,
    expires_at          TIMESTAMP,
    verification_status VARCHAR(20)     NOT NULL DEFAULT 'COLLECTED',
    verified_by         VARCHAR(36),
    verified_at         TIMESTAMP,
    source_hash         VARCHAR(64),
    note                VARCHAR(500),
    created_at          TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMP,
    CONSTRAINT chk_content_sources_type
        CHECK (source_type IN ('OFFICIAL_SITE', 'GOVERNMENT', 'CULTURAL_INSTITUTION', 'MAP_PROVIDER', 'EDITORIAL', 'MANUAL')),
    CONSTRAINT chk_content_sources_verification
        CHECK (verification_status IN ('COLLECTED', 'VERIFIED', 'REJECTED', 'EXPIRED'))
);

CREATE INDEX IF NOT EXISTS idx_content_sources_version_fact
    ON place_content_sources (content_version_id, fact_key)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_content_sources_expires
    ON place_content_sources (expires_at)
    WHERE expires_at IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_content_sources_type_status
    ON place_content_sources (source_type, verification_status)
    WHERE deleted_at IS NULL;


-- 3. 地点媒体资产表
CREATE TABLE IF NOT EXISTS place_media_assets (
    id                  VARCHAR(36)     PRIMARY KEY,
    place_id            VARCHAR(36)     NOT NULL,
    file_id             VARCHAR(36),
    source_type         VARCHAR(40)     NOT NULL,
    source_reference    TEXT,
    license_type        VARCHAR(80),
    attribution         TEXT,
    alt_text            VARCHAR(300),
    width               INTEGER,
    height              INTEGER,
    sort_order          INTEGER         NOT NULL DEFAULT 0,
    status              VARCHAR(20)     NOT NULL,
    reviewed_by         VARCHAR(36),
    reviewed_at         TIMESTAMP,
    rights_expires_at   TIMESTAMP,
    created_at          TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMP,
    CONSTRAINT chk_media_assets_source_type
        CHECK (source_type IN ('OWNED', 'USER_AUTHORIZED', 'OFFICIAL_AUTHORIZED', 'LICENSED_LIBRARY', 'PROVIDER_REFERENCE')),
    CONSTRAINT chk_media_assets_status
        CHECK (status IN ('DRAFT', 'APPROVED', 'REJECTED', 'EXPIRED', 'ARCHIVED')),
    CONSTRAINT chk_media_assets_has_ref
        CHECK (file_id IS NOT NULL OR source_reference IS NOT NULL)
);

-- 同一 Place 同一文件不重复绑定
CREATE UNIQUE INDEX IF NOT EXISTS uniq_media_assets_place_file
    ON place_media_assets (place_id, file_id)
    WHERE file_id IS NOT NULL AND deleted_at IS NULL;

-- 按 Place 查询已审核媒体（按排序）
CREATE INDEX IF NOT EXISTS idx_media_assets_place_status_sort
    ON place_media_assets (place_id, status, sort_order)
    WHERE deleted_at IS NULL;

-- 授权到期查询
CREATE INDEX IF NOT EXISTS idx_media_assets_rights_expires
    ON place_media_assets (rights_expires_at)
    WHERE rights_expires_at IS NOT NULL AND deleted_at IS NULL;

-- 4. 外键（ON DELETE RESTRICT，业务删除仍走 deleted_at 逻辑删除）
-- 注意：places / files 表已存在；source 绑定 content version
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_content_versions_place'
    ) THEN
        ALTER TABLE place_content_versions
            ADD CONSTRAINT fk_content_versions_place
            FOREIGN KEY (place_id) REFERENCES places(id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_content_sources_version'
    ) THEN
        ALTER TABLE place_content_sources
            ADD CONSTRAINT fk_content_sources_version
            FOREIGN KEY (content_version_id) REFERENCES place_content_versions(id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_media_assets_place'
    ) THEN
        ALTER TABLE place_media_assets
            ADD CONSTRAINT fk_media_assets_place
            FOREIGN KEY (place_id) REFERENCES places(id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_media_assets_file'
    ) THEN
        ALTER TABLE place_media_assets
            ADD CONSTRAINT fk_media_assets_file
            FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE RESTRICT;
    END IF;
END $$;
