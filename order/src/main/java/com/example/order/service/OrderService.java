package com.example.order.service;

import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.dto.response.OrderResponse;
import com.example.order.entity.OrderStatus;

import java.util.List;

public interface OrderService {
    OrderResponse createOrder(CreateOrderRequest request, String authorizationHeader);
    OrderResponse getOrderById(Long orderId);
    List<OrderResponse> getOrdersByUserId(String userId);
    OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus, String authorizationHeader);
}