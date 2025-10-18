package com.example.order.dto.request;


import java.util.List;

import com.example.order.dto.AddressDTO;
import com.example.order.dto.CartItemDTO;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateOrderRequest {

    private String userId; // Sẽ được lấy từ JWT token trong thực tế, hoặc truyền vào

    @NotNull(message = "Shipping address is required")
    @Valid
    private AddressDTO shippingAddress;

    @NotNull(message = "Billing address is required")
    @Valid
    private AddressDTO billingAddress;

    @NotBlank(message = "Payment method is required")
    private String paymentMethod; // Ví dụ: "CREDIT_CARD", "COD"
    // Thông tin chi tiết thẻ tín dụng sẽ được xử lý bởi Payment Gateway/Service riêng, không lưu ở đây.
    private List<CartItemDTO> items;
}