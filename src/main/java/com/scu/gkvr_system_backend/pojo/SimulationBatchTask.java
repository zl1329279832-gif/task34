package com.scu.gkvr_system_backend.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模拟批次任务表
 */
@TableName(value = "simulation_batch_task")
@Data
public class SimulationBatchTask implements Serializable {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String userName;

    private Integer basePlanId;

    /** 提交时的基础方案版本号, 用于过时检测 */
    private Integer basePlanVersion;

    /** pending/running/completed/failed/stale */
    private String status;

    private Integer totalVariants;

    private Integer completedVariants;

    private BigDecimal progressPercent;

    private String errorMessage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @Serial
    private static final long serialVersionUID = 1L;
}
