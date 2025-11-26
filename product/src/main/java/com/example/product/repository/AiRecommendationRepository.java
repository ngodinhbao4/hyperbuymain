package com.example.product.repository;

import com.example.product.entity.AiRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiRecommendationRepository extends JpaRepository<AiRecommendation, Long> {

    // Lấy top N theo username, sort descending theo predicted_score
    List<AiRecommendation> findTop50ByUsernameOrderByPredictedScoreDesc(String username);

    List<AiRecommendation> findTop200ByOrderByPredictedScoreDesc();
}
