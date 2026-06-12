package com.scu.gkvr_system_backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 模拟版本对比请求
 */
@Data
public class SimulationCompareRequestDTO {

    @NotBlank(message = "用户名不能为空")
    private String userName;

    /** 变体A ID (与 planIdA 二选一) */
    private Integer simulationIdA;
    /** 方案A ID */
    private Integer planIdA;

    /** 变体B ID (与 planIdB 二选一) */
    private Integer simulationIdB;
    /** 方案B ID */
    private Integer planIdB;
}
