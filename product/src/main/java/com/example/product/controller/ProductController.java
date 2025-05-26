package com.example.product.controller;

import com.example.product.dto.request.ProductRequest;
import com.example.product.dto.response.ProductResponse;
import com.example.product.dto.request.UpdateStockRequest;
import com.example.product.service.ProductService;
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
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestPart("productRequest") ProductRequest productRequest,
            @RequestPart(value = "imageFile", required = false) MultipartFile imageFile) {
        ProductResponse createdProduct = productService.createProduct(productRequest, imageFile);
        return new ResponseEntity<>(createdProduct, HttpStatus.CREATED);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestPart("productRequest") ProductRequest productRequest,
            @RequestPart(value = "imageFile", required = false) MultipartFile imageFile) {
        ProductResponse updatedProduct = productService.updateProduct(id, productRequest, imageFile);
        return ResponseEntity.ok(updatedProduct);
    }

    @PutMapping(value = "/{id}/stock", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductResponse> updateStock(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStockRequest updateStockRequest) {
        ProductResponse updatedProduct = productService.updateStock(id, updateStockRequest.getChange());
        return ResponseEntity.ok(updatedProduct);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long id) {
        ProductResponse product = productService.getProductById(id);
        return ResponseEntity.ok(product);
    }

    @GetMapping
    public ResponseEntity<Page<ProductResponse>> findProducts(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @PageableDefault(size = 10, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<ProductResponse> productPage = productService.findProducts(categoryId, q, minPrice, maxPrice, pageable);
        return ResponseEntity.ok(productPage);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
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