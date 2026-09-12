-- Lưu mốc seller đã xem từng nhóm badge trong Seller Center.
-- Chạy một lần trên database hiện có trước khi khởi động backend mới.
BEGIN;

CREATE TABLE IF NOT EXISTS seller_notification_reads (
    seller_id               BIGINT      PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    instant_orders_read_at  TIMESTAMPTZ NOT NULL DEFAULT TIMESTAMPTZ 'epoch',
    pre_orders_read_at      TIMESTAMPTZ NOT NULL DEFAULT TIMESTAMPTZ 'epoch',
    disputes_read_at        TIMESTAMPTZ NOT NULL DEFAULT TIMESTAMPTZ 'epoch',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMIT;
