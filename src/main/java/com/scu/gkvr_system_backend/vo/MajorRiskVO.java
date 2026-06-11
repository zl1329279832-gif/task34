package com.scu.gkvr_system_backend.vo;

import lombok.Data;

@Data
public class MajorRiskVO {
    private String majorName;
    private Integer avgScore;
    private Integer minScore;
    private boolean atRisk;
}
