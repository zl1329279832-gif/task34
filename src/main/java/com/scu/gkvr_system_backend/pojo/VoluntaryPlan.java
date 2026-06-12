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
 * 志愿方案主表
 * @TableName voluntary_plan
 */
@TableName(value = "voluntary_plan")
@Data
public class VoluntaryPlan implements Serializable {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String planName;

    private String userName;

    private Integer score;

    private Integer userRank;

    private String subjectType;

    private String regionPref;

    private String schoolTier;

    private String majorPref;

    private String batchName;

    private Integer version;

    private Integer parentId;

    private Integer status;

    private BigDecimal totalRiskScore;

    /** 关联的模拟批次任务ID(NULL=正式方案, 非NULL=模拟方案) */
    private Integer simulationBatchTaskId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @Serial
    private static final long serialVersionUID = 1L;
}
