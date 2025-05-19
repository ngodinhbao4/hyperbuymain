// Package: com.example.cartservice.controller
package com.example.cartservice.controller;

import com.example.cartservice.dto.request.CartItemRequest;
import com.example.cartservice.dto.response.ApiResponse;
import com.example.cartservice.dto.response.CartResponse;
import com.example.cartservice.exception.CartException;
import com.example.cartservice.exception.ErrorCodeCart;
import com.example.cartservice.service.CartService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal; // Import @AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt; // Import Jwt
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/carts") // Base path vẫn giữ nguyên
@RequiredArgsConstructor
@Slf4j
public class CartController {

    private final CartService cartService;

    /**
     * Lấy userId từ JWT principal.
     *
     * @param jwtPrincipal Đối tượng Jwt được inject bởi Spring Security.
     * @return userId (subject từ JWT).
     * @throws CartException nếu không thể lấy được userId (ví dụ: jwtPrincipal là null).
     */
    private String getAuthenticatedUserId(Jwt jwtPrincipal) {
        if (jwtPrincipal == null) {
            log.warn("JWT Principal is null. User not authenticated or token not provided correctly.");
            throw new CartException(ErrorCodeCart.USER_NOT_AUTHENTICATED); // Hoặc một mã lỗi phù hợp
        }
        // Claim 'sub' (subject) thường chứa user ID trong JWT.
        // Bạn có thể cần điều chỉnh nếu user ID nằm trong một claim khác.
        String userId = jwtPrincipal.getSubject();
        if (userId == null || userId.trim().isEmpty()) {
            log.warn("User ID (subject) is null or empty in JWT.");
            throw new CartException(ErrorCodeCart.USER_NOT_AUTHENTICATED, "User identifier not found in token.");
        }
        return userId;
    }

    // Endpoint để lấy/tạo Cart ID cho người dùng hiện tại
    // Path không cần {userId} nữa vì chúng ta lấy từ token
    @PostMapping("/init") // Hoặc bạn có thể dùng GET nếu không có side effect tạo mới rõ ràng
    public ResponseEntity<ApiResponse<String>> getOrCreateCartIdForCurrentUser(@AuthenticationPrincipal Jwt jwtPrincipal) {
        String userId = getAuthenticatedUserId(jwtPrincipal);
        log.info("Request to get or create cart ID for authenticated user: {}", userId);

        String cartId = cartService.getOrCreateCartIdForUser(userId);
        ApiResponse<String> response = ApiResponse.<String>builder()
                .code(HttpStatus.OK.value())
                .message("Cart ID retrieved or created successfully for current user.")
                .result(cartId)
                .build();
        return ResponseEntity.ok(response);
    }

    // Lấy giỏ hàng của người dùng hiện tại
    @GetMapping("/my-cart") // Endpoint mới, rõ ràng hơn cho giỏ hàng của người dùng hiện tại
    public ResponseEntity<ApiResponse<CartResponse>> getCurrentUserCart(@AuthenticationPrincipal Jwt jwtPrincipal) {
        String userId = getAuthenticatedUserId(jwtPrincipal);
        log.info("Request to get cart for authenticated user: {}", userId);

        CartResponse cartDto = cartService.getCartByUserId(userId);
        ApiResponse<CartResponse> response = ApiResponse.<CartResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Cart retrieved successfully for current user.")
                .result(cartDto)
                .build();
        return ResponseEntity.ok(response);
    }

    // Thêm item vào giỏ hàng của người dùng hiện tại
    @PostMapping("/my-cart/items")
    public ResponseEntity<ApiResponse<CartResponse>> addItemToCurrentUsersCart(
            @AuthenticationPrincipal Jwt jwtPrincipal,
            @Valid @RequestBody CartItemRequest itemRequestDto) {
        String userId = getAuthenticatedUserId(jwtPrincipal);
        log.info("Request to add item to cart for authenticated user: {}. Item: {}", userId, itemRequestDto);

        CartResponse updatedCart = cartService.addItemToCart(userId, itemRequestDto);
        ApiResponse<CartResponse> response = ApiResponse.<CartResponse>builder()
                .code(HttpStatus.CREATED.value())
                .message("Item added to current user's cart successfully.")
                .result(updatedCart)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Cập nhật số lượng item trong giỏ hàng của người dùng hiện tại
    @PutMapping("/my-cart/items/{productId}")
    public ResponseEntity<ApiResponse<CartResponse>> updateCurrentUsersCartItemQuantity(
            @AuthenticationPrincipal Jwt jwtPrincipal,
            @PathVariable String productId,
            @RequestParam Integer quantity) {
        String userId = getAuthenticatedUserId(jwtPrincipal);
        log.info("Request to update quantity for product {} in cart of authenticated user {} to {}", productId, userId, quantity);

        CartResponse updatedCart = cartService.updateCartItemQuantity(userId, productId, quantity);
        ApiResponse<CartResponse> response = ApiResponse.<CartResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Current user's cart item quantity updated successfully.")
                .result(updatedCart)
                .build();
        return ResponseEntity.ok(response);
    }

    // Xóa item khỏi giỏ hàng của người dùng hiện tại
    @DeleteMapping("/my-cart/items/{productId}")
    public ResponseEntity<ApiResponse<CartResponse>> removeItemFromCurrentUsersCart(
            @AuthenticationPrincipal Jwt jwtPrincipal,
            @PathVariable String productId) {
        String userId = getAuthenticatedUserId(jwtPrincipal);
        log.info("Request to remove product {} from cart of authenticated user {}", productId, userId);

        CartResponse updatedCart = cartService.removeItemFromCart(userId, productId);
        ApiResponse<CartResponse> response = ApiResponse.<CartResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Item removed from current user's cart successfully.")
                .result(updatedCart)
                .build();
        return ResponseEntity.ok(response);
    }

    // Xóa toàn bộ giỏ hàng của người dùng hiện tại
    @DeleteMapping("/my-cart")
    public ResponseEntity<ApiResponse<Void>> clearCurrentUsersCart(@AuthenticationPrincipal Jwt jwtPrincipal) {
        String userId = getAuthenticatedUserId(jwtPrincipal);
        log.info("Request to clear cart for authenticated user: {}", userId);

        cartService.clearCart(userId);
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .code(HttpStatus.OK.value())
                .message("Current user's cart cleared successfully.")
                .build();
        return ResponseEntity.ok(response);
    }

    // --- Các endpoint dành cho Admin (ví dụ, nếu cần) ---
    // Các endpoint này sẽ giữ lại @PathVariable String userId và được bảo vệ bằng vai trò Admin trong SecurityConfig

    // Ví dụ: Admin xem giỏ hàng của một user cụ thể
    // @PreAuthorize("hasRole('ADMIN')") // Sử dụng @EnableMethodSecurity trong SecurityConfig
    // @GetMapping("/{userIdForAdmin}")
    // public ResponseEntity<ApiResponse<CartResponse>> getCartByUserIdForAdmin(
    //         @PathVariable("userIdForAdmin") String userId,
    //         @AuthenticationPrincipal Jwt jwtPrincipal // Để log admin nào thực hiện
    // ) {
    //     log.info("Admin {} requesting cart for user ID: {}", jwtPrincipal.getSubject(), userId);
    //     CartResponse cartDto = cartService.getCartByUserId(userId);
    //     ApiResponse<CartResponse> response = ApiResponse.<CartResponse>builder()
    //             .code(HttpStatus.OK.value())
    //             .message("Cart retrieved successfully by admin.")
    //             .result(cartDto)
    //             .build();
    //     return ResponseEntity.ok(response);
    // }
}