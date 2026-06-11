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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/voluntaryPlan")
@CrossOrigin
public class VoluntaryPlanController {

    @Autowired
    private VoluntaryPlanService voluntaryPlanService;

    @PostMapping("/generate")
    public Result<Map<String, Object>> generatePlans(@Valid @RequestBody PlanGenerateRequestDTO request) {
        Map<String, Object> data = voluntaryPlanService.generatePlans(request);
        if (data.containsKey("error")) {
            return Result.fail((String) data.get("error"));
        }
        return Result.success(data);
    }

    @PostMapping("/save")
    public Result<Map<String, Object>> savePlan(@Valid @RequestBody PlanSaveRequestDTO request) {
        Integer planId = voluntaryPlanService.savePlan(request);
        if (planId != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("planId", planId);
            return Result.success(data);
        }
        return Result.fail("保存失败");
    }

    @GetMapping("/detail")
    public Result<Map<String, Object>> getDetail(@RequestParam Integer planId,
                                                  @RequestParam String userName) {
        PlanDetailVO detail = voluntaryPlanService.getPlanDetail(planId, userName);
        if (detail != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("plan", detail);
            return Result.success(data);
        }
        return Result.fail("方案不存在或无权访问");
    }

    @GetMapping("/list")
    public Result<Map<String, Object>> listPlans(@RequestParam String userName) {
        List<VoluntaryPlan> plans = voluntaryPlanService.listPlans(userName);
        Map<String, Object> data = new HashMap<>();
        data.put("plans", plans);
        return Result.success(data);
    }

    @PostMapping("/delete")
    public Result<Map<String, Object>> deletePlan(@RequestParam Integer planId,
                                                   @RequestParam String userName) {
        boolean ok = voluntaryPlanService.deletePlan(planId, userName);
        return ok ? Result.success("删除成功") : Result.fail("删除失败");
    }

    @PostMapping("/copy")
    public Result<Map<String, Object>> copyPlan(@RequestParam Integer planId,
                                                 @RequestParam String userName) {
        Integer newId = voluntaryPlanService.copyPlan(planId, userName);
        if (newId != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("planId", newId);
            return Result.success(data);
        }
        return Result.fail("复制失败");
    }

    @PostMapping("/reorder")
    public Result<Map<String, Object>> reorderSchools(@Valid @RequestBody SchoolReorderDTO request) {
        boolean ok = voluntaryPlanService.reorderSchools(request);
        return ok ? Result.success("排序成功") : Result.fail("排序失败");
    }

    @PostMapping("/reevaluate")
    public Result<Map<String, Object>> reevaluatePlan(@RequestParam Integer planId,
                                                      @RequestParam String userName) {
        PlanDetailVO detail = voluntaryPlanService.reevaluatePlan(planId, userName);
        if (detail != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("plan", detail);
            return Result.success(data);
        }
        return Result.fail("重新评估失败");
    }

    @GetMapping("/compare")
    public Result<Map<String, Object>> comparePlans(@RequestParam Integer planIdA,
                                                     @RequestParam Integer planIdB,
                                                     @RequestParam String userName) {
        PlanComparisonVO comparison = voluntaryPlanService.comparePlans(planIdA, planIdB, userName);
        if (comparison != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("comparison", comparison);
            return Result.success(data);
        }
        return Result.fail("对比失败");
    }
}
