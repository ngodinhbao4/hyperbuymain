# GIAI ĐOẠN 1: Chỉ cần base image JRE để chạy
FROM eclipse-temurin:21-jre-alpine

# Đặt thư mục làm việc bên trong container
WORKDIR /app

# Thay thế bằng tên file JAR thực tế của product-service sau khi build
# ARG JAR_FILE=target/product-service-*.jar
COPY target/product-service-*.jar app.jar
# Hoặc nếu tên file JAR là cố định:
# COPY target/product-service-0.0.1-SNAPSHOT.jar app.jar

# Mở cổng mà ứng dụng Spring Boot của bạn đang lắng nghe (ví dụ: 8081 cho product-service)
EXPOSE 8081

# Lệnh để chạy ứng dụng khi container được khởi động
ENTRYPOINT ["java", "-jar", "app.jar"]
