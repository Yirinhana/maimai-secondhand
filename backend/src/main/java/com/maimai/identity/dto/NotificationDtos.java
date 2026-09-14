package com.maimai.identity.dto;

import jakarta.validation.constraints.NotEmpty;

import java.time.Instant;
import java.util.List;

/** 站内通知端点的请求/响应 DTO。 */
public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationView(Long id, String type, String title, String content, boolean read,
                                   Instant createdAt) {
    }

    public record MarkReadRequest(
            @NotEmpty List<Long> ids) {
    }

    public record UnreadCount(long count) {
    }

    /** 分页响应，结构遵循契约 {content,totalElements,totalPages,page,size}。 */
    public record PageResponse<T>(List<T> content, long totalElements, int totalPages, int page,
                                  int size) {
    }
}
