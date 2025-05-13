package com.example.product.service;

import com.example.product.dto.request.ProductRequest;
import com.example.product.dto.response.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductService {
    ProductResponse createProduct(ProductRequest productRequest);
    ProductResponse getProductById(Long id);
    Page<ProductResponse> findProducts(Long categoryId, String nameQuery, Pageable pageable);
    ProductResponse updateProduct(Long id, ProductRequest productRequest);
    void deleteProduct(Long id); // Soft delete
    ProductResponse updateStock(Long id, int quantityChange);
    void activateProduct(Long id);
    void deactivateProduct(Long id);
}