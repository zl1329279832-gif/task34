package com.scu.gkvr_system_backend.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 模拟任务对比VO.
 */
@Data
public class SimulationComparisonVO {

    private SimulationTaskDetailVO taskA;

    private SimulationTaskDetailVO taskB;

    /** 院校级别差异列表 */
    private List<SimulationSchoolDiff> differences;

    /** 对比摘要 */
    private String summary;

    /** 参数差异: key -> [valueA, valueB] */
    private Map<String, String[]> parameterDiffs;

    @Data
    public static class SimulationSchoolDiff {
        private Integer schoolId;
        private String schoolName;
        /** added / removed / category_changed / prob_changed / risk_changed */
        private String changeType;
        private String detail;
        private BigDecimal probA;
        private BigDecimal probB;
        private String categoryA;
        private String categoryB;
    }
}
