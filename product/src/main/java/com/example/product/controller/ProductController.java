package com.example.product.controller;

import com.example.product.dto.request.ProductRequest;
import com.example.product.dto.response.ApiResponse;
import com.example.product.dto.response.ProductResponse;
import com.example.product.dto.request.UpdateStockRequest;
import com.example.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Sort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Slf4j
public class ProductController {

    private final ProductService productService;
    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestPart("productRequest") ProductRequest productRequest,
            @RequestPart(value = "imageFile", required = false) MultipartFile imageFile,
            @RequestHeader(value = "Authorization", required = true) String token) {
        ProductResponse createdProduct = productService.createProduct(productRequest, imageFile, token);
        return new ResponseEntity<>(createdProduct, HttpStatus.CREATED);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestPart("productRequest") ProductRequest productRequest,
            @RequestPart(value = "imageFile", required = false) MultipartFile imageFile,
            @RequestHeader(value = "Authorization", required = true) String token) {
        ProductResponse updatedProduct = productService.updateProduct(id, productRequest, imageFile, token);
        return ResponseEntity.ok(updatedProduct);
    }

    @PutMapping(value = "/{id}/stock", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductResponse> updateStock(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStockRequest updateStockRequest,
            @RequestHeader(value = "Authorization", required = true) String token) {
        ProductResponse updatedProduct = productService.updateStock(id, updateStockRequest.getChange(), token);
        return ResponseEntity.ok(updatedProduct);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = true) String token) {
        ProductResponse product = productService.getProductById(id, token);
        return ResponseEntity.ok(product);
    }

    @GetMapping
    public ResponseEntity<Page<ProductResponse>> findProducts(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @PageableDefault(size = 10, sort = "name", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestHeader(value = "Authorization", required = true) String token) {
        Page<ProductResponse> productPage = productService.findProducts(categoryId, q, minPrice, maxPrice, pageable, token);
        return ResponseEntity.ok(productPage);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @PathVariable Long id,
            @RequestHeader(value = "X-Store-Id", required = true) String storeId) {
        try {
            productService.deleteProduct(id, storeId);
            ApiResponse<Void> response = ApiResponse.<Void>builder()
                    .code(1000)
                    .message("Xóa sản phẩm thành công")
                    .build();
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            logger.error("Lỗi khi xóa sản phẩm với ID: {} bởi storeId: {}", id, storeId, e);
            ApiResponse<Void> errorResponse = ApiResponse.<Void>builder()
                    .code(4000)
                    .message(e.getMessage())
                    .build();
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
        }
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activateProduct(
            @PathVariable Long id,
            @RequestHeader(value = "X-Store-Id", required = true) String storeId) {
        try {
            productService.activateProduct(id, storeId);
            ApiResponse<Void> response = ApiResponse.<Void>builder()
                    .code(1000)
                    .message("Kích hoạt sản phẩm thành công")
                    .build();
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            logger.error("Lỗi khi kích hoạt sản phẩm với ID: {} bởi storeId: {}", id, storeId, e);
            ApiResponse<Void> errorResponse = ApiResponse.<Void>builder()
                    .code(4000)
                    .message(e.getMessage())
                    .build();
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
        }
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivateProduct(
            @PathVariable Long id,
            @RequestHeader(value = "X-Store-Id", required = true) String storeId) {
        try {
            productService.deactivateProduct(id, storeId);
            ApiResponse<Void> response = ApiResponse.<Void>builder()
                    .code(1000)
                    .message("Hủy kích hoạt sản phẩm thành công")
                    .build();
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            logger.error("Lỗi khi hủy kích hoạt sản phẩm với ID: {} bởi storeId: {}", id, storeId, e);
            ApiResponse<Void> errorResponse = ApiResponse.<Void>builder()
                    .code(4000)
                    .message(e.getMessage())
                    .build();
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
        }
    }

    @GetMapping("/store/{storeId}")
    public ResponseEntity<List<Map<String, Object>>> getProductsByStoreId(
            @PathVariable String storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        logger.info("Lấy danh sách sản phẩm cho storeId: {}, page: {}, size: {}", storeId, page, size);
        Pageable pageable = PageRequest.of(page, size);
        List<Map<String, Object>> products = productService.findProductsByStoreId(storeId, pageable);
        return ResponseEntity.ok(products);
    }
}