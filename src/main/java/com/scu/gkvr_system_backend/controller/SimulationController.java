package com.scu.gkvr_system_backend.controller;

import com.scu.gkvr_system_backend.dto.SimulationBatchRequestDTO;
import com.scu.gkvr_system_backend.dto.SimulationCompareRequestDTO;
import com.scu.gkvr_system_backend.service.SimulationService;
import com.scu.gkvr_system_backend.utils.Result;
import com.scu.gkvr_system_backend.vo.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/simulation")
@CrossOrigin
public class SimulationController {

    @Autowired
    private SimulationService simulationService;

    @PostMapping("/submitBatch")
    public Result<Map<String, Object>> submitBatch(@Valid @RequestBody SimulationBatchRequestDTO request) {
        Integer batchId = simulationService.submitBatch(request);
        if (batchId != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("batchId", batchId);
            return Result.success(data);
        }
        return Result.fail("源方案不存在或无权限");
    }

    @GetMapping("/batchStatus")
    public Result<SimulationBatchVO> getBatchStatus(@RequestParam Integer batchId,
                                                     @RequestParam String userName) {
        SimulationBatchVO vo = simulationService.getBatchStatus(batchId, userName);
        if (vo != null) {
            return Result.success(vo);
        }
        return Result.fail("批次不存在或无权限");
    }

    @GetMapping("/taskDetail")
    public Result<SimulationTaskDetailVO> getTaskDetail(@RequestParam Integer taskId,
                                                         @RequestParam String userName) {
        SimulationTaskDetailVO vo = simulationService.getTaskDetail(taskId, userName);
        if (vo != null) {
            return Result.success(vo);
        }
        return Result.fail("任务不存在或无权限");
    }

    @GetMapping("/explanation")
    public Result<ProbabilityExplanationVO> getExplanation(@RequestParam Integer taskId,
                                                            @RequestParam Integer schoolId,
                                                            @RequestParam String userName) {
        ProbabilityExplanationVO vo = simulationService.getSchoolExplanation(taskId, schoolId, userName);
        if (vo != null) {
            return Result.success(vo);
        }
        return Result.fail("任务或院校不存在或无权限");
    }

    @PostMapping("/compare")
    public Result<SimulationComparisonVO> compareTasks(@Valid @RequestBody SimulationCompareRequestDTO request) {
        SimulationComparisonVO vo = simulationService.compareTasks(request);
        if (vo != null) {
            return Result.success(vo);
        }
        return Result.fail("任务不存在、未完成或无权限");
    }

    @PostMapping("/cancel")
    public Result<Map<String, Object>> cancelBatch(@RequestParam Integer batchId,
                                                    @RequestParam String userName) {
        Boolean success = simulationService.cancelBatch(batchId, userName);
        if (Boolean.TRUE.equals(success)) {
            Map<String, Object> data = new HashMap<>();
            data.put("cancelled", true);
            return Result.success(data);
        }
        return Result.fail("批次不存在或无权限");
    }

    @PostMapping("/promote")
    public Result<Map<String, Object>> promoteToPlan(@RequestParam Integer taskId,
                                                      @RequestParam String userName,
                                                      @RequestParam String planName) {
        Integer planId = simulationService.promoteToPlan(taskId, userName, planName);
        if (planId != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("planId", planId);
            return Result.success(data);
        }
        return Result.fail("任务未完成或无权限");
    }

    @PostMapping("/rollback")
    public Result<Map<String, Object>> rollbackFromSnapshot(@RequestParam Integer taskId,
                                                             @RequestParam String userName) {
        Boolean success = simulationService.rollbackFromSnapshot(taskId, userName);
        if (Boolean.TRUE.equals(success)) {
            Map<String, Object> data = new HashMap<>();
            data.put("rolledBack", true);
            return Result.success(data);
        }
        return Result.fail("快照不存在或无权限");
    }

    @GetMapping("/listBatches")
    public Result<List<SimulationBatchVO>> listBatches(@RequestParam String userName) {
        List<SimulationBatchVO> list = simulationService.listUserBatches(userName);
        return Result.success(list);
    }
}
