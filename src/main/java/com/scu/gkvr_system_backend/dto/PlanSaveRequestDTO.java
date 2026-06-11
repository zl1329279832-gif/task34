package com.scu.gkvr_system_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class PlanSaveRequestDTO {
    @NotBlank(message = "方案名称不能为空")
    private String planName;

    @NotBlank(message = "用户名不能为空")
    private String userName;

    @NotNull(message = "分数不能为空")
    private Integer score;

    @NotNull(message = "位次不能为空")
    private Integer userRank;

    @NotBlank(message = "科类不能为空")
    private String subjectType;

    private String regionPref;
    private String schoolTier;
    private String majorPref;
    private String batchName;

    @NotEmpty(message = "院校列表不能为空")
    private List<PlanSchoolDTO> schools;
}
