package com.scu.gkvr_system_backend.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PlanDetailVO {
    private Integer planId;
    private String planName;
    private Integer score;
    private Integer userRank;
    private String subjectType;
    private String batchName;
    private Integer version;
    private Integer status;
    private PlanRiskSummary riskSummary;
    private List<PlanSchoolVO> reachSchools;
    private List<PlanSchoolVO> matchSchools;
    private List<PlanSchoolVO> safetySchools;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
