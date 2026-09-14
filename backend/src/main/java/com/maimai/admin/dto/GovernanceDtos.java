package com.maimai.admin.dto;

import jakarta.validation.constraints.*;

public final class GovernanceDtos {
    private GovernanceDtos() {}
    public record RoleChange(@NotBlank String role,@NotNull Boolean grant,@NotBlank @Size(max=500) String reason) {}
    public record CategoryWrite(Long parentId,@NotBlank @Size(max=50) String name,@Min(0) int sort,
                                @NotBlank String status,@NotBlank @Size(max=500) String reason) {}
    public record CategoryItem(long id,Long parentId,String name,int sort,String status) {}
}
