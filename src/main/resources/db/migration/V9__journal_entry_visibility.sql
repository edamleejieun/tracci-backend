ALTER TABLE journal_entries
    ADD COLUMN IF NOT EXISTS visibility VARCHAR(20) NOT NULL DEFAULT 'all';

ALTER TABLE journal_entries
    DROP CONSTRAINT IF EXISTS chk_journal_entries_visibility;

ALTER TABLE journal_entries
    ADD CONSTRAINT chk_journal_entries_visibility
        CHECK (visibility IN ('all', 'private'));

CREATE INDEX IF NOT EXISTS idx_journal_entries_trip_visibility_user
    ON journal_entries (trip_id, visibility, user_id)
    WHERE deleted_at IS NULL;
