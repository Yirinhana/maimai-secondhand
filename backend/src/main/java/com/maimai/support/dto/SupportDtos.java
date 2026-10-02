package com.maimai.support.dto;

import com.maimai.support.service.SupportFaq;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class SupportDtos {
    private SupportDtos() { }
    public record CreateTicket(@NotBlank @Size(max=100) String title, @NotBlank @Size(max=2000) String body,
                               @Size(max=32) String orderNo, @Size(max=5) List<String> aiRequestIds) {
        public CreateTicket(String title,String body,String orderNo){this(title,body,orderNo,List.of());}
    }
    public record WriteMessage(@NotBlank @Size(max=2000) String body) { }
    public record AiHelp(@NotNull SupportFaq.Topic topic) { }
    public record Ticket(long id, long ownerId, String ownerNickname, String title, String orderNo, String status,
                         Long assignedTo, Instant createdAt, Instant updatedAt) { }
    public record Message(long id, long ticketId, Long authorId, String authorKind, String authorNickname, String body, Instant createdAt) { }
    public record Page<T>(List<T> items, long total, int page, int size, int totalPages) { }
}
