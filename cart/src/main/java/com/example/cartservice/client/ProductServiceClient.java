package com.example.cartservice.client;

import com.example.cartservice.dto.request.ProductDetailRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// name = "product-service" nếu dùng Service Discovery (Eureka, Consul)
// url = "${product.service.url}" nếu không dùng Service Discovery
@FeignClient(name = "product-service", url = "${product.service.url:http://localhost:8081}")
public interface ProductServiceClient {

    // Ví dụ tên fallback method, bạn cần tự implement
    // @CircuitBreaker(name = "productService", fallbackMethod = "getProductByIdFallback")
    @GetMapping("/api/v1/products/{productId}") // Điều chỉnh path cho đúng với ProductService
    ProductDetailRequest getProductById(@PathVariable("productId") String productId);

    // Fallback method (cần được implement trong cùng class hoặc một bean khác)
    // default ProductDetailDto getProductByIdFallback(String productId, Throwable t) {
    //     // Log lỗi, trả về DTO mặc định hoặc null
    //     System.err.println("Fallback for getProductById: " + productId + ", error: " + t.getMessage());
    //     ProductDetailDto fallbackDto = new ProductDetailDto();
    //     fallbackDto.setId(productId);
    //     fallbackDto.setName("Product information unavailable");
    //     fallbackDto.setPrice(BigDecimal.ZERO);
    //     return fallbackDto;
    // }
}