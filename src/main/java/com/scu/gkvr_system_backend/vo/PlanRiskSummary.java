package com.scu.gkvr_system_backend.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 方案总体风险摘要VO
 */
@Data
public class PlanRiskSummary {
    /** 综合风险评分(0-100, 越低越安全) */
    private BigDecimal overallRiskScore;
    /** 风险等级: 低风险/中风险/高风险 */
    private String overallRiskLevel;
    private int reachCount;
    private int matchCount;
    private int safetyCount;
    /** 平均录取概率 */
    private BigDecimal avgAdmissionProb;
    /** 平均调剂风险 */
    private BigDecimal avgMajorAdjustRisk;
    /** 文本建议 */
    private String recommendation;
}
