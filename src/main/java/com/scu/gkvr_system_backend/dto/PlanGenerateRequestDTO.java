package com.scu.gkvr_system_backend.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class PlanGenerateRequestDTO {
    @NotBlank(message = "用户名不能为空")
    private String userName;

    @NotNull(message = "分数不能为空")
    @Min(value = 100, message = "分数不能低于100")
    @Max(value = 750, message = "分数不能超过750")
    private Integer score;

    private Integer userRank;

    @NotBlank(message = "科类不能为空")
    private String subjectType;

    private List<String> regionPref;
    private String schoolTier;
    private List<String> majorPref;
    private String batchName;
}
