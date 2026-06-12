package com.scu.gkvr_system_backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scu.gkvr_system_backend.pojo.VoluntaryPlan;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface VoluntaryPlanMapper extends BaseMapper<VoluntaryPlan> {

    @Select("SELECT * FROM voluntary_plan WHERE user_name = #{userName} ORDER BY update_time DESC")
    List<VoluntaryPlan> selectByUserName(@Param("userName") String userName);

    @Select("SELECT MAX(version) FROM voluntary_plan WHERE id = #{planId}")
    Integer selectMaxVersion(@Param("planId") Integer planId);

    /** 仅查询正式方案(排除模拟方案) */
    @Select("SELECT * FROM voluntary_plan WHERE user_name = #{userName} " +
            "AND simulation_batch_task_id IS NULL ORDER BY update_time DESC")
    List<VoluntaryPlan> selectRealPlansByUserName(@Param("userName") String userName);

    /** 将模拟方案提升为正式方案: 清除simulation_batch_task_id, 更新名称和parentId */
    @Update("UPDATE voluntary_plan SET simulation_batch_task_id = NULL, " +
            "plan_name = #{planName}, parent_id = #{parentId} WHERE id = #{planId}")
    int promoteSimulation(@Param("planId") Integer planId,
                          @Param("planName") String planName,
                          @Param("parentId") Integer parentId);
}
