package com.maimai.identity.controller;

import com.maimai.common.security.SecurityUtils;
import com.maimai.identity.dto.NotificationDtos.MarkReadRequest;
import com.maimai.identity.dto.NotificationDtos.NotificationView;
import com.maimai.identity.dto.NotificationDtos.PageResponse;
import com.maimai.identity.dto.NotificationDtos.UnreadCount;
import com.maimai.notification.NotificationService;
import com.maimai.notification.domain.Notification;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 站内通知端点（identity 侧装配，业务由 NotificationService 提供）。均需登录。 */
@RestController
@RequestMapping("/api/v1/me/notifications")
public class NotificationController {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public PageResponse<NotificationView> list(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<Notification> result = notificationService.listMine(
                SecurityUtils.currentUserId(), PageRequest.of(safePage, safeSize));
        return new PageResponse<>(result.getContent().stream().map(NotificationController::toView).toList(),
                result.getTotalElements(), result.getTotalPages(), safePage, safeSize);
    }

    @PostMapping("/read")
    public ResponseEntity<Void> markRead(@Valid @RequestBody MarkReadRequest request) {
        notificationService.markRead(SecurityUtils.currentUserId(), request.ids());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/unread-count")
    public UnreadCount unreadCount() {
        return new UnreadCount(notificationService.unreadCount(SecurityUtils.currentUserId()));
    }

    private static NotificationView toView(Notification notification) {
        return new NotificationView(notification.getId(), notification.getType(),
                notification.getTitle(), notification.getContent(), notification.isRead(),
                notification.getCreatedAt());
    }
}
