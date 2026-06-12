package com.scu.gkvr_system_backend.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 模拟批次状态VO
 */
@Data
public class SimulationBatchStatusVO {
    private Integer batchTaskId;
    /** pending/running/completed/failed/stale */
    private String status;
    private BigDecimal progressPercent;
    private Integer totalVariants;
    private Integer completedVariants;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<SimulationVariantStatusVO> variants;
}
