package com.example.product.config; // Hoặc package chứa lớp SecurityConfig của bạn

// CÁC IMPORT CẦN THIẾT CHO LOGGER VÀ POSTCONSTRUCT
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod; // Cần import HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource; // Đảm bảo import đúng
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfigProduct {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfigProduct.class);

    @Value("${app.static-resource.public-path-pattern}")
    private String publicImagesPathPattern; // Sẽ có giá trị là /product-images/**

    // Danh sách các endpoint công khai
    // publicImagesPathPattern đã được xử lý riêng, có thể không cần đưa vào đây nữa
    // nếu PUBLIC_ENDPOINTS chỉ dành cho các API endpoints khác.
    private final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/**",
            "/api/v1/products/**",    // Bao gồm tất cả HTTP methods cho products (cân nhắc chỉ cho phép GET nếu không cần xác thực cho POST/PUT/DELETE)
            "/api/v1/categories/**",  // Bao gồm tất cả HTTP methods cho categories (tương tự, cân nhắc chỉ GET)
            // "/product-images/**" // Đã được xử lý bởi publicImagesPathPattern, có thể bỏ ở đây để tránh trùng lặp và rõ ràng hơn
    };

    @PostConstruct // Đảm bảo phương thức này chạy sau khi dependency injection
    public void displayInjectedValues() {
        logger.info("Giá trị publicImagesPathPattern được inject trong SecurityConfigProduct: '{}'", publicImagesPathPattern);
    }

    @Bean
    public SecurityFilterChain filterChainProduct(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable) // Tắt CSRF (cân nhắc kỹ cho môi trường production)
                .authorizeHttpRequests(request ->
                        request
                                // Cho phép GET request đến đường dẫn phục vụ ảnh
                                .requestMatchers(HttpMethod.GET, publicImagesPathPattern).permitAll()
                                .requestMatchers("/api/v1/products/store/**").permitAll() // Cho phép truy cập công khai
                                .requestMatchers(HttpMethod.PUT, publicImagesPathPattern).permitAll()
                                .requestMatchers(HttpMethod.GET, "api/v1/products").permitAll()
                                // .requestMatchers(publicImagesPathPattern).permitAll() // Dòng này thừa nếu đã có dòng trên và publicImagesPathPattern chỉ dành cho ảnh
                                .requestMatchers(PUBLIC_ENDPOINTS).permitAll() // Cho phép các endpoint công khai khác
                                .anyRequest().authenticated() // Tất cả các yêu cầu khác cần được xác thực
                );
        return httpSecurity.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Nếu muốn cho phép các origin cụ thể:
        configuration.setAllowedOrigins(Arrays.asList(
                "http://127.0.0.1:5500",
                "http://localhost:5500",
                "http://192.168.0.29:3000" // Frontend LAN
                // "http://192.168.0.29:8081" // Thường không cần nếu frontend và backend cùng origin khi gọi từ frontend
        ));
        // HOẶC nếu muốn cho phép TẤT CẢ các origin (thường dùng cho dev, cẩn thận với production):
        // configuration.setAllowedOriginPatterns(List.of("*"));

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*")); // Cho phép tất cả các header
        configuration.setAllowCredentials(true); // Cho phép gửi cookie và thông tin xác thực
        configuration.setMaxAge(3600L); // Thời gian pre-flight request được cache (giây)

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // Áp dụng cấu hình CORS cho tất cả các path
        return source;
    }
}
