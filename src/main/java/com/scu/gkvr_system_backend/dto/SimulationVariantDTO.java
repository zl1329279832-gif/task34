package com.scu.gkvr_system_backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SimulationVariantDTO {

    @Size(max = 100)
    private String taskLabel;

    /** 覆盖分数(null=继承源方案) */
    @Min(value = 0, message = "分数不能为负")
    @Max(value = 750, message = "分数不能超过750")
    private Integer score;

    /** 覆盖位次(null=继承源方案) */
    @Min(value = 1, message = "位次至少为1")
    private Integer userRank;

    /** 覆盖批次(null=继承源方案) */
    private String batchName;

    /** 覆盖地区偏好(null=继承源方案) */
    private List<String> regionPref;

    /** 覆盖院校层次(null=继承源方案) */
    private List<String> schoolTier;

    /** 覆盖专业偏好(null=继承源方案) */
    private List<String> majorPref;
}
