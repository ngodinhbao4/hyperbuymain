package com.example.product.controller;

import com.example.product.dto.response.ApiResponse;
import com.example.product.dto.response.ProductResponse;
import com.example.product.service.AiRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
public class AiRecommendationController {

    private final AiRecommendationService aiRecommendationService;

    // 🟢 Gợi ý cho chính user hiện tại
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getMyRecommendations(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "12") int limit
    ) {
        String username = jwt.getSubject(); // subject = username

        List<ProductResponse> products =
                aiRecommendationService.getRecommendationsForUser(username, limit);

        ApiResponse<List<ProductResponse>> response = ApiResponse.<List<ProductResponse>>builder()
                .code(1000)
                .message("Lấy danh sách gợi ý AI thành công")
                .result(products)
                .build();

        return ResponseEntity.ok(response);
    }

    // 🟡 Endpoint debug: gợi ý cho username bất kỳ (dùng lúc test Postman)
    @GetMapping("/{username}")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getRecommendationsForUser(
            @PathVariable String username,
            @RequestParam(defaultValue = "12") int limit
    ) {
        List<ProductResponse> products =
                aiRecommendationService.getRecommendationsForUser(username, limit);

        ApiResponse<List<ProductResponse>> response = ApiResponse.<List<ProductResponse>>builder()
                .code(1000)
                .message("Lấy danh sách gợi ý AI cho user " + username + " thành công")
                .result(products)
                .build();

        return ResponseEntity.ok(response);
    }
}
