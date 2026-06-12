package com.scu.gkvr_system_backend.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@TableName(value = "simulation_batch")
@Data
public class SimulationBatch implements Serializable {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String userName;

    private Integer sourcePlanId;

    private String batchName;

    private Integer sequenceNumber;

    /** PENDING/COMPUTING/COMPLETED/FAILED/CANCELLED */
    private String status;

    private Integer totalTasks;

    private Integer completedTasks;

    private Integer failedTasks;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @Serial
    private static final long serialVersionUID = 1L;
}
