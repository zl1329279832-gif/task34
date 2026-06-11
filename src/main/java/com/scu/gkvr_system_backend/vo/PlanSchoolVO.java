package com.scu.gkvr_system_backend.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PlanSchoolVO {
    private Integer id;
    private Integer schoolId;
    private String schoolName;
    private String category;
    private Integer sortOrder;
    private BigDecimal admissionProb;
    private String admissionProbLevel;
    private BigDecimal majorAdjustRisk;
    private String majorAdjustRiskLevel;
    private BigDecimal popularityScore;
    private String popularityTrend;
    private String rankFluctuation;
    private BigDecimal avgRank3yr;
    private BigDecimal rankStdDev;
    private String rankTrend;
    private List<String> selectedMajors;
}
