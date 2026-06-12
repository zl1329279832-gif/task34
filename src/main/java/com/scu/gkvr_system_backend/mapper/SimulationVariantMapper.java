package com.scu.gkvr_system_backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scu.gkvr_system_backend.pojo.SimulationVariant;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SimulationVariantMapper extends BaseMapper<SimulationVariant> {

    @Select("SELECT * FROM simulation_variant WHERE batch_task_id = #{batchTaskId} " +
            "ORDER BY variant_index ASC")
    List<SimulationVariant> selectByBatchTaskId(@Param("batchTaskId") Integer batchTaskId);
}
