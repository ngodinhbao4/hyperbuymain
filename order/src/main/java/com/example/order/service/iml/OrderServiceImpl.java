package com.example.order.service.iml;

import com.example.order.dto.AddressDTO;
import com.example.order.dto.CartDTO;
import com.example.order.dto.CartItemDTO;
import com.example.order.dto.ProductDTO;
import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.dto.response.OrderItemResponse;
import com.example.order.dto.response.OrderResponse;
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

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;

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

@Override
@Transactional
public OrderResponse createOrder(CreateOrderRequest createOrderRequest, String authorizationHeader) {
    logger.info("Attempting to create order for user ID: {}, Authorization: {}, Request: {}", 
        createOrderRequest.getUserId(), authorizationHeader, createOrderRequest);

    // Lấy giỏ hàng từ cart-service
    CartDTO cart = fetchCartFromService(createOrderRequest.getUserId(), authorizationHeader);
    logger.info("Cart fetched for user {}: 1 items", createOrderRequest.getUserId());

    // Kiểm tra giỏ hàng
    if (cart.getItems() == null || cart.getItems().isEmpty()) {
        logger.warn("Cart is empty for user ID: {}. Cannot create order. Cart: {}", createOrderRequest.getUserId(), cart);
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

    // Tính totalAmount từ OrderItem
    BigDecimal totalAmount = orderItems.stream()
        .map(OrderItem::getSubtotal)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
        throw new OrderException(ErrorCodeOrder.INVALID_ORDER_REQUEST, "Total amount must be greater than zero.");
    }
    order.setTotalAmount(totalAmount);
    logger.info("Calculated totalAmount for user {}: {}", createOrderRequest.getUserId(), totalAmount);

    // Lưu đơn hàng
    Order savedOrder = saveOrderToDatabase(order);

    // Giảm tồn kho
    decreaseProductStock(savedOrder, authorizationHeader);

    // Xóa giỏ hàng của người dùng
    clearUserCart(createOrderRequest.getUserId(), savedOrder.getId(), authorizationHeader);

    // Xác nhận đơn hàng
    confirmOrder(savedOrder);

    // Tạo OrderResponse và ánh xạ đầy đủ các trường
    OrderResponse orderResponse = new OrderResponse();
    orderResponse.setId(savedOrder.getId());
    orderResponse.setUserId(savedOrder.getUserId());
    orderResponse.setTotalAmount(savedOrder.getTotalAmount());
    orderResponse.setOrderDate(savedOrder.getOrderDate());
    orderResponse.setStatus(savedOrder.getStatus());
    orderResponse.setPaymentMethod(savedOrder.getPaymentMethod());
    orderResponse.setPaymentTransactionId(savedOrder.getPaymentTransactionId());
    orderResponse.setCreatedAt(savedOrder.getCreatedAt());
    orderResponse.setUpdatedAt(savedOrder.getUpdatedAt());

    // Ánh xạ shippingAddress
    if (savedOrder.getShippingAddressLine1() != null) {
        AddressDTO shippingAddress = new AddressDTO();
        shippingAddress.setAddressLine1(savedOrder.getShippingAddressLine1());
        shippingAddress.setAddressLine2(savedOrder.getShippingAddressLine2());
        shippingAddress.setCity(savedOrder.getShippingCity());
        shippingAddress.setCountry(savedOrder.getShippingCountry());
        shippingAddress.setPostalCode(savedOrder.getShippingPostalCode());
        orderResponse.setShippingAddress(shippingAddress);
    }

    // Ánh xạ billingAddress
    if (savedOrder.getBillingAddressLine1() != null) {
        AddressDTO billingAddress = new AddressDTO();
        billingAddress.setAddressLine1(savedOrder.getBillingAddressLine1());
        billingAddress.setAddressLine2(savedOrder.getBillingAddressLine2());
        billingAddress.setCity(savedOrder.getBillingCity());
        billingAddress.setCountry(savedOrder.getBillingCountry());
        billingAddress.setPostalCode(savedOrder.getBillingPostalCode());
        orderResponse.setBillingAddress(billingAddress);
    }

    // Ánh xạ items
    if (savedOrder.getItems() != null && !savedOrder.getItems().isEmpty()) {
        List<OrderItemResponse> itemResponses = savedOrder.getItems().stream()
            .map(item -> {
                OrderItemResponse itemResponse = new OrderItemResponse();
                itemResponse.setProductId(item.getProductId());
                itemResponse.setProductName(item.getProductName());
                itemResponse.setQuantity(item.getQuantity());
                itemResponse.setPrice(item.getPrice());
                itemResponse.setSubtotal(item.getSubtotal());
                return itemResponse;
            })
            .collect(Collectors.toList());
        orderResponse.setItems(itemResponses);
    }

    return orderResponse;
}
    private CartDTO fetchCartFromService(String userId, String authorizationHeader) {
        logger.info("Fetching cart for user {} with Authorization header: {}", userId, authorizationHeader);
        CartDTO cart;
        try {
            cart = cartServiceClient.getCartByUserId(authorizationHeader);
            logger.info("Cart fetched for user {}: {}", userId, cart != null ? cart.toString() : "null");
        } catch (Exception e) {
            logger.error("Exception while fetching cart for user {}: {}", userId, e.getMessage(), e);
            throw new OrderException(ErrorCodeOrder.CART_SERVICE_UNREACHABLE, "Could not fetch cart for user " + userId + ". " + e.getMessage(), e);
        }
        if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            logger.warn("Cart is empty for user ID: {}. Cannot create order. Cart: {}", userId, cart);
            throw new OrderException(ErrorCodeOrder.EMPTY_CART_FOR_ORDER);
        }
        return cart;
    }
    private ProductDTO fetchProductFromService(Long productId, String authorizationHeader) {
        ProductDTO product;
        try {
            product = productServiceClient.getProductById(productId, authorizationHeader);
        } catch (FeignException e) {
            logger.error("FeignException while fetching product {}: Status {}, Message {}", productId, e.status(), e.getMessage(), e);
            if (e.status() == 404) {
                throw new OrderException(ErrorCodeOrder.PRODUCT_NOT_FOUND_FOR_ORDER, "Product with ID " + productId + " not found (from ProductService).", e);
            }
            throw new OrderException(ErrorCodeOrder.PRODUCT_SERVICE_UNREACHABLE_FOR_ORDER, "Could not fetch product " + productId + ". " + e.getMessage(), e);
        } catch (ResourceAccessException e) {
            logger.error("ResourceAccessException while fetching product {}: {}", productId, e.getMessage(), e);
            throw new OrderException(ErrorCodeOrder.PRODUCT_SERVICE_UNREACHABLE_FOR_ORDER, "Product Service is unreachable.", e);
        } catch (RuntimeException e) {
            logger.error("RuntimeException from ProductServiceClient for product {}: {}", productId, e.getMessage(), e);
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("not found")) {
                throw new OrderException(ErrorCodeOrder.PRODUCT_NOT_FOUND_FOR_ORDER, "Product with ID " + productId + " not found (mock client).", e);
            }
            throw new OrderException(ErrorCodeOrder.PRODUCT_SERVICE_UNREACHABLE_FOR_ORDER, "Error from Product Service (mock client) for product " + productId + ". " + e.getMessage(), e);
        }

        if (product == null) {
            logger.error("Product with ID {} returned as null from ProductService.", productId);
            throw new OrderException(ErrorCodeOrder.PRODUCT_NOT_FOUND_FOR_ORDER,
                "Product with ID " + productId + " not found during order creation.");
        }
        return product;
    }

    private void validateProductAvailability(ProductDTO product, int requestedQuantity) {
        if (!product.isActive() || product.isDeleted()) {
            String unavailableMsg = "Product '" + product.getName() + "' (ID: " + product.getId() + ") is currently unavailable.";
            logger.warn(unavailableMsg);
            throw new OrderException(ErrorCodeOrder.PRODUCT_UNAVAILABLE_FOR_ORDER, unavailableMsg);
        }
        if (product.getStockQuantity() < requestedQuantity) {
            String insufficientMsg = "Insufficient stock for product: '" + product.getName() + "' (ID: " + product.getId() +
                                     "). Requested: " + requestedQuantity + ", Available: " + product.getStockQuantity();
            logger.warn(insufficientMsg);
            throw new OrderException(ErrorCodeOrder.INSUFFICIENT_STOCK_FOR_ORDER, insufficientMsg);
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
        orderItem.setOrder(order); // Gán Order cho OrderItem
        orderItem.setProductId(product.getId());
        orderItem.setQuantity(quantity);
        orderItem.setPrice(product.getPrice());
        orderItem.setProductName(product.getName());
        orderItem.setSubtotal(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        return orderItem;
    }

    private Order saveOrderToDatabase(Order order) {
    logger.info("Saving order to database for user: {}", order.getUserId());
    try {
        Order savedOrder = orderRepository.save(order);
        logger.info("Successfully saved order for user {}: Order ID: {}", order.getUserId(), savedOrder.getId());
        return savedOrder;
    } catch (Exception e) {
        logger.error("Failed to save order for user {}: {}", order.getUserId(), e.getMessage(), e);
        throw new OrderException(ErrorCodeOrder.DATABASE_ERROR, "Could not save the order to the database.");
    }
}

    private void decreaseProductStock(Order savedOrder, String authorizationHeader) {
    try {
        for (OrderItem item : savedOrder.getItems()) {
            UpdateStockRequest request = new UpdateStockRequest(-item.getQuantity());
            productServiceClient.decreaseStock(item.getProductId(), request, authorizationHeader);
            logger.debug("Decreased stock for product ID: {} by quantity: {}", item.getProductId(), item.getQuantity());
        }
    } catch (FeignException e) {
        logger.error("FeignException while decreasing stock after order {} creation: Status {}, Message {}",
                     savedOrder.getId(), e.status(), e.getMessage(), e);
        ErrorCodeOrder stockErrorCode = ErrorCodeOrder.PRODUCT_SERVICE_STOCK_UPDATE_FAILED;
        String stockErrorMsg = "Failed to update stock via Product Service. Order will be rolled back.";
        if (e.status() == 400) {
            stockErrorCode = ErrorCodeOrder.INSUFFICIENT_STOCK;
            stockErrorMsg = "Failed to decrease stock: Product Service reported an issue (possibly insufficient stock or bad request). Order will be rolled back.";
        } else if (e.status() == 404) {
            stockErrorCode = ErrorCodeOrder.PRODUCT_NOT_AVAILABLE;
            stockErrorMsg = "Failed to decrease stock: Product not found via Product Service. Order will be rolled back.";
        }
        throw new OrderException(stockErrorCode, stockErrorMsg + " " + e.getMessage(), e);
    } catch (ResourceAccessException e) {
        logger.error("ResourceAccessException while decreasing stock for order {}: {}", savedOrder.getId(), e.getMessage(), e);
        throw new OrderException(ErrorCodeOrder.PRODUCT_SERVICE_STOCK_UPDATE_FAILED,
                                 "Product Service is unreachable while updating stock. Order will be rolled back.", e);
    } catch (RuntimeException e) {
        logger.error("RuntimeException while decreasing stock for order {}: {}", savedOrder.getId(), e.getMessage(), e);
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        if (msg.contains("insufficient stock")) {
            throw new OrderException(ErrorCodeOrder.INSUFFICIENT_STOCK, "Failed to decrease stock (mock client): " + e.getMessage(), e);
        } else if (msg.contains("not found")) {
            throw new OrderException(ErrorCodeOrder.PRODUCT_NOT_AVAILABLE, "Failed to decrease stock, product not found (mock client): " + e.getMessage(), e);
        }
        throw new OrderException(ErrorCodeOrder.PRODUCT_SERVICE_STOCK_UPDATE_FAILED,
                                 "Error from Product Service (mock client) while updating stock. Order will be rolled back. " + e.getMessage(), e);
    }
    }

    private void clearUserCart(String userId, Long orderId, String authorizationHeader) {
    try {
        cartServiceClient.clearCart(authorizationHeader); // Bỏ userId
        logger.info("Cart cleared for user ID: {} after order {} creation.", userId, orderId);
    } catch (Exception e) {
        logger.error("CRITICAL: Exception while clearing cart for user {} after order {} creation: {}. Manual intervention may be needed.", userId, orderId, e.getMessage(), e);
    }
}

    private void confirmOrder(Order order) {
        order.setStatus(OrderStatus.CONFIRMED);
        try {
            orderRepository.save(order);
        } catch (Exception e) {
            logger.error("Failed to update order status to CONFIRMED for order {}: {}. Manual follow-up needed.", order.getId(), e.getMessage(), e);
        }
    }

    @Override
    public OrderResponse getOrderById(Long orderId) { // Sửa kiểu trả về
        logger.debug("Fetching order by ID: {}", orderId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(ErrorCodeOrder.ORDER_NOT_FOUND, "Order not found with ID: " + orderId));
        return mapOrderToResponseDTO(order);
    }

    @Override
    public List<OrderResponse> getOrdersByUserId(String userId) { // Sửa kiểu trả về của List
        logger.debug("Fetching orders for user ID: {}", userId);
        List<Order> orders;
        try {
            orders = orderRepository.findByUserId(userId);
        } catch (Exception e) {
            logger.error("Error fetching orders for user {}: {}", userId, e.getMessage(), e);
            throw new OrderException(ErrorCodeOrder.UNCATEGORIZED_ORDER_EXCEPTION, "Could not retrieve orders for user " + userId, e);
        }
        return orders.stream()
                .map(this::mapOrderToResponseDTO) // mapOrderToResponseDTO trả về OrderResponse
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus, String authorizationHeader) { // Sửa kiểu trả về
        logger.info("Attempting to update status for order ID: {} to {}", orderId, newStatus);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(ErrorCodeOrder.ORDER_NOT_FOUND, "Cannot update status: Order not found with ID: " + orderId));

        order.setStatus(newStatus);
        Order updatedOrder;
        try {
            updatedOrder = orderRepository.save(order);
        } catch (Exception e) {
            logger.error("Failed to update status for order {}: {}", orderId, e.getMessage(), e);
            throw new OrderException(ErrorCodeOrder.ORDER_UPDATE_FAILED, "Could not save updated order status.", e);
        }
        logger.info("Order ID {} status updated to {}.", updatedOrder.getId(), updatedOrder.getStatus());

        if (newStatus == OrderStatus.CANCELLED) {
            handleOrderCancellationStockAdjustment(updatedOrder, authorizationHeader );
        }
        return mapOrderToResponseDTO(updatedOrder);
    }

    private void handleOrderCancellationStockAdjustment(Order cancelledOrder, String authorizationHeader) {
    logger.info("Order {} cancelled. Attempting to increase stock for items.", cancelledOrder.getId());
    try {
        for (OrderItem item : cancelledOrder.getItems()) {
            UpdateStockRequest request = new UpdateStockRequest(item.getQuantity()); // Tăng kho
            productServiceClient.increaseStock(item.getProductId(), request, authorizationHeader);
            logger.debug("Increased stock for product ID: {} by quantity: {}", item.getProductId(), item.getQuantity());
        }
    } catch (Exception e) {
        logger.error("CRITICAL: Failed to increase stock for cancelled order {}. Manual intervention needed. Error: {}",
                     cancelledOrder.getId(), e.getMessage(), e);
    }
}

    // Sửa tên phương thức và kiểu trả về để nhất quán với việc sử dụng OrderResponse
    private OrderResponse mapOrderToResponseDTO(Order order) { // Đổi tên từ mapOrderToResponseDTO thành mapOrderToResponse nếu muốn, hoặc giữ nguyên và đổi kiểu trả về
        OrderResponse dto = new OrderResponse(); // Sử dụng OrderResponse
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
                    .map(this::mapOrderItemToResponse) // Sử dụng mapOrderItemToResponse
                    .collect(Collectors.toList()));
        }
        return dto;
    }

    // Sửa tên phương thức và kiểu trả về để nhất quán với việc sử dụng OrderItemResponse
    private OrderItemResponse mapOrderItemToResponse(OrderItem item) { // Đổi tên từ mapOrderItemToResponseDTO thành mapOrderItemToResponse nếu muốn, hoặc giữ nguyên và đổi kiểu trả về
        OrderItemResponse dto = new OrderItemResponse(); // Sử dụng OrderItemResponse
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
            // Đảm bảo Order entity có các setter này (ví dụ: billingAddressLine1,...)
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
            // Đảm bảo Order entity có các getter này (ví dụ: getBillingAddressLine1,...)
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
