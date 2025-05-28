-- File: mysql-init-scripts/01-create-databases.sql

-- Tạo database cho UserService (nếu chưa tồn tại)
CREATE DATABASE IF NOT EXISTS hyperbuy_user_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Tạo database cho ProductService (nếu chưa tồn tại)
CREATE DATABASE IF NOT EXISTS hyperbuy_product_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Tạo database cho CartService (nếu chưa tồn tại)
CREATE DATABASE IF NOT EXISTS hyperbuy_cart_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Tạo database cho OrderService (nếu chưa tồn tại)
CREATE DATABASE IF NOT EXISTS hyperbuy_order_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Tạo database cho PaymentService (nếu chưa tồn tại)
CREATE DATABASE IF NOT EXISTS hyperbuy_payment_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Tạo database cho NotificationService (nếu chưa tồn tại)
CREATE DATABASE IF NOT EXISTS hyperbuy_notification_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Tạo database cho ChatService (nếu chưa tồn tại)
CREATE DATABASE IF NOT EXISTS hyperbuy_chat_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
