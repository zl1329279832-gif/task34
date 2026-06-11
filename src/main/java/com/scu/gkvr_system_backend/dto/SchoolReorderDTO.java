package com.scu.gkvr_system_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class SchoolReorderDTO {
    @NotNull
    private Integer planId;

    @NotBlank
    private String userName;

    @NotEmpty
    private List<SchoolOrderItem> items;

    @Data
    public static class SchoolOrderItem {
        @NotNull
        private Integer planSchoolId;

        @NotNull
        private Integer sortOrder;
    }
}
