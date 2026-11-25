-- 行程节点 title 改为可选：空字符串表示"无自定义标题"，展示层用地点名称兜底。
-- 保留 NOT NULL 约束，使用 DEFAULT '' 作为"无标题"哨兵，避免改动消费方 NULL 处理逻辑。

ALTER TABLE itinerary_nodes
    ALTER COLUMN title SET DEFAULT '';

-- 存量数据无需迁移（已有 title 均非空）。
