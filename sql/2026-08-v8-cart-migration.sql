-- ============================================================
-- MIGRATION TỪ SCHEMA HIỆN TẠI LÊN v8 (2026-08-04)
-- Chạy trên DB đang có (đã áp dụng sql/2026-08-money-fixes.sql).
-- Idempotent — chạy lại nhiều lần an toàn (PostgreSQL).
--
-- Nội dung:
--   1. XÓA tính năng "shop yêu thích" (favorite_shops)
--   2. THÊM giỏ hàng: carts + cart_items
--   3. Cập nhật phí sàn mặc định lên 4% (0.0400)
-- ============================================================

BEGIN;

-- ------------------------------------------------------------
-- 1. XÓA FAVORITE (sản phẩm/shop yêu thích) — không còn dùng
-- ------------------------------------------------------------
DROP TABLE IF EXISTS favorite_shops;

-- ------------------------------------------------------------
-- 2. GIỎ HÀNG
--    Mỗi user có đúng 1 giỏ hàng (carts.user_id UNIQUE).
--    Mỗi variant chỉ xuất hiện 1 dòng trong giỏ (unique cart+variant),
--    thêm trùng sẽ cộng dồn số lượng ở tầng ứng dụng.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS carts (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS cart_items (
    id                 BIGSERIAL   PRIMARY KEY,
    cart_id            BIGINT      NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_variant_id BIGINT      NOT NULL REFERENCES product_variants(id) ON DELETE CASCADE,
    quantity           INT         NOT NULL CHECK (quantity > 0 AND quantity <= 1000),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cart_item_variant UNIQUE (cart_id, product_variant_id)
);
CREATE INDEX IF NOT EXISTS idx_cart_items_cart ON cart_items(cart_id);

-- ------------------------------------------------------------
-- 3. PHÍ SÀN MẶC ĐỊNH 4%
--    Deactivate mọi config cũ rồi tạo config 4% mới.
--    created_by có FK sang users → ưu tiên ADMIN, fallback user đầu tiên.
--    Nếu DB chưa có user nào: bỏ qua (admin tạo qua API sau, hoặc chạy
--    lại script này sau khi có user đầu tiên).
-- ------------------------------------------------------------
UPDATE platform_fee_configs
SET is_active = false, effective_until = NOW()
WHERE is_active = true
  AND fee_rate <> 0.0400;

INSERT INTO platform_fee_configs
    (fee_rate, min_fee_amount, max_fee_amount, description, is_active, effective_from, created_by, created_at)
SELECT 0.0400, 0, NULL, 'Phí sàn mặc định 4% (v8)', true, NOW(), seed_user.id, NOW()
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
