-- 行程节点类型扩展字段（Phase 1）
-- transport / hotel 等类型专属内容以 JSONB 存储，旧客户端不传 details 时保持 {}。

ALTER TABLE itinerary_nodes
    ADD COLUMN IF NOT EXISTS details_json JSONB NOT NULL DEFAULT '{}'::jsonb;

COMMENT ON COLUMN itinerary_nodes.details_json IS
    '节点类型扩展 JSON：transport(mode+工具字段) / hotel(确认号与入住退房时间等)；空对象表示无扩展内容';
