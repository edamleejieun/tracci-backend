-- AI 旅行助手（tracci-assistant 运行时）的会话绑定表。
-- 运行时持有画布与方案卡真值；本表只保存「谁拥有哪个会话、绑了哪个旅程、有哪些权限范围」，
-- 供产品 API 做归属校验与按旅程反查会话。刻意不复用 ai_workflow_bindings：
-- 旧 Workflow 退场时只删它自己那条链路，助手不受影响。

CREATE TABLE IF NOT EXISTS ai_assistant_sessions (
    id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL UNIQUE,
    user_id VARCHAR(64) NOT NULL,
    trip_id VARCHAR(64),
    kind VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    auth_scope TEXT NOT NULL DEFAULT '',
    canvas_revision INTEGER NOT NULL DEFAULT 0,
    itinerary_version VARCHAR(128),
    pending_proposal_id VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_assistant_sessions_user
    ON ai_assistant_sessions(user_id, created_at DESC) WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_ai_assistant_sessions_trip
    ON ai_assistant_sessions(trip_id, created_at DESC)
    WHERE deleted_at IS NULL AND trip_id IS NOT NULL;

-- 同一用户同时只有一个活跃 GLOBAL 会话、一个活跃 TRIP 会话（契约 §3.1）。
CREATE UNIQUE INDEX IF NOT EXISTS uniq_ai_assistant_sessions_active_global
    ON ai_assistant_sessions(user_id)
    WHERE deleted_at IS NULL AND status = 'ACTIVE' AND trip_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uniq_ai_assistant_sessions_active_trip
    ON ai_assistant_sessions(user_id, trip_id)
    WHERE deleted_at IS NULL AND status = 'ACTIVE' AND trip_id IS NOT NULL;
