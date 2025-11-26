package com.example.product.service.impl;

import com.example.product.dto.response.ProductResponse;
import com.example.product.entity.AiRecommendation;
import com.example.product.entity.Product;
import com.example.product.repository.AiRecommendationRepository;
import com.example.product.repository.ProductRepository;
import com.example.product.service.AiRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiRecommendationServiceImpl implements AiRecommendationService {

    private final AiRecommendationRepository aiRecommendationRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getRecommendationsForUser(String username, int limit) {

    // 1. Lấy top 50 sản phẩm có score cao nhất
        List<AiRecommendation> recs =
                aiRecommendationRepository.findTop50ByUsernameOrderByPredictedScoreDesc(username);

// ⭐ Fallback nếu user không có dữ liệu AI
        if (recs.isEmpty()) {
            return getRecommendationsForGuest(limit);   // ✔ ĐÚNG
        }

// 🔥 Shuffle nhẹ để thay đổi kết quả mỗi lần load
        Collections.shuffle(recs);

// 2. Giới hạn số lượng trả về
        if (limit > 0 && recs.size() > limit) {
            recs = recs.subList(0, limit);
        }

// 3. Lấy productId
        List<Long> productIds = recs.stream()
                .map(AiRecommendation::getProductId)
                .collect(Collectors.toList());

// 4. Query Product từ DB
        List<Product> products = productRepository.findAllById(productIds);

        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

// 5. Convert sang ProductResponse theo thứ tự recs
        List<ProductResponse> result = new ArrayList<>();

        for (AiRecommendation rec : recs) {
            Product p = productMap.get(rec.getProductId());
            if (p != null) {
                result.add(toProductResponse(p));
            }
        }

        return result;
    }

    // Map tối thiểu Product -> ProductResponse; sellerInfo để null cho JSON bỏ qua
    private ProductResponse toProductResponse(Product product) {
        ProductResponse res = new ProductResponse();
        res.setId(product.getId());
        res.setSku(product.getSku());
        res.setName(product.getName());
        res.setDescription(product.getDescription());
        res.setPrice(product.getPrice());
        res.setStockQuantity(product.getStockQuantity());
        res.setImageUrl(product.getImageUrl());
        res.setActive(product.isActive());
        res.setCreatedAt(product.getCreatedAt());
        res.setUpdatedAt(product.getUpdatedAt());

        if (product.getCategory() != null) {
            res.setCategoryId(product.getCategory().getId());
            res.setCategoryName(product.getCategory().getName());
        }

        // SellerInfo: nếu sau này bạn có seller_profile client thì set thêm ở đây
        // ProductResponse.SellerInfo sellerInfo = new ProductResponse.SellerInfo();
        // sellerInfo.setStoreId(product.getStoreId());
        // res.setSellerInfo(sellerInfo);

        return res;
    }

    @Override
@Transactional(readOnly = true)
public List<ProductResponse> getRecommendationsForGuest(int limit) {

    // Lấy tất cả recommendation & sort theo score giảm dần
    List<AiRecommendation> recs =
            aiRecommendationRepository.findTop200ByOrderByPredictedScoreDesc();

    if (recs.isEmpty()) {
        return Collections.emptyList();
    }

    // Shuffle để tạo cảm giác mới
    Collections.shuffle(recs);

    // Giới hạn số lượng
    recs = recs.subList(0, Math.min(limit, recs.size()));

    List<Long> productIds = recs.stream()
            .map(AiRecommendation::getProductId)
            .toList();

    List<Product> products = productRepository.findAllById(productIds);

    return products.stream()
            .map(this::toProductResponse)
            .toList();
}

}
