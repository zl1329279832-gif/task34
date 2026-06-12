package com.scu.gkvr_system_backend.controller;

import com.scu.gkvr_system_backend.dto.SimulationBatchRequestDTO;
import com.scu.gkvr_system_backend.dto.SimulationCompareRequestDTO;
import com.scu.gkvr_system_backend.service.SimulationService;
import com.scu.gkvr_system_backend.vo.*;
import com.scu.gkvr_system_backend.utils.Result;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 模拟批处理控制器
 */
@RestController
@RequestMapping("/simulation")
@CrossOrigin
public class SimulationController {

    @Autowired
    private SimulationService simulationService;

    /**
     * 提交批量模拟
     */
    @PostMapping("/batch")
    public Result<Map<String, Object>> submitBatch(@Valid @RequestBody SimulationBatchRequestDTO request) {
        try {
            Integer batchTaskId = simulationService.submitBatchSimulation(request);
            return Result.success(Map.of("batchTaskId", batchTaskId));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (IllegalStateException e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 查询计算状态
     */
    @GetMapping("/status/{batchTaskId}")
    public Result<SimulationBatchStatusVO> getStatus(@PathVariable Integer batchTaskId,
                                                      @RequestParam String userName) {
        SimulationBatchStatusVO status = simulationService.getBatchStatus(batchTaskId, userName);
        if (status != null) {
            return Result.success(status);
        }
        return Result.fail("批次任务不存在或无权限");
    }

    /**
     * 获取概率解释明细
     */
    @GetMapping("/explanation/{simulationId}")
    public Result<SimulationExplanationVO> getExplanation(@PathVariable Integer simulationId,
                                                            @RequestParam String userName) {
        SimulationExplanationVO exp = simulationService.getExplanation(simulationId, userName);
        if (exp != null) {
            return Result.success(exp);
        }
        return Result.fail("模拟结果不存在或尚未完成");
    }

    /**
     * 获取风险快照
     */
    @GetMapping("/snapshot/{simulationId}")
    public Result<SimulationSnapshotVO> getSnapshot(@PathVariable Integer simulationId,
                                                     @RequestParam String userName) {
        SimulationSnapshotVO snap = simulationService.getSnapshot(simulationId, userName);
        if (snap != null) {
            return Result.success(snap);
        }
        return Result.fail("快照不存在");
    }

    /**
     * 版本对比
     */
    @GetMapping("/compare")
    public Result<SimulationCompareVO> compare(
            @RequestParam(required = false) Integer simulationIdA,
            @RequestParam(required = false) Integer planIdA,
            @RequestParam(required = false) Integer simulationIdB,
            @RequestParam(required = false) Integer planIdB,
            @RequestParam String userName) {
        SimulationCompareRequestDTO dto = new SimulationCompareRequestDTO();
        dto.setUserName(userName);
        dto.setSimulationIdA(simulationIdA);
        dto.setPlanIdA(planIdA);
        dto.setSimulationIdB(simulationIdB);
        dto.setPlanIdB(planIdB);
        SimulationCompareVO result = simulationService.compareSimulations(dto);
        if (result != null) {
            return Result.success(result);
        }
        return Result.fail("对比失败");
    }

    /**
     * 提升模拟方案为正式方案
     */
    @PostMapping("/promote")
    public Result<Map<String, Object>> promote(@RequestParam Integer simulationVariantId,
                                                @RequestParam String userName,
                                                @RequestParam String newPlanName) {
        Integer planId = simulationService.promoteToPlan(simulationVariantId, userName, newPlanName);
        if (planId != null) {
            return Result.success(Map.of("planId", planId));
        }
        return Result.fail("提升失败");
    }

    /**
     * 历史版本回滚
     */
    @PostMapping("/rollback")
    public Result<Map<String, Object>> rollback(@RequestParam Integer planId,
                                                 @RequestParam Integer targetVersion,
                                                 @RequestParam String userName) {
        Integer simId = simulationService.rollbackToVersion(planId, targetVersion, userName);
        if (simId != null) {
            return Result.success(Map.of("simulationVariantId", simId));
        }
        return Result.fail("回滚失败");
    }
}
