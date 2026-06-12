package com.scu.gkvr_system_backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scu.gkvr_system_backend.pojo.SimulationTaskSchool;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SimulationTaskSchoolMapper extends BaseMapper<SimulationTaskSchool> {

    @Select("SELECT * FROM simulation_task_school WHERE task_id = #{taskId} " +
            "ORDER BY CASE category WHEN '冲' THEN 1 WHEN '稳' THEN 2 WHEN '保' THEN 3 ELSE 4 END, sort_order ASC")
    List<SimulationTaskSchool> selectByTaskIdOrdered(@Param("taskId") Integer taskId);

    @Select("SELECT * FROM simulation_task_school WHERE task_id = #{taskId} AND school_id = #{schoolId}")
    SimulationTaskSchool selectByTaskIdAndSchoolId(@Param("taskId") Integer taskId, @Param("schoolId") Integer schoolId);
}
