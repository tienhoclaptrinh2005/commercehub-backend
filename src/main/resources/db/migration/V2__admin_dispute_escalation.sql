ALTER TABLE order_disputes ADD COLUMN IF NOT EXISTS escalated_at TIMESTAMPTZ;
ALTER TABLE order_disputes ADD COLUMN IF NOT EXISTS escalated_by VARCHAR(20);
ALTER TABLE order_disputes ADD COLUMN IF NOT EXISTS escalation_reason VARCHAR(200);

ALTER TABLE order_disputes DROP CONSTRAINT IF EXISTS order_disputes_escalated_by_check;
ALTER TABLE order_disputes DROP CONSTRAINT IF EXISTS chk_order_disputes_escalation;
ALTER TABLE order_disputes DROP CONSTRAINT IF EXISTS chk_order_disputes_resolution;

-- Các hồ sơ cũ không có actor/lý do chuyển cấp. Giá trị được suy luận để dữ
-- liệu local tiếp tục hợp lệ; mọi hồ sơ mới phải gửi lý do thật từ API.
UPDATE order_disputes
SET escalated_at = COALESCE(escalated_at, updated_at, created_at, NOW()),
    escalated_by = COALESCE(
        escalated_by,
        CASE WHEN NULLIF(BTRIM(shop_response), '') IS NOT NULL THEN 'SELLER' ELSE 'BUYER' END
    ),
    escalation_reason = COALESCE(
        NULLIF(BTRIM(escalation_reason), ''),
        NULLIF(LEFT(BTRIM(shop_response), 200), ''),
        'Hồ sơ cũ đã được chuyển đến Admin'
    )
WHERE status = 'ADMIN_REVIEW'
   OR (status = 'RESOLVED' AND resolved_by = 'ADMIN');

UPDATE order_disputes
SET resolution_note = 'Phán quyết Admin từ dữ liệu cũ'
WHERE status = 'RESOLVED'
  AND resolved_by = 'ADMIN'
  AND NULLIF(BTRIM(resolution_note), '') IS NULL;

ALTER TABLE order_disputes ADD CONSTRAINT order_disputes_escalated_by_check CHECK (
    escalated_by IS NULL OR escalated_by IN ('BUYER','SELLER')
);

ALTER TABLE order_disputes ADD CONSTRAINT chk_order_disputes_escalation CHECK (
    ((escalated_at IS NULL AND escalated_by IS NULL AND escalation_reason IS NULL)
        OR (escalated_at IS NOT NULL AND escalated_by IS NOT NULL
            AND escalation_reason IS NOT NULL AND BTRIM(escalation_reason) <> ''))
    AND (status <> 'ADMIN_REVIEW'
        OR (escalated_at IS NOT NULL AND escalated_by IS NOT NULL
            AND escalation_reason IS NOT NULL AND BTRIM(escalation_reason) <> ''))
);

ALTER TABLE order_disputes ADD CONSTRAINT chk_order_disputes_resolution CHECK (
    (status = 'RESOLVED'
        AND resolution IS NOT NULL
        AND resolved_by IS NOT NULL
        AND resolved_at IS NOT NULL
        AND ((resolved_by = 'ADMIN' AND resolver_id IS NOT NULL
                AND resolution_note IS NOT NULL AND BTRIM(resolution_note) <> '')
            OR (resolved_by IN ('BUYER','SYSTEM') AND resolver_id IS NULL)))
    OR
    (status <> 'RESOLVED'
        AND resolution IS NULL
        AND resolved_by IS NULL
        AND resolver_id IS NULL
        AND resolved_at IS NULL)
);
