-- Cho phép Seller chủ động hoàn 100% cho OrderItem đang khiếu nại và lưu đúng
-- actor/kết quả thay vì giả lập thành một phán quyết của Admin hoặc System.
ALTER TABLE order_disputes DROP CONSTRAINT IF EXISTS chk_order_disputes_resolution;
ALTER TABLE order_disputes DROP CONSTRAINT IF EXISTS order_disputes_resolution_check;
ALTER TABLE order_disputes DROP CONSTRAINT IF EXISTS order_disputes_resolved_by_check;

ALTER TABLE order_disputes ADD CONSTRAINT order_disputes_resolution_check CHECK (
    resolution IS NULL OR resolution IN (
        'BUYER_WIN','SELLER_WIN','SELLER_REFUND','BUYER_WITHDREW',
        'WARRANTY_ACCEPTED','BUYER_CONFIRMATION_TIMEOUT'
    )
);

ALTER TABLE order_disputes ADD CONSTRAINT order_disputes_resolved_by_check CHECK (
    resolved_by IS NULL OR resolved_by IN ('BUYER','SELLER','ADMIN','SYSTEM')
);

ALTER TABLE order_disputes ADD CONSTRAINT chk_order_disputes_resolution CHECK (
    (status = 'RESOLVED'
        AND resolution IS NOT NULL
        AND resolved_by IS NOT NULL
        AND resolved_at IS NOT NULL
        AND ((resolved_by = 'ADMIN' AND resolver_id IS NOT NULL
                AND resolution_note IS NOT NULL AND BTRIM(resolution_note) <> '')
            OR (resolved_by = 'SELLER' AND resolver_id IS NOT NULL
                AND resolution = 'SELLER_REFUND')
            OR (resolved_by IN ('BUYER','SYSTEM') AND resolver_id IS NULL)))
    OR
    (status <> 'RESOLVED'
        AND resolution IS NULL
        AND resolved_by IS NULL
        AND resolver_id IS NULL
        AND resolved_at IS NULL)
);
