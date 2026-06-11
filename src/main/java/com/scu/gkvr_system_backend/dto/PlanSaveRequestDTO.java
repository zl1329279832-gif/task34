package com.scu.gkvr_system_backend.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

/**
 * 方案保存请求DTO
 */
@Data
public class PlanSaveRequestDTO {

    @NotBlank(message = "用户名不能为空")
    private String userName;

    @NotBlank(message = "方案名称不能为空")
    @Size(max = 100, message = "方案名称不能超过100个字符")
    private String planName;

    @NotNull(message = "分数不能为空")
    @Min(value = 0, message = "分数不能为负")
    @Max(value = 750, message = "分数不能超过750")
    private Integer score;

    @NotNull(message = "位次不能为空")
    @Min(value = 1, message = "位次至少为1")
    private Integer userRank;

    @NotBlank(message = "科类不能为空")
    private String subjectType;

    private String regionPref;
    private String schoolTier;
    private String majorPref;
    private String batchName;

    @NotEmpty(message = "方案院校列表不能为空")
    @Size(max = 90, message = "单个方案院校不能超过90所")
    private List<PlanSchoolDTO> schools;
}
