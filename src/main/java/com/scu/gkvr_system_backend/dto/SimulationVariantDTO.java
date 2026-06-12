package com.scu.gkvr_system_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 单个模拟变体参数
 */
@Data
public class SimulationVariantDTO {

    @NotBlank(message = "变体名称不能为空")
    @Size(max = 100)
    private String variantName;

    /** 分数覆盖(NULL=继承基础方案) */
    private Integer scoreOverride;

    /** 位次覆盖 */
    private Integer rankOverride;

    /** 批次覆盖 */
    private String batchNameOverride;

    /** 地区偏好覆盖 */
    private String regionPrefOverride;

    /** 院校层次覆盖 */
    private String schoolTierOverride;

    /** 专业偏好覆盖 */
    private String majorPrefOverride;
}
