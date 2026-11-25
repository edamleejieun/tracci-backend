-- Idempotent alignment to the current business schema.
-- Safe for: fresh DBs, prod DBs with partial alter/collaboration scripts already applied.

CREATE TABLE IF NOT EXISTS users (
    id                VARCHAR(36)  NOT NULL,
    email             VARCHAR(255),
    email_verified    BOOLEAN      NOT NULL DEFAULT FALSE,
    email_verified_at TIMESTAMP,
    phone             VARCHAR(20),
    phone_verified    BOOLEAN      NOT NULL DEFAULT FALSE,
    phone_verified_at TIMESTAMP,
    password_hash     VARCHAR(100),
    nickname          VARCHAR(50),
    avatar_file_id    VARCHAR(36),
    avatar_object_key VARCHAR(500),
    login_type        VARCHAR(30),
    last_login_at     TIMESTAMP,
    status            VARCHAR(20)  NOT NULL DEFAULT 'active',
    deleted_reason    VARCHAR(200),
    anonymized_at     TIMESTAMP,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at        TIMESTAMP,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uniq_users_phone UNIQUE (phone)
);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS phone_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS phone_verified_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS avatar_file_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS avatar_object_key VARCHAR(500),
    ADD COLUMN IF NOT EXISTS login_type VARCHAR(30),
    ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deleted_reason VARCHAR(200),
    ADD COLUMN IF NOT EXISTS anonymized_at TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS uniq_users_email
    ON users (email) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_users_last_login_at ON users (last_login_at);
CREATE INDEX IF NOT EXISTS idx_users_avatar_file_id ON users (avatar_file_id);

COMMENT ON TABLE users IS '用户表';

CREATE TABLE IF NOT EXISTS user_auth_identities (
    id               VARCHAR(36)  NOT NULL,
    user_id          VARCHAR(36)  NOT NULL,
    provider         VARCHAR(30)  NOT NULL,
    provider_user_id VARCHAR(128) NOT NULL,
    union_id         VARCHAR(128),
    email            VARCHAR(255),
    phone            VARCHAR(20),
    status           VARCHAR(20)  NOT NULL DEFAULT 'active',
    bound_at         TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at       TIMESTAMP,
    CONSTRAINT pk_user_auth_identities PRIMARY KEY (id)
);

ALTER TABLE user_auth_identities
    ADD COLUMN IF NOT EXISTS email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS phone VARCHAR(20),
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'active',
    ADD COLUMN IF NOT EXISTS bound_at TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS uniq_auth_identity_provider_user
    ON user_auth_identities (provider, provider_user_id)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_auth_identity_user_id ON user_auth_identities (user_id);
CREATE INDEX IF NOT EXISTS idx_auth_identity_union_id
    ON user_auth_identities (union_id) WHERE union_id IS NOT NULL;

COMMENT ON TABLE user_auth_identities IS '用户身份绑定表';

CREATE TABLE IF NOT EXISTS files (
    id                 VARCHAR(36)  NOT NULL,
    user_id            VARCHAR(36)  NOT NULL,
    business_type      VARCHAR(32)  NOT NULL,
    business_id        VARCHAR(36),
    original_file_name VARCHAR(255) NOT NULL,
    content_type       VARCHAR(64)  NOT NULL,
    size               BIGINT       NOT NULL,
    object_key         VARCHAR(500) NOT NULL,
    bucket             VARCHAR(100) NOT NULL,
    region             VARCHAR(50)  NOT NULL,
    url                VARCHAR(500),
    width              INTEGER,
    height             INTEGER,
    etag               VARCHAR(64),
    status             VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at         TIMESTAMP,
    CONSTRAINT pk_files PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_files_user_status ON files (user_id, status);
CREATE INDEX IF NOT EXISTS idx_files_object_key ON files (object_key);
CREATE INDEX IF NOT EXISTS idx_files_business ON files (business_type, business_id);
CREATE INDEX IF NOT EXISTS idx_files_user_business_deleted ON files (user_id, business_type, business_id, deleted_at);

COMMENT ON TABLE files IS '文件元数据表';

CREATE TABLE IF NOT EXISTS trips (
    id                            VARCHAR(36)  NOT NULL,
    user_id                       VARCHAR(36)  NOT NULL,
    title                         VARCHAR(100) NOT NULL,
    cover_file_id                 VARCHAR(36),
    destination_name              VARCHAR(100),
    destination_adcode            VARCHAR(20),
    destination_city_code         VARCHAR(20),
    destination_province_name     VARCHAR(50),
    destination_city_name         VARCHAR(50),
    destination_district_name     VARCHAR(50),
    destination_latitude          DECIMAL(10, 7),
    destination_longitude         DECIMAL(10, 7),
    destination_provider          VARCHAR(20),
    destination_provider_place_id VARCHAR(100),
    destination_country           VARCHAR(50),
    start_date                    DATE         NOT NULL,
    end_date                      DATE         NOT NULL,
    status                        VARCHAR(20)  NOT NULL DEFAULT 'planning',
    base_currency                 VARCHAR(3)   NOT NULL DEFAULT 'CNY',
    share_token                   VARCHAR(36),
    share_enabled                 BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at                    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at                    TIMESTAMP,
    CONSTRAINT pk_trips PRIMARY KEY (id),
    CONSTRAINT uniq_trips_share_token UNIQUE (share_token)
);

CREATE INDEX IF NOT EXISTS idx_trips_user_id ON trips (user_id);
CREATE INDEX IF NOT EXISTS idx_trips_cover_file_id ON trips (cover_file_id);
CREATE INDEX IF NOT EXISTS idx_trips_user_status ON trips (user_id, status);
CREATE INDEX IF NOT EXISTS idx_trips_user_start_date ON trips (user_id, start_date);
CREATE INDEX IF NOT EXISTS idx_trips_destination_adcode ON trips (destination_adcode);
CREATE INDEX IF NOT EXISTS idx_trips_user_deleted_created ON trips (user_id, deleted_at, created_at);

COMMENT ON TABLE trips IS '旅行表';

CREATE TABLE IF NOT EXISTS trip_members (
    id         VARCHAR(36) NOT NULL,
    trip_id    VARCHAR(36) NOT NULL,
    user_id    VARCHAR(36) NOT NULL,
    role       VARCHAR(20) NOT NULL DEFAULT 'owner',
    joined_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT pk_trip_members PRIMARY KEY (id),
    CONSTRAINT uniq_trip_members_trip_user UNIQUE (trip_id, user_id)
);

ALTER TABLE trip_members
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'active',
    ADD COLUMN IF NOT EXISTS invited_by_user_id VARCHAR(36);

CREATE INDEX IF NOT EXISTS idx_trip_members_user_id ON trip_members (user_id);
CREATE INDEX IF NOT EXISTS idx_trip_members_trip_status
    ON trip_members (trip_id, status, deleted_at);

COMMENT ON TABLE trip_members IS '旅行成员表';

CREATE TABLE IF NOT EXISTS trip_invitations (
    id                 VARCHAR(36) NOT NULL,
    trip_id            VARCHAR(36) NOT NULL,
    created_by_user_id VARCHAR(36) NOT NULL,
    invite_code_hash   VARCHAR(128) NOT NULL,
    role_to_grant      VARCHAR(20) NOT NULL,
    expires_at         TIMESTAMP NOT NULL,
    max_uses           INTEGER NOT NULL DEFAULT 1,
    used_count         INTEGER NOT NULL DEFAULT 0,
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at         TIMESTAMP,
    CONSTRAINT pk_trip_invitations PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uniq_trip_invitations_code_hash
    ON trip_invitations (invite_code_hash)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_trip_invitations_trip_created
    ON trip_invitations (trip_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS trip_activity_logs (
    id                 VARCHAR(36) NOT NULL,
    trip_id            VARCHAR(36) NOT NULL,
    actor_user_id      VARCHAR(36) NOT NULL,
    actor_member_id    VARCHAR(36),
    action             VARCHAR(50) NOT NULL,
    target_type        VARCHAR(30) NOT NULL,
    target_id          VARCHAR(36),
    field_changes_json JSONB,
    summary            VARCHAR(300),
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at         TIMESTAMP,
    CONSTRAINT pk_trip_activity_logs PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_trip_activity_logs_trip_created
    ON trip_activity_logs (trip_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS trip_days (
    id         VARCHAR(36) NOT NULL,
    trip_id    VARCHAR(36) NOT NULL,
    user_id    VARCHAR(36) NOT NULL,
    day_index  SMALLINT    NOT NULL,
    date       DATE        NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT pk_trip_days PRIMARY KEY (id),
    CONSTRAINT uniq_trip_days_trip_index UNIQUE (trip_id, day_index)
);

CREATE INDEX IF NOT EXISTS idx_trip_days_trip_id ON trip_days (trip_id);
CREATE INDEX IF NOT EXISTS idx_trip_days_user_trip_deleted_index ON trip_days (user_id, trip_id, deleted_at, day_index);

COMMENT ON TABLE trip_days IS '行程天表';

CREATE TABLE IF NOT EXISTS itinerary_nodes (
    id                         VARCHAR(36)  NOT NULL,
    trip_day_id                VARCHAR(36)  NOT NULL,
    trip_id                    VARCHAR(36)  NOT NULL,
    user_id                    VARCHAR(36)  NOT NULL,
    title                      VARCHAR(100) NOT NULL,
    type                       VARCHAR(20)  NOT NULL DEFAULT 'other',
    start_time                 TIME,
    end_time                   TIME,
    address                    VARCHAR(200),
    location_name              VARCHAR(100),
    location_address           VARCHAR(200),
    location_latitude          DECIMAL(10, 7),
    location_longitude         DECIMAL(10, 7),
    location_poi_id            VARCHAR(100),
    location_adcode            VARCHAR(20),
    location_city_code         VARCHAR(20),
    location_province_name     VARCHAR(50),
    location_city_name         VARCHAR(50),
    location_district_name     VARCHAR(50),
    location_provider          VARCHAR(20),
    location_country           VARCHAR(50),
    note                       TEXT,
    sort_order                 INTEGER      NOT NULL DEFAULT 0,
    created_at                 TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                 TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at                 TIMESTAMP,
    CONSTRAINT pk_itinerary_nodes PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_itin_nodes_trip_day_id ON itinerary_nodes (trip_day_id);
CREATE INDEX IF NOT EXISTS idx_itin_nodes_trip_id ON itinerary_nodes (trip_id);
CREATE INDEX IF NOT EXISTS idx_itin_nodes_user_trip_deleted ON itinerary_nodes (user_id, trip_id, deleted_at);
CREATE INDEX IF NOT EXISTS idx_itin_nodes_location_poi_id ON itinerary_nodes (location_poi_id);
CREATE INDEX IF NOT EXISTS idx_itin_nodes_location_adcode ON itinerary_nodes (location_adcode);
CREATE INDEX IF NOT EXISTS idx_itin_nodes_sort ON itinerary_nodes (trip_day_id, sort_order);

COMMENT ON TABLE itinerary_nodes IS '行程节点表';

CREATE TABLE IF NOT EXISTS expenses (
    id            VARCHAR(36)    NOT NULL,
    trip_id       VARCHAR(36)    NOT NULL,
    user_id       VARCHAR(36)    NOT NULL,
    amount_input  DECIMAL(12, 2) NOT NULL,
    currency      VARCHAR(3)     NOT NULL,
    amount_cny    DECIMAL(12, 2) NOT NULL,
    exchange_rate DECIMAL(16, 8) NOT NULL,
    category      VARCHAR(20)    NOT NULL,
    note          VARCHAR(200),
    spent_at      TIMESTAMP      NOT NULL,
    created_at    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at    TIMESTAMP,
    CONSTRAINT pk_expenses PRIMARY KEY (id)
);

ALTER TABLE expenses
    ADD COLUMN IF NOT EXISTS expense_scope VARCHAR(20) NOT NULL DEFAULT 'personal',
    ADD COLUMN IF NOT EXISTS payer_member_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS created_by_member_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS split_method VARCHAR(20);

CREATE INDEX IF NOT EXISTS idx_expenses_trip_id ON expenses (trip_id);
CREATE INDEX IF NOT EXISTS idx_expenses_trip_category ON expenses (trip_id, category);
CREATE INDEX IF NOT EXISTS idx_expenses_trip_spent_at ON expenses (trip_id, spent_at);
CREATE INDEX IF NOT EXISTS idx_expenses_user_id ON expenses (user_id);
CREATE INDEX IF NOT EXISTS idx_expenses_user_trip_deleted_spent ON expenses (user_id, trip_id, deleted_at, spent_at);
CREATE INDEX IF NOT EXISTS idx_expenses_user_trip_deleted_category ON expenses (user_id, trip_id, deleted_at, category);
CREATE INDEX IF NOT EXISTS idx_expenses_trip_scope
    ON expenses (trip_id, expense_scope)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE expenses IS '开支记录表';

CREATE TABLE IF NOT EXISTS expense_splits (
    id                 VARCHAR(36) NOT NULL,
    expense_id         VARCHAR(36) NOT NULL,
    trip_id            VARCHAR(36) NOT NULL,
    member_id          VARCHAR(36) NOT NULL,
    user_id            VARCHAR(36) NOT NULL,
    share_amount_cny   DECIMAL(12, 2) NOT NULL,
    share_amount_input DECIMAL(12, 2) NOT NULL,
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at         TIMESTAMP,
    CONSTRAINT pk_expense_splits PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_expense_splits_expense
    ON expense_splits (expense_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_expense_splits_trip_member
    ON expense_splits (trip_id, member_id)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS journal_entries (
    id                         VARCHAR(36) NOT NULL,
    trip_id                    VARCHAR(36) NOT NULL,
    user_id                    VARCHAR(36) NOT NULL,
    content                    TEXT,
    location_name              VARCHAR(100),
    location_address           VARCHAR(200),
    location_poi_id            VARCHAR(100),
    location_adcode            VARCHAR(20),
    location_city_code         VARCHAR(20),
    location_province_name     VARCHAR(50),
    location_city_name         VARCHAR(50),
    location_district_name     VARCHAR(50),
    location_provider          VARCHAR(20),
    location_country           VARCHAR(50),
    latitude                   DECIMAL(10, 7),
    longitude                  DECIMAL(10, 7),
    occurred_at                TIMESTAMP   NOT NULL,
    mood                       VARCHAR(20),
    is_highlight               BOOLEAN     NOT NULL DEFAULT FALSE,
    sort_order                 INTEGER     NOT NULL DEFAULT 0,
    created_at                 TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                 TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at                 TIMESTAMP,
    CONSTRAINT pk_journal_entries PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_journal_entries_trip_id ON journal_entries (trip_id);
CREATE INDEX IF NOT EXISTS idx_journal_entries_occurred ON journal_entries (trip_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_journal_entries_location_poi_id ON journal_entries (location_poi_id);
CREATE INDEX IF NOT EXISTS idx_journal_entries_location_adcode ON journal_entries (location_adcode);
CREATE INDEX IF NOT EXISTS idx_journal_entries_user_trip_deleted_occurred ON journal_entries (user_id, trip_id, deleted_at, occurred_at);

COMMENT ON TABLE journal_entries IS '旅行日志表';

CREATE TABLE IF NOT EXISTS journal_photos (
    id               VARCHAR(36) NOT NULL,
    journal_entry_id VARCHAR(36) NOT NULL,
    trip_id          VARCHAR(36) NOT NULL,
    user_id          VARCHAR(36) NOT NULL,
    file_id          VARCHAR(36) NOT NULL,
    sort_order       SMALLINT    NOT NULL DEFAULT 0,
    is_featured      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at       TIMESTAMP,
    CONSTRAINT pk_journal_photos PRIMARY KEY (id)
);

ALTER TABLE journal_photos
    ADD COLUMN IF NOT EXISTS is_featured BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_journal_photos_entry_sort ON journal_photos (journal_entry_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_journal_photos_trip_id ON journal_photos (trip_id);
CREATE INDEX IF NOT EXISTS idx_journal_photos_user_trip ON journal_photos (user_id, trip_id);
CREATE INDEX IF NOT EXISTS idx_journal_photos_user_trip_deleted ON journal_photos (user_id, trip_id, deleted_at);
CREATE INDEX IF NOT EXISTS idx_journal_photos_file_id ON journal_photos (file_id);
CREATE INDEX IF NOT EXISTS idx_journal_photos_trip_featured
    ON journal_photos (trip_id, is_featured, sort_order)
    WHERE deleted_at IS NULL AND is_featured = TRUE;

COMMENT ON TABLE journal_photos IS '日志图片表';

CREATE TABLE IF NOT EXISTS feedbacks (
    id              VARCHAR(36) NOT NULL,
    user_id         VARCHAR(36) NOT NULL,
    type            VARCHAR(32) NOT NULL,
    title           VARCHAR(100),
    content         TEXT        NOT NULL,
    contact_type    VARCHAR(16),
    contact         VARCHAR(100),
    status          VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED',
    app_platform    VARCHAR(32),
    app_version     VARCHAR(32),
    system_info     TEXT,
    handled_at      TIMESTAMP,
    handler_note    TEXT,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at      TIMESTAMP,
    CONSTRAINT pk_feedbacks PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_feedbacks_user_created ON feedbacks (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_feedbacks_status ON feedbacks (status);
CREATE INDEX IF NOT EXISTS idx_feedbacks_type ON feedbacks (type);

COMMENT ON TABLE feedbacks IS '用户反馈表';

CREATE TABLE IF NOT EXISTS feedback_attachments (
    id           VARCHAR(36) NOT NULL,
    feedback_id  VARCHAR(36) NOT NULL,
    user_id      VARCHAR(36) NOT NULL,
    file_id      VARCHAR(36) NOT NULL,
    sort_order   SMALLINT    NOT NULL DEFAULT 0,
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at   TIMESTAMP,
    CONSTRAINT pk_feedback_attachments PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_feedback_attachments_feedback ON feedback_attachments (feedback_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_feedback_attachments_file ON feedback_attachments (file_id);
CREATE INDEX IF NOT EXISTS idx_feedback_attachments_user ON feedback_attachments (user_id, deleted_at);

COMMENT ON TABLE feedback_attachments IS '反馈截图附件关联表';

-- Legacy account schema cleanup (idempotent)
ALTER TABLE users DROP CONSTRAINT IF EXISTS uniq_users_username;
ALTER TABLE users DROP CONSTRAINT IF EXISTS uniq_users_wechat_openid;
ALTER TABLE users DROP COLUMN IF EXISTS username;
ALTER TABLE users DROP COLUMN IF EXISTS wechat_openid;
ALTER TABLE users DROP COLUMN IF EXISTS wechat_unionid;
DROP INDEX IF EXISTS idx_users_wechat_unionid;

ALTER TABLE user_auth_identities DROP CONSTRAINT IF EXISTS uniq_auth_identity_provider_channel_user;
ALTER TABLE user_auth_identities DROP COLUMN IF EXISTS channel;

ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS destination_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS destination_adcode VARCHAR(20),
    ADD COLUMN IF NOT EXISTS destination_city_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS destination_province_name VARCHAR(50),
    ADD COLUMN IF NOT EXISTS destination_city_name VARCHAR(50),
    ADD COLUMN IF NOT EXISTS destination_district_name VARCHAR(50),
    ADD COLUMN IF NOT EXISTS destination_latitude DECIMAL(10, 7),
    ADD COLUMN IF NOT EXISTS destination_longitude DECIMAL(10, 7),
    ADD COLUMN IF NOT EXISTS destination_provider VARCHAR(20),
    ADD COLUMN IF NOT EXISTS destination_provider_place_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS destination_country VARCHAR(50);

ALTER TABLE itinerary_nodes
    ADD COLUMN IF NOT EXISTS location_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS location_address VARCHAR(200),
    ADD COLUMN IF NOT EXISTS location_latitude DECIMAL(10, 7),
    ADD COLUMN IF NOT EXISTS location_longitude DECIMAL(10, 7),
    ADD COLUMN IF NOT EXISTS location_poi_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS location_adcode VARCHAR(20),
    ADD COLUMN IF NOT EXISTS location_city_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS location_province_name VARCHAR(50),
    ADD COLUMN IF NOT EXISTS location_city_name VARCHAR(50),
    ADD COLUMN IF NOT EXISTS location_district_name VARCHAR(50),
    ADD COLUMN IF NOT EXISTS location_provider VARCHAR(20),
    ADD COLUMN IF NOT EXISTS location_country VARCHAR(50);

ALTER TABLE journal_entries
    ADD COLUMN IF NOT EXISTS location_address VARCHAR(200),
    ADD COLUMN IF NOT EXISTS location_poi_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS location_adcode VARCHAR(20),
    ADD COLUMN IF NOT EXISTS location_city_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS location_province_name VARCHAR(50),
    ADD COLUMN IF NOT EXISTS location_city_name VARCHAR(50),
    ADD COLUMN IF NOT EXISTS location_district_name VARCHAR(50),
    ADD COLUMN IF NOT EXISTS location_provider VARCHAR(20),
    ADD COLUMN IF NOT EXISTS location_country VARCHAR(50),
    ADD COLUMN IF NOT EXISTS mood VARCHAR(20);

CREATE TABLE IF NOT EXISTS ai_workflow_bindings (
    id VARCHAR(64) PRIMARY KEY,
    workflow_id VARCHAR(64) NOT NULL UNIQUE,
    user_id VARCHAR(64) NOT NULL,
    trip_id VARCHAR(64),
    workflow_type VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL,
    artifact_id VARCHAR(64),
    artifact_type VARCHAR(64),
    auth_scope TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_workflow_bindings_user
    ON ai_workflow_bindings(user_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_ai_workflow_bindings_trip
    ON ai_workflow_bindings(trip_id, created_at DESC)
    WHERE deleted_at IS NULL AND trip_id IS NOT NULL;