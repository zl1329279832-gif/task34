package com.scu.gkvr_system_backend.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 单校概率分解VO
 */
@Data
public class SchoolProbabilityBreakdownVO {
    private Integer schoolId;
    private String schoolName;
    private String category;
    private BigDecimal admissionProb;
    private String admissionProbLevel;
    private BigDecimal avgRank3yr;
    private BigDecimal rankStdDev;
    private String rankTrend;
    private String rankFluctuation;
    private BigDecimal majorAdjustRisk;
    private String majorAdjustRiskLevel;
    private List<MajorRiskVO> majorDetails;
    // ── 相对基础方案的变化 ──
    /** 概率变化(正=提升) */
    private BigDecimal probDelta;
    /** 风险变化(正=更危险) */
    private BigDecimal riskDelta;
    /** 人类可读变化原因 */
    private String changeReason;
}
