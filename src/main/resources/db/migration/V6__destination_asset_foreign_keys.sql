ALTER TABLE destination_asset_versions
    ADD CONSTRAINT fk_destination_asset_versions_pack
        FOREIGN KEY (pack_id) REFERENCES destination_asset_packs (id);

ALTER TABLE destination_asset_places
    ADD CONSTRAINT fk_destination_asset_places_pack
        FOREIGN KEY (pack_id) REFERENCES destination_asset_packs (id);

ALTER TABLE destination_asset_places
    ADD CONSTRAINT fk_destination_asset_places_version
        FOREIGN KEY (version_id) REFERENCES destination_asset_versions (id);

ALTER TABLE destination_asset_usage_metrics
    ADD CONSTRAINT fk_destination_asset_usage_metrics_pack
        FOREIGN KEY (pack_id) REFERENCES destination_asset_packs (id);