-- 自定义开支分类：每趟旅行可创建最多 20 个自定义分类
-- expenses.category 字段存 custom:<id> 表示自定义分类
CREATE TABLE expense_categories (
    id         VARCHAR(36) PRIMARY KEY,
    trip_id    VARCHAR(36) NOT NULL,
    name       VARCHAR(32) NOT NULL,
    sort_order INT DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT fk_expense_categories_trip FOREIGN KEY (trip_id) REFERENCES trips(id)
);
CREATE INDEX idx_expense_categories_trip ON expense_categories(trip_id, deleted_at, sort_order);
