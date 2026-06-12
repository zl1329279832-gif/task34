package com.scu.gkvr_system_backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scu.gkvr_system_backend.pojo.SimulationTask;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface SimulationTaskMapper extends BaseMapper<SimulationTask> {

    @Select("SELECT * FROM simulation_task WHERE batch_id = #{batchId} ORDER BY id ASC")
    List<SimulationTask> selectByBatchId(@Param("batchId") Integer batchId);

    @Update("UPDATE simulation_task SET status = 'COMPUTING', compute_start = NOW(), " +
            "sequence_number = sequence_number + 1 " +
            "WHERE id = #{taskId} AND status = 'PENDING'")
    int markComputing(@Param("taskId") Integer taskId);

    @Update("UPDATE simulation_task SET status = 'COMPLETED', compute_end = NOW(), " +
            "result_data = #{resultData}, risk_snapshot = #{riskSnapshot}, " +
            "total_risk_score = #{totalRiskScore}, reach_count = #{reachCount}, " +
            "match_count = #{matchCount}, safety_count = #{safetyCount}, " +
            "sequence_number = sequence_number + 1 " +
            "WHERE id = #{taskId} AND sequence_number = #{expectedSeq}")
    int completeTask(@Param("taskId") Integer taskId,
                     @Param("resultData") String resultData,
                     @Param("riskSnapshot") String riskSnapshot,
                     @Param("totalRiskScore") java.math.BigDecimal totalRiskScore,
                     @Param("reachCount") int reachCount,
                     @Param("matchCount") int matchCount,
                     @Param("safetyCount") int safetyCount,
                     @Param("expectedSeq") int expectedSeq);

    @Update("UPDATE simulation_task SET status = 'FAILED', compute_end = NOW(), " +
            "error_message = #{errorMessage}, sequence_number = sequence_number + 1 " +
            "WHERE id = #{taskId}")
    int failTask(@Param("taskId") Integer taskId, @Param("errorMessage") String errorMessage);

    @Update("UPDATE simulation_task SET status = 'CANCELLED', sequence_number = sequence_number + 1 " +
            "WHERE batch_id = #{batchId} AND status = 'PENDING'")
    int cancelPendingByBatchId(@Param("batchId") Integer batchId);
}
