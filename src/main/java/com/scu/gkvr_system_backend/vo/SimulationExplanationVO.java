package com.scu.gkvr_system_backend.vo;

import lombok.Data;

import java.util.List;

/**
 * 模拟概率解释VO
 */
@Data
public class SimulationExplanationVO {
    private Integer simulationId;
    private String variantName;
    private Integer simulatedPlanId;
    /** 有效参数(覆盖后) */
    private Integer effectiveScore;
    private Integer effectiveRank;
    private String effectiveBatchName;
    /** 逐校概率分解 */
    private List<SchoolProbabilityBreakdownVO> schoolBreakdowns;
    /** 总体风险摘要 */
    private PlanRiskSummary riskSummary;
    /** 相对基础方案的变化解释 */
    private SimulationChangeExplanationVO changeExplanation;
}
