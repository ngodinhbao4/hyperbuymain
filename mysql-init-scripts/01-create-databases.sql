-- File: mysql-init-scripts/01-create-databases.sql

-- Tạo database cho UserService (nếu chưa tồn tại)
CREATE DATABASE IF NOT EXISTS hyperbuy_user_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Tạo database cho ProductService (nếu chưa tồn tại)
CREATE DATABASE IF NOT EXISTS hyperbuy_product_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Tạo database cho CartService (nếu chưa tồn tại)
CREATE DATABASE IF NOT EXISTS hyperbuy_cart_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Tạo database cho OrderService (nếu chưa tồn tại)
CREATE DATABASE IF NOT EXISTS hyperbuy_order_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- (Tùy chọn) Bạn có thể muốn tạo một user riêng cho các ứng dụng thay vì dùng root
-- CREATE USER 'appuser'@'%' IDENTIFIED BY 'apppassword';
-- GRANT ALL PRIVILEGES ON hyperbuy_user_db.* TO 'appuser'@'%';
-- GRANT ALL PRIVILEGES ON hyperbuy_product_db.* TO 'appuser'@'%';
-- GRANT ALL PRIVILEGES ON hyperbuy_cart_db.* TO 'appuser'@'%';
-- FLUSH PRIVILEGES;