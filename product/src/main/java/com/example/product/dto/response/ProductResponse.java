package com.example.product.dto.response;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProductResponse {
    private Long id;
    private String sku;
    private String name;
    private String description;
    private BigDecimal price;
    private int stockQuantity;
    private String imageUrl;
    private boolean isActive;
    private Long categoryId; // Thêm categoryId
    private String categoryName; // Thêm categoryName cho tiện hiển thị
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
