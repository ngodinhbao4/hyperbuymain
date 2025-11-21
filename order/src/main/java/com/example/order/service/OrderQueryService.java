package com.example.order.service;

import com.example.order.entity.OrderStatus;
import com.example.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderRepository orderRepository;

    public boolean hasUserPurchasedProduct(String username, Long productId) {
        return orderRepository.existsByUserIdAndStatusAndItemsProductId(
                username,
                OrderStatus.DELIVERED,
                productId
        );
    }
}
