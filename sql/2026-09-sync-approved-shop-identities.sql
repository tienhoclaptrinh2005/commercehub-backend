BEGIN;

-- Các shop đã được duyệt trước khi có logic đồng bộ có thể vẫn giữ tên
-- hồ sơ cũ. Chỉ sửa tài khoản thực sự đang mang role SELLER; không đụng
-- đến tài khoản quản trị hoặc hồ sơ shop còn PENDING/REJECTED.
UPDATE users AS owner
SET full_name = shop.name,
    updated_at = NOW()
FROM shops AS shop
WHERE shop.owner_id = owner.id
  AND shop.status IN ('ACTIVE', 'BANNED')
  AND owner.full_name IS DISTINCT FROM shop.name
  AND EXISTS (
      SELECT 1
      FROM user_roles AS user_role
      JOIN roles AS role ON role.id = user_role.role_id
      WHERE user_role.user_id = owner.id
        AND role.name = 'SELLER'
  );

COMMIT;
