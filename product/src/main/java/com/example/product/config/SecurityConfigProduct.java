package com.example.product.config; // Hoặc package cấu hình của bạn

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer; // Đảm bảo import này đúng
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource; // Import CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
// import org.springframework.web.filter.CorsFilter; // Có thể không cần nếu dùng httpSecurity.cors(...)

import java.util.Arrays; // Import Arrays

@Configuration
@EnableWebSecurity
public class SecurityConfigProduct {

    // Danh sách các endpoint GET công khai của product-service
    private final String[] PUBLIC_GET_ENDPOINTS = {
            "/api/v1/products/**",      // Bao gồm GET /api/v1/products và GET /api/v1/products/{id}
            "/api/v1/categories/**"   // Bao gồm GET /api/v1/categories và GET /api/v1/categories/{id}
    };

    // Endpoint để tạo category (ví dụ)
    private final String CATEGORY_CREATE_ENDPOINT = "/api/v1/categories";
    // Endpoint để tạo product (ví dụ)
    private final String PRODUCT_CREATE_ENDPOINT = "/api/v1/products";
    private final String PRODUCT_PUT_ENDPOINT = "/api/v1/products/**";

    @Bean
    public SecurityFilterChain filterChainProduct(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(request ->
                        request
                                .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll() // Cho phép GET public
                                // Cho phép POST đến endpoint tạo category (tạm thời để test)
                                .requestMatchers(HttpMethod.POST, CATEGORY_CREATE_ENDPOINT).permitAll()
                                .requestMatchers(HttpMethod.PUT, CATEGORY_CREATE_ENDPOINT).permitAll()
                                .requestMatchers(HttpMethod.DELETE, CATEGORY_CREATE_ENDPOINT).permitAll()
                                // THÊM MỚI: Cho phép POST đến endpoint tạo product (tạm thời để test)
                                // Sau này, bạn nên bảo vệ endpoint này, ví dụ: yêu cầu role "SELLER" hoặc "ADMIN",
                                // hoặc ít nhất là .authenticated()
                                .requestMatchers(HttpMethod.POST, PRODUCT_CREATE_ENDPOINT).permitAll() // CHO PHÉP TẠM THỜI ĐỂ TEST
                                .requestMatchers(HttpMethod.PUT, PRODUCT_CREATE_ENDPOINT).permitAll()
                                .requestMatchers(HttpMethod.DELETE, PRODUCT_CREATE_ENDPOINT).permitAll()
                                // Cấu hình gốc của bạn (hoặc cấu hình bảo mật hơn):
                                .requestMatchers(HttpMethod.PUT, PRODUCT_PUT_ENDPOINT).permitAll()
                                .requestMatchers(HttpMethod.DELETE, PRODUCT_PUT_ENDPOINT).permitAll()
                                .requestMatchers(HttpMethod.GET, PRODUCT_PUT_ENDPOINT).permitAll()
                                .anyRequest().authenticated() // Tất cả các request khác cần xác thực
                );

        // Nếu product-service cũng dùng JWT để xác thực token (ví dụ từ user-service),
        // bạn cần cấu hình oauth2ResourceServer tương tự như user-service.
        // Bỏ comment và cấu hình nếu bạn dùng JWT:
        /*
        httpSecurity.oauth2ResourceServer(oauth2 ->
                oauth2.jwt(Customizer.withDefaults()) // Cấu hình JWT resource server cơ bản
        );
        */

        return httpSecurity.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(
                "http://127.0.0.1:5500",
                "http://localhost:5500"
                // Thêm các origin khác nếu cần
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
