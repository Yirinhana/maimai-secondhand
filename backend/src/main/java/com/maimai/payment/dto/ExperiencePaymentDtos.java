package com.maimai.payment.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public final class ExperiencePaymentDtos {
    private ExperiencePaymentDtos() {}
    public enum Result { SUCCESS, CANCEL, FAIL }
    public record ResultRequest(@NotNull Result result) {}
    public record Item(String title, int quantity, long priceCents) {}
    public record Session(String token, String payNo, String orderNo, long amountCents,
                          String status, String refundStatus, Instant expiresAt,
                          String checkoutPath, String qrPath, List<Item> items) {}
}
