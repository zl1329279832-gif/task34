package com.scu.gkvr_system_backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.scu.gkvr_system_backend.dto.PlanGenerateRequestDTO;
import com.scu.gkvr_system_backend.dto.PlanSaveRequestDTO;
import com.scu.gkvr_system_backend.dto.SchoolReorderDTO;
import com.scu.gkvr_system_backend.pojo.VoluntaryPlan;
import com.scu.gkvr_system_backend.vo.PlanComparisonVO;
import com.scu.gkvr_system_backend.vo.PlanDetailVO;

import java.util.List;
import java.util.Map;

public interface VoluntaryPlanService extends IService<VoluntaryPlan> {

    Map<String, Object> generatePlans(PlanGenerateRequestDTO request);

    Integer savePlan(PlanSaveRequestDTO request);

    PlanDetailVO getPlanDetail(Integer planId, String userName);

    List<VoluntaryPlan> listPlans(String userName);

    boolean deletePlan(Integer planId, String userName);

    Integer copyPlan(Integer planId, String userName);

    boolean reorderSchools(SchoolReorderDTO request);

    PlanDetailVO reevaluatePlan(Integer planId, String userName);

    PlanComparisonVO comparePlans(Integer planIdA, Integer planIdB, String userName);
}
