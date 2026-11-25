-- 开支可选挂到行程节点或住宿 Stay Group。节点删除时由业务层置空，不建 FK。
ALTER TABLE expenses
    ADD COLUMN IF NOT EXISTS linked_node_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS linked_stay_group_id VARCHAR(36);

CREATE INDEX IF NOT EXISTS idx_expenses_trip_linked_node
    ON expenses (trip_id, linked_node_id)
    WHERE deleted_at IS NULL AND linked_node_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_expenses_trip_linked_stay
    ON expenses (trip_id, linked_stay_group_id)
    WHERE deleted_at IS NULL AND linked_stay_group_id IS NOT NULL;
