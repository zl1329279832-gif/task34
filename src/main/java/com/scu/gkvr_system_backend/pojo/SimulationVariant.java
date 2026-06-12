package com.scu.gkvr_system_backend.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 模拟变体表
 */
@TableName(value = "simulation_variant")
@Data
public class SimulationVariant implements Serializable {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer batchTaskId;

    private String variantName;

    private Integer variantIndex;

    // ── 参数覆盖 (NULL = 继承基础方案) ──

    private Integer scoreOverride;

    private Integer rankOverride;

    private String batchNameOverride;

    private String regionPrefOverride;

    private String schoolTierOverride;

    private String majorPrefOverride;

    // ── 计算结果 ──

    /** pending/running/completed/failed */
    private String status;

    /** 生成的模拟方案ID */
    private Integer simulatedPlanId;

    private String errorMessage;

    /** 计算耗时(毫秒) */
    private Long computationTimeMs;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @Serial
    private static final long serialVersionUID = 1L;
}
