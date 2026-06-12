package com.scu.gkvr_system_backend.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName(value = "simulation_task_school")
@Data
public class SimulationTaskSchool implements Serializable {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer taskId;

    private Integer schoolId;

    private String schoolName;

    /** 冲/稳/保 */
    private String category;

    private Integer sortOrder;

    /** 录取概率(0-100) */
    private BigDecimal admissionProb;

    /** 高/中/低 */
    private String admissionProbLevel;

    /** 专业调剂风险(0-100) */
    private BigDecimal majorAdjustRisk;

    private String majorAdjustRiskLevel;

    private BigDecimal popularityScore;

    private String popularityTrend;

    private String rankFluctuation;

    private BigDecimal avgRank3yr;

    private BigDecimal rankStdDev;

    private String rankTrend;

    /** 已选专业名,逗号分隔 */
    private String selectedMajors;

    /** avgRank/userRank比值 */
    private BigDecimal probRankRatio;

    /** 稳定性因子 */
    private BigDecimal probStabilityFactor;

    /** 类别调整说明 */
    private String probCategoryAdj;

    /** 调整前概率 */
    private BigDecimal probBaseValue;

    /** 完整概率解释 */
    private String probExplanation;

    /** 源方案录取概率(用于对比) */
    private BigDecimal sourceAdmissionProb;

    /** 源方案类别 */
    private String sourceCategory;

    /** 概率变化量 */
    private BigDecimal probDelta;

    /** 类别是否变化: 0=否, 1=是 */
    private Integer categoryChanged;

    private LocalDateTime createTime;

    @Serial
    private static final long serialVersionUID = 1L;
}
