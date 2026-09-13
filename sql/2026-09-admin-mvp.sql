BEGIN;

-- Admin ẩn sản phẩm bằng BANNED; khác INACTIVE do seller tự tạm dừng.
ALTER TABLE products DROP CONSTRAINT IF EXISTS products_status_check;
ALTER TABLE products ADD CONSTRAINT products_status_check
    CHECK (status IN ('ACTIVE','INACTIVE','BANNED','DELETED'));

CREATE INDEX IF NOT EXISTS idx_products_admin_status_created
    ON products(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_users_admin_status_created
    ON users(status, created_at DESC);

-- Audit tối thiểu cho các thao tác quản trị nhạy cảm.
CREATE TABLE IF NOT EXISTS audit_logs (
    id          BIGSERIAL    PRIMARY KEY,
    actor_id    BIGINT       NOT NULL REFERENCES users(id),
    actor_role  VARCHAR(50)  NOT NULL,
    action      VARCHAR(100) NOT NULL,
    target_type VARCHAR(50)  NOT NULL,
    target_id   BIGINT       NOT NULL,
    old_value   JSONB,
    new_value   JSONB,
    reason      TEXT,
    ip_address  VARCHAR(45),
    user_agent  TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_audit_logs_target
    ON audit_logs(target_type, target_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_actor_created
    ON audit_logs(actor_id, created_at DESC);

COMMIT;
