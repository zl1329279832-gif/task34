package com.scu.gkvr_system_backend.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("plan_school")
@Data
public class PlanSchool implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer planId;
    private Integer schoolId;
    private String schoolName;
    private String category;
    private Integer sortOrder;
    private BigDecimal admissionProb;
    private BigDecimal majorAdjustRisk;
    private BigDecimal popularityScore;
    private String popularityTrend;
    private String rankFluctuation;
    @TableField("avg_rank_3yr")
    private BigDecimal avgRank3yr;
    private BigDecimal rankStdDev;
    private String rankTrend;
    private String selectedMajors;
    private LocalDateTime createTime;
}
