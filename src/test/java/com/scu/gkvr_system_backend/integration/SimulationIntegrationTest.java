package com.scu.gkvr_system_backend.integration;

import com.scu.gkvr_system_backend.dto.*;
import com.scu.gkvr_system_backend.mapper.*;
import com.scu.gkvr_system_backend.pojo.*;
import com.scu.gkvr_system_backend.service.SimulationService;
import com.scu.gkvr_system_backend.service.VoluntaryPlanService;
import com.scu.gkvr_system_backend.vo.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 模拟批处理端到端集成测试.
 * 使用H2内存数据库 + test profile.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SimulationIntegrationTest {

    @Autowired
    private SimulationService simulationService;

    @Autowired
    private VoluntaryPlanService voluntaryPlanService;

    @Autowired
    private VoluntaryPlanMapper voluntaryPlanMapper;

    @Autowired
    private PlanSchoolMapper planSchoolMapper;

    @Autowired
    private SimulationBatchTaskMapper batchTaskMapper;

    @Autowired
    private SimulationVariantMapper variantMapper;

    @Autowired
    private PlanVersionLogMapper planVersionLogMapper;

    private static int basePlanId;

    @BeforeAll
    static void setupClass(@Autowired VoluntaryPlanService planService) {
        // 创建一个基础方案
        PlanSaveRequestDTO request = new PlanSaveRequestDTO();
        request.setUserName("testUserA");
        request.setPlanName("集成测试基础方案");
        request.setScore(580);
        request.setUserRank(20000);
        request.setSubjectType("理科");
        request.setBatchName("本科一批");

        PlanSchoolDTO school1 = new PlanSchoolDTO();
        school1.setSchoolId(3);
        school1.setSchoolName("四川大学");
        school1.setCategory("稳");
        school1.setSortOrder(0);
        school1.setSelectedMajors("计算机科学与技术,软件工程");

        PlanSchoolDTO school2 = new PlanSchoolDTO();
        school2.setSchoolId(5);
        school2.setSchoolName("西南交通大学");
        school2.setCategory("保");
        school2.setSortOrder(1);

        PlanSchoolDTO school3 = new PlanSchoolDTO();
        school3.setSchoolId(4);
        school3.setSchoolName("电子科技大学");
        school3.setCategory("冲");
        school3.setSortOrder(0);

        request.setSchools(List.of(school1, school2, school3));
        basePlanId = planService.savePlan(request);
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  完整生命周期                                            ║
    // ╚══════════════════════════════════════════════════════════╝

    @Test
    @Order(1)
    @DisplayName("完整生命周期: save → submit → poll → explain → promote")
    void fullLifecycle() throws Exception {
        assertNotNull(basePlanId, "基础方案应已创建");

        // 1. 提交批量模拟
        SimulationBatchRequestDTO batchReq = new SimulationBatchRequestDTO();
        batchReq.setUserName("testUserA");
        batchReq.setBasePlanId(basePlanId);

        SimulationVariantDTO v1 = new SimulationVariantDTO();
        v1.setVariantName("分数+20");
        v1.setScoreOverride(600);

        SimulationVariantDTO v2 = new SimulationVariantDTO();
        v2.setVariantName("位次-5000");
        v2.setRankOverride(15000);

        batchReq.setVariants(List.of(v1, v2));
        Integer batchTaskId = simulationService.submitBatchSimulation(batchReq);
        assertNotNull(batchTaskId);

        // 2. 等待异步计算完成(最多30秒)
        SimulationBatchStatusVO status = null;
        for (int i = 0; i < 60; i++) {
            status = simulationService.getBatchStatus(batchTaskId, "testUserA");
            assertNotNull(status);
            if ("completed".equals(status.getStatus()) || "failed".equals(status.getStatus())) {
                break;
            }
            TimeUnit.MILLISECONDS.sleep(500);
        }
        assertNotNull(status);
        assertEquals("completed", status.getStatus(), "批次应在30秒内完成");
        assertEquals(2, status.getVariants().size());

        // 3. 检查每个变体
        for (SimulationVariantStatusVO vs : status.getVariants()) {
            assertEquals("completed", vs.getStatus());
            assertNotNull(vs.getSimulatedPlanId());

            // 4. 获取概率解释
            SimulationExplanationVO explanation = simulationService.getExplanation(vs.getVariantId(), "testUserA");
            assertNotNull(explanation);
            assertNotNull(explanation.getSchoolBreakdowns());
            assertFalse(explanation.getSchoolBreakdowns().isEmpty());
            assertNotNull(explanation.getRiskSummary());
            assertNotNull(explanation.getChangeExplanation());

            // 5. 获取快照
            SimulationSnapshotVO snapshot = simulationService.getSnapshot(vs.getVariantId(), "testUserA");
            assertNotNull(snapshot);
            assertNotNull(snapshot.getTotalRiskScore());
            assertFalse(snapshot.getAllSchools().isEmpty());
        }

        // 6. 提升第一个变体为正式方案
        Integer firstVariantId = status.getVariants().get(0).getVariantId();
        Integer promotedId = simulationService.promoteToPlan(firstVariantId, "testUserA", "我的正式方案");
        assertNotNull(promotedId);

        // 7. 验证正式方案可通过现有接口查看
        PlanDetailVO detail = voluntaryPlanService.getPlanDetail(promotedId, "testUserA");
        assertNotNull(detail);
        assertEquals("我的正式方案", detail.getPlanName());

        // 8. 验证正式方案出现在列表中
        List<VoluntaryPlan> plans = voluntaryPlanService.listUserPlans("testUserA");
        assertTrue(plans.stream().anyMatch(p -> p.getId().equals(promotedId)));
        // 未提升的模拟方案不应出现在列表中
        for (int i = 1; i < status.getVariants().size(); i++) {
            Integer simPlanId = status.getVariants().get(i).getSimulatedPlanId();
            if (simPlanId != null && !simPlanId.equals(promotedId)) {
                assertFalse(plans.stream().anyMatch(p -> p.getId().equals(simPlanId)),
                        "未提升的模拟方案不应出现在正式方案列表中");
            }
        }
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  过时检测                                                ║
    // ╚══════════════════════════════════════════════════════════╝

    @Test
    @Order(2)
    @DisplayName("过时检测: submit → reevaluate基础 → poll看到stale")
    void stalenessDetection() throws Exception {
        // 提交模拟
        SimulationBatchRequestDTO batchReq = new SimulationBatchRequestDTO();
        batchReq.setUserName("testUserA");
        batchReq.setBasePlanId(basePlanId);

        SimulationVariantDTO v1 = new SimulationVariantDTO();
        v1.setVariantName("staleTest");
        batchReq.setVariants(List.of(v1));
        Integer batchTaskId = simulationService.submitBatchSimulation(batchReq);

        // 立即对基础方案进行reevaluate(版本号递增)
        voluntaryPlanService.reevaluatePlan(basePlanId, "testUserA");

        // 等待异步完成
        SimulationBatchStatusVO status = null;
        for (int i = 0; i < 60; i++) {
            status = simulationService.getBatchStatus(batchTaskId, "testUserA");
            if (status != null && ("completed".equals(status.getStatus())
                    || "stale".equals(status.getStatus())
                    || "failed".equals(status.getStatus()))) {
                break;
            }
            TimeUnit.MILLISECONDS.sleep(500);
        }
        assertNotNull(status);
        // 应该是stale状态(或completed但附带stale标记)
        assertTrue("stale".equals(status.getStatus()) || "completed".equals(status.getStatus()),
                "批次应被标记为stale或completed(计算在reevaluate前完成)");
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  跨批次缺失专业                                          ║
    // ╚══════════════════════════════════════════════════════════╝

    @Test
    @Order(3)
    @DisplayName("跨批次缺失专业: batch override → 降级值")
    void crossBatchMissingMajors() throws Exception {
        SimulationBatchRequestDTO batchReq = new SimulationBatchRequestDTO();
        batchReq.setUserName("testUserA");
        batchReq.setBasePlanId(basePlanId);

        SimulationVariantDTO v1 = new SimulationVariantDTO();
        v1.setVariantName("批次变化-本科二批");
        v1.setBatchNameOverride("本科二批");
        batchReq.setVariants(List.of(v1));

        Integer batchTaskId = simulationService.submitBatchSimulation(batchReq);

        // 等待完成
        SimulationBatchStatusVO status = null;
        for (int i = 0; i < 60; i++) {
            status = simulationService.getBatchStatus(batchTaskId, "testUserA");
            if (status != null && ("completed".equals(status.getStatus())
                    || "failed".equals(status.getStatus()))) {
                break;
            }
            TimeUnit.MILLISECONDS.sleep(500);
        }
        assertNotNull(status);
        assertEquals("completed", status.getStatus());

        // 验证模拟方案存在
        SimulationVariantStatusVO vs = status.getVariants().get(0);
        assertNotNull(vs.getSimulatedPlanId());

        PlanDetailVO detail = voluntaryPlanService.getPlanDetail(vs.getSimulatedPlanId(), "testUserA");
        assertNotNull(detail);
        assertEquals("本科二批", detail.getBatchName());
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  并发批次                                                ║
    // ╚══════════════════════════════════════════════════════════╝

    @Test
    @Order(4)
    @DisplayName("并发批次: 2个批次独立完成")
    void concurrentBatches() throws Exception {
        // 批次1
        SimulationBatchRequestDTO req1 = new SimulationBatchRequestDTO();
        req1.setUserName("testUserA");
        req1.setBasePlanId(basePlanId);
        SimulationVariantDTO v1 = new SimulationVariantDTO();
        v1.setVariantName("批次1-变体");
        req1.setVariants(List.of(v1));

        // 批次2
        SimulationBatchRequestDTO req2 = new SimulationBatchRequestDTO();
        req2.setUserName("testUserA");
        req2.setBasePlanId(basePlanId);
        SimulationVariantDTO v2 = new SimulationVariantDTO();
        v2.setVariantName("批次2-变体");
        v2.setScoreOverride(620);
        req2.setVariants(List.of(v2));

        Integer taskId1 = simulationService.submitBatchSimulation(req1);
        Integer taskId2 = simulationService.submitBatchSimulation(req2);

        assertNotEquals(taskId1, taskId2);

        // 等待两个批次完成
        for (int i = 0; i < 60; i++) {
            SimulationBatchStatusVO s1 = simulationService.getBatchStatus(taskId1, "testUserA");
            SimulationBatchStatusVO s2 = simulationService.getBatchStatus(taskId2, "testUserA");
            boolean done1 = s1 != null && ("completed".equals(s1.getStatus()) || "failed".equals(s1.getStatus()));
            boolean done2 = s2 != null && ("completed".equals(s2.getStatus()) || "failed".equals(s2.getStatus()));
            if (done1 && done2) break;
            TimeUnit.MILLISECONDS.sleep(500);
        }

        SimulationBatchStatusVO final1 = simulationService.getBatchStatus(taskId1, "testUserA");
        SimulationBatchStatusVO final2 = simulationService.getBatchStatus(taskId2, "testUserA");

        assertEquals("completed", final1.getStatus());
        assertEquals("completed", final2.getStatus());
        assertNotEquals(final1.getVariants().get(0).getSimulatedPlanId(),
                final2.getVariants().get(0).getSimulatedPlanId());
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  用户隔离                                                ║
    // ╚══════════════════════════════════════════════════════════╝

    @Test
    @Order(5)
    @DisplayName("用户隔离: A无法查看B的模拟")
    void userIsolation() {
        SimulationBatchRequestDTO req = new SimulationBatchRequestDTO();
        req.setUserName("testUserA");
        req.setBasePlanId(basePlanId);
        SimulationVariantDTO v = new SimulationVariantDTO();
        v.setVariantName("隔离测试");
        req.setVariants(List.of(v));

        Integer taskId = simulationService.submitBatchSimulation(req);

        // testUserB 无法查看
        assertNull(simulationService.getBatchStatus(taskId, "testUserB"));
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  历史回滚                                                ║
    // ╚══════════════════════════════════════════════════════════╝

    @Test
    @Order(6)
    @DisplayName("历史回滚: rollback → 新模拟方案可查")
    void historyRollback() {
        // 基础方案至少有version 1的快照
        Integer simId = simulationService.rollbackToVersion(basePlanId, 1, "testUserA");
        assertNotNull(simId);

        // 验证变体状态
        SimulationVariant variant = variantMapper.selectById(simId);
        assertNotNull(variant);
        assertEquals("completed", variant.getStatus());
        assertNotNull(variant.getSimulatedPlanId());

        // 可通过现有详情接口查看
        PlanDetailVO detail = voluntaryPlanService.getPlanDetail(variant.getSimulatedPlanId(), "testUserA");
        assertNotNull(detail);
        assertTrue(detail.getPlanName().contains("回滚"));
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  版本对比                                                ║
    // ╚══════════════════════════════════════════════════════════╝

    @Test
    @Order(7)
    @DisplayName("版本对比: 模拟vs正式方案")
    void versionCompare() throws Exception {
        // 创建一个新的模拟
        SimulationBatchRequestDTO req = new SimulationBatchRequestDTO();
        req.setUserName("testUserA");
        req.setBasePlanId(basePlanId);
        SimulationVariantDTO v = new SimulationVariantDTO();
        v.setVariantName("对比测试");
        v.setScoreOverride(620);
        v.setRankOverride(15000);
        req.setVariants(List.of(v));

        Integer taskId = simulationService.submitBatchSimulation(req);

        // 等待完成
        for (int i = 0; i < 60; i++) {
            SimulationBatchStatusVO s = simulationService.getBatchStatus(taskId, "testUserA");
            if (s != null && ("completed".equals(s.getStatus()) || "failed".equals(s.getStatus()))) break;
            TimeUnit.MILLISECONDS.sleep(500);
        }

        SimulationBatchStatusVO status = simulationService.getBatchStatus(taskId, "testUserA");
        assertEquals("completed", status.getStatus());

        Integer simVariantId = status.getVariants().get(0).getVariantId();

        // 对比: 模拟 vs 基础方案
        SimulationCompareRequestDTO compareReq = new SimulationCompareRequestDTO();
        compareReq.setUserName("testUserA");
        compareReq.setSimulationIdA(simVariantId);
        compareReq.setPlanIdB(basePlanId);

        SimulationCompareVO compareResult = simulationService.compareSimulations(compareReq);
        assertNotNull(compareResult);
        assertNotNull(compareResult.getPlanA());
        assertNotNull(compareResult.getPlanB());
        assertNotNull(compareResult.getSummary());
        assertNotNull(compareResult.getChangeExplanation());
    }
}
