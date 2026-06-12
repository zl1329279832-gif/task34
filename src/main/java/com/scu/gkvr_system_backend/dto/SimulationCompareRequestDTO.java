package com.scu.gkvr_system_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SimulationCompareRequestDTO {

    @NotBlank(message = "用户名不能为空")
    private String userName;

    @NotNull(message = "任务A ID不能为空")
    private Integer taskIdA;

    @NotNull(message = "任务B ID不能为空")
    private Integer taskIdB;
}
