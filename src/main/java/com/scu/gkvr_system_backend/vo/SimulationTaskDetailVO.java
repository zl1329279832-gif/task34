package com.scu.gkvr_system_backend.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 模拟任务详情VO(含院校列表、风险摘要、冲突预警).
 */
@Data
public class SimulationTaskDetailVO {

    private Integer taskId;

    private Integer batchId;

    private String taskLabel;

    private String status;

    /** 生效参数 */
    private Integer effectiveScore;
    private Integer effectiveUserRank;
    private String effectiveBatchName;
    private String effectiveRegionPref;
    private String effectiveSchoolTier;
    private String effectiveMajorPref;

    /** 冲一冲院校 */
    private List<SimulationSchoolVO> reachSchools;

    /** 稳一稳院校 */
    private List<SimulationSchoolVO> matchSchools;

    /** 保一保院校 */
    private List<SimulationSchoolVO> safetySchools;

    /** 风险摘要(复用已有VO) */
    private PlanRiskSummary riskSummary;

    /** 同校跨类别冲突预警 */
    private List<String> conflictWarnings;

    private LocalDateTime snapshotTime;
}
