package com.maimai.identity.dto;

import jakarta.validation.constraints.*;
public final class AccountClosureDtos {
    private AccountClosureDtos() {}
    public record ClosureRequest(@NotBlank @Size(max=72) String password,@NotBlank @Size(max=500) String reason) {}
    public record ClosureResult(long id,String status,String notice) {}
}
