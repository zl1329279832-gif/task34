package com.scu.gkvr_system_backend.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模拟任务摘要VO(列表中使用,不含院校明细).
 */
@Data
public class SimulationTaskSummaryVO {

    private Integer taskId;

    private String taskLabel;

    private String status;

    /** 生效分数(覆盖值或继承值) */
    private Integer effectiveScore;

    /** 生效位次 */
    private Integer effectiveUserRank;

    /** 生效批次 */
    private String effectiveBatchName;

    private BigDecimal totalRiskScore;

    private Integer reachCount;

    private Integer matchCount;

    private Integer safetyCount;

    private String errorMessage;

    private LocalDateTime computeStart;

    private LocalDateTime computeEnd;
}
