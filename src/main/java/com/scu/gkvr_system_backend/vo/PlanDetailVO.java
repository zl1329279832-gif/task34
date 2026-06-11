package com.scu.gkvr_system_backend.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 方案详情VO
 */
@Data
public class PlanDetailVO {
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

    /** 冲一冲院校 */
    private List<PlanSchoolVO> reachSchools;
    /** 稳一稳院校 */
    private List<PlanSchoolVO> matchSchools;
    /** 保一保院校 */
    private List<PlanSchoolVO> safetySchools;

    private PlanRiskSummary riskSummary;
}
