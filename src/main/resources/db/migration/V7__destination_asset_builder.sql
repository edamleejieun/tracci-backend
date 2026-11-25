-- =============================================================================
-- Destination Asset Builder: build jobs, candidates, review history
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 5.1 destination_asset_build_jobs
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS destination_asset_build_jobs (
    id                      VARCHAR(36)  NOT NULL,
    city_key                VARCHAR(80)  NOT NULL,
    city_name               VARCHAR(100) NOT NULL,
    country_code            VARCHAR(8)   NOT NULL DEFAULT 'CN',
    version                 VARCHAR(40)  NOT NULL,
    status                  VARCHAR(32)  NOT NULL,
    source_config_json      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    quality_report_json     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    stats_json              JSONB        NOT NULL DEFAULT '{}'::jsonb,
    error_message           TEXT,
    created_by              VARCHAR(64),
    published_version_id    VARCHAR(36),
    published_at            TIMESTAMP,
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at              TIMESTAMP,
    CONSTRAINT pk_destination_asset_build_jobs PRIMARY KEY (id)
);

-- -----------------------------------------------------------------------------
-- 5.2 destination_asset_candidates
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS destination_asset_candidates (
    id                      VARCHAR(36)  NOT NULL,
    job_id                  VARCHAR(36)  NOT NULL,
    stable_fact_key         VARCHAR(160),
    place_id                VARCHAR(120) NOT NULL,
    title                   VARCHAR(160) NOT NULL,
    type                    VARCHAR(40)  NOT NULL,
    latitude                DECIMAL(10, 7),
    longitude               DECIMAL(10, 7),
    provider                VARCHAR(30),
    provider_place_id       VARCHAR(120),
    city_key                VARCHAR(80),
    city_name               VARCHAR(100),
    zone                    VARCHAR(80),
    themes_json             JSONB        NOT NULL DEFAULT '[]'::jsonb,
    weights_json            JSONB        NOT NULL DEFAULT '{}'::jsonb,
    payload_json            JSONB        NOT NULL DEFAULT '{}'::jsonb,
    source_type             VARCHAR(40),
    source_ref              VARCHAR(200),
    confidence              DECIMAL(5, 4),
    review_status           VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    review_note             TEXT,
    reviewed_by             VARCHAR(64),
    reviewed_at             TIMESTAMP,
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at              TIMESTAMP,
    CONSTRAINT pk_destination_asset_candidates PRIMARY KEY (id)
);

-- -----------------------------------------------------------------------------
-- 5.3 destination_asset_candidate_reviews
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS destination_asset_candidate_reviews (
    id                      VARCHAR(36)  NOT NULL,
    candidate_id            VARCHAR(36)  NOT NULL,
    job_id                  VARCHAR(36)  NOT NULL,
    action                  VARCHAR(32)  NOT NULL,
    before_json             JSONB        NOT NULL DEFAULT '{}'::jsonb,
    after_json              JSONB        NOT NULL DEFAULT '{}'::jsonb,
    review_note             TEXT,
    operator_id             VARCHAR(64),
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at              TIMESTAMP,
    CONSTRAINT pk_destination_asset_candidate_reviews PRIMARY KEY (id)
);

-- -----------------------------------------------------------------------------
-- Indexes
-- -----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_destination_asset_build_jobs_city_key
    ON destination_asset_build_jobs (city_key)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_destination_asset_build_jobs_status
    ON destination_asset_build_jobs (status)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_destination_asset_build_jobs_created_at
    ON destination_asset_build_jobs (created_at DESC)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uniq_destination_asset_build_jobs_city_version
    ON destination_asset_build_jobs (city_key, version)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_destination_asset_candidates_job_id
    ON destination_asset_candidates (job_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_destination_asset_candidates_city_key
    ON destination_asset_candidates (city_key)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_destination_asset_candidates_review_status
    ON destination_asset_candidates (review_status)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_destination_asset_candidates_provider_place_id
    ON destination_asset_candidates (provider_place_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_destination_asset_candidates_stable_fact_key
    ON destination_asset_candidates (stable_fact_key)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_destination_asset_candidate_reviews_candidate_id
    ON destination_asset_candidate_reviews (candidate_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_destination_asset_candidate_reviews_job_id
    ON destination_asset_candidate_reviews (job_id)
    WHERE deleted_at IS NULL;

-- -----------------------------------------------------------------------------
-- Comments
-- -----------------------------------------------------------------------------
COMMENT ON TABLE destination_asset_build_jobs
    IS 'Destination asset builder build job records';
COMMENT ON TABLE destination_asset_candidates
    IS 'Candidate POIs within a build job; transitional candidate facts pool';
COMMENT ON TABLE destination_asset_candidate_reviews
    IS 'Review history for candidate POIs';
