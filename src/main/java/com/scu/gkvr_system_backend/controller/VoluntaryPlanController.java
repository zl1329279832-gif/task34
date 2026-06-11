package com.scu.gkvr_system_backend.controller;

import com.scu.gkvr_system_backend.dto.PlanGenerateRequestDTO;
import com.scu.gkvr_system_backend.dto.PlanSaveRequestDTO;
import com.scu.gkvr_system_backend.dto.SchoolReorderDTO;
import com.scu.gkvr_system_backend.pojo.VoluntaryPlan;
import com.scu.gkvr_system_backend.service.VoluntaryPlanService;
import com.scu.gkvr_system_backend.utils.Result;
import com.scu.gkvr_system_backend.vo.PlanComparisonVO;
import com.scu.gkvr_system_backend.vo.PlanDetailVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 志愿方案模拟与录取风险评估控制器
 */
@RestController
@RequestMapping("/voluntaryPlan")
@CrossOrigin
public class VoluntaryPlanController {

    @Autowired
    private VoluntaryPlanService voluntaryPlanService;

    /**
     * 生成冲/稳/保方案(不保存到数据库)
     */
    @PostMapping("/generate")
    public Result<Map<String, Object>> generatePlans(
            @Valid @RequestBody PlanGenerateRequestDTO request) {
        Map<String, Object> data = voluntaryPlanService.generatePlans(request);
        if (data != null && !data.containsKey("error")) {
            return Result.success(data);
        }
        return Result.fail("方案生成失败: " + data.getOrDefault("error", "未知错误"));
    }

    /**
     * 保存方案(含所有院校)
     */
    @PostMapping("/save")
    public Result<Map<String, Object>> savePlan(
            @Valid @RequestBody PlanSaveRequestDTO request) {
        Integer planId = voluntaryPlanService.savePlan(request);
        if (planId != null) {
            return Result.success(Map.of("planId", planId));
        }
        return Result.fail("保存失败");
    }

    /**
     * 获取方案详情
     */
    @GetMapping("/detail")
    public Result<PlanDetailVO> getPlanDetail(@RequestParam Integer planId,
                                              @RequestParam String userName) {
        PlanDetailVO detail = voluntaryPlanService.getPlanDetail(planId, userName);
        if (detail != null) {
            return Result.success(detail);
        }
        return Result.fail("方案不存在或无权限");
    }

    /**
     * 列出用户所有方案
     */
    @GetMapping("/list")
    public Result<List<VoluntaryPlan>> listPlans(@RequestParam String userName) {
        List<VoluntaryPlan> plans = voluntaryPlanService.listUserPlans(userName);
        return Result.success(plans);
    }

    /**
     * 复制方案
     */
    @PostMapping("/copy")
    public Result<Map<String, Object>> copyPlan(@RequestParam Integer planId,
                                                 @RequestParam String userName,
                                                 @RequestParam String newPlanName) {
        Integer newId = voluntaryPlanService.copyPlan(planId, userName, newPlanName);
        if (newId != null) {
            return Result.success(Map.of("planId", newId));
        }
        return Result.fail("复制失败，方案不存在或无权限");
    }

    /**
     * 调整院校排序
     */
    @PostMapping("/reorder")
    public Result<Map<String, Object>> reorderSchools(
            @Valid @RequestBody SchoolReorderDTO request) {
        Boolean success = voluntaryPlanService.reorderSchools(request);
        if (success) {
            return Result.success("排序更新成功");
        }
        return Result.fail("排序更新失败，方案不存在或无权限");
    }

    /**
     * 重新评估方案(重算所有风险指标)
     */
    @PostMapping("/reevaluate")
    public Result<PlanDetailVO> reevaluatePlan(@RequestParam Integer planId,
                                                @RequestParam String userName) {
        PlanDetailVO detail = voluntaryPlanService.reevaluatePlan(planId, userName);
        if (detail != null) {
            return Result.success(detail);
        }
        return Result.fail("重新评估失败，方案不存在或无权限");
    }

    /**
     * 对比两个方案
     */
    @GetMapping("/compare")
    public Result<PlanComparisonVO> comparePlans(@RequestParam Integer planIdA,
                                                  @RequestParam Integer planIdB,
                                                  @RequestParam String userName) {
        PlanComparisonVO comparison = voluntaryPlanService.comparePlans(planIdA, planIdB, userName);
        if (comparison != null) {
            return Result.success(comparison);
        }
        return Result.fail("对比失败，方案不存在或无权限");
    }

    /**
     * 删除方案
     */
    @PostMapping("/delete")
    public Result<Map<String, Object>> deletePlan(@RequestParam Integer planId,
                                                   @RequestParam String userName) {
        Boolean success = voluntaryPlanService.deletePlan(planId, userName);
        if (success) {
            return Result.success("删除成功");
        }
        return Result.fail("删除失败，方案不存在或无权限");
    }

    /**
     * 更新方案状态(0=草稿, 1=已定稿)
     */
    @PostMapping("/updateStatus")
    public Result<Map<String, Object>> updateStatus(@RequestParam Integer planId,
                                                     @RequestParam String userName,
                                                     @RequestParam Integer status) {
        Boolean success = voluntaryPlanService.updatePlanStatus(planId, userName, status);
        if (success) {
            return Result.success("状态更新成功");
        }
        return Result.fail("状态更新失败");
    }
}
