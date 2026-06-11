package com.scu.gkvr_system_backend.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

/**
 * 院校排序请求DTO
 */
@Data
public class SchoolReorderDTO {

    @NotNull(message = "方案ID不能为空")
    private Integer planId;

    @NotBlank(message = "用户名不能为空")
    private String userName;

    @NotEmpty(message = "排序列表不能为空")
    private List<SchoolOrderItem> orderedSchools;

    @Data
    public static class SchoolOrderItem {
        @NotNull(message = "院校记录ID不能为空")
        private Integer planSchoolId;

        @NotNull(message = "排序值不能为空")
        private Integer sortOrder;
    }
}
