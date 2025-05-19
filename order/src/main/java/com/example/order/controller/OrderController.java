package com.example.order.controller;

import com.example.order.dto.request.CreateOrderRequest;
// Sử dụng OrderResponse từ package dto.response
import com.example.order.dto.response.OrderResponse;
import com.example.order.entity.OrderStatus;
import com.example.order.exception.ErrorCodeOrder;
import com.example.order.exception.OrderException;
import com.example.order.service.OrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
// import org.springframework.security.oauth2.jwt.Jwt; // Nếu cần truy cập trực tiếp vào JWT claims
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    // Giả sử bạn có OrderSecurityService được inject hoặc có sẵn như một bean
    // private final OrderSecurityService orderSecurityService; // Nếu không dùng @beanName trong @PreAuthorize

    /**
     * Tạo một đơn hàng mới.
     * Người dùng phải được xác thực.
     * UserId trong CreateOrderRequest sẽ được ghi đè bằng userId của người dùng đã xác thực.
     *
     * @param createOrderRequest DTO chứa thông tin để tạo đơn hàng.
     * @param authentication   Thông tin xác thực của người dùng hiện tại.
     * @return
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
        @Valid @RequestBody CreateOrderRequest createOrderRequest,
        @RequestHeader("Authorization") String authorizationHeader,
        Authentication authentication) {

    if (authentication == null || !authentication.isAuthenticated()) {
        throw new OrderException(ErrorCodeOrder.USER_NOT_AUTHENTICATED_FOR_ORDER);
    }
    String authenticatedUserId = authentication.getName();
    createOrderRequest.setUserId(authenticatedUserId);

    OrderResponse orderResponse = orderService.createOrder(createOrderRequest, authorizationHeader);
    return new ResponseEntity<>(orderResponse, HttpStatus.CREATED);
}
    /**
     * Lấy thông tin chi tiết của một đơn hàng dựa trên ID đơn hàng.
     * Chỉ chủ sở hữu đơn hàng hoặc người dùng có vai trò ADMIN mới có quyền truy cập.
     *
     * @param orderId        ID của đơn hàng cần truy vấn.
     * @param authentication Thông tin xác thực của người dùng hiện tại.
     * @return ResponseEntity chứa OrderResponse và HTTP status OK.
     */
    @GetMapping("/{orderId}")
    @PreAuthorize("@orderSecurityService.canAccessOrder(authentication, #orderId) or hasRole('ADMIN')")
    public ResponseEntity<OrderResponse> getOrderById(
            @PathVariable Long orderId,
            Authentication authentication) { // authentication được Spring Security tự động inject
        OrderResponse orderResponse = orderService.getOrderById(orderId);
        return ResponseEntity.ok(orderResponse);
    }

    /**
     * Lấy danh sách các đơn hàng của người dùng hiện tại đã xác thực.
     *
     * @param authentication Thông tin xác thực của người dùng hiện tại.
     * @return ResponseEntity chứa danh sách OrderResponse và HTTP status OK.
     */
    @GetMapping("/my-orders")
    public ResponseEntity<List<OrderResponse>> getMyOrders(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new OrderException(ErrorCodeOrder.USER_NOT_AUTHENTICATED_FOR_ORDER);
        }
        String userId = authentication.getName(); // Lấy userId từ 'sub' claim của JWT
        List<OrderResponse> orders = orderService.getOrdersByUserId(userId);
        return ResponseEntity.ok(orders);
    }

    /**
     * @param userId     
     * @param authentication
     * @return ResponseEntity chứa danh sách OrderResponse và HTTP status OK.
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("@orderSecurityService.isOwnerOrAdmin(authentication, #userId)")
    public ResponseEntity<List<OrderResponse>> getOrdersByUserIdOfUser(
            @PathVariable String userId,
            Authentication authentication) {
        List<OrderResponse> orders = orderService.getOrdersByUserId(userId);
        return ResponseEntity.ok(orders);
    }

    /**
     * Cập nhật trạng thái của một đơn hàng.
     * Chỉ người dùng có vai trò ADMIN mới có quyền thực hiện hành động này.
     * @param orderId   
     * @param statusUpdate  
     * @param authentication 
     * @return
     */
    @PutMapping("/{orderId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable Long orderId,
            @RequestBody Map<String, String> statusUpdate,
            @RequestHeader("Authorization") String authorizationHeader,
            Authentication authentication) { // authentication có thể không cần thiết nếu chỉ check role

        String newStatusString = statusUpdate.get("status");
        if (newStatusString == null || newStatusString.trim().isEmpty()) {
            throw new OrderException(ErrorCodeOrder.INVALID_ORDER_REQUEST, "Trường 'status' không được để trống trong yêu cầu.");
        }
        try {
            OrderStatus newStatus = OrderStatus.valueOf(newStatusString.toUpperCase());
            OrderResponse updatedOrder = orderService.updateOrderStatus(orderId, newStatus, authorizationHeader);
            return ResponseEntity.ok(updatedOrder);
        } catch (IllegalArgumentException e) {
            throw new OrderException(ErrorCodeOrder.INVALID_ORDER_REQUEST, "Giá trị trạng thái đơn hàng không hợp lệ: " + newStatusString);
        }
    }
}
