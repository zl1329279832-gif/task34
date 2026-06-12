package com.scu.gkvr_system_backend.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 录取概率因子逐步解释VO.
 * 将 calcAdmissionProb 的每个中间步骤暴露出来.
 */
@Data
public class ProbabilityExplanationVO {

    /** avgRank / userRank 比值 */
    private BigDecimal rankRatio;

    /** 基础概率 = rankRatio * 50 */
    private BigDecimal baseProbability;

    /** 变异系数 = stdDev / avgRank */
    private BigDecimal coefficientOfVariation;

    /** 稳定性因子 = max(0.5, 1.0 - cv * 0.5) */
    private BigDecimal stabilityFactor;

    /** 调整后概率 = baseProbability * stabilityFactor */
    private BigDecimal adjustedProbability;

    /** 类别调整说明 */
    private String categoryAdjustment;

    /** 最终概率(经类别调整及[1,99]截断) */
    private BigDecimal finalProbability;

    /** 近三年位次 */
    private Integer rank2020;
    private Integer rank2021;
    private Integer rank2022;

    /** 三年平均位次 */
    private BigDecimal avgRank3yr;

    /** 位次标准差 */
    private BigDecimal rankStdDev;

    /** 完整的人类可读解释 */
    private String explanation;
}
