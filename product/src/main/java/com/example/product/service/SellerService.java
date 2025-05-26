package com.example.product.service;

import com.example.product.dto.request.ProductRequest;
import com.example.product.dto.response.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

public interface SellerService {
    ProductResponse createProduct(ProductRequest productRequest, MultipartFile imageFile, String token);
    ProductResponse updateProduct(Long id, ProductRequest productRequest, MultipartFile imageFile, String token);
    void deleteProduct(Long id, String token);
    Page<ProductResponse> getSellerProducts(Long categoryId, String nameQuery, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable, String token);
    ProductResponse updateStock(Long id, int quantityChange, String token);
}