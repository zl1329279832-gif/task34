package com.scu.gkvr_system_backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scu.gkvr_system_backend.pojo.SimulationBatchTask;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface SimulationBatchTaskMapper extends BaseMapper<SimulationBatchTask> {

    @Select("SELECT * FROM simulation_batch_task WHERE user_name = #{userName} " +
            "ORDER BY create_time DESC LIMIT 20")
    List<SimulationBatchTask> selectRecentByUserName(@Param("userName") String userName);

    /** 原子递增已完成数, 防止并发竞争 */
    @Update("UPDATE simulation_batch_task SET " +
            "completed_variants = completed_variants + 1, " +
            "progress_percent = (completed_variants + 1) * 100.0 / total_variants, " +
            "update_time = NOW() WHERE id = #{id}")
    int incrementCompletedVariants(@Param("id") Integer id);

    @Update("UPDATE simulation_batch_task SET status = #{status}, update_time = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Integer id, @Param("status") String status);
}
