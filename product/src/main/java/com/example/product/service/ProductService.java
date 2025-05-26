package com.example.product.service;

import com.example.product.dto.request.ProductRequest;
import com.example.product.dto.response.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

public interface ProductService {
    ProductResponse createProduct(ProductRequest productRequest, MultipartFile imageFile);
    ProductResponse getProductById(Long id);
    Page<ProductResponse> findProducts(Long categoryId, String nameQuery, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);
    ProductResponse updateProduct(Long id, ProductRequest productRequest, MultipartFile imageFile);
    void deleteProduct(Long id);
    ProductResponse updateStock(Long id, int quantityChange);
    void activateProduct(Long id);
    void deactivateProduct(Long id);
    Page<ProductResponse> findMyProducts(Long categoryId, String nameQuery, Pageable pageable);
}