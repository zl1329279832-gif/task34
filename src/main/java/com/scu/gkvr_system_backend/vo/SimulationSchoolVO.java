package com.scu.gkvr_system_backend.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 模拟任务中单个院校的详细VO.
 */
@Data
public class SimulationSchoolVO {

    private Integer taskSchoolId;

    private Integer schoolId;

    private String schoolName;

    /** 冲/稳/保 */
    private String category;

    private Integer sortOrder;

    /** 录取概率 0-100 */
    private BigDecimal admissionProb;

    /** 录取概率等级: 高/中/低 */
    private String admissionProbLevel;

    /** 调剂风险 0-100 */
    private BigDecimal majorAdjustRisk;

    /** 调剂风险等级: 高/中/低 */
    private String majorAdjustRiskLevel;

    private BigDecimal popularityScore;

    private String popularityTrend;

    /** 位次波动解释(人类可读) */
    private String rankFluctuation;

    private BigDecimal avgRank3yr;

    private BigDecimal rankStdDev;

    private String rankTrend;

    /** 已选专业列表 */
    private List<String> selectedMajors;

    /** 专业维度风险详情 */
    private List<MajorRiskVO> majorDetails;

    /** 概率因子逐步解释 */
    private ProbabilityExplanationVO probabilityExplanation;

    /** 源方案录取概率(用于对比) */
    private BigDecimal sourceAdmissionProb;

    /** 源方案类别 */
    private String sourceCategory;

    /** 概率变化量 = admissionProb - sourceAdmissionProb */
    private BigDecimal probDelta;

    /** 类别是否变化 */
    private Boolean categoryChanged;
}
