CREATE TABLE IF NOT EXISTS destination_asset_packs (
    id                VARCHAR(36)  NOT NULL,
    city_key          VARCHAR(80)  NOT NULL,
    city_name         VARCHAR(100) NOT NULL,
    country_code      VARCHAR(8)   NOT NULL,
    active_version_id VARCHAR(36),
    status            VARCHAR(24)  NOT NULL DEFAULT 'DRAFT',
    payload_json      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    published_at      TIMESTAMP,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at        TIMESTAMP,
    CONSTRAINT pk_destination_asset_packs PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS destination_asset_versions (
    id           VARCHAR(36) NOT NULL,
    pack_id      VARCHAR(36) NOT NULL,
    version      VARCHAR(40) NOT NULL,
    status       VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    payload_json JSONB       NOT NULL DEFAULT '{}'::jsonb,
    reviewed_by  VARCHAR(100),
    reviewed_at  TIMESTAMP,
    published_at TIMESTAMP,
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at   TIMESTAMP,
    CONSTRAINT pk_destination_asset_versions PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS destination_asset_places (
    id                VARCHAR(36)  NOT NULL,
    pack_id           VARCHAR(36)  NOT NULL,
    version_id        VARCHAR(36)  NOT NULL,
    place_id          VARCHAR(120) NOT NULL,
    title             VARCHAR(160) NOT NULL,
    type              VARCHAR(40)  NOT NULL,
    latitude          DECIMAL(10, 7),
    longitude         DECIMAL(10, 7),
    provider          VARCHAR(30),
    provider_place_id VARCHAR(120),
    zone              VARCHAR(80),
    themes_json       JSONB        NOT NULL DEFAULT '[]'::jsonb,
    weights_json      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    payload_json      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    source_type       VARCHAR(40),
    source_note       VARCHAR(300),
    reviewed_by       VARCHAR(100),
    reviewed_at       TIMESTAMP,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at        TIMESTAMP,
    CONSTRAINT pk_destination_asset_places PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS destination_asset_usage_metrics (
    id                    VARCHAR(36) NOT NULL,
    pack_id               VARCHAR(36),
    version               VARCHAR(40),
    city_key              VARCHAR(80),
    planning_tier         VARCHAR(20),
    workflow_id           VARCHAR(80),
    registry_hits         INTEGER,
    poi_hydration_lookups INTEGER,
    unresolved_remaining  INTEGER,
    repair_rounds         INTEGER,
    validation_passed     BOOLEAN,
    latency_ms            BIGINT,
    created_at            TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at            TIMESTAMP,
    CONSTRAINT pk_destination_asset_usage_metrics PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uniq_destination_asset_pack_city_country
    ON destination_asset_packs (city_key, country_code)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_destination_asset_packs_city_key ON destination_asset_packs (city_key);
CREATE INDEX IF NOT EXISTS idx_destination_asset_packs_status ON destination_asset_packs (status);
CREATE INDEX IF NOT EXISTS idx_destination_asset_packs_active_version_id ON destination_asset_packs (active_version_id);
CREATE INDEX IF NOT EXISTS idx_destination_asset_versions_pack_id ON destination_asset_versions (pack_id);
CREATE INDEX IF NOT EXISTS idx_destination_asset_versions_status ON destination_asset_versions (status);
CREATE INDEX IF NOT EXISTS idx_destination_asset_places_pack_id ON destination_asset_places (pack_id);
CREATE INDEX IF NOT EXISTS idx_destination_asset_places_version_id ON destination_asset_places (version_id);
CREATE INDEX IF NOT EXISTS idx_destination_asset_places_place_id ON destination_asset_places (place_id);
CREATE INDEX IF NOT EXISTS idx_destination_asset_places_provider_place_id ON destination_asset_places (provider_place_id);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_destination_asset_versions_pack_version
    ON destination_asset_versions (pack_id, version)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_destination_asset_usage_metrics_city_key ON destination_asset_usage_metrics (city_key);
CREATE INDEX IF NOT EXISTS idx_destination_asset_usage_metrics_pack_id ON destination_asset_usage_metrics (pack_id);
CREATE INDEX IF NOT EXISTS idx_destination_asset_usage_metrics_workflow_id ON destination_asset_usage_metrics (workflow_id);

COMMENT ON TABLE destination_asset_packs IS 'AI trip planning destination asset pack master records';
COMMENT ON TABLE destination_asset_versions IS 'Versioned destination asset payloads';
COMMENT ON TABLE destination_asset_places IS 'Reviewed places belonging to destination asset versions';
COMMENT ON TABLE destination_asset_usage_metrics IS 'Destination asset usage metrics reserved for later AI Platform integration';

INSERT INTO destination_asset_packs (id, city_key, city_name, country_code, active_version_id, status, payload_json, published_at)
VALUES
    ('dap-cn-beijing', 'cn:beijing', '北京', 'CN', 'dav-cn-beijing-202607v1', 'PUBLISHED', '{"schemaVersion":"v1","adcode":"110000","cityCode":"010"}'::jsonb, NOW()),
    ('dap-cn-shanghai', 'cn:shanghai', '上海', 'CN', 'dav-cn-shanghai-202607v1', 'PUBLISHED', '{"schemaVersion":"v1","adcode":"310000","cityCode":"021"}'::jsonb, NOW()),
    ('dap-cn-chengdu', 'cn:chengdu', '成都', 'CN', 'dav-cn-chengdu-202607v1', 'PUBLISHED', '{"schemaVersion":"v1","adcode":"510100","cityCode":"028"}'::jsonb, NOW()),
    ('dap-cn-chongqing', 'cn:chongqing', '重庆', 'CN', 'dav-cn-chongqing-202607v1', 'PUBLISHED', '{"schemaVersion":"v1","adcode":"500000","cityCode":"023"}'::jsonb, NOW())
ON CONFLICT (id) DO UPDATE
   SET active_version_id = EXCLUDED.active_version_id,
       status = EXCLUDED.status,
       payload_json = EXCLUDED.payload_json,
       published_at = EXCLUDED.published_at,
       updated_at = NOW();

INSERT INTO destination_asset_versions (id, pack_id, version, status, payload_json, reviewed_by, reviewed_at, published_at)
VALUES
('dav-cn-beijing-202607v1', 'dap-cn-beijing', '2026.07.v1', 'PUBLISHED', $json$
{"zones":[{"id":"bj-palace","name":"天安门-故宫"},{"id":"bj-hutong","name":"什刹海-胡同"},{"id":"bj-art","name":"朝阳-艺术商业"}],"routeArchetypes":[{"id":"classic-core","name":"中轴线经典"},{"id":"hutong-evening","name":"胡同傍晚"},{"id":"art-citywalk","name":"艺术商业轻步行"}],"routeMatrix":{"bj-palace":{"bj-hutong":25,"bj-art":45},"bj-hutong":{"bj-art":40}},"rules":[{"id":"reservation","text":"热门博物馆优先安排预约"},{"id":"walking","text":"核心区按步行和地铁衔接"}],"freshness":{"sourceType":"manual_review","updatedAt":"2026-06-29"}}
$json$::jsonb, 'seed', NOW(), NOW()),
('dav-cn-shanghai-202607v1', 'dap-cn-shanghai', '2026.07.v1', 'PUBLISHED', $json$
{"zones":[{"id":"sh-bund","name":"外滩-人民广场"},{"id":"sh-french","name":"衡复-徐汇"},{"id":"sh-pudong","name":"陆家嘴-浦东"}],"routeArchetypes":[{"id":"river-classic","name":"浦江经典"},{"id":"lane-citywalk","name":"街区漫步"},{"id":"museum-food","name":"展馆与餐饮"}],"routeMatrix":{"sh-bund":{"sh-french":30,"sh-pudong":20},"sh-french":{"sh-pudong":35}},"rules":[{"id":"night-view","text":"江景点优先放在傍晚后"},{"id":"rain","text":"雨天可替换为室内展馆"}],"freshness":{"sourceType":"manual_review","updatedAt":"2026-06-29"}}
$json$::jsonb, 'seed', NOW(), NOW()),
('dav-cn-chengdu-202607v1', 'dap-cn-chengdu', '2026.07.v1', 'PUBLISHED', $json$
{"zones":[{"id":"cd-center","name":"天府广场-春熙路"},{"id":"cd-culture","name":"武侯祠-锦里"},{"id":"cd-panda","name":"熊猫基地"},{"id":"cd-west","name":"青羊-宽窄"}],"routeArchetypes":[{"id":"food-citywalk","name":"美食 citywalk"},{"id":"panda-culture","name":"熊猫与文化"},{"id":"tea-relaxed","name":"茶馆慢游"}],"routeMatrix":{"cd-center":{"cd-culture":20,"cd-west":18,"cd-panda":45},"cd-culture":{"cd-west":20}},"rules":[{"id":"panda-morning","text":"熊猫基地建议上午"},{"id":"relaxed","text":"美食主题避免单日过密"},{"id":"spicy","text":"餐饮可提示辣度选择"}],"freshness":{"sourceType":"manual_review","updatedAt":"2026-06-29"}}
$json$::jsonb, 'seed', NOW(), NOW()),
('dav-cn-chongqing-202607v1', 'dap-cn-chongqing', '2026.07.v1', 'PUBLISHED', $json$
{"zones":[{"id":"cq-yuzhong","name":"解放碑-洪崖洞"},{"id":"cq-river","name":"两江-南滨路"},{"id":"cq-old","name":"磁器口-沙坪坝"}],"routeArchetypes":[{"id":"mountain-citywalk","name":"山城步道"},{"id":"river-night","name":"两江夜景"},{"id":"old-town-food","name":"老街与火锅"}],"routeMatrix":{"cq-yuzhong":{"cq-river":25,"cq-old":45},"cq-river":{"cq-old":50}},"rules":[{"id":"stairs","text":"山城步道需控制步行强度"},{"id":"night-view","text":"夜景点优先安排傍晚后"},{"id":"traffic","text":"跨江移动预留弹性时间"}],"freshness":{"sourceType":"manual_review","updatedAt":"2026-06-29"}}
$json$::jsonb, 'seed', NOW(), NOW())
ON CONFLICT (id) DO UPDATE
   SET status = EXCLUDED.status,
       payload_json = EXCLUDED.payload_json,
       reviewed_by = EXCLUDED.reviewed_by,
       reviewed_at = EXCLUDED.reviewed_at,
       published_at = EXCLUDED.published_at,
       updated_at = NOW();

INSERT INTO destination_asset_places
    (id, pack_id, version_id, place_id, title, type, latitude, longitude, provider, provider_place_id, zone, themes_json, weights_json, payload_json, source_type, source_note, reviewed_by, reviewed_at)
VALUES
('dapl-bj-001','dap-cn-beijing','dav-cn-beijing-202607v1','cn:beijing:forbidden_city','故宫博物院','attraction',39.9163000,116.3972000,'AMAP',NULL,'天安门-故宫','["历史文化","经典必看"]','{"classic":1.0}','{"verificationStatus":"verified","manualReviewed":true,"reviewStatus":"manual_reviewed","suggestedDurationMinutes":180,"bestTimeOfDay":["morning"],"reservationRequired":true}','manual_review','pilot seed','seed',NOW()),
('dapl-bj-002','dap-cn-beijing','dav-cn-beijing-202607v1','cn:beijing:tiananmen','天安门广场','attraction',39.9055000,116.3976000,'AMAP',NULL,'天安门-故宫','["城市地标","摄影"]','{"classic":0.9}','{"verificationStatus":"verified","manualReviewed":true,"reviewStatus":"manual_reviewed","suggestedDurationMinutes":60}','manual_review','pilot seed','seed',NOW()),
('dapl-bj-003','dap-cn-beijing','dav-cn-beijing-202607v1','cn:beijing:jingshan','景山公园','attraction',39.9250000,116.3965000,'AMAP',NULL,'天安门-故宫','["登高","摄影"]','{"classic":0.8}','{"verificationStatus":"verified","manualReviewed":true,"reviewStatus":"manual_reviewed","suggestedDurationMinutes":75}','manual_review','pilot seed','seed',NOW()),
('dapl-bj-004','dap-cn-beijing','dav-cn-beijing-202607v1','cn:beijing:shichahai','什刹海','attraction',39.9372000,116.3883000,'AMAP',NULL,'什刹海-胡同','["citywalk","夜景"]','{"relaxed":0.8}','{"verificationStatus":"verified","manualReviewed":true,"reviewStatus":"manual_reviewed","suggestedDurationMinutes":120}','manual_review','pilot seed','seed',NOW()),
('dapl-bj-005','dap-cn-beijing','dav-cn-beijing-202607v1','cn:beijing:nanluoguxiang','南锣鼓巷','attraction',39.9371000,116.4039000,'AMAP',NULL,'什刹海-胡同','["街区","小吃"]','{"food":0.7}','{"verificationStatus":"verified","manualReviewed":true,"reviewStatus":"manual_reviewed","suggestedDurationMinutes":90}','manual_review','pilot seed','seed',NOW()),
('dapl-bj-006','dap-cn-beijing','dav-cn-beijing-202607v1','cn:beijing:798','798艺术区','attraction',39.9840000,116.4947000,'AMAP',NULL,'朝阳-艺术商业','["艺术","展览"]','{"art":0.9}','{"verificationStatus":"verified","manualReviewed":true,"reviewStatus":"manual_reviewed","suggestedDurationMinutes":150}','manual_review','pilot seed','seed',NOW()),
('dapl-bj-007','dap-cn-beijing','dav-cn-beijing-202607v1','cn:beijing:sanlitun','三里屯太古里','shopping',39.9335000,116.4551000,'AMAP',NULL,'朝阳-艺术商业','["购物","餐饮"]','{"food":0.6}','{"verificationStatus":"verified","manualReviewed":true,"reviewStatus":"manual_reviewed","suggestedDurationMinutes":120}','manual_review','pilot seed','seed',NOW()),
('dapl-bj-008','dap-cn-beijing','dav-cn-beijing-202607v1','cn:beijing:summer_palace','颐和园','attraction',39.9999000,116.2755000,'AMAP',NULL,'海淀-园林','["园林","历史文化"]','{"classic":0.9}','{"verificationStatus":"verified","manualReviewed":true,"reviewStatus":"manual_reviewed","suggestedDurationMinutes":180}','manual_review','pilot seed','seed',NOW()),
('dapl-sh-001','dap-cn-shanghai','dav-cn-shanghai-202607v1','cn:shanghai:bund','外滩','attraction',31.2400000,121.4900000,'AMAP',NULL,'外滩-人民广场','["城市地标","夜景"]','{"classic":1.0}','{"verificationStatus":"verified","manualReviewed":true,"reviewStatus":"manual_reviewed","suggestedDurationMinutes":90}','manual_review','pilot seed','seed',NOW()),
('dapl-sh-002','dap-cn-shanghai','dav-cn-shanghai-202607v1','cn:shanghai:yu_garden','豫园','attraction',31.2272000,121.4922000,'AMAP',NULL,'外滩-人民广场','["园林","历史文化"]','{"classic":0.8}','{"verificationStatus":"verified","manualReviewed":true,"reviewStatus":"manual_reviewed","suggestedDurationMinutes":90}','manual_review','pilot seed','seed',NOW()),
('dapl-sh-003','dap-cn-shanghai','dav-cn-shanghai-202607v1','cn:shanghai:shanghai_museum','上海博物馆','museum',31.2304000,121.4706000,'AMAP',NULL,'外滩-人民广场','["博物馆","雨天友好"]','{"culture":0.9}','{"verificationStatus":"verified","manualReviewed":true,"reviewStatus":"manual_reviewed","suggestedDurationMinutes":120}','manual_review','pilot seed','seed',NOW()),
('dapl-sh-004','dap-cn-shanghai','dav-cn-shanghai-202607v1','cn:shanghai:wukang_road','武康路','attraction',31.2091000,121.4387000,'AMAP',NULL,'衡复-徐汇','["citywalk","建筑"]','{"relaxed":0.9}','{"verificationStatus":"verified","manualReviewed":true,"reviewStatus":"manual_reviewed","suggestedDurationMinutes":120}','manual_review','pilot seed','seed',NOW()),
('dapl-sh-005','dap-cn-shanghai','dav-cn-shanghai-202607v1','cn:shanghai:tianzifang','田子坊','attraction',31.2107000,121.4680000,'AMAP',NULL,'衡复-徐汇','["街区","餐饮"]','{"food":0.7}','{"verificationStatus":"verified","manualReviewed":true,"reviewStatus":"manual_reviewed","suggestedDurationMinutes":90}','manual_review','pilot seed','seed',NOW()),
('dapl-sh-006','dap-cn-shanghai','dav-cn-shanghai-202607v1','cn:shanghai:lujiazui','陆家嘴','attraction',31.2397000,121.4998000,'AMAP',NULL,'陆家嘴-浦东','["天际线","夜景"]','{"classic":0.9}','{"verificationStatus":"verified","manualReviewed":true,"reviewStatus":"manual_reviewed","suggestedDurationMinutes":120}','manual_review','pilot seed','seed',NOW()),
('dapl-sh-007','dap-cn-shanghai','dav-cn-shanghai-202607v1','cn:shanghai:power_station','上海当代艺术博物馆','museum',31.2084000,121.4810000,'AMAP',NULL,'黄浦-展馆','["艺术","雨天友好"]','{"art":0.8}','{"verificationStatus":"verified","manualReviewed":true,"reviewStatus":"manual_reviewed","suggestedDurationMinutes":120}','manual_review','pilot seed','seed',NOW()),
('dapl-sh-008','dap-cn-shanghai','dav-cn-shanghai-202607v1','cn:shanghai:xintiandi','新天地','shopping',31.2199000,121.4752000,'AMAP',NULL,'衡复-徐汇','["餐饮","街区"]','{"food":0.8}','{"verificationStatus":"verified","manualReviewed":true,"reviewStatus":"manual_reviewed","suggestedDurationMinutes":100}','manual_review','pilot seed','seed',NOW()),
('dapl-cd-001','dap-cn-chengdu','dav-cn-chengdu-202607v1','cn:chengdu:panda_base','成都大熊猫繁育研究基地','attraction',30.7350000,104.1456000,'AMAP',NULL,'熊猫基地','["亲子","经典必看"]','{"classic":1.0}','{"verificationStatus":"verified","manualReviewed":true,"reviewStatus":"manual_reviewed","suggestedDurationMinutes":180,"bestTimeOfDay":["morning"]}','manual_review','pilot seed','seed',NOW()),
('dapl-cd-002','dap-cn-chengdu','dav-cn-chengdu-202607v1','cn:chengdu:wuhou_shrine','武侯祠','attraction',30.6459000,104.0497000,'AMAP',NULL,'武侯祠-锦里','["历史文化"]','{"culture":0.9}','{"verificationStatus":"verified","manualReviewed":true,"reviewStatus":"manual_reviewed","suggestedDurationMinutes":90}','manual_review','pilot seed','seed',NOW()),
('dapl-cd-003','dap-cn-chengdu','dav-cn-chengdu-202607v1','cn:chengdu:jinli','锦里古街','attraction',30.6466000,104.0478000,'AMAP',NULL,'武侯祠-锦里','["小吃","街区"]','{"food":0.9}','{"verificationStatus":"verified","manualReviewed":true,"reviewStatus":"manual_reviewed","suggestedDurationMinutes":90}','manual_review','pilot seed','seed',NOW()),
('dapl-cd-004','dap-cn-chengdu','dav-cn-chengdu-202607v1','cn:chengdu:kuanzhai','宽窄巷子','attraction',30.6736000,104.0552000,'AMAP',NULL,'青羊-宽窄','["citywalk","茶馆"]','{"relaxed":0.9}','{"verificationStatus":"verified","manualReviewed":true,"reviewStatus":"manual_reviewed","suggestedDurationMinutes":120}','manual_review','pilot seed','seed',NOW()),
('dapl-cd-005','dap-cn-chengdu','dav-cn-chengdu-202607v1','cn:chengdu:peoples_park','人民公园','park',30.6608000,104.0595000,'AMAP',NULL,'青羊-宽窄','["茶馆","慢游"]','{"relaxed":1.0}','{"verificationStatus":"verified","manualReviewed":true,"reviewStatus":"manual_reviewed","suggestedDurationMinutes":90}','manual_review','pilot seed','seed',NOW()),
('dapl-cd-006','dap-cn-chengdu','dav-cn-chengdu-202607v1','cn:chengdu:taikoo_li','成都远洋太古里','shopping',30.6538000,104.0809000,'AMAP',NULL,'天府广场-春熙路','["购物","餐饮"]','{"food":0.8}','{"verificationStatus":"verified","manualReviewed":true,"reviewStatus":"manual_reviewed","suggestedDurationMinutes":120}','manual_review','pilot seed','seed',NOW()),
('dapl-cd-007','dap-cn-chengdu','dav-cn-chengdu-202607v1','cn:chengdu:ifs','成都IFS','shopping',30.6572000,104.0813000,'AMAP',NULL,'天府广场-春熙路','["城市地标","餐饮"]','{"food":0.7}','{"verificationStatus":"verified","manualReviewed":true,"reviewStatus":"manual_reviewed","suggestedDurationMinutes":80}','manual_review','pilot seed','seed',NOW()),
('dapl-cd-008','dap-cn-chengdu','dav-cn-chengdu-202607v1','cn:chengdu:dufu_cottage','杜甫草堂','attraction',30.6592000,104.0276000,'AMAP',NULL,'青羊-宽窄','["历史文化","园林"]','{"culture":0.8}','{"verificationStatus":"verified","manualReviewed":true,"reviewStatus":"manual_reviewed","suggestedDurationMinutes":120}','manual_review','pilot seed','seed',NOW()),
('dapl-cq-001','dap-cn-chongqing','dav-cn-chongqing-202607v1','cn:chongqing:jiefangbei','解放碑','attraction',29.5637000,106.5750000,'AMAP',NULL,'解放碑-洪崖洞','["城市地标","餐饮"]','{"classic":0.9}','{"verificationStatus":"verified","manualReviewed":true,"reviewStatus":"manual_reviewed","suggestedDurationMinutes":80}','manual_review','pilot seed','seed',NOW()),
('dapl-cq-002','dap-cn-chongqing','dav-cn-chongqing-202607v1','cn:chongqing:hongyadong','洪崖洞','attraction',29.5663000,106.5833000,'AMAP',NULL,'解放碑-洪崖洞','["夜景","经典必看"]','{"classic":1.0}','{"verificationStatus":"verified","manualReviewed":true,"reviewStatus":"manual_reviewed","suggestedDurationMinutes":90,"bestTimeOfDay":["evening"]}','manual_review','pilot seed','seed',NOW()),
('dapl-cq-003','dap-cn-chongqing','dav-cn-chongqing-202607v1','cn:chongqing:shancheng_trail','山城步道','attraction',29.5549000,106.5625000,'AMAP',NULL,'解放碑-洪崖洞','["citywalk","摄影"]','{"walking":0.9}','{"verificationStatus":"verified","manualReviewed":true,"reviewStatus":"manual_reviewed","suggestedDurationMinutes":120}','manual_review','pilot seed','seed',NOW()),
('dapl-cq-004','dap-cn-chongqing','dav-cn-chongqing-202607v1','cn:chongqing:liziba','李子坝单轨穿楼观景平台','attraction',29.5528000,106.5334000,'AMAP',NULL,'两江-南滨路','["城市地标","摄影"]','{"classic":0.8}','{"verificationStatus":"verified","manualReviewed":true,"reviewStatus":"manual_reviewed","suggestedDurationMinutes":45}','manual_review','pilot seed','seed',NOW()),
('dapl-cq-005','dap-cn-chongqing','dav-cn-chongqing-202607v1','cn:chongqing:nanbin_road','南滨路','attraction',29.5410000,106.5868000,'AMAP',NULL,'两江-南滨路','["江景","夜景"]','{"relaxed":0.8}','{"verificationStatus":"verified","manualReviewed":true,"reviewStatus":"manual_reviewed","suggestedDurationMinutes":90}','manual_review','pilot seed','seed',NOW()),
('dapl-cq-006','dap-cn-chongqing','dav-cn-chongqing-202607v1','cn:chongqing:ciqikou','磁器口古镇','attraction',29.5812000,106.4497000,'AMAP',NULL,'磁器口-沙坪坝','["老街","小吃"]','{"food":0.8}','{"verificationStatus":"verified","manualReviewed":true,"reviewStatus":"manual_reviewed","suggestedDurationMinutes":120}','manual_review','pilot seed','seed',NOW()),
('dapl-cq-007','dap-cn-chongqing','dav-cn-chongqing-202607v1','cn:chongqing:three_gorges_museum','重庆中国三峡博物馆','museum',29.5630000,106.5501000,'AMAP',NULL,'解放碑-洪崖洞','["博物馆","雨天友好"]','{"culture":0.8}','{"verificationStatus":"verified","manualReviewed":true,"reviewStatus":"manual_reviewed","suggestedDurationMinutes":120}','manual_review','pilot seed','seed',NOW()),
('dapl-cq-008','dap-cn-chongqing','dav-cn-chongqing-202607v1','cn:chongqing:longmenhao','龙门浩老街','attraction',29.5521000,106.5927000,'AMAP',NULL,'两江-南滨路','["老街","江景"]','{"relaxed":0.8}','{"verificationStatus":"verified","manualReviewed":true,"reviewStatus":"manual_reviewed","suggestedDurationMinutes":100}','manual_review','pilot seed','seed',NOW())
ON CONFLICT (id) DO UPDATE
   SET title = EXCLUDED.title,
       type = EXCLUDED.type,
       latitude = EXCLUDED.latitude,
       longitude = EXCLUDED.longitude,
       provider = EXCLUDED.provider,
       provider_place_id = EXCLUDED.provider_place_id,
       zone = EXCLUDED.zone,
       themes_json = EXCLUDED.themes_json,
       weights_json = EXCLUDED.weights_json,
       payload_json = EXCLUDED.payload_json,
       source_type = EXCLUDED.source_type,
       source_note = EXCLUDED.source_note,
       reviewed_by = EXCLUDED.reviewed_by,
       reviewed_at = EXCLUDED.reviewed_at,
       updated_at = NOW();
