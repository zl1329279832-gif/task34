package com.scu.gkvr_system_backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scu.gkvr_system_backend.pojo.PlanVersionLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface PlanVersionLogMapper extends BaseMapper<PlanVersionLog> {

    @Select("SELECT * FROM plan_version_log WHERE plan_id = #{planId} AND version = #{version}")
    PlanVersionLog selectByPlanIdAndVersion(@Param("planId") Integer planId,
                                            @Param("version") Integer version);

    @Select("SELECT * FROM plan_version_log WHERE plan_id = #{planId} ORDER BY version ASC")
    List<PlanVersionLog> selectByPlanId(@Param("planId") Integer planId);
}
