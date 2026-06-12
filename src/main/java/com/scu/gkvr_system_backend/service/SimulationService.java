package com.scu.gkvr_system_backend.service;

import com.scu.gkvr_system_backend.dto.SimulationBatchRequestDTO;
import com.scu.gkvr_system_backend.dto.SimulationCompareRequestDTO;
import com.scu.gkvr_system_backend.vo.*;

import java.util.List;

/**
 * 模拟批处理服务接口.
 */
public interface SimulationService {

    /**
     * 提交模拟批次(含多个变体任务).
     * @return 批次ID, null表示失败
     */
    Integer submitBatch(SimulationBatchRequestDTO request);

    /**
     * 查询批次状态(含各任务摘要).
     */
    SimulationBatchVO getBatchStatus(Integer batchId, String userName);

    /**
     * 查询已完成任务的详细结果.
     */
    SimulationTaskDetailVO getTaskDetail(Integer taskId, String userName);

    /**
     * 查询单个院校的概率解释明细.
     */
    ProbabilityExplanationVO getSchoolExplanation(Integer taskId, Integer schoolId, String userName);

    /**
     * 对比两个模拟任务的结果差异.
     */
    SimulationComparisonVO compareTasks(SimulationCompareRequestDTO request);

    /**
     * 取消批次中尚未开始的任务.
     */
    Boolean cancelBatch(Integer batchId, String userName);

    /**
     * 将已完成的模拟任务提升为正式方案.
     * @return 新方案ID, null表示失败
     */
    Integer promoteToPlan(Integer taskId, String userName, String planName);

    /**
     * 从模拟任务的风险快照回滚源方案数据.
     */
    Boolean rollbackFromSnapshot(Integer taskId, String userName);

    /**
     * 列出用户的所有模拟批次.
     */
    List<SimulationBatchVO> listUserBatches(String userName);
}
