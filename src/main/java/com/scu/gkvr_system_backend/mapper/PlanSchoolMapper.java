package com.scu.gkvr_system_backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scu.gkvr_system_backend.pojo.PlanSchool;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

public interface PlanSchoolMapper extends BaseMapper<PlanSchool> {

    @Select("SELECT * FROM plan_school WHERE plan_id = #{planId} " +
            "ORDER BY FIELD(category, '冲', '稳', '保'), sort_order ASC")
    List<PlanSchool> selectByPlanIdOrdered(@Param("planId") Integer planId);

    @Select("SELECT * FROM plan_school WHERE plan_id = #{planId} AND school_id = #{schoolId}")
    PlanSchool selectByPlanIdAndSchoolId(@Param("planId") Integer planId,
                                         @Param("schoolId") Integer schoolId);

    @Update("UPDATE plan_school SET sort_order = #{sortOrder} WHERE id = #{id}")
    int updateSortOrder(@Param("id") Integer id, @Param("sortOrder") Integer sortOrder);

    /**
     * 查询候选院校(带院校属性), 用于方案生成算法.
     * 省份/层次过滤通过 XML 动态 SQL 实现.
     */
    List<Map<String, Object>> selectCandidateSchools(@Param("upperRank") int upperRank,
                                                      @Param("lowerRank") int lowerRank,
                                                      @Param("provinces") List<String> provinces,
                                                      @Param("require985") boolean require985,
                                                      @Param("require211") boolean require211,
                                                      @Param("requireDoubleHigh") boolean requireDoubleHigh);

    /**
     * 查询某院校的专业录取分数, 用于调剂风险计算.
     */
    List<Map<String, Object>> selectMajorScoresForSchool(@Param("schoolId") int schoolId,
                                                          @Param("batchName") String batchName);
}
