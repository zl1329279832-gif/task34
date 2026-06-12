package com.scu.gkvr_system_backend.vo;

import lombok.Data;

import java.util.List;

/**
 * 模拟对比VO
 */
@Data
public class SimulationCompareVO {
    private PlanDetailVO planA;
    private PlanDetailVO planB;
    private List<PlanComparisonVO.SchoolDiff> differences;
    private SimulationChangeExplanationVO changeExplanation;
    private String summary;
}
