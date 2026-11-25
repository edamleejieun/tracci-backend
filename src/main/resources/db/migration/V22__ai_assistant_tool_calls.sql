-- 助手内部写工具幂等：同一会话同一 Idempotency-Key 只落库一次。
-- 运行时超时重试 apply_expense_draft / apply_journal_draft / apply_itinerary_canvas 时回放首次结果。

CREATE TABLE IF NOT EXISTS ai_assistant_tool_calls (
    id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    response_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uniq_ai_assistant_tool_calls_idempotency
    ON ai_assistant_tool_calls (session_id, idempotency_key)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_ai_assistant_tool_calls_session
    ON ai_assistant_tool_calls (session_id, created_at DESC)
    WHERE deleted_at IS NULL;
