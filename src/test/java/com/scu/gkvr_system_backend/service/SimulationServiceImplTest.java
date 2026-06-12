package com.scu.gkvr_system_backend.service;

import com.scu.gkvr_system_backend.dto.SimulationBatchRequestDTO;
import com.scu.gkvr_system_backend.dto.SimulationCompareRequestDTO;
import com.scu.gkvr_system_backend.dto.SimulationVariantDTO;
import com.scu.gkvr_system_backend.mapper.*;
import com.scu.gkvr_system_backend.pojo.*;
import com.scu.gkvr_system_backend.service.impl.SimulationAsyncExecutor;
import com.scu.gkvr_system_backend.service.impl.SimulationServiceImpl;
import com.scu.gkvr_system_backend.vo.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SimulationServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
class SimulationServiceImplTest {

    @InjectMocks
    private SimulationServiceImpl simulationService;

    @Mock private SimulationBatchTaskMapper batchTaskMapper;
    @Mock private SimulationVariantMapper variantMapper;
    @Mock private VoluntaryPlanMapper voluntaryPlanMapper;
    @Mock private PlanSchoolMapper planSchoolMapper;
    @Mock private PlanVersionLogMapper planVersionLogMapper;
    @Mock private VoluntaryPlanService voluntaryPlanService;
    @Mock private SimulationAsyncExecutor simulationAsyncExecutor;

    // ══════════════════════════════════════════
    // 辅助构建方法
    // ══════════════════════════════════════════

    private VoluntaryPlan buildPlan(int id, String userName, int version) {
        VoluntaryPlan p = new VoluntaryPlan();
        p.setId(id);
        p.setPlanName("测试方案");
        p.setUserName(userName);
        p.setScore(580);
        p.setUserRank(20000);
        p.setSubjectType("理科");
        p.setBatchName("本科一批");
        p.setVersion(version);
        p.setStatus(0);
        p.setTotalRiskScore(BigDecimal.valueOf(45));
        return p;
    }

    private PlanSchool buildPlanSchool(int planId, int schoolId, String category,
                                        BigDecimal prob, BigDecimal risk) {
        PlanSchool ps = new PlanSchool();
        ps.setId(planId * 100 + schoolId);
        ps.setPlanId(planId);
        ps.setSchoolId(schoolId);
        ps.setSchoolName("院校" + schoolId);
        ps.setCategory(category);
        ps.setSortOrder(0);
        ps.setAdmissionProb(prob);
        ps.setMajorAdjustRisk(risk);
        ps.setPopularityScore(BigDecimal.valueOf(60));
        ps.setPopularityTrend("stable");
        ps.setAvgRank3yr(BigDecimal.valueOf(20000));
        ps.setRankStdDev(BigDecimal.valueOf(500));
        ps.setRankTrend("stable");
        ps.setRankFluctuation("波动正常");
        return ps;
    }

    private SimulationBatchTask buildBatchTask(int id, String userName, int basePlanId, int version) {
        SimulationBatchTask t = new SimulationBatchTask();
        t.setId(id);
        t.setUserName(userName);
        t.setBasePlanId(basePlanId);
        t.setBasePlanVersion(version);
        t.setStatus("pending");
        t.setTotalVariants(1);
        t.setCompletedVariants(0);
        t.setProgressPercent(BigDecimal.ZERO);
        t.setCreateTime(LocalDateTime.now());
        t.setUpdateTime(LocalDateTime.now());
        return t;
    }

    private SimulationVariant buildVariant(int id, int batchTaskId, String status) {
        SimulationVariant v = new SimulationVariant();
        v.setId(id);
        v.setBatchTaskId(batchTaskId);
        v.setVariantName("变体1");
        v.setVariantIndex(0);
        v.setStatus(status);
        v.setSimulatedPlanId(status.equals("completed") ? 100 : null);
        v.setCreateTime(LocalDateTime.now());
        v.setUpdateTime(LocalDateTime.now());
        return v;
    }

    private SimulationBatchRequestDTO buildBatchRequest() {
        SimulationBatchRequestDTO req = new SimulationBatchRequestDTO();
        req.setUserName("testUser");
        req.setBasePlanId(1);
        SimulationVariantDTO variant = new SimulationVariantDTO();
        variant.setVariantName("分数+20");
        variant.setScoreOverride(600);
        req.setVariants(List.of(variant));
        return req;
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  提交批量模拟                                           ║
    // ╚══════════════════════════════════════════════════════════╝

    @Nested
    @DisplayName("提交批量模拟测试")
    class SubmitBatchTests {

        @Test
        @DisplayName("正常提交返回batchTaskId")
        void submitBatch_validRequest_returnsBatchTaskId() {
            VoluntaryPlan basePlan = buildPlan(1, "testUser", 1);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(basePlan);
            when(batchTaskMapper.selectRecentByUserName("testUser")).thenReturn(Collections.emptyList());
            when(batchTaskMapper.insert(any())).thenAnswer(inv -> {
                SimulationBatchTask t = inv.getArgument(0);
                t.setId(10);
                return 1;
            });
            when(variantMapper.insert(any())).thenReturn(1);

            Integer taskId = simulationService.submitBatchSimulation(buildBatchRequest());

            assertEquals(10, taskId);
            verify(batchTaskMapper).insert(any());
            verify(variantMapper).insert(any());
            verify(simulationAsyncExecutor).executeBatch(any(), any(), eq(basePlan));
        }

        @Test
        @DisplayName("方案不存在抛异常")
        void submitBatch_nonExistentPlan_throwsException() {
            when(voluntaryPlanMapper.selectById(1)).thenReturn(null);
            assertThrows(IllegalArgumentException.class,
                    () -> simulationService.submitBatchSimulation(buildBatchRequest()));
        }

        @Test
        @DisplayName("错误用户抛异常")
        void submitBatch_wrongUser_throwsException() {
            VoluntaryPlan plan = buildPlan(1, "otherUser", 1);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);
            assertThrows(IllegalArgumentException.class,
                    () -> simulationService.submitBatchSimulation(buildBatchRequest()));
        }

        @Test
        @DisplayName("第4个活跃批次被拒绝")
        void submitBatch_rateLimit_4thBatchRejected() {
            VoluntaryPlan plan = buildPlan(1, "testUser", 1);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);

            List<SimulationBatchTask> activeTasks = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                SimulationBatchTask t = buildBatchTask(i + 1, "testUser", 1, 1);
                t.setStatus(i < 2 ? "running" : "pending");
                activeTasks.add(t);
            }
            when(batchTaskMapper.selectRecentByUserName("testUser")).thenReturn(activeTasks);

            assertThrows(IllegalStateException.class,
                    () -> simulationService.submitBatchSimulation(buildBatchRequest()));
        }

        @Test
        @DisplayName("提交时捕获basePlanVersion")
        void submitBatch_capturesBasePlanVersionForStaleness() {
            VoluntaryPlan plan = buildPlan(1, "testUser", 5);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);
            when(batchTaskMapper.selectRecentByUserName("testUser")).thenReturn(Collections.emptyList());
            when(batchTaskMapper.insert(any())).thenAnswer(inv -> {
                SimulationBatchTask t = inv.getArgument(0);
                t.setId(10);
                // 验证版本号被正确捕获
                assertEquals(5, t.getBasePlanVersion());
                return 1;
            });
            when(variantMapper.insert(any())).thenReturn(1);

            simulationService.submitBatchSimulation(buildBatchRequest());

            verify(batchTaskMapper).insert(argThat(task ->
                    task.getBasePlanVersion().equals(5)));
        }
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  查询批次状态                                           ║
    // ╚══════════════════════════════════════════════════════════╝

    @Nested
    @DisplayName("查询批次状态测试")
    class GetBatchStatusTests {

        @Test
        @DisplayName("pending任务返回0进度")
        void getStatus_pendingTask_returnsZeroProgress() {
            SimulationBatchTask task = buildBatchTask(1, "testUser", 1, 1);
            when(batchTaskMapper.selectById(1)).thenReturn(task);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(buildPlan(1, "testUser", 1));
            when(variantMapper.selectByBatchTaskId(1)).thenReturn(Collections.emptyList());

            SimulationBatchStatusVO vo = simulationService.getBatchStatus(1, "testUser");

            assertNotNull(vo);
            assertEquals("pending", vo.getStatus());
            assertEquals(BigDecimal.ZERO, vo.getProgressPercent());
        }

        @Test
        @DisplayName("completed任务返回全部变体")
        void getStatus_completedTask_returnsAllVariants() {
            SimulationBatchTask task = buildBatchTask(1, "testUser", 1, 1);
            task.setStatus("completed");
            task.setCompletedVariants(2);
            task.setProgressPercent(BigDecimal.valueOf(100));
            when(batchTaskMapper.selectById(1)).thenReturn(task);

            List<SimulationVariant> variants = List.of(
                    buildVariant(1, 1, "completed"),
                    buildVariant(2, 1, "completed"));
            when(variantMapper.selectByBatchTaskId(1)).thenReturn(variants);

            SimulationBatchStatusVO vo = simulationService.getBatchStatus(1, "testUser");

            assertNotNull(vo);
            assertEquals("completed", vo.getStatus());
            assertEquals(2, vo.getVariants().size());
        }

        @Test
        @DisplayName("错误用户返回null")
        void getStatus_wrongUser_returnsNull() {
            SimulationBatchTask task = buildBatchTask(1, "testUserA", 1, 1);
            when(batchTaskMapper.selectById(1)).thenReturn(task);
            assertNull(simulationService.getBatchStatus(1, "testUserB"));
        }

        @Test
        @DisplayName("过时检测: 基础方案版本前进标记stale")
        void statusCheck_staleDetection_marksStale() {
            SimulationBatchTask task = buildBatchTask(1, "testUser", 1, 1);
            task.setStatus("running");
            when(batchTaskMapper.selectById(1)).thenReturn(task);

            // 基础方案版本已前进到2
            VoluntaryPlan basePlan = buildPlan(1, "testUser", 2);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(basePlan);
            when(batchTaskMapper.updateStatus(1, "stale")).thenReturn(1);
            when(variantMapper.selectByBatchTaskId(1)).thenReturn(Collections.emptyList());

            SimulationBatchStatusVO vo = simulationService.getBatchStatus(1, "testUser");

            assertEquals("stale", vo.getStatus());
            verify(batchTaskMapper).updateStatus(1, "stale");
        }
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  概率解释                                               ║
    // ╚══════════════════════════════════════════════════════════╝

    @Nested
    @DisplayName("概率解释测试")
    class GetExplanationTests {

        @Test
        @DisplayName("已完成变体返回完整分解")
        void explanation_completedVariant_returnsFullBreakdown() {
            SimulationVariant variant = buildVariant(1, 10, "completed");
            SimulationBatchTask task = buildBatchTask(10, "testUser", 1, 1);
            VoluntaryPlan simPlan = buildPlan(100, "testUser", 1);
            VoluntaryPlan basePlan = buildPlan(1, "testUser", 1);

            when(variantMapper.selectById(1)).thenReturn(variant);
            when(batchTaskMapper.selectById(10)).thenReturn(task);
            when(voluntaryPlanMapper.selectById(100)).thenReturn(simPlan);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(basePlan);

            PlanSchool simPs = buildPlanSchool(100, 3, "稳", BigDecimal.valueOf(65), BigDecimal.valueOf(30));
            PlanSchool basePs = buildPlanSchool(1, 3, "稳", BigDecimal.valueOf(60), BigDecimal.valueOf(35));

            when(planSchoolMapper.selectByPlanIdOrdered(100)).thenReturn(List.of(simPs));
            when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(List.of(basePs));
            when(planSchoolMapper.selectMajorScoresForSchool(anyInt(), any())).thenReturn(Collections.emptyList());

            SimulationExplanationVO vo = simulationService.getExplanation(1, "testUser");

            assertNotNull(vo);
            assertEquals(1, vo.getSchoolBreakdowns().size());
            assertEquals(100, vo.getSimulatedPlanId());

            SchoolProbabilityBreakdownVO bd = vo.getSchoolBreakdowns().get(0);
            assertEquals(3, bd.getSchoolId());
            // probDelta = 65 - 60 = 5
            assertEquals(BigDecimal.valueOf(5).setScale(2), bd.getProbDelta());
            // riskDelta = 30 - 35 = -5
            assertEquals(BigDecimal.valueOf(-5).setScale(2), bd.getRiskDelta());
        }

        @Test
        @DisplayName("pending变体返回null")
        void explanation_pendingVariant_returnsNull() {
            SimulationVariant variant = buildVariant(1, 10, "pending");
            when(variantMapper.selectById(1)).thenReturn(variant);
            assertNull(simulationService.getExplanation(1, "testUser"));
        }

        @Test
        @DisplayName("错误用户返回null")
        void explanation_wrongUser_returnsNull() {
            SimulationVariant variant = buildVariant(1, 10, "completed");
            SimulationBatchTask task = buildBatchTask(10, "testUserA", 1, 1);
            when(variantMapper.selectById(1)).thenReturn(variant);
            when(batchTaskMapper.selectById(10)).thenReturn(task);
            assertNull(simulationService.getExplanation(1, "testUserB"));
        }

        @Test
        @DisplayName("使用schoolId作key非schoolName")
        void explanation_usesSchoolIdAsKey_notSchoolName() {
            SimulationVariant variant = buildVariant(1, 10, "completed");
            SimulationBatchTask task = buildBatchTask(10, "testUser", 1, 1);
            VoluntaryPlan simPlan = buildPlan(100, "testUser", 1);
            VoluntaryPlan basePlan = buildPlan(1, "testUser", 1);

            when(variantMapper.selectById(1)).thenReturn(variant);
            when(batchTaskMapper.selectById(10)).thenReturn(task);
            when(voluntaryPlanMapper.selectById(100)).thenReturn(simPlan);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(basePlan);

            // 两个同名但不同ID的院校
            PlanSchool sim1 = buildPlanSchool(100, 3, "稳", BigDecimal.valueOf(65), BigDecimal.valueOf(30));
            sim1.setSchoolName("同名院校");
            PlanSchool sim2 = buildPlanSchool(100, 4, "冲", BigDecimal.valueOf(40), BigDecimal.valueOf(45));
            sim2.setSchoolName("同名院校");

            PlanSchool base1 = buildPlanSchool(1, 3, "稳", BigDecimal.valueOf(60), BigDecimal.valueOf(35));
            base1.setSchoolName("同名院校");

            when(planSchoolMapper.selectByPlanIdOrdered(100)).thenReturn(List.of(sim1, sim2));
            when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(List.of(base1));
            when(planSchoolMapper.selectMajorScoresForSchool(anyInt(), any())).thenReturn(Collections.emptyList());

            SimulationExplanationVO vo = simulationService.getExplanation(1, "testUser");

            assertNotNull(vo);
            assertEquals(2, vo.getSchoolBreakdowns().size());
            // 第二所院校(schoolId=4)在基础方案中不存在, 应显示"新增院校"
            SchoolProbabilityBreakdownVO bd2 = vo.getSchoolBreakdowns().get(1);
            assertEquals(4, bd2.getSchoolId());
            assertTrue(bd2.getChangeReason().contains("新增"));
        }
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  提升为正式方案                                         ║
    // ╚══════════════════════════════════════════════════════════╝

    @Nested
    @DisplayName("提升为正式方案测试")
    class PromoteToPlanTests {

        @Test
        @DisplayName("提升清除simulationBatchTaskId")
        void promote_clearsSimFlag() {
            SimulationVariant variant = buildVariant(1, 10, "completed");
            SimulationBatchTask task = buildBatchTask(10, "testUser", 1, 1);
            VoluntaryPlan simPlan = buildPlan(100, "testUser", 1);
            simPlan.setSimulationBatchTaskId(10);

            when(variantMapper.selectById(1)).thenReturn(variant);
            when(batchTaskMapper.selectById(10)).thenReturn(task);
            when(voluntaryPlanMapper.selectById(100)).thenReturn(simPlan);
            when(voluntaryPlanMapper.promoteSimulation(100, "我的方案", 1)).thenReturn(1);

            Integer planId = simulationService.promoteToPlan(1, "testUser", "我的方案");

            assertEquals(100, planId);
            verify(voluntaryPlanMapper).promoteSimulation(100, "我的方案", 1);
        }

        @Test
        @DisplayName("pending变体返回null")
        void promote_pendingReturnsNull() {
            SimulationVariant variant = buildVariant(1, 10, "pending");
            when(variantMapper.selectById(1)).thenReturn(variant);
            assertNull(simulationService.promoteToPlan(1, "testUser", "方案"));
        }

        @Test
        @DisplayName("错误用户返回null")
        void promote_wrongUser_returnsNull() {
            SimulationVariant variant = buildVariant(1, 10, "completed");
            SimulationBatchTask task = buildBatchTask(10, "testUserA", 1, 1);
            when(variantMapper.selectById(1)).thenReturn(variant);
            when(batchTaskMapper.selectById(10)).thenReturn(task);
            assertNull(simulationService.promoteToPlan(1, "testUserB", "方案"));
        }
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  过时检测                                               ║
    // ╚══════════════════════════════════════════════════════════╝

    @Nested
    @DisplayName("过时检测测试")
    class StalenessDetectionTests {

        @Test
        @DisplayName("基础方案版本前进 → 批次标记stale")
        void basePlanVersionAdvanced_marksBatchStale() {
            SimulationBatchTask task = buildBatchTask(1, "testUser", 1, 1);
            task.setStatus("pending");
            when(batchTaskMapper.selectById(1)).thenReturn(task);

            VoluntaryPlan basePlan = buildPlan(1, "testUser", 3);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(basePlan);
            when(batchTaskMapper.updateStatus(1, "stale")).thenReturn(1);
            when(variantMapper.selectByBatchTaskId(1)).thenReturn(Collections.emptyList());

            SimulationBatchStatusVO vo = simulationService.getBatchStatus(1, "testUser");
            assertEquals("stale", vo.getStatus());
        }

        @Test
        @DisplayName("completed批次 → 过时检测不覆盖结果")
        void completedBatch_stalenessCheckDoesNotOverride() {
            SimulationBatchTask task = buildBatchTask(1, "testUser", 1, 1);
            task.setStatus("completed");
            when(batchTaskMapper.selectById(1)).thenReturn(task);
            when(variantMapper.selectByBatchTaskId(1)).thenReturn(Collections.emptyList());

            SimulationBatchStatusVO vo = simulationService.getBatchStatus(1, "testUser");
            assertEquals("completed", vo.getStatus());
            // 不应对completed的批次调用过时检测
            verify(batchTaskMapper, never()).updateStatus(eq(1), eq("stale"));
        }
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  历史版本回滚                                           ║
    // ╚══════════════════════════════════════════════════════════╝

    @Nested
    @DisplayName("历史版本回滚测试")
    class RollbackTests {

        @Test
        @DisplayName("有效版本创建模拟批次")
        void rollbackToVersion_validVersion_createsBatch() {
            VoluntaryPlan plan = buildPlan(1, "testUser", 3);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);

            PlanSchool ps = buildPlanSchool(1, 3, "稳", BigDecimal.valueOf(60), BigDecimal.valueOf(30));
            PlanVersionLog versionLog = new PlanVersionLog();
            versionLog.setId(1);
            versionLog.setPlanId(1);
            versionLog.setVersion(2);
            versionLog.setSnapshotData("[{\"id\":101,\"planId\":1,\"schoolId\":3,\"schoolName\":\"四川大学\","
                    + "\"category\":\"稳\",\"sortOrder\":0,\"admissionProb\":60.00,\"majorAdjustRisk\":30.00,"
                    + "\"popularityScore\":60.00,\"popularityTrend\":\"stable\",\"avgRank3yr\":20000.00,"
                    + "\"rankStdDev\":500.00,\"rankTrend\":\"stable\",\"rankFluctuation\":\"正常\"}]");
            when(planVersionLogMapper.selectList(any())).thenReturn(List.of(versionLog));
            when(batchTaskMapper.insert(any())).thenAnswer(inv -> {
                SimulationBatchTask t = inv.getArgument(0);
                t.setId(20);
                return 1;
            });
            when(variantMapper.insert(any())).thenAnswer(inv -> {
                SimulationVariant v = inv.getArgument(0);
                v.setId(30);
                return 1;
            });
            when(voluntaryPlanMapper.insert(any())).thenAnswer(inv -> {
                VoluntaryPlan p = inv.getArgument(0);
                p.setId(200);
                return 1;
            });
            when(planSchoolMapper.insert(any())).thenReturn(1);
            when(voluntaryPlanMapper.updateById(any())).thenReturn(1);
            when(variantMapper.updateById(any())).thenReturn(1);
            when(batchTaskMapper.updateById(any())).thenReturn(1);

            Integer simId = simulationService.rollbackToVersion(1, 2, "testUser");

            assertNotNull(simId);
            verify(batchTaskMapper).insert(any());
            verify(voluntaryPlanMapper).insert(any());
        }

        @Test
        @DisplayName("错误用户返回null")
        void rollbackToVersion_wrongUser_returnsNull() {
            VoluntaryPlan plan = buildPlan(1, "testUserA", 3);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);
            assertNull(simulationService.rollbackToVersion(1, 2, "testUserB"));
        }

        @Test
        @DisplayName("无效版本返回null")
        void rollbackToVersion_invalidVersion_returnsNull() {
            VoluntaryPlan plan = buildPlan(1, "testUser", 3);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);
            when(planVersionLogMapper.selectList(any())).thenReturn(Collections.emptyList());
            assertNull(simulationService.rollbackToVersion(1, 99, "testUser"));
        }
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  版本对比                                               ║
    // ╚══════════════════════════════════════════════════════════╝

    @Nested
    @DisplayName("版本对比测试")
    class CompareTests {

        @Test
        @DisplayName("两个正式方案对比成功")
        void compare_twoPlans_success() {
            PlanComparisonVO comparison = new PlanComparisonVO();
            comparison.setPlanA(new PlanDetailVO());
            comparison.getPlanA().setTotalRiskScore(BigDecimal.valueOf(40));
            comparison.getPlanA().setRiskSummary(new PlanRiskSummary());
            comparison.getPlanA().getRiskSummary().setAvgAdmissionProb(BigDecimal.valueOf(60));
            comparison.getPlanA().getRiskSummary().setAvgMajorAdjustRisk(BigDecimal.valueOf(30));

            comparison.setPlanB(new PlanDetailVO());
            comparison.getPlanB().setTotalRiskScore(BigDecimal.valueOf(50));
            comparison.getPlanB().setRiskSummary(new PlanRiskSummary());
            comparison.getPlanB().getRiskSummary().setAvgAdmissionProb(BigDecimal.valueOf(55));
            comparison.getPlanB().getRiskSummary().setAvgMajorAdjustRisk(BigDecimal.valueOf(35));

            comparison.setDifferences(Collections.emptyList());
            comparison.setSummary("对比摘要");

            VoluntaryPlan planA = buildPlan(1, "testUser", 1);
            VoluntaryPlan planB = buildPlan(2, "testUser", 1);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(planA);
            when(voluntaryPlanMapper.selectById(2)).thenReturn(planB);
            when(voluntaryPlanService.comparePlans(1, 2, "testUser")).thenReturn(comparison);

            SimulationCompareRequestDTO req = new SimulationCompareRequestDTO();
            req.setUserName("testUser");
            req.setPlanIdA(1);
            req.setPlanIdB(2);

            SimulationCompareVO vo = simulationService.compareSimulations(req);

            assertNotNull(vo);
            assertEquals("对比摘要", vo.getSummary());
            assertNotNull(vo.getChangeExplanation());
        }

        @Test
        @DisplayName("方案不存在返回null")
        void compare_nonExistent_returnsNull() {
            when(voluntaryPlanMapper.selectById(999)).thenReturn(null);

            SimulationCompareRequestDTO req = new SimulationCompareRequestDTO();
            req.setUserName("testUser");
            req.setPlanIdA(999);
            req.setPlanIdB(1);

            assertNull(simulationService.compareSimulations(req));
        }
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  用户隔离                                               ║
    // ╚══════════════════════════════════════════════════════════╝

    @Nested
    @DisplayName("用户隔离测试")
    class UserIsolationTests {

        @Test
        @DisplayName("用户A无法查看用户B的批次状态")
        void getBatchStatus_userA_cannotSeeUserB() {
            SimulationBatchTask task = buildBatchTask(1, "userB", 1, 1);
            when(batchTaskMapper.selectById(1)).thenReturn(task);
            assertNull(simulationService.getBatchStatus(1, "userA"));
        }

        @Test
        @DisplayName("用户A无法获取用户B的概率解释")
        void getExplanation_userA_cannotSeeUserB() {
            SimulationVariant variant = buildVariant(1, 10, "completed");
            SimulationBatchTask task = buildBatchTask(10, "userB", 1, 1);
            when(variantMapper.selectById(1)).thenReturn(variant);
            when(batchTaskMapper.selectById(10)).thenReturn(task);
            assertNull(simulationService.getExplanation(1, "userA"));
        }

        @Test
        @DisplayName("用户A无法获取用户B的快照")
        void getSnapshot_userA_cannotSeeUserB() {
            SimulationVariant variant = buildVariant(1, 10, "completed");
            SimulationBatchTask task = buildBatchTask(10, "userB", 1, 1);
            when(variantMapper.selectById(1)).thenReturn(variant);
            when(batchTaskMapper.selectById(10)).thenReturn(task);
            assertNull(simulationService.getSnapshot(1, "userA"));
        }

        @Test
        @DisplayName("用户A无法提升用户B的模拟")
        void promote_userA_cannotPromoteUserB() {
            SimulationVariant variant = buildVariant(1, 10, "completed");
            SimulationBatchTask task = buildBatchTask(10, "userB", 1, 1);
            when(variantMapper.selectById(1)).thenReturn(variant);
            when(batchTaskMapper.selectById(10)).thenReturn(task);
            assertNull(simulationService.promoteToPlan(1, "userA", "方案"));
        }
    }
}
