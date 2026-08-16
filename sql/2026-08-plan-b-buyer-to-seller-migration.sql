-- ============================================================
-- MIGRATION: Phương án B — BUYER → SELLER
-- Mục tiêu: User nào đã có shop thì xóa role BUYER, giữ SELLER.
-- User chưa có shop thì giữ nguyên role BUYER.
-- ============================================================

-- Xem trước: danh sách user bị ảnh hưởng (chạy SELECT trước để kiểm tra)
-- SELECT u.id, u.email, r.name AS current_role
-- FROM users u
-- JOIN user_roles ur ON u.id = ur.user_id
-- JOIN roles r ON ur.role_id = r.id
-- WHERE u.id IN (SELECT owner_id FROM shops)
-- ORDER BY u.id;

-- ============================================================
-- BƯỚC 1: Thêm role SELLER cho tất cả user đã có shop
--         (phòng trường hợp ai đó chỉ có BUYER mà đã có shop)
-- ============================================================
INSERT INTO user_roles (user_id, role_id)
SELECT DISTINCT s.owner_id, r.id
FROM shops s
CROSS JOIN roles r
WHERE r.name = 'SELLER'
  AND NOT EXISTS (
      SELECT 1 FROM user_roles ur2
      WHERE ur2.user_id = s.owner_id AND ur2.role_id = r.id
  );

-- ============================================================
-- BƯỚC 2: Xóa role BUYER khỏi những user đã có shop
--         (những user này giờ chỉ còn SELLER)
-- ============================================================
DELETE FROM user_roles
WHERE role_id = (SELECT id FROM roles WHERE name = 'BUYER')
  AND user_id IN (SELECT owner_id FROM shops);

-- ============================================================
-- KIỂM TRA SAU KHI CHẠY:
-- Tất cả chủ shop phải chỉ có role SELLER, không còn BUYER.
-- ============================================================
-- SELECT u.id, u.email, r.name AS role
-- FROM users u
-- JOIN user_roles ur ON u.id = ur.user_id
-- JOIN roles r ON ur.role_id = r.id
-- WHERE u.id IN (SELECT owner_id FROM shops)
-- ORDER BY u.id;
