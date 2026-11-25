-- Phase 3 AI generation trace and concurrency-safe idempotency.
ALTER TABLE place_content_versions
    ADD COLUMN IF NOT EXISTS generation_idempotency_key VARCHAR(128),
    ADD COLUMN IF NOT EXISTS source_workflow_id VARCHAR(36);

CREATE UNIQUE INDEX IF NOT EXISTS uniq_content_versions_generation_key
    ON place_content_versions (place_id, generation_idempotency_key)
    WHERE generation_idempotency_key IS NOT NULL AND deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uniq_content_versions_active_draft_digest
    ON place_content_versions (place_id, locale, source_digest, prompt_version)
    WHERE source_digest IS NOT NULL
      AND prompt_version IS NOT NULL
      AND status = 'DRAFT'
      AND deleted_at IS NULL;
