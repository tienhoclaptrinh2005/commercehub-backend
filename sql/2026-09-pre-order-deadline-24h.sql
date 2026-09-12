-- Chuẩn hóa chính sách đơn đặt hàng:
-- - Shop có 24 giờ để nhận đơn.
-- - Sau khi nhận, shop có 24 giờ để hoàn thành.
BEGIN;

UPDATE pre_order_configs
SET max_processing_hours = 24,
    updated_at = NOW()
WHERE max_processing_hours <> 24;

UPDATE orders
SET approval_deadline_at = placed_at + INTERVAL '24 hours',
    updated_at = NOW()
WHERE delivery_type = 'PRE_ORDER'
  AND status = 'WAITING_APPROVAL'
  AND placed_at IS NOT NULL
  AND approval_deadline_at IS DISTINCT FROM placed_at + INTERVAL '24 hours';

UPDATE orders
SET processing_deadline_at = approved_at + INTERVAL '24 hours',
    updated_at = NOW()
WHERE delivery_type = 'PRE_ORDER'
  AND status = 'PROCESSING'
  AND approved_at IS NOT NULL
  AND processing_deadline_at IS DISTINCT FROM approved_at + INTERVAL '24 hours';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_pre_order_processing_hours_24'
          AND conrelid = 'pre_order_configs'::regclass
    ) THEN
        ALTER TABLE pre_order_configs
            ADD CONSTRAINT chk_pre_order_processing_hours_24
            CHECK (max_processing_hours = 24);
    END IF;
END $$;

COMMIT;
