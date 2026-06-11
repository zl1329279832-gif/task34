package com.scu.gkvr_system_backend.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PlanRiskSummary {
    private BigDecimal totalRiskScore;
    private String riskLevel;
    private int reachCount;
    private int matchCount;
    private int safetyCount;
    private String suggestion;
}
