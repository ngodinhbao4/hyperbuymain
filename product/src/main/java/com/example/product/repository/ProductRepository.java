package com.example.product.repository;

import com.example.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // Tìm sản phẩm theo category ID, chưa bị xóa (isDeleted = false), và đang active (isActive = true)
    // Có phân trang
    Page<Product> findByCategoryIdAndIsDeletedFalseAndIsActiveTrue(Long categoryId, Pageable pageable);

    // Tìm kiếm sản phẩm theo tên (chứa chuỗi, không phân biệt hoa thường), chưa bị xóa, và đang active
    // Có phân trang
    Page<Product> findByNameContainingIgnoreCaseAndIsDeletedFalseAndIsActiveTrue(String nameQuery, Pageable pageable);

    // Tìm tất cả sản phẩm chưa bị xóa và đang active
    // Có phân trang
    Page<Product> findByIsDeletedFalseAndIsActiveTrue(Pageable pageable);

    // Ví dụ một truy vấn JPQL phức tạp hơn nếu cần (tìm sản phẩm theo category_id hoặc tên sản phẩm)
    @Query("SELECT p FROM Product p WHERE p.isDeleted = false AND p.isActive = true AND " +
           "(:categoryId IS NULL OR p.category.id = :categoryId) AND " +
           "(:nameQuery IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :nameQuery, '%')))")
    Page<Product> searchProducts(
            @Param("categoryId") Long categoryId,
            @Param("nameQuery") String nameQuery,
            Pageable pageable
    );

    // Tìm các sản phẩm theo SKU
    List<Product> findBySkuAndIsDeletedFalseAndIsActiveTrue(String sku);

    // Bạn có thể thêm các phương thức truy vấn tùy chỉnh khác nếu cần
    boolean existsByCategoryIdAndIsDeletedFalseAndIsActiveTrue(Long categoryId);
}