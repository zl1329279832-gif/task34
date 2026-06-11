package com.scu.gkvr_system_backend.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 方案版本快照日志
 * @TableName plan_version_log
 */
@TableName(value = "plan_version_log")
@Data
public class PlanVersionLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer planId;

    private Integer version;

    /** 该版本完整快照(JSON) */
    private String snapshotData;

    private LocalDateTime createTime;

    @Serial
    private static final long serialVersionUID = 1L;
}
