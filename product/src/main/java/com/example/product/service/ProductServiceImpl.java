package com.example.product.service; // Giả sử đây là package cho các lớp implement

// LƯU Ý: Lớp Product Entity của bạn (com.example.product.entity.Product)
// đã sử dụng trường `private String imageUrl;` và Lombok (@Getter, @Setter)
// để tạo các phương thức `getImageUrl()` và `setImageUrl()`.
// ProductRequest DTO của bạn sử dụng BigDecimal cho price.
// ProductRequest DTO của bạn KHÔNG có trường isActive.

import com.example.product.dto.request.ProductRequest;
import com.example.product.dto.response.ProductResponse;
import com.example.product.entity.Product; // Hoặc com.example.product.entity.Product tùy theo cấu trúc của bạn
import com.example.product.entity.Category; // Hoặc com.example.product.entity.Category
import com.example.product.repository.ProductRepository;
import com.example.product.repository.CategoryRepository;
import com.example.product.service.ProductService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils; // Vẫn cần cho cleanPath
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal; // Import BigDecimal
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements ProductService {

    private static final Logger logger = LoggerFactory.getLogger(ProductServiceImpl.class);

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final Path rootLocation;

    @Value("${app.static-resource.public-path-pattern}")
    private String publicPathPattern;

    public ProductServiceImpl(ProductRepository productRepository,
                              CategoryRepository categoryRepository,
                              @Value("${app.upload.dir}") String uploadDirConfiguration) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.rootLocation = Paths.get(uploadDirConfiguration);
    }

    @PostConstruct
    public void init() {
        ensureUploadDirectoryExists();
    }

    private void ensureUploadDirectoryExists() {
        try {
            Files.createDirectories(rootLocation);
            logger.info("Thư mục upload đã được kiểm tra/tạo: {}", rootLocation.toAbsolutePath());
        } catch (IOException e) {
            logger.error("Không thể khởi tạo vị trí lưu trữ: {}", rootLocation.toAbsolutePath(), e);
            throw new RuntimeException("Không thể khởi tạo vị trí lưu trữ", e);
        }
    }

    private String storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            logger.warn("Không có file nào được cung cấp hoặc file rỗng.");
            return null;
        }

        String originalFilenameFromMultipart = file.getOriginalFilename();
        // Sử dụng strip() thay cho StringUtils.trimWhitespace() (deprecated)
        if (originalFilenameFromMultipart == null || originalFilenameFromMultipart.strip().isEmpty()) {
            logger.warn("Tên file gốc từ MultipartFile là null hoặc rỗng. Không thể lưu file.");
            return null;
        }

        String originalFilename = StringUtils.cleanPath(originalFilenameFromMultipart);
        String extension = "";
        int i = originalFilename.lastIndexOf('.');
        if (i > 0) {
            extension = originalFilename.substring(i);
        }
        String uniqueFileName = UUID.randomUUID().toString() + extension;

        try {
            if (originalFilename.contains("..")) {
                throw new RuntimeException("Không thể lưu file với đường dẫn tương đối ngoài thư mục hiện tại: " + originalFilename);
            }
            Path destinationFile = this.rootLocation.resolve(uniqueFileName).normalize().toAbsolutePath();
            if (!destinationFile.getParent().equals(this.rootLocation.toAbsolutePath())) {
                 throw new RuntimeException("Không thể lưu file ngoài thư mục hiện tại: " + originalFilename);
            }

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            }
            logger.info("Đã lưu file: {}", uniqueFileName);
            return uniqueFileName; // Trả về tên file đã được tạo duy nhất (ví dụ: abc.jpg)
        } catch (IOException e) {
            logger.error("Lưu file {} thất bại: {}", uniqueFileName, e.getMessage());
            throw new RuntimeException("Lưu file " + originalFilename + " thất bại", e);
        }
    }

    private void deleteStoredFile(String filename) {
        if (filename == null || filename.isEmpty()) {
            return;
        }
        try {
            Path fileToDelete = rootLocation.resolve(filename).normalize().toAbsolutePath();
            Files.deleteIfExists(fileToDelete);
            logger.info("Đã xóa file đã lưu: {}", filename);
        } catch (IOException e) {
            logger.error("Xóa file {} thất bại: {}", filename, e.getMessage());
        }
    }

    private ProductResponse convertToProductResponseWithImageUrl(Product product) {
        ProductResponse dto = new ProductResponse();
        dto.setId(product.getId());
        dto.setName(product.getName());
        if (product.getDescription() != null) dto.setDescription(product.getDescription());
        // ProductResponse.setPrice() mong muốn BigDecimal, Product.getPrice() cũng là BigDecimal
        if (product.getPrice() != null) dto.setPrice(product.getPrice());
        if (product.getSku() != null) dto.setSku(product.getSku());
        dto.setStockQuantity(product.getStockQuantity());
        dto.setCategoryId(product.getCategory() != null ? product.getCategory().getId() : null);
        dto.setActive(product.isActive()); // isActive từ Product Entity

        String storedImageIdentifier = product.getImageUrl();

        if (storedImageIdentifier != null && !storedImageIdentifier.isEmpty()) {
            String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
            String cleanPublicPath = publicPathPattern.endsWith("/**")
                    ? publicPathPattern.substring(0, publicPathPattern.length() - 3)
                    : publicPathPattern;
            if (cleanPublicPath.endsWith("/")) {
                 cleanPublicPath = cleanPublicPath.substring(0, cleanPublicPath.length() -1);
            }
             if (!cleanPublicPath.startsWith("/")) {
                cleanPublicPath = "/" + cleanPublicPath;
            }
            String fullImageUrl = baseUrl + cleanPublicPath + "/" + storedImageIdentifier;
            dto.setImageUrl(fullImageUrl);
        } else {
            dto.setImageUrl(null);
        }
        return dto;
    }

    @Override
    @Transactional
    public ProductResponse createProduct(ProductRequest productRequest, MultipartFile imageFile) {
        String storedFileName = storeFile(imageFile);

        Product product = new Product();
        product.setName(productRequest.getName());
        product.setDescription(productRequest.getDescription());
        // ProductRequest.getPrice() là BigDecimal, gán trực tiếp
        if (productRequest.getPrice() != null) {
            product.setPrice(productRequest.getPrice());
        }
        product.setSku(productRequest.getSku());
        // ProductRequest.getStockQuantity() là Integer, Product.setStockQuantity() mong muốn int
        if (productRequest.getStockQuantity() != null) {
            product.setStockQuantity(productRequest.getStockQuantity());
        }


        if (productRequest.getCategoryId() != null) {
            Category category = categoryRepository.findById(productRequest.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy Category với ID: " + productRequest.getCategoryId()));
            product.setCategory(category);
        }

        product.setActive(true); // Mặc định active là true khi tạo mới
        product.setDeleted(false); // Mặc định isDeleted là false khi tạo mới
        product.setImageUrl(storedFileName);

        Product savedProduct = productRepository.save(product);
        logger.info("Đã tạo sản phẩm với ID: {} và file ảnh (nếu có): {}", savedProduct.getId(), storedFileName);
        return convertToProductResponseWithImageUrl(savedProduct);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm với ID: " + id));
        return convertToProductResponseWithImageUrl(product);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> findProducts(Long categoryId, String nameQuery, Pageable pageable) {
        Page<Product> productPage;
        // Dựa trên các trường is_active và is_deleted của Product entity
        // Ví dụ: productRepository.findByCategoryIdAndNameContainingIgnoreCaseAndIsActiveTrueAndIsDeletedFalse(...)
        productPage = productRepository.findAll(pageable); // Placeholder

        List<ProductResponse> productResponses = productPage.getContent().stream()
                .map(this::convertToProductResponseWithImageUrl)
                .collect(Collectors.toList());
        return new PageImpl<>(productResponses, pageable, productPage.getTotalElements());
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest productRequest, MultipartFile imageFile) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm với ID: " + id));

        if (imageFile != null && !imageFile.isEmpty()) {
            deleteStoredFile(product.getImageUrl());
            String newStoredFileName = storeFile(imageFile);
            product.setImageUrl(newStoredFileName);
        }

        product.setName(productRequest.getName());
        product.setDescription(productRequest.getDescription());
        // ProductRequest.getPrice() là BigDecimal, gán trực tiếp
        if (productRequest.getPrice() != null) {
            product.setPrice(productRequest.getPrice());
        }
        product.setSku(productRequest.getSku());
        // ProductRequest.getStockQuantity() là Integer, Product.setStockQuantity() mong muốn int
        if (productRequest.getStockQuantity() != null) {
            product.setStockQuantity(productRequest.getStockQuantity());
        }

        // LOẠI BỎ CẬP NHẬT `isActive` TỪ ProductRequest VÌ ProductRequest KHÔNG CÓ TRƯỜNG NÀY
        // Trạng thái active sẽ được quản lý qua activateProduct/deactivateProduct
        // if (productRequest.getIsActive() != null) { // Dòng này đã bị xóa
        //      product.setActive(productRequest.getIsActive()); // Dòng này đã bị xóa
        // }


        if (productRequest.getCategoryId() != null) {
            Category category = categoryRepository.findById(productRequest.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy Category với ID: " + productRequest.getCategoryId()));
            product.setCategory(category);
        } else {
            product.setCategory(null);
        }

        Product updatedProduct = productRepository.save(product);
        logger.info("Đã cập nhật sản phẩm với ID: {}", updatedProduct.getId());
        return convertToProductResponseWithImageUrl(updatedProduct);
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm với ID: " + id));
        productRepository.delete(product);
        logger.info("Đã xóa sản phẩm với ID: {}", id);
        }

    @Override
    @Transactional
    public ProductResponse updateStock(Long id, int quantityChange) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm với ID: " + id));
        int newStock = product.getStockQuantity() + quantityChange;
        if (newStock < 0) {
            throw new RuntimeException("Số lượng tồn kho không thể âm.");
        }
        product.setStockQuantity(newStock);
        Product updatedProduct = productRepository.save(product);
        logger.info("Đã cập nhật tồn kho cho sản phẩm ID: {} thành {}", id, newStock);
        return convertToProductResponseWithImageUrl(updatedProduct);
    }

    @Override
    @Transactional
    public void activateProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm với ID: " + id));
        product.setActive(true);
        product.setDeleted(false);
        productRepository.save(product);
        logger.info("Đã kích hoạt sản phẩm với ID: {}", id);
    }

    @Override
    @Transactional
    public void deactivateProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm với ID: " + id));
        product.setActive(false);
        productRepository.save(product);
        logger.info("Đã hủy kích hoạt sản phẩm với ID: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> findMyProducts(Long categoryId, String nameQuery, Pageable pageable) {
        logger.warn("findMyProducts chưa được triển khai đầy đủ với ngữ cảnh người dùng, hoạt động giống findProducts.");
        return findProducts(categoryId, nameQuery, pageable);
    }
}
