-- =====================================================================
-- MIGRATION: Money-flow fixes (2026-08)
-- Chạy script này TRƯỚC khi khởi động phiên bản code mới
-- (ứng dụng dùng ddl-auto: validate nên cột mới phải tồn tại sẵn trong DB).
--
-- Script an toàn chạy lại nhiều lần (idempotent) trên PostgreSQL.
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- 1. ORDER_ITEMS: snapshot phí sàn chốt tại thời điểm buyer thanh toán
-- ---------------------------------------------------------------------
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS fee_config_id     BIGINT;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS fee_rate_snapshot NUMERIC(5,4);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS fee_amount        NUMERIC(18,2);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS seller_net_amount NUMERIC(18,2);

-- ---------------------------------------------------------------------
-- 2. ORDERS: idempotency key chống double-submit checkout
-- ---------------------------------------------------------------------
ALTER TABLE orders ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS processing_deadline_at TIMESTAMPTZ;

-- Cột entity đã khai báo nhưng DB chưa có (đồng bộ schema với code)
ALTER TABLE pre_order_items ADD COLUMN IF NOT EXISTS seller_notes TEXT;
CREATE INDEX IF NOT EXISTS idx_orders_user_idem_key
    ON orders (user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- ---------------------------------------------------------------------
-- 3. SHOP_FEE_SUMMARIES: bỏ cột total_waived (đã xóa chức năng miễn phí sàn)
--    + unique (shop_id, period_year, period_month) chống duplicate row
-- ---------------------------------------------------------------------
ALTER TABLE shop_fee_summaries DROP COLUMN IF EXISTS total_waived;
CREATE UNIQUE INDEX IF NOT EXISTS uq_shop_fee_summary_period
    ON shop_fee_summaries (shop_id, period_year, period_month);

-- ---------------------------------------------------------------------
-- 4. PLATFORM_FEE_LEDGERS: bỏ cột waived_at + đảm bảo 1 order_item = 1 ledger
--    SET NOT NULL chỉ chạy khi không còn dòng NULL (dữ liệu legacy per-order).
-- ---------------------------------------------------------------------
ALTER TABLE platform_fee_ledgers DROP COLUMN IF EXISTS waived_at;
CREATE UNIQUE INDEX IF NOT EXISTS uq_fee_ledger_order_item
    ON platform_fee_ledgers (order_item_id);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM platform_fee_ledgers WHERE order_item_id IS NULL) THEN
        ALTER TABLE platform_fee_ledgers ALTER COLUMN order_item_id SET NOT NULL;
    ELSE
        RAISE NOTICE 'Còn ledger có order_item_id NULL — bỏ qua SET NOT NULL, cần xử lý dữ liệu legacy trước.';
    END IF;
END $$;

-- ---------------------------------------------------------------------
-- 5. HOLD_RELEASES: 1 order_item = 1 hold release
--    + các cột phục vụ luồng khiếu nại/bảo hành (entity đã khai báo)
-- ---------------------------------------------------------------------
ALTER TABLE hold_releases ADD COLUMN IF NOT EXISTS complaint_reason       TEXT;
ALTER TABLE hold_releases ADD COLUMN IF NOT EXISTS complained_at          TIMESTAMPTZ;
ALTER TABLE hold_releases ADD COLUMN IF NOT EXISTS remaining_hold_seconds BIGINT;
ALTER TABLE hold_releases ADD COLUMN IF NOT EXISTS warranty_started_at    TIMESTAMPTZ;

-- Mô hình mới: 1 Order có nhiều HoldRelease (mỗi item 1 bản ghi)
-- → thêm order_id (backfill từ order_items nếu có dữ liệu cũ),
--   order_item_id chuyển thành nullable (unique khi có giá trị).
ALTER TABLE hold_releases ADD COLUMN IF NOT EXISTS order_id BIGINT;
UPDATE hold_releases hr
SET order_id = (SELECT oi.order_id FROM order_items oi WHERE oi.id = hr.order_item_id)
WHERE hr.order_id IS NULL AND hr.order_item_id IS NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM hold_releases WHERE order_id IS NULL) THEN
        ALTER TABLE hold_releases ALTER COLUMN order_id SET NOT NULL;
    ELSE
        RAISE NOTICE 'Còn hold_releases chưa backfill được order_id — cần xử lý thủ công.';
    END IF;
END $$;

ALTER TABLE hold_releases ALTER COLUMN order_item_id DROP NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_hold_release_order_item
    ON hold_releases (order_item_id)
    WHERE order_item_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- 6. PLATFORM_FEE_CONFIGS: chỉ cho phép ĐÚNG 1 config đang active
-- ---------------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS uq_fee_config_active
    ON platform_fee_configs (is_active)
    WHERE is_active = true;

-- ---------------------------------------------------------------------
-- 7. WALLETS: chỉ cho phép ĐÚNG 1 ví platform
--    (Ví platform được app tự tạo lúc khởi động nếu chưa có —
--     xem PlatformWalletInitializer)
-- ---------------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS uq_wallets_platform
    ON wallets (is_platform)
    WHERE is_platform = true;

-- ---------------------------------------------------------------------
-- 8. SHEDLOCK: bảng lock cho scheduler đa instance
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

-- ---------------------------------------------------------------------
-- 9. WALLET_TRANSACTIONS: index phục vụ đối soát (reconciliation).
--    LƯU Ý: bảng này là PARTITIONED theo created_at nên PostgreSQL không
--    cho phép unique index toàn cục thiếu partition key. Việc chống ghi
--    trùng được đảm bảo ở tầng ứng dụng (pessimistic lock + status guard).
--    Query đối soát trùng định kỳ:
--      SELECT wallet_id, transaction_type, reference_type, reference_id, COUNT(*)
--      FROM wallet_transactions WHERE reference_id IS NOT NULL
--      GROUP BY 1,2,3,4 HAVING COUNT(*) > 1;
-- ---------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_wallet_tx_reference
    ON wallet_transactions (wallet_id, transaction_type, reference_type, reference_id)
    WHERE reference_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- 10. SEED FEE CONFIG MẶC ĐỊNH (4%) nếu chưa có config nào active.
--     Không có config active thì mọi checkout sẽ fail (FEE_CONFIG_NOT_FOUND)
--     vì phí sàn là BẮT BUỘC với mọi giao dịch.
--     created_by tham chiếu FK users → lấy 1 admin (fallback: user đầu tiên).
--     Nếu DB chưa có user nào, seed bị bỏ qua — admin cần tạo config qua API.
-- ---------------------------------------------------------------------
INSERT INTO platform_fee_configs
    (fee_rate, min_fee_amount, max_fee_amount, description, is_active, effective_from, created_by, created_at)
SELECT 0.0400, 0, NULL, 'Phí sàn mặc định 4% (seed tự động)', true, NOW(), seed_user.id, NOW()
FROM (
    SELECT u.id FROM users u
    ORDER BY (EXISTS (
        SELECT 1 FROM user_roles ur JOIN roles r ON r.id = ur.role_id
        WHERE ur.user_id = u.id AND r.name = 'ADMIN'
    )) DESC, u.id ASC
    LIMIT 1
) seed_user
WHERE NOT EXISTS (SELECT 1 FROM platform_fee_configs WHERE is_active = true);

COMMIT;
