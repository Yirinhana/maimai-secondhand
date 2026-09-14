package com.maimai.messaging.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MessageDtos {
    private MessageDtos() {}
    public record OpenConversation(@NotNull @Positive Long recipientId, @Positive Long productId) {}
    public record SendMessage(@NotNull UUID clientId, @Size(max=2000) String body, UUID attachmentId) {}
    public record ReadMessages(@PositiveOrZero long throughId) {}
    public record Conversation(long id, long otherUserId, String otherNickname, Long productId,
                               String lastMessage, Instant updatedAt, long unread) {}
    public record Message(long id, long conversationId, long senderId, UUID clientId,
                          String body, String attachmentUrl, Instant createdAt) {}
    public record History(List<Message> items, boolean hasMore, Long nextBeforeId) {}
    public record UploadedImage(UUID id, String url, long byteSize) {}
    public record SetBlock(@NotNull Boolean blocked) {}
    public record BlockState(boolean blockedByMe, boolean blockedEitherDirection) {}
    public record Changed(long userId, long conversationId) {}
}
