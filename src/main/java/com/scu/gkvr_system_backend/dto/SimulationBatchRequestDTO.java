package com.scu.gkvr_system_backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量模拟提交请求
 */
@Data
public class SimulationBatchRequestDTO {

    @NotBlank(message = "用户名不能为空")
    private String userName;

    @NotNull(message = "基础方案ID不能为空")
    private Integer basePlanId;

    @NotEmpty(message = "变体列表不能为空")
    @Size(max = 20, message = "单次批量模拟最多20个变体")
    @Valid
    private List<SimulationVariantDTO> variants;
}
