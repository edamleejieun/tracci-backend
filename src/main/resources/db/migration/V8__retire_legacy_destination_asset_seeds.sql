-- Retire the pilot JSON seed data as an active runtime source.
-- City assets are now produced by the destination asset builder candidate-pool workflow.

UPDATE destination_asset_places
SET deleted_at = NOW(),
    updated_at = NOW()
WHERE deleted_at IS NULL
  AND version_id IN (
      SELECT id
      FROM destination_asset_versions
      WHERE reviewed_by = 'seed'
        AND deleted_at IS NULL
  )
  AND source_note = 'pilot seed';

UPDATE destination_asset_versions
SET status = 'ARCHIVED',
    updated_at = NOW()
WHERE reviewed_by = 'seed'
  AND deleted_at IS NULL
  AND status = 'PUBLISHED';

UPDATE destination_asset_packs
SET active_version_id = NULL,
    status = 'DRAFT',
    published_at = NULL,
    updated_at = NOW()
WHERE deleted_at IS NULL
  AND active_version_id IN (
      SELECT id
      FROM destination_asset_versions
      WHERE reviewed_by = 'seed'
  )
  AND NOT EXISTS (
      SELECT 1
      FROM destination_asset_versions
      WHERE destination_asset_versions.pack_id = destination_asset_packs.id
        AND destination_asset_versions.status = 'PUBLISHED'
        AND destination_asset_versions.deleted_at IS NULL
        AND COALESCE(destination_asset_versions.reviewed_by, '') <> 'seed'
  );
