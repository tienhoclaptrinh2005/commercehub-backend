-- Cho phép mỗi tài khoản chủ động đổi username đúng một lần.
-- NULL: chưa từng đổi; có giá trị: thời điểm đã sử dụng quyền đổi username.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS username_changed_at TIMESTAMPTZ;
