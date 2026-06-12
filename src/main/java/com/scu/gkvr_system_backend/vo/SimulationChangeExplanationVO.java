package com.scu.gkvr_system_backend.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 模拟变化解释VO
 */
@Data
public class SimulationChangeExplanationVO {
    /** 综合风险评分变化 */
    private BigDecimal totalRiskScoreDelta;
    /** 平均录取概率变化 */
    private BigDecimal avgProbDelta;
    /** 平均调剂风险变化 */
    private BigDecimal avgMajorRiskDelta;
    /** 整体解释 */
    private String overallExplanation;
    /** 显著变化列表 */
    private List<String> notableChanges;
}
