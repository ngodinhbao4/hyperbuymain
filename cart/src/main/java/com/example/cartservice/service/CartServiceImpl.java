// Package: com.example.cartservice.service.impl
package com.example.cartservice.service;

import com.example.cartservice.client.ProductServiceClient;
import com.example.cartservice.dto.request.CartItemRequest; // Giả sử CartItemRequestDto nằm trong .request
import com.example.cartservice.dto.response.CartResponse; // Giả sử CartResponseDto nằm trong .response
import com.example.cartservice.dto.response.CartItemResponse; // Giả sử CartItemResponseDto nằm trong .response
import com.example.cartservice.dto.request.ProductDetailRequest; // DTO cho thông tin sản phẩm từ ProductService
import com.example.cartservice.entity.Cart;
import com.example.cartservice.entity.CartItem;
import com.example.cartservice.exception.CartException;
import com.example.cartservice.exception.ErrorCodeCart;
import com.example.cartservice.mapper.CartItemMapper;
import com.example.cartservice.mapper.CartMapper;
import com.example.cartservice.repository.CartItemRepository;
import com.example.cartservice.repository.CartRepository;

import feign.FeignException; // Import FeignException
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductServiceClient productServiceClient;
    private final CartMapper cartMapper;
    private final CartItemMapper cartItemMapper;

    // --- Helper Methods ---

    /**
     * Lấy entity Cart của user, nếu chưa có thì tạo mới.
     */
    private Cart getOrCreateCartEntityByUserId(String userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> {
                    log.info("No cart found for user {}, creating a new one.", userId);
                    Cart newCart = new Cart(userId); // Giả sử Cart constructor chấp nhận userId
                    return cartRepository.save(newCart);
                });
    }

    /**
     * Gọi ProductService để lấy thông tin chi tiết sản phẩm.
     * Ném CartException nếu có lỗi.
     */
    private ProductDetailRequest fetchProductDetailsOrFail(String productId) {
        try {
            log.debug("Fetching product details for productId: {}", productId);
            ProductDetailRequest productDetail = productServiceClient.getProductById(productId);
            if (productDetail == null || productDetail.getId() == null) {
                log.warn("ProductService returned null or invalid data for productId: {}", productId);
                throw new CartException(ErrorCodeCart.PRODUCT_NOT_AVAILABLE, "Product details are invalid for ID: " + productId);
            }
            return productDetail;
        } catch (FeignException e) {
            log.error("FeignException while calling ProductService for productId {}: status={}, message={}", productId, e.status(), e.getMessage(), e);
            if (e.status() == 404) { // Not Found
                throw new CartException(ErrorCodeCart.PRODUCT_NOT_AVAILABLE, "Product with ID " + productId + " not found via Product Service.");
            }
            // Các lỗi Feign khác (5xx, timeout, etc.)
            throw new CartException(ErrorCodeCart.PRODUCT_SERVICE_UNREACHABLE, "Could not retrieve product details for ID: " + productId + ". Service might be down.");
        } catch (Exception e) { // Các lỗi không mong muốn khác khi gọi ProductService
            log.error("Unexpected exception while fetching product details for ID {}: {}", productId, e.getMessage(), e);
            throw new CartException(ErrorCodeCart.PRODUCT_SERVICE_UNREACHABLE, "Unexpected error fetching product details for ID: " + productId + ".");
        }
    }

    /**
     * Chuyển đổi Cart entity sang CartResponseDto, bao gồm làm giàu thông tin sản phẩm.
     */
    private CartResponse mapCartToEnrichedDto(Cart cart) {
        CartResponse cartDto = cartMapper.toCartResponse(cart); // Mapper cơ bản

        if (cartDto.getItems() != null && !cartDto.getItems().isEmpty()) {
            List<CartItemResponse> enrichedItems = cart.getItems().stream()
                .map(cartItemEntity -> {
                    ProductDetailRequest productDetail = fetchProductDetailsOrFail(cartItemEntity.getProductId());
                    return cartItemMapper.toCartItemResponse(cartItemEntity, productDetail);
                })
                .collect(Collectors.toList());
            cartDto.setItems(enrichedItems);
            // Recalculate totals in DTO if CartMapper doesn't handle enriched items for totals
            // (Giả sử CartMapper.calculateTotalQuantity và CartMapper.calculateGrandTotal
            // hoạt động dựa trên List<CartItem> của entity, không phải List<CartItemResponseDto> đã làm giàu)
            // Nếu cần, bạn có thể tính toán lại ở đây hoặc điều chỉnh CartMapper.
        }
        return cartDto;
    }

    // --- CartService Interface Implementations ---

   @Override
@Transactional(readOnly = true)
public CartResponse getCartByUserId(String userId) {
    if (userId == null || userId.trim().isEmpty()) {
        throw new CartException(ErrorCodeCart.INVALID_CART_REQUEST, "User ID cannot be empty for fetching cart.");
    }
    log.info("Fetching cart for user ID: {}", userId);
    Cart cart = getOrCreateCartEntityByUserId(userId);
    try {
        CartResponse cartResponse = mapCartToEnrichedDto(cart);
        log.info("Successfully fetched cart for user {}: {}", userId, cartResponse);
        return cartResponse;
    } catch (CartException e) {
        log.error("CartException while mapping cart for user {}: {}", userId, e.getMessage(), e);
        CartResponse cartResponse = cartMapper.toCartResponse(cart);
        cartResponse.setItems(List.of()); // Trả về items rỗng nếu lỗi
        return cartResponse;
    } catch (Exception e) {
        log.error("Unexpected error while mapping cart for user {}: {}", userId, e.getMessage(), e);
        CartResponse cartResponse = cartMapper.toCartResponse(cart);
        cartResponse.setItems(List.of()); // Trả về items rỗng nếu lỗi
        return cartResponse;
    }
}

    @Override
    @Transactional
    public CartResponse addItemToCart(String userId, CartItemRequest itemRequestDto) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new CartException(ErrorCodeCart.INVALID_CART_REQUEST, "User ID cannot be empty for adding item to cart.");
        }
        log.info("Adding item to cart for user ID: {}. Item: {}", userId, itemRequestDto);

        Cart cart = getOrCreateCartEntityByUserId(userId);
        ProductDetailRequest productDetail = fetchProductDetailsOrFail(itemRequestDto.getProductId());

        // Kiểm tra tồn kho
        int requestedQuantity = itemRequestDto.getQuantity();
        if (productDetail.getStockQuantity() == null || productDetail.getStockQuantity() < requestedQuantity) {
            throw new CartException(ErrorCodeCart.INSUFFICIENT_STOCK,
                    "Not enough stock for product '" + productDetail.getName() +
                    "'. Available: " + (productDetail.getStockQuantity() == null ? 0 : productDetail.getStockQuantity()) +
                    ", Requested: " + requestedQuantity);
        }

        Optional<CartItem> existingItemOpt = cart.getItems().stream()
                .filter(item -> item.getProductId().equals(itemRequestDto.getProductId()))
                .findFirst();

        if (existingItemOpt.isPresent()) {
            CartItem existingItem = existingItemOpt.get();
            int newQuantity = existingItem.getQuantity() + requestedQuantity;
            // Kiểm tra lại tồn kho cho số lượng mới
            if (productDetail.getStockQuantity() < newQuantity) {
                throw new CartException(ErrorCodeCart.INSUFFICIENT_STOCK,
                        "Not enough stock to increase quantity for product '" + productDetail.getName() +
                        "'. Available: " + productDetail.getStockQuantity() + ", Requested total: " + newQuantity);
            }
            existingItem.setQuantity(newQuantity);
            // Giá tại thời điểm thêm (priceAtAddition) không thay đổi khi chỉ tăng số lượng
            cartItemRepository.save(existingItem);
            log.info("Updated quantity for product {} in cart {} to {}", itemRequestDto.getProductId(), cart.getId(), newQuantity);
        } else {
            CartItem newItem = new CartItem();
            newItem.setCart(cart);
            newItem.setProductId(itemRequestDto.getProductId());
            newItem.setQuantity(requestedQuantity);
            newItem.setPriceAtAddition(productDetail.getPrice()); // Lưu giá tại thời điểm thêm
            cart.addItem(newItem); // Thêm vào list của Cart, cascade save CartItem
            // cartItemRepository.save(newItem); // Không cần nếu cascade ALL từ Cart
            log.info("Added new product {} to cart {}", itemRequestDto.getProductId(), cart.getId());
        }

        cartRepository.save(cart); // Cập nhật updatedAt của Cart và cascade CartItem nếu là new
        return mapCartToEnrichedDto(cart);
    }

    @Override
    @Transactional
    public CartResponse updateCartItemQuantity(String userId, String productId, Integer newQuantity) {
         if (userId == null || userId.trim().isEmpty()) {
            throw new CartException(ErrorCodeCart.INVALID_CART_REQUEST, "User ID cannot be empty for updating item quantity.");
        }
        if (productId == null || productId.trim().isEmpty()) {
            throw new CartException(ErrorCodeCart.INVALID_CART_REQUEST, "Product ID cannot be empty for updating item quantity.");
        }
        if (newQuantity == null || newQuantity < 0) { // Cho phép newQuantity = 0 để xóa (hoặc dùng API riêng)
            throw new CartException(ErrorCodeCart.INVALID_CART_REQUEST, "New quantity must be zero or positive.");
        }

        log.info("Updating quantity for product {} in cart of user {} to {}", productId, userId, newQuantity);

        if (newQuantity == 0) {
            return removeItemFromCart(userId, productId); // Nếu số lượng bằng 0 thì xóa sản phẩm
        }

        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new CartException(ErrorCodeCart.CART_NOT_FOUND));

        CartItem cartItem = cart.getItems().stream()
                .filter(item -> item.getProductId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new CartException(ErrorCodeCart.PRODUCT_NOT_FOUND_IN_CART, "Product " + productId + " not in cart."));

        ProductDetailRequest productDetail = fetchProductDetailsOrFail(productId);
        if (productDetail.getStockQuantity() == null || productDetail.getStockQuantity() < newQuantity) {
            throw new CartException(ErrorCodeCart.INSUFFICIENT_STOCK,
                    "Not enough stock for product '" + productDetail.getName() +
                    "'. Available: " + (productDetail.getStockQuantity() == null ? 0 : productDetail.getStockQuantity()) +
                    ", Requested: " + newQuantity);
        }

        cartItem.setQuantity(newQuantity);
        cartItemRepository.save(cartItem);
        cartRepository.save(cart); // Cập nhật updatedAt của Cart
        log.info("Successfully updated quantity for product {} in cart {} to {}", productId, cart.getId(), newQuantity);
        return mapCartToEnrichedDto(cart);
    }

    @Override
    @Transactional
    public CartResponse removeItemFromCart(String userId, String productId) {
         if (userId == null || userId.trim().isEmpty()) {
            throw new CartException(ErrorCodeCart.INVALID_CART_REQUEST, "User ID cannot be empty for removing item.");
        }
        if (productId == null || productId.trim().isEmpty()) {
            throw new CartException(ErrorCodeCart.INVALID_CART_REQUEST, "Product ID cannot be empty for removing item.");
        }
        log.info("Removing product {} from cart of user {}", productId, userId);

        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new CartException(ErrorCodeCart.CART_NOT_FOUND));

        CartItem itemToRemove = cart.getItems().stream()
            .filter(item -> item.getProductId().equals(productId))
            .findFirst()
            .orElseThrow(() -> new CartException(ErrorCodeCart.PRODUCT_NOT_FOUND_IN_CART, "Product " + productId + " not in cart to remove."));

        // cart.getItems().remove(itemToRemove); // Sẽ hoạt động nếu orphanRemoval=true và Cart được save
        // Hoặc xóa trực tiếp CartItem
        cart.getItems().remove(itemToRemove);
        // Cần đảm bảo list trong Cart entity cũng được cập nhật nếu không dùng orphanRemoval
        // Để an toàn, load lại cart sau khi xóa item
        
        Cart updatedCart = cartRepository.findById(cart.getId())
                                 .orElseThrow(() -> new CartException(ErrorCodeCart.CART_NOT_FOUND)); // Không nên xảy ra
        cartRepository.save(updatedCart);
         // Cập nhật updatedAt
        log.info("Successfully removed product {} from cart {}", productId, cart.getId());
        return mapCartToEnrichedDto(updatedCart);
    }

    @Override
    @Transactional
    public void clearCart(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new CartException(ErrorCodeCart.INVALID_CART_REQUEST, "User ID cannot be empty for clearing cart.");
        }
        log.info("Clearing cart for user ID: {}", userId);
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new CartException(ErrorCodeCart.CART_NOT_FOUND));

        // Xóa tất cả các CartItem liên quan đến Cart này
        // Cách 1: Nếu có cascade và orphanRemoval=true trên Cart.items
        // cart.getItems().clear();
        // cartRepository.save(cart);

        // Cách 2: Xóa trực tiếp từ CartItemRepository (thường hiệu quả hơn nếu nhiều items)
        if (cart.getItems() != null && !cart.getItems().isEmpty()) {
            cartItemRepository.deleteAll(cart.getItems()); // Xóa các items đã được load
            // Hoặc cartItemRepository.deleteAllByCart_Id(cart.getId()); // Nếu bạn có phương thức này
        }
        // Sau khi xóa items, list items trong entity Cart cần được clear
        cart.getItems().clear();
        cartRepository.save(cart); // Cập nhật updatedAt của Cart
        log.info("Successfully cleared cart for user ID: {}", userId);
    }

    public String getOrCreateCartIdForUser(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            log.warn("Attempted to get or create cart with null or empty userId.");
            throw new CartException(ErrorCodeCart.INVALID_CART_REQUEST, "User ID cannot be null or empty.");
        }

        log.info("Attempting to get or create cart for user ID: {}", userId);

        try {
            // 2. Tìm kiếm giỏ hàng hiện có của người dùng
            Optional<Cart> existingCartOpt = cartRepository.findByUserId(userId);

            if (existingCartOpt.isPresent()) {
                // 3. Nếu giỏ hàng đã tồn tại, trả về ID của nó
                Cart existingCart = existingCartOpt.get();
                log.info("Cart found for user {}. Cart ID: {}", userId, existingCart.getId());
                return existingCart.getId();
            } else {
                // 4. Nếu giỏ hàng chưa tồn tại, tạo một giỏ hàng mới
                log.info("No cart found for user {}. Creating a new cart.", userId);
                
                // Tạo thực thể Cart mới.
                // Giả định constructor Cart(String userId) sẽ tự động tạo một ID duy nhất (ví dụ UUID).
                // Nếu không, bạn cần tạo ID ở đây:
                // String newCartId = UUID.randomUUID().toString();
                // Cart newCart = new Cart(newCartId, userId); // Nếu constructor là Cart(String id, String userId)
                Cart newCart = new Cart(userId); // Sử dụng constructor Cart(String userId)

                // Lưu giỏ hàng mới vào cơ sở dữ liệu
                Cart savedCart = cartRepository.save(newCart);
                log.info("Successfully created and saved a new cart for user {}. New Cart ID: {}", userId, savedCart.getId());
                
                // Trả về ID của giỏ hàng mới tạo
                return savedCart.getId();
            }
        } catch (CartException ce) {
            // Ném lại CartException đã được xử lý (ví dụ từ validation)
            throw ce;
        } catch (DataAccessException dae) { // Bắt các lỗi truy cập dữ liệu cụ thể từ Spring
            log.error("Data access error while getting or creating cart for user {}: {}", userId, dae.getMessage(), dae);
            throw new CartException(ErrorCodeCart.UNCATEGORIZED_CART_EXCEPTION, "A database error occurred while processing the cart.");
        } catch (Exception e) {
            // Bắt các lỗi không mong muốn khác
            log.error("Unexpected error while getting or creating cart for user {}: {}", userId, e.getMessage(), e);
            throw new CartException(ErrorCodeCart.UNCATEGORIZED_CART_EXCEPTION, "An unexpected error occurred.");
        }
    }
}