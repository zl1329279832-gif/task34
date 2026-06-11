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
 * 方案-院校明细表
 * @TableName plan_school
 */
@TableName(value = "plan_school")
@Data
public class PlanSchool implements Serializable {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer planId;

    private Integer schoolId;

    private String schoolName;

    /** 冲/稳/保 */
    private String category;

    private Integer sortOrder;

    /** 录取概率(0-100) */
    private BigDecimal admissionProb;

    /** 专业调剂风险(0-100, 越高风险越大) */
    private BigDecimal majorAdjustRisk;

    /** 院校热度评分(0-100) */
    private BigDecimal popularityScore;

    /** 热度趋势: rising/stable/declining */
    private String popularityTrend;

    /** 位次波动解释 */
    private String rankFluctuation;

    /** 三年平均位次 */
    private BigDecimal avgRank3yr;

    /** 位次标准差 */
    private BigDecimal rankStdDev;

    /** 位次趋势: rising/stable/declining */
    private String rankTrend;

    /** 已选专业名,逗号分隔 */
    private String selectedMajors;

    private LocalDateTime createTime;

    @Serial
    private static final long serialVersionUID = 1L;
}
