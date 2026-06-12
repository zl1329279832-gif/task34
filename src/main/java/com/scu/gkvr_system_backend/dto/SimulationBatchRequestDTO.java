package com.scu.gkvr_system_backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SimulationBatchRequestDTO {

    @NotBlank(message = "用户名不能为空")
    private String userName;

    @NotNull(message = "源方案ID不能为空")
    private Integer sourcePlanId;

    @NotBlank(message = "批次名称不能为空")
    @Size(max = 100, message = "批次名称不能超过100个字符")
    private String batchName;

    @NotEmpty(message = "至少需要一个模拟变体")
    @Size(max = 20, message = "单批次最多20个模拟变体")
    @Valid
    private List<SimulationVariantDTO> variants;
}
