package com.scu.gkvr_system_backend.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("voluntary_plan")
@Data
public class VoluntaryPlan implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

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
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
