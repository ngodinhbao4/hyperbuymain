# GIAI ĐOẠN 1: Chỉ cần base image JRE để chạy
FROM eclipse-temurin:21-jre-alpine

# Đặt thư mục làm việc bên trong container
WORKDIR /app

# Thay thế bằng tên file JAR thực tế của user-service sau khi build
# Bạn có thể dùng wildcard hoặc tên cụ thể.
# ARG JAR_FILE=target/user-service-*.jar
COPY target/user-service-*.jar app.jar
# Hoặc nếu tên file JAR là cố định:
# COPY target/user-service-0.0.1-SNAPSHOT.jar app.jar

# Mở cổng mà ứng dụng Spring Boot của bạn đang lắng nghe (ví dụ: 8080 cho user-service)
EXPOSE 8080

# Lệnh để chạy ứng dụng khi container được khởi động
ENTRYPOINT ["java", "-jar", "app.jar"]
