package com.scu.gkvr_system_backend.service;

/**
 * 模拟任务异步计算服务.
 * 独立接口以确保 Spring AOP @Async 代理正常工作(避免自调用绕过代理).
 */
public interface SimulationComputationService {

    /**
     * 异步计算单个模拟任务.
     * @param taskId 模拟任务ID
     */
    void computeVariantAsync(Integer taskId);
}
