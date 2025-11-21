package com.example.minigame.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 🎟️ Thông tin voucher mà user đang sở hữu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserVoucherDTO {
    private String code;          // Mã voucher (VD: SALE50)
    private String discountType;  // Loại giảm giá (PERCENT / FIXED)
    private double discountValue; // Giá trị giảm giá
    private boolean used;         // Đã sử dụng hay chưa
    private String status;        // ACTIVE / EXPIRED / USED
}
