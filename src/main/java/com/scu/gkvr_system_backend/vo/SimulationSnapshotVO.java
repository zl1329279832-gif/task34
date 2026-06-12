package com.scu.gkvr_system_backend.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 模拟风险快照VO
 */
@Data
public class SimulationSnapshotVO {
    private Integer simulationId;
    private String variantName;
    private Integer simulatedPlanId;
    private BigDecimal totalRiskScore;
    private PlanRiskSummary riskSummary;
    private List<PlanSchoolVO> allSchools;
    private LocalDateTime computedAt;
}
