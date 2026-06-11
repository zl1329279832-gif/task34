package com.scu.gkvr_system_backend.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 专业维度风险VO
 */
@Data
public class MajorRiskVO {
    private String majorName;
    private Integer historicalMax;
    private Integer historicalMin;
    private Integer historicalAvg;
    /** 分数匹配度(0-100) */
    private BigDecimal matchScore;
}
