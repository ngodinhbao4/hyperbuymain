package com.example.product.repository;

       import com.example.product.entity.Product;
       import org.springframework.data.domain.Page;
       import org.springframework.data.domain.Pageable;
       import org.springframework.data.jpa.repository.JpaRepository;
       import org.springframework.data.jpa.repository.Query;
       import org.springframework.data.repository.query.Param;
       import org.springframework.stereotype.Repository;

       import java.util.List;
       import java.util.Optional;

       @Repository
       public interface ProductRepository extends JpaRepository<Product, Long> {

           // Sửa: Bỏ userId
           List<Product> findBySkuAndIsDeletedFalseAndIsActiveTrue(String sku);

           // Sửa: Bỏ userId
           Optional<Product> findByIdAndIsDeletedFalse(Long id);

           // Sửa: Bỏ userId
           Optional<Product> findById(Long id);

           // Sửa: Bỏ userId, tìm tất cả sản phẩm không cần userId
           Page<Product> findByIsDeletedFalseAndIsActiveTrue(Pageable pageable);

           // Sửa: Bỏ userId trong truy vấn
           @Query("SELECT p FROM Product p WHERE p.isDeleted = false AND p.isActive = true AND " +
                  "(:categoryId IS NULL OR p.category.id = :categoryId) AND " +
                  "(:nameQuery IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :nameQuery, '%')))")
           Page<Product> searchProducts(
                   @Param("categoryId") Long categoryId,
                   @Param("nameQuery") String nameQuery,
                   Pageable pageable
           );

           // Các phương thức khác không liên quan đến userId
           Page<Product> findByCategoryIdAndIsDeletedFalseAndIsActiveTrue(Long categoryId, Pageable pageable);
           Page<Product> findByNameContainingIgnoreCaseAndIsDeletedFalseAndIsActiveTrue(String nameQuery, Pageable pageable);

           @Query("SELECT p FROM Product p WHERE p.isDeleted = false AND p.isActive = true AND " +
                  "(:categoryId IS NULL OR p.category.id = :categoryId) AND " +
                  "(:nameQuery IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :nameQuery, '%')))")
           Page<Product> searchProductsGeneral(
                   @Param("categoryId") Long categoryId,
                   @Param("nameQuery") String nameQuery,
                   Pageable pageable
           );

           boolean existsByCategoryIdAndIsDeletedFalseAndIsActiveTrue(Long categoryId);
       }