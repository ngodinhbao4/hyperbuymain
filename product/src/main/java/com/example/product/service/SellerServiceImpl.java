package com.example.product.service;

import com.example.product.dto.request.ProductRequest;
import com.example.product.dto.response.ProductResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class SellerServiceImpl implements SellerService {

    private static final Logger logger = LoggerFactory.getLogger(SellerServiceImpl.class);

    private final ProductService productService;

    @Override
    @Transactional
    public ProductResponse createProduct(ProductRequest productRequest, MultipartFile imageFile, String token) {
        logger.info("Tạo sản phẩm cho seller");
        // Giả định token đã được xác thực, gọi ProductService trực tiếp
        return productService.createProduct(productRequest, imageFile);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest productRequest, MultipartFile imageFile, String token) {
        logger.info("Cập nhật sản phẩm ID: {}", id);
        return productService.updateProduct(id, productRequest, imageFile);
    }

    @Override
    @Transactional
    public void deleteProduct(Long id, String token) {
        logger.info("Xóa sản phẩm ID: {}", id);
        productService.deleteProduct(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getSellerProducts(Long categoryId, String nameQuery, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable, String token) {
        logger.info("Lấy danh sách sản phẩm của seller");
        return productService.findProducts(categoryId, nameQuery, minPrice, maxPrice, pageable);
    }

    @Override
    @Transactional
    public ProductResponse updateStock(Long id, int quantityChange, String token) {
        logger.info("Cập nhật tồn kho sản phẩm ID: {}", id);
        return productService.updateStock(id, quantityChange);
    }
}