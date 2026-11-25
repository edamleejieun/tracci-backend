-- ============================================================
-- V14: 想去的地点（Saved Places）
-- 新增 places / place_provider_refs / saved_places / saved_place_usages
-- trips 新增 destination_place_id，itinerary_nodes 新增 location_place_id
-- ============================================================

-- 1. 通用地点主数据
CREATE TABLE IF NOT EXISTS places (
    id              VARCHAR(36)     PRIMARY KEY,
    place_scope     VARCHAR(20)     NOT NULL,
    category        VARCHAR(20)     NOT NULL,
    canonical_name  VARCHAR(200)    NOT NULL,
    address         VARCHAR(500),
    city_name       VARCHAR(100),
    country         VARCHAR(100),
    latitude        DECIMAL(10,7),
    longitude       DECIMAL(10,7),
    destination_key VARCHAR(200),
    parent_place_id VARCHAR(36),
    created_source  VARCHAR(20)     NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    merged_into_id  VARCHAR(36),
    created_at      TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMP,
    CONSTRAINT chk_places_scope CHECK (place_scope IN ('DESTINATION', 'POI')),
    CONSTRAINT chk_places_category CHECK (category IN ('CITY', 'REGION', 'ATTRACTION', 'RESTAURANT', 'HOTEL', 'SHOPPING', 'TRANSPORT', 'OTHER')),
    CONSTRAINT chk_places_status CHECK (status IN ('ACTIVE', 'MERGED', 'DEPRECATED')),
    CONSTRAINT chk_places_created_source CHECK (created_source IN ('APPLE', 'AMAP', 'MANUAL'))
);

CREATE INDEX IF NOT EXISTS idx_places_scope_destination
    ON places (place_scope, destination_key)
    WHERE deleted_at IS NULL AND status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_places_parent
    ON places (parent_place_id)
    WHERE deleted_at IS NULL;

-- 2. Place 与地图服务 ID 映射
CREATE TABLE IF NOT EXISTS place_provider_refs (
    id                  VARCHAR(36)     PRIMARY KEY,
    place_id            VARCHAR(36)     NOT NULL,
    provider            VARCHAR(20)     NOT NULL,
    provider_place_id   VARCHAR(200)    NOT NULL,
    is_primary          BOOLEAN         NOT NULL DEFAULT false,
    last_checked_at     TIMESTAMP,
    created_at          TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uniq_place_provider_refs_provider_id
    ON place_provider_refs (provider, provider_place_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_place_provider_refs_place
    ON place_provider_refs (place_id)
    WHERE deleted_at IS NULL;

-- 3. 用户收藏地点关系表
CREATE TABLE IF NOT EXISTS saved_places (
    id          VARCHAR(36)     PRIMARY KEY,
    user_id     VARCHAR(36)     NOT NULL,
    place_id    VARCHAR(36)     NOT NULL,
    note        VARCHAR(500),
    source      VARCHAR(20)     NOT NULL,
    created_at  TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMP,
    CONSTRAINT chk_saved_places_source CHECK (source IN ('POI_SEARCH', 'MANUAL', 'AI_PLAN', 'SHARE'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uniq_saved_places_user_place
    ON saved_places (user_id, place_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_saved_places_user_created
    ON saved_places (user_id, created_at DESC)
    WHERE deleted_at IS NULL;

-- 4. 收藏使用关系表
CREATE TABLE IF NOT EXISTS saved_place_usages (
    id                  VARCHAR(36)     PRIMARY KEY,
    user_id             VARCHAR(36)     NOT NULL,
    saved_place_id      VARCHAR(36)     NOT NULL,
    trip_id             VARCHAR(36)     NOT NULL,
    itinerary_node_id   VARCHAR(36),
    usage_type          VARCHAR(20)     NOT NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMP,
    CONSTRAINT chk_saved_place_usages_type CHECK (usage_type IN ('TRIP_DESTINATION', 'ITINERARY_NODE'))
);

CREATE INDEX IF NOT EXISTS idx_saved_place_usages_user_place
    ON saved_place_usages (user_id, saved_place_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uniq_saved_place_usages_node
    ON saved_place_usages (saved_place_id, itinerary_node_id)
    WHERE deleted_at IS NULL AND itinerary_node_id IS NOT NULL;

-- 5. 已有表新增可空 Place 引用
ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS destination_place_id VARCHAR(36);

ALTER TABLE itinerary_nodes
    ADD COLUMN IF NOT EXISTS location_place_id VARCHAR(36);

CREATE INDEX IF NOT EXISTS idx_trips_destination_place_id
    ON trips (destination_place_id)
    WHERE destination_place_id IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_itinerary_nodes_location_place_id
    ON itinerary_nodes (location_place_id)
    WHERE location_place_id IS NOT NULL AND deleted_at IS NULL;
