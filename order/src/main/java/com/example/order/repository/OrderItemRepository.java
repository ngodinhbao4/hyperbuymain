package com.example.order.repository;

import com.example.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    // Có thể thêm các phương thức truy vấn tùy chỉnh nếu cần
}