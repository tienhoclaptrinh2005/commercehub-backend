ALTER TABLE vouchers ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE vouchers DROP CONSTRAINT IF EXISTS chk_voucher_discount_rule;
ALTER TABLE vouchers DROP CONSTRAINT IF EXISTS chk_voucher_amounts;
ALTER TABLE vouchers DROP CONSTRAINT IF EXISTS chk_voucher_usage_count;
ALTER TABLE vouchers ADD CONSTRAINT chk_voucher_discount_rule CHECK (
    (discount_type = 'PERCENT' AND discount_value < 100)
    OR (discount_type = 'FIXED' AND max_discount_amount IS NULL)
);
ALTER TABLE vouchers ADD CONSTRAINT chk_voucher_amounts CHECK (
    min_order_amount >= 0
    AND (max_discount_amount IS NULL OR max_discount_amount > 0)
);
ALTER TABLE vouchers ADD CONSTRAINT chk_voucher_usage_count CHECK (
    usage_limit > 0 AND used_count >= 0 AND used_count <= usage_limit
);

ALTER TABLE voucher_usages ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'APPLIED';
ALTER TABLE voucher_usages ADD COLUMN IF NOT EXISTS released_at TIMESTAMPTZ;
ALTER TABLE voucher_usages DROP CONSTRAINT IF EXISTS uq_voucher_user;
ALTER TABLE voucher_usages DROP CONSTRAINT IF EXISTS voucher_usages_status_check;
ALTER TABLE voucher_usages DROP CONSTRAINT IF EXISTS chk_voucher_usage_release;
ALTER TABLE voucher_usages ADD CONSTRAINT voucher_usages_status_check
    CHECK (status IN ('APPLIED','RELEASED'));
ALTER TABLE voucher_usages ADD CONSTRAINT chk_voucher_usage_release CHECK (
    (status = 'APPLIED' AND released_at IS NULL)
    OR (status = 'RELEASED' AND released_at IS NOT NULL)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_voucher_user_active
    ON voucher_usages(voucher_id, user_id) WHERE status = 'APPLIED';

ALTER TABLE order_items ADD COLUMN IF NOT EXISTS line_subtotal NUMERIC(18,2);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS voucher_discount NUMERIC(18,2) NOT NULL DEFAULT 0;
UPDATE order_items
SET line_subtotal = line_total + COALESCE(voucher_discount, 0)
WHERE line_subtotal IS NULL;
ALTER TABLE order_items ALTER COLUMN line_subtotal SET NOT NULL;
ALTER TABLE order_items DROP CONSTRAINT IF EXISTS chk_order_items_voucher_amounts;
ALTER TABLE order_items ADD CONSTRAINT chk_order_items_voucher_amounts CHECK (
    line_subtotal > 0
    AND voucher_discount >= 0
    AND line_total > 0
    AND line_total = line_subtotal - voucher_discount
);

ALTER TABLE orders ADD COLUMN IF NOT EXISTS voucher_id BIGINT REFERENCES vouchers(id);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS voucher_discount NUMERIC(18,2) NOT NULL DEFAULT 0;
ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_orders_voucher_amounts;
ALTER TABLE orders ADD CONSTRAINT chk_orders_voucher_amounts CHECK (
    subtotal_amount > 0
    AND voucher_discount >= 0
    AND total_amount > 0
    AND total_amount = subtotal_amount - voucher_discount
);
