package com.example.notification.controller;

import com.example.notification.dto.request.CreateNotificationRequest;
import com.example.notification.dto.response.ApiResponRequest;
import com.example.notification.entity.Notification;
import com.example.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    // Lấy danh sách thông báo của user
    @GetMapping("/{userId}")
    public ResponseEntity<List<Notification>> getNotifications(@PathVariable String userId) {
        return ResponseEntity.ok(notificationService.getUserNotifications(userId));
    }

    // Admin tạo thông báo
    @PostMapping("/admin/send")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponRequest<String> sendAdminNotification(@RequestBody CreateNotificationRequest request, @RequestHeader("Authorization") String authorizationHeader) {
        notificationService.createAdminNotification(request.getUserId(), request.getMessage(), authorizationHeader);
        return ApiResponRequest.<String>builder()
                .code(1000)
                .result("Notification sent successfully")
                .build();
    }

    // User đánh dấu thông báo đã đọc
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long notificationId) {
        notificationService.markAsRead(notificationId);
        return ResponseEntity.noContent().build();
    }
}