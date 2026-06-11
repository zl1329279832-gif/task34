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

/**
 * 志愿方案服务接口
 */
public interface VoluntaryPlanService extends IService<VoluntaryPlan> {

    /**
     * 生成冲/稳/保方案(不保存到数据库)
     */
    Map<String, Object> generatePlans(PlanGenerateRequestDTO request);

    /**
     * 保存方案(含所有院校)
     */
    Integer savePlan(PlanSaveRequestDTO request);

    /**
     * 获取方案详情(含所有院校及风险指标)
     */
    PlanDetailVO getPlanDetail(Integer planId, String userName);

    /**
     * 列出用户所有方案(摘要)
     */
    List<VoluntaryPlan> listUserPlans(String userName);

    /**
     * 复制方案
     */
    Integer copyPlan(Integer planId, String userName, String newPlanName);

    /**
     * 调整院校排序
     */
    Boolean reorderSchools(SchoolReorderDTO request);

    /**
     * 重新评估方案(重算所有风险指标, 创建新版本)
     */
    PlanDetailVO reevaluatePlan(Integer planId, String userName);

    /**
     * 对比两个方案
     */
    PlanComparisonVO comparePlans(Integer planIdA, Integer planIdB, String userName);

    /**
     * 删除方案
     */
    Boolean deletePlan(Integer planId, String userName);

    /**
     * 更新方案状态(0=草稿, 1=已定稿)
     */
    Boolean updatePlanStatus(Integer planId, String userName, Integer status);
}
