package com.example.order.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private Long productId; // ID của sản phẩm từ ProductService

    @Column(nullable = false)
    private String productName; // Lưu tên sản phẩm tại thời điểm mua

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price; // Giá sản phẩm tại thời điểm mua

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    // không cần createdAt, updatedAt ở đây nếu không quá cần thiết
}