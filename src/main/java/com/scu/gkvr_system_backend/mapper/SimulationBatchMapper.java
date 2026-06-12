package com.scu.gkvr_system_backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scu.gkvr_system_backend.pojo.SimulationBatch;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface SimulationBatchMapper extends BaseMapper<SimulationBatch> {

    @Select("SELECT * FROM simulation_batch WHERE user_name = #{userName} ORDER BY create_time DESC")
    List<SimulationBatch> selectByUserName(@Param("userName") String userName);

    @Select("SELECT * FROM simulation_batch WHERE id = #{batchId} AND user_name = #{userName}")
    SimulationBatch selectByIdAndUser(@Param("batchId") Integer batchId, @Param("userName") String userName);

    @Update("UPDATE simulation_batch SET completed_tasks = completed_tasks + 1, " +
            "sequence_number = sequence_number + 1, " +
            "status = CASE WHEN completed_tasks + failed_tasks + 1 >= total_tasks THEN 'COMPLETED' ELSE 'COMPUTING' END " +
            "WHERE id = #{batchId}")
    int incrementCompletedTasks(@Param("batchId") Integer batchId);

    @Update("UPDATE simulation_batch SET failed_tasks = failed_tasks + 1, " +
            "sequence_number = sequence_number + 1, " +
            "status = CASE WHEN completed_tasks + failed_tasks + 1 >= total_tasks THEN 'FAILED' ELSE 'COMPUTING' END " +
            "WHERE id = #{batchId}")
    int incrementFailedTasks(@Param("batchId") Integer batchId);

    @Update("UPDATE simulation_batch SET status = #{status}, sequence_number = sequence_number + 1 WHERE id = #{batchId}")
    int updateStatus(@Param("batchId") Integer batchId, @Param("status") String status);
}
