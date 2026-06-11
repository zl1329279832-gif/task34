package com.scu.gkvr_system_backend.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * 方案中单个院校DTO
 */
@Data
public class PlanSchoolDTO {

    @NotNull(message = "院校ID不能为空")
    private Integer schoolId;

    @NotBlank(message = "院校名称不能为空")
    private String schoolName;

    @NotBlank(message = "冲/稳/保类别不能为空")
    @Pattern(regexp = "冲|稳|保", message = "类别必须为冲、稳或保")
    private String category;

    private Integer sortOrder;

    /** 已选专业名,逗号分隔 */
    private String selectedMajors;
}
