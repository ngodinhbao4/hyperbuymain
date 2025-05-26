package com.example.order.service.iml;

import com.example.order.dto.AddressDTO;
import com.example.order.dto.CartDTO;
import com.example.order.dto.CartItemDTO;
import com.example.order.dto.OrderEvent;
import com.example.order.dto.ProductDTO;
import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.dto.response.ApiResponRequest;
import com.example.order.dto.response.OrderItemResponse;
import com.example.order.dto.response.OrderResponse;
import com.example.order.dto.response.UserResponse;
import com.example.order.entity.Order;
import com.example.order.entity.OrderItem;
import com.example.order.entity.OrderStatus;
import com.example.order.exception.ErrorCodeOrder;
import com.example.order.exception.OrderException;
import com.example.order.repository.OrderRepository;
import com.example.order.service.OrderService;
import com.example.order.service.client.CartServiceClient;
import com.example.order.service.client.ProductServiceClient;
import com.example.order.service.client.UpdateStockRequest;
import com.example.order.service.client.UserServiceClient;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final ProductServiceClient productServiceClient;
    private final CartServiceClient cartServiceClient;
    private final RabbitTemplate rabbitTemplate;
    private final UserServiceClient userServiceClient; // Thêm FeignClient

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest createOrderRequest, String authorizationHeader) {
        logger.info("Attempting to create order for user ID: {}, Authorization: {}", 
            createOrderRequest.getUserId(), authorizationHeader);

        // Lấy username và UUID từ UserService
        String username = createOrderRequest.getUserId(); // baodh
        logger.debug("Fetching user for username: {}", username);
        ApiResponRequest<UserResponse> response;
        try {
            response = userServiceClient.getUserByUsername(username, authorizationHeader);
            if (response.getResult() == null || response.getResult().getId() == null || response.getResult().getUsername() == null) {
                logger.error("UserResponse, id, or username is null for username: {}", username);
                throw new OrderException(ErrorCodeOrder.USER_NOT_FOUND, "User not found for username: " + username);
            }
        } catch (FeignException e) {
            logger.error("Failed to fetch user for username: {}. Error: {}", username, e.getMessage(), e);
            throw new OrderException(ErrorCodeOrder.USER_NOT_FOUND, "User not found for username: " + username, e);
        }
        String userIdForOrder = response.getResult().getUsername(); // Lưu username (baodh)
        String userIdForNotification = response.getResult().getId(); // UUID cho thông báo
        logger.debug("Retrieved username: {} and UUID: {} for username: {}", userIdForOrder, userIdForNotification, username);

        // Lấy giỏ hàng từ cart-service
        CartDTO cart = fetchCartFromService(userIdForNotification, authorizationHeader);
        logger.info("Cart fetched for user {}: {} items", userIdForNotification, cart.getItems().size());

        // Kiểm tra giỏ hàng
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            logger.warn("Cart is empty for user ID: {}. Cannot create order.", userIdForNotification);
            throw new OrderException(ErrorCodeOrder.EMPTY_CART_FOR_ORDER);
        }

        // Lấy thông tin sản phẩm và xác thực tính khả dụng
        List<CartItemDTO> cartItems = cart.getItems();
        for (CartItemDTO item : cartItems) {
            logger.info("Processing cart item: productId={}, quantity={}", item.getProductId(), item.getQuantity());
            ProductDTO product = fetchProductFromService(item.getProductId(), authorizationHeader);
            validateProductAvailability(product, item.getQuantity());
        }

        // Khởi tạo đơn hàng
        Order order = initializeOrder(createOrderRequest, cartItems);
        order.setUserId(userIdForOrder); // Lưu username thay vì UUID

        // Gán thông tin địa chỉ và phương thức thanh toán
        if (createOrderRequest.getShippingAddress() != null) {
            order.setShippingAddressLine1(createOrderRequest.getShippingAddress().getAddressLine1());
            order.setShippingAddressLine2(createOrderRequest.getShippingAddress().getAddressLine2());
            order.setShippingCity(createOrderRequest.getShippingAddress().getCity());
            order.setShippingCountry(createOrderRequest.getShippingAddress().getCountry());
            order.setShippingPostalCode(createOrderRequest.getShippingAddress().getPostalCode());
        }
        if (createOrderRequest.getBillingAddress() != null) {
            order.setBillingAddressLine1(createOrderRequest.getBillingAddress().getAddressLine1());
            order.setBillingAddressLine2(createOrderRequest.getBillingAddress().getAddressLine2());
            order.setBillingCity(createOrderRequest.getBillingAddress().getCity());
            order.setBillingCountry(createOrderRequest.getBillingAddress().getCountry());
            order.setBillingPostalCode(createOrderRequest.getBillingAddress().getPostalCode());
        }
        order.setPaymentMethod(createOrderRequest.getPaymentMethod());

        // Tạo các mục đơn hàng
        List<OrderItem> orderItems = cartItems.stream()
            .map(item -> {
                ProductDTO product = fetchProductFromService(item.getProductId(), authorizationHeader);
                return createOrderItem(product, item.getQuantity(), order);
            })
            .collect(Collectors.toList());
        order.setItems(orderItems);

        // Tính totalAmount
        BigDecimal totalAmount = orderItems.stream()
            .map(OrderItem::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new OrderException(ErrorCodeOrder.INVALID_ORDER_REQUEST, "Total amount must be greater than zero.");
        }
        order.setTotalAmount(totalAmount);
        logger.info("Calculated totalAmount for user {}: {}", userIdForOrder, totalAmount);

        // Lưu đơn hàng
        Order savedOrder = saveOrderToDatabase(order);

        // Giảm tồn kho
        decreaseProductStock(savedOrder, authorizationHeader);

        // Xóa giỏ hàng
        clearUserCart(userIdForNotification, savedOrder.getId(), authorizationHeader);

        // Xác nhận đơn hàng
        confirmOrder(savedOrder);

        // Gửi OrderEvent với UUID để gửi thông báo
        OrderEvent event = new OrderEvent();
        event.setId(savedOrder.getId());
        event.setUserId(userIdForNotification); // Sử dụng UUID
        event.setStatus(savedOrder.getStatus().name());
        event.setAuthorizationHeader(authorizationHeader);
        logger.debug("Sending OrderEvent for order creation: {}", event);
        rabbitTemplate.convertAndSend("order_notifications", event);

        // Tạo OrderResponse
        return mapOrderToResponseDTO(savedOrder);
    }
    private CartDTO fetchCartFromService(String userId, String authorizationHeader) {
        logger.info("Fetching cart for user {}", userId);
        try {
            return cartServiceClient.getCartByUserId(authorizationHeader);
        } catch (Exception e) {
            logger.error("Failed to fetch cart for user {}: {}", userId, e.getMessage(), e);
            throw new OrderException(ErrorCodeOrder.CART_SERVICE_UNREACHABLE, "Could not fetch cart for user " + userId, e);
        }
    }

    private ProductDTO fetchProductFromService(Long productId, String authorizationHeader) {
        try {
            ProductDTO product = productServiceClient.getProductById(productId, authorizationHeader);
            if (product == null) {
                throw new OrderException(ErrorCodeOrder.PRODUCT_NOT_FOUND_FOR_ORDER, "Product with ID " + productId + " not found.");
            }
            return product;
        } catch (FeignException e) {
            logger.error("FeignException fetching product {}: {}", productId, e.getMessage(), e);
            throw new OrderException(ErrorCodeOrder.PRODUCT_SERVICE_UNREACHABLE_FOR_ORDER, "Could not fetch product " + productId, e);
        }
    }

    private void validateProductAvailability(ProductDTO product, int requestedQuantity) {
        if (!product.isActive() || product.isDeleted()) {
            throw new OrderException(ErrorCodeOrder.PRODUCT_UNAVAILABLE_FOR_ORDER, "Product " + product.getId() + " is unavailable.");
        }
        if (product.getStockQuantity() < requestedQuantity) {
            throw new OrderException(ErrorCodeOrder.INSUFFICIENT_STOCK_FOR_ORDER, "Insufficient stock for product " + product.getId());
        }
    }

    private Order initializeOrder(CreateOrderRequest request, List<CartItemDTO> cartItems) {
        Order order = new Order();
        order.setUserId(request.getUserId());
        order.setOrderDate(LocalDateTime.now());
        order.setStatus(OrderStatus.PENDING);
        mapAddressDtoToOrder(request.getShippingAddress(), order, true);
        mapAddressDtoToOrder(request.getBillingAddress(), order, false);
        order.setPaymentMethod(request.getPaymentMethod());
        return order;
    }

    private OrderItem createOrderItem(ProductDTO product, int quantity, Order order) {
        OrderItem orderItem = new OrderItem();
        orderItem.setOrder(order);
        orderItem.setProductId(product.getId());
        orderItem.setQuantity(quantity);
        orderItem.setPrice(product.getPrice());
        orderItem.setProductName(product.getName());
        orderItem.setSubtotal(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        return orderItem;
    }

    private Order saveOrderToDatabase(Order order) {
        logger.info("Saving order for user: {}", order.getUserId());
        try {
            return orderRepository.save(order);
        } catch (Exception e) {
            logger.error("Failed to save order: {}", e.getMessage(), e);
            throw new OrderException(ErrorCodeOrder.DATABASE_ERROR, "Could not save order.");
        }
    }

    private void decreaseProductStock(Order savedOrder, String authorizationHeader) {
        try {
            for (OrderItem item : savedOrder.getItems()) {
                UpdateStockRequest request = new UpdateStockRequest(-item.getQuantity());
                productServiceClient.decreaseStock(item.getProductId(), request, authorizationHeader);
                logger.debug("Decreased stock for product {} by {}", item.getProductId(), item.getQuantity());
            }
        } catch (FeignException e) {
            logger.error("FeignException decreasing stock for order {}: {}", savedOrder.getId(), e.getMessage(), e);
            throw new OrderException(ErrorCodeOrder.PRODUCT_SERVICE_STOCK_UPDATE_FAILED, "Failed to update stock.", e);
        }
    }

    private void clearUserCart(String userId, Long orderId, String authorizationHeader) {
        try {
            cartServiceClient.clearCart(authorizationHeader);
            logger.info("Cleared cart for user {} after order {}", userId, orderId);
        } catch (Exception e) {
            logger.error("Failed to clear cart for user {} after order {}: {}", userId, orderId, e.getMessage(), e);
        }
    }

    private void confirmOrder(Order order) {
        order.setStatus(OrderStatus.CONFIRMED);
        try {
            orderRepository.save(order);
        } catch (Exception e) {
            logger.error("Failed to confirm order {}: {}", order.getId(), e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId) {
        logger.debug("Fetching order by ID: {}", orderId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(ErrorCodeOrder.ORDER_NOT_FOUND, "Order not found with ID: " + orderId));
        return mapOrderToResponseDTO(order);
    }

    @Override
    public List<OrderResponse> getOrdersByUserId(String userId) {
        logger.debug("Fetching orders for user ID: {}", userId);
        List<Order> orders = orderRepository.findByUserId(userId);
        return orders.stream().map(this::mapOrderToResponseDTO).collect(Collectors.toList());
    }

   @Override
@Transactional
public OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus, String authorizationHeader) {
    logger.info("Attempting to update status for order ID: {} to {}", orderId, newStatus);
    Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderException(ErrorCodeOrder.ORDER_NOT_FOUND, "Order not found with ID: " + orderId));

    order.setStatus(newStatus);
    Order updatedOrder;
    try {
        updatedOrder = orderRepository.save(order);
    } catch (Exception e) {
        logger.error("Failed to update status for order {}: {}", orderId, e.getMessage(), e);
        throw new OrderException(ErrorCodeOrder.ORDER_UPDATE_FAILED, "Could not save updated order status.", e);
    }
    logger.info("Order ID {} status updated to {}.", updatedOrder.getId(), updatedOrder.getStatus());

    // Lấy UUID từ UserService
    String username = updatedOrder.getUserId(); // username (baodh)
    logger.debug("Fetching user for username: {}", username);
    ApiResponRequest<UserResponse> response;
    try {
        response = userServiceClient.getUserByUsername(username, authorizationHeader);
        if (response.getResult() == null || response.getResult().getId() == null) {
            logger.error("UserResponse or id is null for username: {}", username);
            throw new OrderException(ErrorCodeOrder.USER_NOT_FOUND, "User ID is null for username: " + username);
        }
    } catch (FeignException e) {
        logger.error("Failed to fetch user for username: {}. Error: {}", username, e.getMessage(), e);
        throw new OrderException(ErrorCodeOrder.USER_NOT_FOUND, "User not found for username: " + username, e);
    }
    String userIdForNotification = response.getResult().getId(); // UUID
    logger.debug("Retrieved UUID: {} for username: {}", userIdForNotification, username);

    // Gửi OrderEvent với UUID
    OrderEvent event = new OrderEvent();
    event.setId(updatedOrder.getId());
    event.setUserId(userIdForNotification);
    event.setStatus(updatedOrder.getStatus().name());
    event.setAuthorizationHeader(authorizationHeader);
    logger.debug("Sending OrderEvent: {}", event);
    rabbitTemplate.convertAndSend("order_notifications", event);

    if (newStatus == OrderStatus.CANCELLED) {
        handleOrderCancellationStockAdjustment(updatedOrder, authorizationHeader);
    }
    return mapOrderToResponseDTO(updatedOrder);
}
    private void handleOrderCancellationStockAdjustment(Order cancelledOrder, String authorizationHeader) {
        logger.info("Order {} cancelled. Increasing stock.", cancelledOrder.getId());
        try {
            for (OrderItem item : cancelledOrder.getItems()) {
                UpdateStockRequest request = new UpdateStockRequest(item.getQuantity());
                productServiceClient.increaseStock(item.getProductId(), request, authorizationHeader);
                logger.debug("Increased stock for product {} by {}", item.getProductId(), item.getQuantity());
            }
        } catch (Exception e) {
            logger.error("Failed to increase stock for order {}: {}", cancelledOrder.getId(), e.getMessage(), e);
        }
    }

    private OrderResponse mapOrderToResponseDTO(Order order) {
        OrderResponse dto = new OrderResponse();
        dto.setId(order.getId());
        dto.setUserId(order.getUserId());
        dto.setOrderDate(order.getOrderDate());
        dto.setStatus(order.getStatus());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setPaymentMethod(order.getPaymentMethod());
        dto.setPaymentTransactionId(order.getPaymentTransactionId());
        dto.setCreatedAt(order.getCreatedAt());
        dto.setUpdatedAt(order.getUpdatedAt());
        dto.setShippingAddress(mapOrderToAddressDTO(order, true));
        dto.setBillingAddress(mapOrderToAddressDTO(order, false));
        if (order.getItems() != null) {
            dto.setItems(order.getItems().stream()
                    .map(this::mapOrderItemToResponse)
                    .collect(Collectors.toList()));
        }
        return dto;
    }

    private OrderItemResponse mapOrderItemToResponse(OrderItem item) {
        OrderItemResponse dto = new OrderItemResponse();
        dto.setProductId(item.getProductId());
        dto.setProductName(item.getProductName());
        dto.setQuantity(item.getQuantity());
        dto.setPrice(item.getPrice());
        dto.setSubtotal(item.getSubtotal());
        return dto;
    }

    private void mapAddressDtoToOrder(AddressDTO addressDTO, Order order, boolean isShipping) {
        if (addressDTO == null) return;
        if (isShipping) {
            order.setShippingAddressLine1(addressDTO.getAddressLine1());
            order.setShippingAddressLine2(addressDTO.getAddressLine2());
            order.setShippingCity(addressDTO.getCity());
            order.setShippingPostalCode(addressDTO.getPostalCode());
            order.setShippingCountry(addressDTO.getCountry());
        } else {
            order.setBillingAddressLine1(addressDTO.getAddressLine1());
            order.setBillingAddressLine2(addressDTO.getAddressLine2());
            order.setBillingCity(addressDTO.getCity());
            order.setBillingPostalCode(addressDTO.getPostalCode());
            order.setBillingCountry(addressDTO.getCountry());
        }
    }

    private AddressDTO mapOrderToAddressDTO(Order order, boolean isShipping) {
        AddressDTO dto = new AddressDTO();
        if (isShipping) {
            dto.setAddressLine1(order.getShippingAddressLine1());
            dto.setAddressLine2(order.getShippingAddressLine2());
            dto.setCity(order.getShippingCity());
            dto.setPostalCode(order.getShippingPostalCode());
            dto.setCountry(order.getShippingCountry());
        } else {
            dto.setAddressLine1(order.getBillingAddressLine1());
            dto.setAddressLine2(order.getBillingAddressLine2());
            dto.setCity(order.getBillingCity());
            dto.setPostalCode(order.getBillingPostalCode());
            dto.setCountry(order.getBillingCountry());
        }
        if (dto.getAddressLine1() == null && dto.getAddressLine2() == null &&
            dto.getCity() == null && dto.getPostalCode() == null && dto.getCountry() == null) {
            return null;
        }
        return dto;
    }
}