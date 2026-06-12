package com.scu.gkvr_system_backend.vo;

import lombok.Data;

/**
 * 单个模拟变体状态VO
 */
@Data
public class SimulationVariantStatusVO {
    private Integer variantId;
    private String variantName;
    private Integer variantIndex;
    private String status;
    private Integer simulatedPlanId;
    private String errorMessage;
    private Long computationTimeMs;
}
