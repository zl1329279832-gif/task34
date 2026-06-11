package com.scu.gkvr_system_backend.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PlanComparisonVO {
    private Integer planIdA;
    private Integer planIdB;
    private int versionA;
    private int versionB;
    private BigDecimal riskScoreA;
    private BigDecimal riskScoreB;
    private List<SchoolDiff> diffs;

    @Data
    public static class SchoolDiff {
        private Integer schoolId;
        private String schoolName;
        private String categoryA;
        private String categoryB;
        private BigDecimal admissionProbA;
        private BigDecimal admissionProbB;
        private String changeType;
    }
}
