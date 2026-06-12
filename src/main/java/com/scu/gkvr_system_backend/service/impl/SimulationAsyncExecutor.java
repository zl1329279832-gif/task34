package com.scu.gkvr_system_backend.service.impl;

import com.scu.gkvr_system_backend.pojo.SimulationBatchTask;
import com.scu.gkvr_system_backend.pojo.SimulationVariant;
import com.scu.gkvr_system_backend.pojo.VoluntaryPlan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 模拟异步执行器.
 * 编排批次任务的并行计算.
 */
@Service
public class SimulationAsyncExecutor {

    @Autowired
    @Qualifier("simulationExecutor")
    private ThreadPoolTaskExecutor executor;

    @Autowired
    private SimulationVariantComputationService computationService;

    /**
     * 异步执行整个批次.
     * 每个变体在独立的 CompletableFuture 中并行计算.
     */
    public void executeBatch(SimulationBatchTask task,
                              List<SimulationVariant> variants,
                              VoluntaryPlan basePlan) {
        executor.execute(() -> {
            try {
                computationService.markBatchRunning(task.getId());

                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (SimulationVariant v : variants) {
                    futures.add(CompletableFuture.runAsync(() ->
                            computationService.computeVariant(task, v, basePlan), executor));
                }

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                computationService.finalizeBatch(task.getId());
            } catch (Exception e) {
                computationService.markBatchFailed(task.getId(),
                        e.getMessage() != null ? e.getMessage() : "批量计算异常");
            }
        });
    }
}
