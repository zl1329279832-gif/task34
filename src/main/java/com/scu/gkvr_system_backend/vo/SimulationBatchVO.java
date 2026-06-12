package com.scu.gkvr_system_backend.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 模拟批次概览VO.
 */
@Data
public class SimulationBatchVO {

    private Integer batchId;

    private String batchName;

    private Integer sourcePlanId;

    private String sourcePlanName;

    private String status;

    private Integer sequenceNumber;

    private Integer totalTasks;

    private Integer completedTasks;

    private Integer failedTasks;

    private List<SimulationTaskSummaryVO> tasks;

    private LocalDateTime createTime;
}
