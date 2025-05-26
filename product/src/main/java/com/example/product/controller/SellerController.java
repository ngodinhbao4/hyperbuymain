package com.example.product.controller;

import com.example.product.dto.request.ProductRequest;
import com.example.product.dto.request.UpdateStockRequest;
import com.example.product.dto.response.ProductResponse;
import com.example.product.service.SellerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/seller")
@RequiredArgsConstructor
public class SellerController {

    private final SellerService sellerService;

    @PostMapping(value = "/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestPart("productRequest") ProductRequest productRequest,
            @RequestPart(value = "imageFile", required = false) MultipartFile imageFile,
            @RequestHeader("Authorization") String token) {
        ProductResponse createdProduct = sellerService.createProduct(productRequest, imageFile, token);
        return new ResponseEntity<>(createdProduct, HttpStatus.CREATED);
    }

    @PutMapping(value = "/products/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestPart("productRequest") ProductRequest productRequest,
            @RequestPart(value = "imageFile", required = false) MultipartFile imageFile,
            @RequestHeader("Authorization") String token) {
        ProductResponse updatedProduct = sellerService.updateProduct(id, productRequest, imageFile, token);
        return ResponseEntity.ok(updatedProduct);
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable Long id,
            @RequestHeader("Authorization") String token) {
        sellerService.deleteProduct(id, token);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/products")
    public ResponseEntity<Page<ProductResponse>> getSellerProducts(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @PageableDefault(size = 10, sort = "name", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestHeader("Authorization") String token) {
        Page<ProductResponse> products = sellerService.getSellerProducts(categoryId, q, minPrice, maxPrice, pageable, token);
        return ResponseEntity.ok(products);
    }

    // Quản lý tồn kho
    @PutMapping("/products/{id}/stock")
    public ResponseEntity<ProductResponse> updateStock(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStockRequest updateStockRequest,
            @RequestHeader("Authorization") String token) {
        ProductResponse updatedProduct = sellerService.updateStock(id, updateStockRequest.getChange(), token);
        return ResponseEntity.ok(updatedProduct);
    }
}