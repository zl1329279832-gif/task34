package com.scu.gkvr_system_backend.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 方案中单个院校详情VO
 */
@Data
public class PlanSchoolVO {
    private Integer planSchoolId;
    private Integer schoolId;
    private String schoolName;
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

    /** 院校热度评分 */
    private BigDecimal popularityScore;
    /** 热度趋势 */
    private String popularityTrend;

    /** 位次波动解释(人类可读) */
    private String rankFluctuation;
    /** 三年平均位次 */
    private BigDecimal avgRank3yr;
    /** 位次标准差 */
    private BigDecimal rankStdDev;
    /** 位次趋势 */
    private String rankTrend;

    /** 已选专业列表 */
    private List<String> selectedMajors;
    /** 专业维度风险详情 */
    private List<MajorRiskVO> majorDetails;
}
