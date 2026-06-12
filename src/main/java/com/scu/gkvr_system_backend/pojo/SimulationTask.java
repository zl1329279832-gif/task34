package com.scu.gkvr_system_backend.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName(value = "simulation_task")
@Data
public class SimulationTask implements Serializable {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer batchId;

    /** 任务标签(如: 分数+10) */
    private String taskLabel;

    /** PENDING/COMPUTING/COMPLETED/FAILED/CANCELLED */
    private String status;

    /** CAS乐观锁序列号 */
    private Integer sequenceNumber;

    /** 覆盖分数(null=继承源方案) */
    private Integer paramScore;

    /** 覆盖位次 */
    private Integer paramUserRank;

    /** 覆盖批次 */
    private String paramBatchName;

    /** 覆盖地区偏好CSV */
    private String paramRegionPref;

    /** 覆盖院校层次CSV */
    private String paramSchoolTier;

    /** 覆盖专业偏好CSV */
    private String paramMajorPref;

    /** 冻结的风险计算输入快照(JSON) */
    private String riskSnapshot;

    /** 完整计算结果(JSON) */
    private String resultData;

    private BigDecimal totalRiskScore;

    private Integer reachCount;

    private Integer matchCount;

    private Integer safetyCount;

    private String errorMessage;

    private LocalDateTime computeStart;

    private LocalDateTime computeEnd;

    private LocalDateTime createTime;

    @Serial
    private static final long serialVersionUID = 1L;
}
