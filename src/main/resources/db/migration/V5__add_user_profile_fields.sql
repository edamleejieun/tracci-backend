-- V5: 用户资料扩展字段（旅行宣言、性别、出生年月）
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS signature  VARCHAR(100),
    ADD COLUMN IF NOT EXISTS gender     VARCHAR(20),
    ADD COLUMN IF NOT EXISTS birth_date VARCHAR(7);
