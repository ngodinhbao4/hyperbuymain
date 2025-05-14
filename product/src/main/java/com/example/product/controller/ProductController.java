package com.example.product.controller;

import com.example.product.dto.request.ProductRequest;
import com.example.product.dto.response.ProductResponse;
import com.example.product.dto.request.UpdateStockRequest;
import com.example.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault; // Để set default pageable
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products") // Base path cho product API
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest productRequest) {
        ProductResponse createdProduct = productService.createProduct(productRequest);
        return new ResponseEntity<>(createdProduct, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long id) {
        ProductResponse product = productService.getProductById(id);
        return ResponseEntity.ok(product);
    }

    @GetMapping
    public ResponseEntity<Page<ProductResponse>> findProducts(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String q, // Tham số tìm kiếm theo tên
            // PageableDefault để đặt giá trị mặc định cho phân trang/sắp xếp nếu client không gửi
            @PageableDefault(size = 10, sort = "name") Pageable pageable) {

        Page<ProductResponse> productPage = productService.findProducts(categoryId, q, pageable);
        return ResponseEntity.ok(productPage);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(@PathVariable Long id,
                                                     @Valid @RequestBody ProductRequest productRequest) {
        ProductResponse updatedProduct = productService.updateProduct(id, productRequest);
        return ResponseEntity.ok(updatedProduct);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
       
        productService.deleteProduct(id);
        return ResponseEntity.noContent()
            .build();
    }

    @PatchMapping("/{id}/stock") // Dùng PATCH vì chỉ cập nhật một phần (tồn kho)
    public ResponseEntity<ProductResponse> updateStock(@PathVariable Long id,
                                                 @Valid @RequestBody UpdateStockRequest updateStockRequest) {
       ProductResponse updatedProduct = productService.updateStock(id, updateStockRequest.getChange());
       return ResponseEntity.ok(updatedProduct);
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activateProduct(@PathVariable Long id) {
        productService.activateProduct(id);
        return ResponseEntity.ok().build();
    }

     @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateProduct(@PathVariable Long id) {
        productService.deactivateProduct(id);
        return ResponseEntity.ok().build();
    }
}