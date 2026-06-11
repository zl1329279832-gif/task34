package com.scu.gkvr_system_backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scu.gkvr_system_backend.pojo.VoluntaryPlan;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface VoluntaryPlanMapper extends BaseMapper<VoluntaryPlan> {

    @Select("SELECT * FROM voluntary_plan WHERE user_name = #{userName} ORDER BY update_time DESC")
    List<VoluntaryPlan> selectByUserName(@Param("userName") String userName);

    @Select("SELECT MAX(version) FROM voluntary_plan WHERE id = #{planId}")
    Integer selectMaxVersion(@Param("planId") Integer planId);
}
