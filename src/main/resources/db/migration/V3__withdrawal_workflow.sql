ALTER TABLE withdrawals
    ADD COLUMN IF NOT EXISTS transfer_reference VARCHAR(100),
    ADD COLUMN IF NOT EXISTS approved_by_id BIGINT REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMPTZ;

ALTER TABLE seller_notification_reads
    ADD COLUMN IF NOT EXISTS withdrawals_read_at TIMESTAMPTZ NOT NULL
        DEFAULT TIMESTAMPTZ 'epoch';

-- Backfill an toàn cho dữ liệu local đã hoàn tất trước khi quy trình hai bước
-- và mã tham chiếu được bổ sung.
UPDATE withdrawals
SET approved_at = COALESCE(approved_at, processed_at, updated_at, created_at),
    approved_by_id = COALESCE(approved_by_id, processor_id)
WHERE status IN ('APPROVED', 'DONE');

UPDATE withdrawals
SET transfer_reference = COALESCE(
        NULLIF(BTRIM(transfer_reference), ''),
        'LEGACY-WD-' || LPAD(id::TEXT, 6, '0')
    ),
    processed_at = COALESCE(processed_at, updated_at, created_at)
WHERE status = 'DONE';

UPDATE withdrawals
SET admin_note = COALESCE(
        NULLIF(BTRIM(admin_note), ''),
        'Từ chối trước khi chuẩn hóa quy trình.'
    ),
    processed_at = COALESCE(processed_at, updated_at, created_at)
WHERE status = 'REJECTED';

ALTER TABLE withdrawals
    DROP CONSTRAINT IF EXISTS withdrawals_approved_state_check,
    DROP CONSTRAINT IF EXISTS withdrawals_done_state_check,
    DROP CONSTRAINT IF EXISTS withdrawals_rejected_state_check;

ALTER TABLE withdrawals
    ADD CONSTRAINT withdrawals_approved_state_check CHECK (
        status NOT IN ('APPROVED','DONE') OR approved_at IS NOT NULL
    ),
    ADD CONSTRAINT withdrawals_done_state_check CHECK (
        status <> 'DONE' OR (transfer_reference IS NOT NULL AND processed_at IS NOT NULL)
    ),
    ADD CONSTRAINT withdrawals_rejected_state_check CHECK (
        status <> 'REJECTED' OR (
            NULLIF(BTRIM(admin_note), '') IS NOT NULL
            AND processed_at IS NOT NULL
        )
    );

CREATE INDEX IF NOT EXISTS idx_withdrawals_wallet_created
    ON withdrawals(wallet_id, created_at DESC);
