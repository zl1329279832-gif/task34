package com.scu.gkvr_system_backend.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

/**
 * 方案生成请求DTO
 */
@Data
public class PlanGenerateRequestDTO {

    @NotBlank(message = "用户名不能为空")
    private String userName;

    @NotNull(message = "分数不能为空")
    @Min(value = 0, message = "分数不能为负")
    @Max(value = 750, message = "分数不能超过750")
    private Integer score;

    @NotNull(message = "位次不能为空")
    @Min(value = 1, message = "位次至少为1")
    private Integer userRank;

    @NotBlank(message = "科类不能为空")
    private String subjectType;

    /** 省份名列表, null或空=不限 */
    private List<String> regionPref;

    /** ["985","211","双一流","普通本科"], null=不限 */
    private List<String> schoolTier;

    /** 专业类名列表, null=不限 */
    private List<String> majorPref;

    @Min(value = 1, message = "冲一冲数量至少为1")
    @Max(value = 30, message = "冲一冲数量不能超过30")
    private Integer reachCount = 5;

    @Min(1)
    @Max(30)
    private Integer matchCount = 10;

    @Min(1)
    @Max(30)
    private Integer safetyCount = 5;
}
