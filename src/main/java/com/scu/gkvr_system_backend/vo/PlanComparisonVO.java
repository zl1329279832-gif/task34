package com.scu.gkvr_system_backend.vo;

import lombok.Data;

import java.util.List;

/**
 * 方案对比VO
 */
@Data
public class PlanComparisonVO {
    private PlanDetailVO planA;
    private PlanDetailVO planB;
    private List<SchoolDiff> differences;
    private String summary;

    @Data
    public static class SchoolDiff {
        private String schoolName;
        /** added / removed / category_changed / prob_changed */
        private String changeType;
        /** 人类可读的差异描述 */
        private String detail;
    }
}
