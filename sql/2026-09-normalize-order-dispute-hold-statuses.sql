-- Chuẩn hóa trạng thái order/payment/dispute/hold và bỏ pre_order_items.status.
-- Chạy cùng phiên bản backend/frontend hỗ trợ các enum mới.
BEGIN;

ALTER TABLE orders ADD COLUMN IF NOT EXISTS cancelled_by VARCHAR(20);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cancellation_code VARCHAR(40);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cancellation_reason TEXT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ;

ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check;
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_payment_status_check;
ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_orders_cancellation_metadata;
ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_orders_cancellation_actor_code;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM orders WHERE payment_status = 'UNPAID') THEN
        RAISE EXCEPTION 'Có order UNPAID; phải đối soát thanh toán thủ công trước khi migration';
    END IF;
    IF EXISTS (
        SELECT 1 FROM orders
        WHERE status = 'PENDING' AND delivery_type <> 'PRE_ORDER'
    ) THEN
        RAISE EXCEPTION 'Có order INSTANT/PENDING; phải đối soát giao hàng thủ công trước khi migration';
    END IF;
END $$;

UPDATE orders
SET cancelled_by = CASE status
        WHEN 'CANCELLED' THEN 'BUYER'
        WHEN 'CANCELLED_BY_BUYER' THEN 'BUYER'
        WHEN 'CANCELLED_BY_SELLER' THEN 'SELLER'
        WHEN 'CANCELLED_BY_SYSTEM' THEN 'SYSTEM'
        ELSE cancelled_by
    END,
    cancellation_code = CASE status
        WHEN 'CANCELLED' THEN 'BUYER_REQUEST'
        WHEN 'CANCELLED_BY_BUYER' THEN 'BUYER_REQUEST'
        WHEN 'CANCELLED_BY_SELLER' THEN 'SELLER_CANCELLED'
        WHEN 'CANCELLED_BY_SYSTEM' THEN CASE
            WHEN approved_at IS NULL THEN 'SELLER_ACCEPTANCE_TIMEOUT'
            ELSE 'SELLER_PROCESSING_TIMEOUT'
        END
        ELSE cancellation_code
    END,
    cancellation_reason = COALESCE(
        cancellation_reason,
        rejection_reason,
        CASE status
            WHEN 'CANCELLED_BY_SYSTEM' THEN 'Hệ thống tự hủy đơn đặt hàng quá hạn 24 giờ'
            WHEN 'CANCELLED_BY_SELLER' THEN 'Người bán hủy đơn'
            ELSE 'Người mua hủy đơn'
        END
    ),
    cancelled_at = COALESCE(cancelled_at, rejected_at, updated_at, NOW())
WHERE status IN ('CANCELLED','CANCELLED_BY_BUYER','CANCELLED_BY_SELLER','CANCELLED_BY_SYSTEM');

UPDATE orders
SET status = CASE status
        WHEN 'PENDING' THEN 'WAITING_SELLER_ACCEPTANCE'
        WHEN 'WAITING_APPROVAL' THEN 'WAITING_SELLER_ACCEPTANCE'
        WHEN 'APPROVED' THEN 'PROCESSING'
        WHEN 'REFUNDED' THEN 'DELIVERED'
        WHEN 'CANCELLED_BY_BUYER' THEN 'CANCELLED'
        WHEN 'CANCELLED_BY_SELLER' THEN 'CANCELLED'
        WHEN 'CANCELLED_BY_SYSTEM' THEN 'CANCELLED'
        ELSE status
    END,
    payment_status = CASE payment_status
        WHEN 'PARTIAL_REFUND' THEN 'PARTIALLY_REFUNDED'
        ELSE payment_status
    END;

UPDATE orders
SET cancelled_by = NULL,
    cancellation_code = NULL,
    cancellation_reason = NULL,
    cancelled_at = NULL
WHERE status <> 'CANCELLED';

UPDATE order_status_logs
SET from_status = CASE from_status
        WHEN 'PENDING' THEN 'WAITING_SELLER_ACCEPTANCE'
        WHEN 'WAITING_APPROVAL' THEN 'WAITING_SELLER_ACCEPTANCE'
        WHEN 'APPROVED' THEN 'PROCESSING'
        WHEN 'REFUNDED' THEN 'DELIVERED'
        WHEN 'CANCELLED_BY_BUYER' THEN 'CANCELLED'
        WHEN 'CANCELLED_BY_SELLER' THEN 'CANCELLED'
        WHEN 'CANCELLED_BY_SYSTEM' THEN 'CANCELLED'
        ELSE from_status
    END,
    to_status = CASE to_status
        WHEN 'PENDING' THEN 'WAITING_SELLER_ACCEPTANCE'
        WHEN 'WAITING_APPROVAL' THEN 'WAITING_SELLER_ACCEPTANCE'
        WHEN 'APPROVED' THEN 'PROCESSING'
        WHEN 'REFUNDED' THEN 'DELIVERED'
        WHEN 'CANCELLED_BY_BUYER' THEN 'CANCELLED'
        WHEN 'CANCELLED_BY_SELLER' THEN 'CANCELLED'
        WHEN 'CANCELLED_BY_SYSTEM' THEN 'CANCELLED'
        ELSE to_status
    END;

ALTER TABLE orders ALTER COLUMN status DROP DEFAULT;
ALTER TABLE orders ALTER COLUMN payment_status DROP DEFAULT;
ALTER TABLE orders ADD CONSTRAINT orders_status_check CHECK (status IN (
    'WAITING_SELLER_ACCEPTANCE','PROCESSING','DELIVERED','REJECTED','CANCELLED'
));
ALTER TABLE orders ADD CONSTRAINT orders_payment_status_check CHECK (
    payment_status IN ('PAID','PARTIALLY_REFUNDED','REFUNDED')
);
ALTER TABLE orders ADD CONSTRAINT chk_orders_cancellation_metadata CHECK (
    (status = 'CANCELLED'
        AND cancelled_by IS NOT NULL
        AND cancellation_code IS NOT NULL
        AND cancelled_at IS NOT NULL)
    OR
    (status <> 'CANCELLED'
        AND cancelled_by IS NULL
        AND cancellation_code IS NULL
        AND cancellation_reason IS NULL
        AND cancelled_at IS NULL)
);
ALTER TABLE orders ADD CONSTRAINT chk_orders_cancellation_actor_code CHECK (
    status <> 'CANCELLED'
    OR (cancelled_by = 'BUYER' AND cancellation_code = 'BUYER_REQUEST')
    OR (cancelled_by = 'SELLER' AND cancellation_code = 'SELLER_CANCELLED')
    OR (cancelled_by = 'SYSTEM' AND cancellation_code IN (
        'SELLER_ACCEPTANCE_TIMEOUT','SELLER_PROCESSING_TIMEOUT'
    ))
    OR (cancelled_by = 'ADMIN' AND cancellation_code = 'ADMIN_CANCELLED')
);

ALTER TABLE pre_order_items DROP COLUMN IF EXISTS status;

ALTER TABLE hold_releases DROP CONSTRAINT IF EXISTS hold_releases_status_check;
UPDATE hold_releases
SET status = 'FROZEN'
WHERE status IN ('COMPLAINED','WARRANTY_IN_PROGRESS','WAITING_BUYER_CONFIRMATION','DISPUTED');
ALTER TABLE hold_releases ADD CONSTRAINT hold_releases_status_check
    CHECK (status IN ('HOLDING','FROZEN','RELEASED','REFUNDED'));

ALTER TABLE order_disputes ADD COLUMN IF NOT EXISTS resolution VARCHAR(40);
ALTER TABLE order_disputes ADD COLUMN IF NOT EXISTS resolved_by VARCHAR(20);
ALTER TABLE order_disputes ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(40);
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'order_disputes'
          AND column_name = 'admin_note'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'order_disputes'
          AND column_name = 'resolution_note'
    ) THEN
        ALTER TABLE order_disputes RENAME COLUMN admin_note TO resolution_note;
    END IF;
END $$;
ALTER TABLE order_disputes ADD COLUMN IF NOT EXISTS resolution_note TEXT;

ALTER TABLE order_disputes DROP CONSTRAINT IF EXISTS order_disputes_status_check;
ALTER TABLE order_disputes DROP CONSTRAINT IF EXISTS order_disputes_closed_reason_check;
ALTER TABLE order_disputes DROP CONSTRAINT IF EXISTS chk_order_disputes_resolution;

UPDATE order_disputes
SET resolution = CASE
        WHEN status = 'BUYER_WIN' THEN 'BUYER_WIN'
        WHEN status = 'SELLER_WIN' THEN 'SELLER_WIN'
        WHEN status = 'CLOSED' AND closed_reason = 'BUYER_WITHDREW' THEN 'BUYER_WITHDREW'
        WHEN status = 'CLOSED' AND closed_reason = 'BUYER_CONFIRMATION_TIMEOUT' THEN 'BUYER_CONFIRMATION_TIMEOUT'
        WHEN status = 'CLOSED' THEN 'WARRANTY_ACCEPTED'
        ELSE resolution
    END,
    resolved_by = CASE
        WHEN status IN ('BUYER_WIN','SELLER_WIN') AND resolver_id IS NOT NULL THEN 'ADMIN'
        WHEN status IN ('BUYER_WIN','SELLER_WIN') THEN 'SYSTEM'
        WHEN status = 'CLOSED' AND closed_reason = 'BUYER_CONFIRMATION_TIMEOUT' THEN 'SYSTEM'
        WHEN status = 'CLOSED' THEN 'BUYER'
        ELSE resolved_by
    END,
    resolved_at = CASE
        WHEN status IN ('BUYER_WIN','SELLER_WIN','CLOSED')
            THEN COALESCE(resolved_at, updated_at, NOW())
        ELSE resolved_at
    END,
    resolver_id = CASE
        WHEN status IN ('BUYER_WIN','SELLER_WIN') AND resolver_id IS NOT NULL THEN resolver_id
        WHEN status = 'CLOSED' THEN NULL
        ELSE resolver_id
    END;

UPDATE order_disputes
SET status = CASE status
        WHEN 'PROCESSING' THEN 'ADMIN_REVIEW'
        WHEN 'BUYER_WIN' THEN 'RESOLVED'
        WHEN 'SELLER_WIN' THEN 'RESOLVED'
        WHEN 'CLOSED' THEN 'RESOLVED'
        ELSE status
    END;

-- Metadata phán quyết chỉ được giữ trên tranh chấp đã kết thúc.
UPDATE order_disputes
SET resolution = NULL,
    resolved_by = NULL,
    resolver_id = NULL,
    resolved_at = NULL
WHERE status <> 'RESOLVED';

ALTER TABLE order_disputes ADD CONSTRAINT order_disputes_status_check CHECK (status IN (
    'OPEN','WARRANTY_IN_PROGRESS','WAITING_BUYER_CONFIRMATION','ADMIN_REVIEW','RESOLVED'
));
ALTER TABLE order_disputes DROP CONSTRAINT IF EXISTS order_disputes_resolution_check;
ALTER TABLE order_disputes DROP CONSTRAINT IF EXISTS order_disputes_resolved_by_check;
ALTER TABLE order_disputes ADD CONSTRAINT order_disputes_resolution_check CHECK (
    resolution IS NULL OR resolution IN (
        'BUYER_WIN','SELLER_WIN','BUYER_WITHDREW',
        'WARRANTY_ACCEPTED','BUYER_CONFIRMATION_TIMEOUT'
    )
);
ALTER TABLE order_disputes ADD CONSTRAINT order_disputes_resolved_by_check CHECK (
    resolved_by IS NULL OR resolved_by IN ('BUYER','ADMIN','SYSTEM')
);
ALTER TABLE order_disputes ADD CONSTRAINT chk_order_disputes_resolution CHECK (
    (status = 'RESOLVED'
        AND resolution IS NOT NULL
        AND resolved_by IS NOT NULL
        AND resolved_at IS NOT NULL
        AND ((resolved_by = 'ADMIN' AND resolver_id IS NOT NULL)
            OR (resolved_by IN ('BUYER','SYSTEM') AND resolver_id IS NULL)))
    OR
    (status <> 'RESOLVED'
        AND resolution IS NULL
        AND resolved_by IS NULL
        AND resolver_id IS NULL
        AND resolved_at IS NULL)
);
ALTER TABLE order_disputes DROP COLUMN IF EXISTS closed_reason;

DROP INDEX IF EXISTS idx_orders_approval_deadline;
CREATE INDEX idx_orders_approval_deadline
    ON orders(approval_deadline_at, id)
    WHERE status = 'WAITING_SELLER_ACCEPTANCE';

COMMIT;
