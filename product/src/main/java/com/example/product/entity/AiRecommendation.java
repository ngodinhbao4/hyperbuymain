package com.example.product.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_recommendations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // username = subject trong JWT (giống Rating)
    @Column(nullable = false, length = 150)
    private String username;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "predicted_score", nullable = false)
    private Double predictedScore;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
