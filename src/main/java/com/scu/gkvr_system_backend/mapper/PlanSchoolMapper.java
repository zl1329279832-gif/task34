package com.scu.gkvr_system_backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scu.gkvr_system_backend.pojo.PlanSchool;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface PlanSchoolMapper extends BaseMapper<PlanSchool> {

    List<Map<String, Object>> selectCandidateSchools(
            @Param("upperRank") int upperRank,
            @Param("lowerRank") int lowerRank,
            @Param("provinces") List<String> provinces,
            @Param("require985") boolean require985,
            @Param("require211") boolean require211,
            @Param("requireDoubleHigh") boolean requireDoubleHigh
    );

    List<Map<String, Object>> selectMajorScoresForSchool(
            @Param("schoolId") int schoolId,
            @Param("batchName") String batchName
    );
}
