package com.example.product.service; // Giữ nguyên package của bạn

import com.example.product.dto.request.ProductRequest;
import com.example.product.dto.response.ProductResponse;
import com.example.product.entity.Category;
import com.example.product.entity.Product;
// Import AppException và ErrorCode
import com.example.product.exception.AppException;
import com.example.product.exception.ErrorCode;
import com.example.product.repository.CategoryRepository;
import com.example.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public ProductResponse createProduct(ProductRequest productRequest) {
        Category category = categoryRepository.findById(productRequest.getCategoryId())
                // Ném AppException với ErrorCode tương ứng
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        if (StringUtils.hasText(productRequest.getSku())) {
            productRepository.findBySkuAndIsDeletedFalseAndIsActiveTrue(productRequest.getSku()).stream().findAny().ifPresent(p -> {
                // Ném AppException với ErrorCode tương ứng
                throw new AppException(ErrorCode.PRODUCT_SKU_EXISTED);
            });
        }

        Product product = mapToEntity(productRequest, category);
        product.setActive(true);
        product.setDeleted(false);

        Product savedProduct = productRepository.save(product);
        return mapToResponse(savedProduct);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findById(id)
                .filter(p -> !p.isDeleted())
                // Ném AppException với ErrorCode tương ứng
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        return mapToResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> findProducts(Long categoryId, String nameQuery, Pageable pageable) {
        Page<Product> productPage = productRepository.searchProducts(categoryId, nameQuery, pageable);
        return productPage.map(this::mapToResponse);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest productRequest) {
        Product existingProduct = productRepository.findById(id)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        Category category = categoryRepository.findById(productRequest.getCategoryId())
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        if (StringUtils.hasText(productRequest.getSku())) {
            productRepository.findBySkuAndIsDeletedFalseAndIsActiveTrue(productRequest.getSku()).stream()
                    .filter(p -> !p.getId().equals(id))
                    .findAny().ifPresent(p -> {
                        throw new AppException(ErrorCode.PRODUCT_SKU_EXISTED);
                    });
        }

        existingProduct.setName(productRequest.getName());
        existingProduct.setDescription(productRequest.getDescription());
        existingProduct.setSku(productRequest.getSku());
        existingProduct.setPrice(productRequest.getPrice());
        existingProduct.setStockQuantity(productRequest.getStockQuantity());
        existingProduct.setImageUrl(productRequest.getImageUrl());
        existingProduct.setCategory(category);

        Product updatedProduct = productRepository.save(existingProduct);
        return mapToResponse(updatedProduct);
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        product.setDeleted(true);
        product.setActive(false);
        productRepository.save(product);
    }

    @Override
    @Transactional
    public ProductResponse updateStock(Long id, int quantityChange) {
        Product product = productRepository.findById(id)
                .filter(p -> !p.isDeleted() && p.isActive())
                // Có thể tạo một ErrorCode cụ thể hơn cho trường hợp này nếu muốn
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND)); // Hoặc một ErrorCode kiểu PRODUCT_INACTIVE_OR_DELETED

        int currentStock = product.getStockQuantity();
        int newStock = currentStock + quantityChange;

        if (newStock < 0) {
            // Ném AppException với ErrorCode tương ứng
            throw new AppException(ErrorCode.STOCK_QUANTITY_INVALID);
        }

        product.setStockQuantity(newStock);
        Product updatedProduct = productRepository.save(product);
        return mapToResponse(updatedProduct);
    }

    @Override
    @Transactional
    public void activateProduct(Long id) {
        Product product = productRepository.findById(id)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        if (product.isDeleted()) { // Kiểm tra thêm vì filter ở trên chỉ là !p.isDeleted() lúc tìm
            // Ném AppException với ErrorCode tương ứng
            throw new AppException(ErrorCode.CANNOT_ACTIVATE_DELETED_PRODUCT);
        }
        product.setActive(true);
        productRepository.save(product);
    }

    @Override
    @Transactional
    public void deactivateProduct(Long id) {
        Product product = productRepository.findById(id)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        product.setActive(false);
        productRepository.save(product);
    }

    private ProductResponse mapToResponse(Product product) {
        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setSku(product.getSku());
        response.setName(product.getName());
        response.setDescription(product.getDescription());
        response.setPrice(product.getPrice());
        response.setStockQuantity(product.getStockQuantity());
        response.setImageUrl(product.getImageUrl());
        response.setActive(product.isActive());
        response.setCreatedAt(product.getCreatedAt());
        response.setUpdatedAt(product.getUpdatedAt());
        if (product.getCategory() != null) {
            response.setCategoryId(product.getCategory().getId());
            response.setCategoryName(product.getCategory().getName());
        }
        return response;
    }

    private Product mapToEntity(ProductRequest request, Category category) {
        Product product = new Product();
        product.setSku(request.getSku());
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setImageUrl(request.getImageUrl());
        product.setCategory(category);
        return product;
    }
}