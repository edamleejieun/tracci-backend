-- Phase 3 publish idempotency and immutable before/after audit trace.
ALTER TABLE place_content_versions
    ADD COLUMN IF NOT EXISTS publish_idempotency_key VARCHAR(128),
    ADD COLUMN IF NOT EXISTS publish_request_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS previous_published_version_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS previous_published_content_hash VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uniq_content_versions_publish_key
    ON place_content_versions (publish_idempotency_key)
    WHERE publish_idempotency_key IS NOT NULL AND deleted_at IS NULL;
