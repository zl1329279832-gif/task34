package com.scu.gkvr_system_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PlanSchoolDTO {
    @NotNull
    private Integer schoolId;

    @NotBlank
    private String schoolName;

    @NotBlank
    private String category;

    private Integer sortOrder;
    private BigDecimal admissionProb;
    private BigDecimal majorAdjustRisk;
    private BigDecimal popularityScore;
    private String popularityTrend;
    private String rankFluctuation;
    private BigDecimal avgRank3yr;
    private BigDecimal rankStdDev;
    private String rankTrend;
    private List<String> selectedMajors;
}
