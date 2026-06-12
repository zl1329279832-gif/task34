package com.scu.gkvr_system_backend.service;

import com.scu.gkvr_system_backend.dto.SimulationBatchRequestDTO;
import com.scu.gkvr_system_backend.dto.SimulationCompareRequestDTO;
import com.scu.gkvr_system_backend.vo.*;

/**
 * 模拟批处理服务接口
 */
public interface SimulationService {

    /**
     * 提交批量模拟.
     * @return 批次任务ID
     */
    Integer submitBatchSimulation(SimulationBatchRequestDTO request);

    /**
     * 查询批次状态(含过时检测).
     */
    SimulationBatchStatusVO getBatchStatus(Integer batchTaskId, String userName);

    /**
     * 获取概率解释明细.
     */
    SimulationExplanationVO getExplanation(Integer simulationVariantId, String userName);

    /**
     * 获取风险快照.
     */
    SimulationSnapshotVO getSnapshot(Integer simulationVariantId, String userName);

    /**
     * 对比两个模拟版本或模拟与正式方案.
     */
    SimulationCompareVO compareSimulations(SimulationCompareRequestDTO request);

    /**
     * 将模拟方案提升为正式方案.
     * @return 正式方案ID
     */
    Integer promoteToPlan(Integer simulationVariantId, String userName, String newPlanName);

    /**
     * 回滚到历史版本(创建单变体模拟批次).
     * @return 模拟变体ID
     */
    Integer rollbackToVersion(Integer planId, Integer targetVersion, String userName);
}
