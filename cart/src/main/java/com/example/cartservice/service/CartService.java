package com.example.cartservice.service;

import com.example.cartservice.dto.request.CartItemRequest;
import com.example.cartservice.dto.response.CartResponse;

public interface CartService {
    CartResponse getCartByUserId(String userId, String token); // Thêm token
    CartResponse addItemToCart(String userId, CartItemRequest itemDto, String token);
    CartResponse updateCartItemQuantity(String userId, String productId, Integer newQuantity);
    CartResponse removeItemFromCart(String userId, String productId);
    void clearCart(String userId);
    String getOrCreateCartIdForUser(String userId);
}