package com.scu.gkvr_system_backend.service;

import com.scu.gkvr_system_backend.mapper.*;
import com.scu.gkvr_system_backend.pojo.*;
import com.scu.gkvr_system_backend.service.impl.SimulationVariantComputationService;
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
 * SimulationVariantComputationService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class SimulationVariantComputationServiceTest {

    @InjectMocks
    private SimulationVariantComputationService computationService;

    @Mock private SimulationBatchTaskMapper batchTaskMapper;
    @Mock private SimulationVariantMapper variantMapper;
    @Mock private VoluntaryPlanMapper voluntaryPlanMapper;
    @Mock private PlanSchoolMapper planSchoolMapper;
    @Mock private PlanVersionLogMapper planVersionLogMapper;
    @Mock private ScLiScoreMapper scLiScoreMapper;
    @Mock private SchoolInfoMapper schoolInfoMapper;

    private VoluntaryPlan buildBasePlan() {
        VoluntaryPlan p = new VoluntaryPlan();
        p.setId(1);
        p.setPlanName("基础方案");
        p.setUserName("testUser");
        p.setScore(580);
        p.setUserRank(20000);
        p.setSubjectType("理科");
        p.setBatchName("本科一批");
        p.setRegionPref("四川,北京");
        p.setSchoolTier("985,211");
        p.setMajorPref("计算机");
        p.setVersion(1);
        p.setStatus(0);
        return p;
    }

    private SimulationBatchTask buildTask(int baseVersion) {
        SimulationBatchTask t = new SimulationBatchTask();
        t.setId(10);
        t.setUserName("testUser");
        t.setBasePlanId(1);
        t.setBasePlanVersion(baseVersion);
        t.setStatus("running");
        t.setTotalVariants(1);
        t.setCompletedVariants(0);
        t.setCreateTime(LocalDateTime.now());
        t.setUpdateTime(LocalDateTime.now());
        return t;
    }

    private SimulationVariant buildPendingVariant() {
        SimulationVariant v = new SimulationVariant();
        v.setId(1);
        v.setBatchTaskId(10);
        v.setVariantName("测试变体");
        v.setVariantIndex(0);
        v.setStatus("pending");
        v.setCreateTime(LocalDateTime.now());
        v.setUpdateTime(LocalDateTime.now());
        return v;
    }

    private ScLiScore buildScLiScore(int schoolId) {
        ScLiScore sc = new ScLiScore();
        sc.setId(1);
        sc.setSchoolId(schoolId);
        sc.setSchoolName("院校" + schoolId);
        sc.setScore2020(630);
        sc.setRank2020(4000);
        sc.setScore2021(632);
        sc.setRank2021(3800);
        sc.setScore2022(635);
        sc.setRank2022(3500);
        return sc;
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  computeVariant 测试                                     ║
    // ╚══════════════════════════════════════════════════════════╝

    @Nested
    @DisplayName("变体计算测试")
    class ComputeVariantTests {

        @Test
        @DisplayName("正常场景: 创建模拟方案+院校记录")
        void normalCase_createsSimPlanAndSchools() {
            VoluntaryPlan basePlan = buildBasePlan();
            SimulationBatchTask task = buildTask(1);
            SimulationVariant variant = buildPendingVariant();

            PlanSchool basePs = new PlanSchool();
            basePs.setId(101);
            basePs.setPlanId(1);
            basePs.setSchoolId(3);
            basePs.setSchoolName("四川大学");
            basePs.setCategory("稳");
            basePs.setSortOrder(0);
            basePs.setAdmissionProb(BigDecimal.valueOf(60));
            basePs.setMajorAdjustRisk(BigDecimal.valueOf(30));
            basePs.setPopularityScore(BigDecimal.valueOf(60));
            basePs.setPopularityTrend("stable");
            basePs.setAvgRank3yr(BigDecimal.valueOf(3800));
            basePs.setRankStdDev(BigDecimal.valueOf(200));
            basePs.setRankTrend("stable");
            basePs.setRankFluctuation("正常");
            basePs.setSelectedMajors("计算机,软件");

            when(variantMapper.updateById(any())).thenReturn(1);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(basePlan);
            when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(List.of(basePs));
            when(voluntaryPlanMapper.insert(any())).thenAnswer(inv -> {
                VoluntaryPlan p = inv.getArgument(0);
                p.setId(100);
                return 1;
            });
            when(scLiScoreMapper.selectOne(any())).thenReturn(buildScLiScore(3));
            when(planSchoolMapper.selectMajorScoresForSchool(eq(3), anyString()))
                    .thenReturn(Collections.emptyList());
            when(schoolInfoMapper.selectById(3)).thenReturn(null);
            when(planSchoolMapper.insert(any())).thenReturn(1);
            when(voluntaryPlanMapper.updateById(any())).thenReturn(1);
            when(planVersionLogMapper.insert(any())).thenReturn(1);
            when(batchTaskMapper.incrementCompletedVariants(10)).thenReturn(1);

            computationService.computeVariant(task, variant, basePlan);

            assertEquals("completed", variant.getStatus());
            assertEquals(100, variant.getSimulatedPlanId());
            assertNotNull(variant.getComputationTimeMs());
            verify(voluntaryPlanMapper).insert(argThat(p ->
                    p.getSimulationBatchTaskId() != null && p.getSimulationBatchTaskId() == 10));
        }

        @Test
        @DisplayName("过时基础方案: 标记failed")
        void staleBasePlan_marksFailed() {
            VoluntaryPlan basePlan = buildBasePlan();
            SimulationBatchTask task = buildTask(1);
            SimulationVariant variant = buildPendingVariant();

            // 基础方案版本已前进到2
            VoluntaryPlan updatedPlan = buildBasePlan();
            updatedPlan.setVersion(2);

            when(variantMapper.updateById(any())).thenReturn(1);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(updatedPlan);
            when(batchTaskMapper.incrementCompletedVariants(10)).thenReturn(1);

            computationService.computeVariant(task, variant, basePlan);

            assertEquals("failed", variant.getStatus());
            assertTrue(variant.getErrorMessage().contains("过时"));
            verify(voluntaryPlanMapper, never()).insert(any());
        }

        @Test
        @DisplayName("scoreOverride生效")
        void scoreOverride_usesEffectiveScore() {
            VoluntaryPlan basePlan = buildBasePlan();
            SimulationBatchTask task = buildTask(1);
            SimulationVariant variant = buildPendingVariant();
            variant.setScoreOverride(620);

            when(variantMapper.updateById(any())).thenReturn(1);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(basePlan);
            when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(Collections.emptyList());
            when(voluntaryPlanMapper.insert(any())).thenAnswer(inv -> {
                VoluntaryPlan p = inv.getArgument(0);
                p.setId(100);
                // 验证使用了覆盖值
                assertEquals(620, p.getScore());
                return 1;
            });
            when(voluntaryPlanMapper.updateById(any())).thenReturn(1);
            when(planVersionLogMapper.insert(any())).thenReturn(1);
            when(batchTaskMapper.incrementCompletedVariants(10)).thenReturn(1);

            computationService.computeVariant(task, variant, basePlan);

            assertEquals("completed", variant.getStatus());
        }

        @Test
        @DisplayName("rankOverride生效")
        void rankOverride_usesEffectiveRank() {
            VoluntaryPlan basePlan = buildBasePlan();
            SimulationBatchTask task = buildTask(1);
            SimulationVariant variant = buildPendingVariant();
            variant.setRankOverride(15000);

            when(variantMapper.updateById(any())).thenReturn(1);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(basePlan);
            when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(Collections.emptyList());
            when(voluntaryPlanMapper.insert(any())).thenAnswer(inv -> {
                VoluntaryPlan p = inv.getArgument(0);
                p.setId(100);
                assertEquals(15000, p.getUserRank());
                return 1;
            });
            when(voluntaryPlanMapper.updateById(any())).thenReturn(1);
            when(planVersionLogMapper.insert(any())).thenReturn(1);
            when(batchTaskMapper.incrementCompletedVariants(10)).thenReturn(1);

            computationService.computeVariant(task, variant, basePlan);

            assertEquals("completed", variant.getStatus());
        }

        @Test
        @DisplayName("batchOverride生效")
        void batchOverride_usesEffectiveBatch() {
            VoluntaryPlan basePlan = buildBasePlan();
            SimulationBatchTask task = buildTask(1);
            SimulationVariant variant = buildPendingVariant();
            variant.setBatchNameOverride("本科二批");

            when(variantMapper.updateById(any())).thenReturn(1);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(basePlan);
            when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(Collections.emptyList());
            when(voluntaryPlanMapper.insert(any())).thenAnswer(inv -> {
                VoluntaryPlan p = inv.getArgument(0);
                p.setId(100);
                assertEquals("本科二批", p.getBatchName());
                return 1;
            });
            when(voluntaryPlanMapper.updateById(any())).thenReturn(1);
            when(planVersionLogMapper.insert(any())).thenReturn(1);
            when(batchTaskMapper.incrementCompletedVariants(10)).thenReturn(1);

            computationService.computeVariant(task, variant, basePlan);

            assertEquals("completed", variant.getStatus());
        }

        @Test
        @DisplayName("异常处理: 标记failed")
        void exceptionThrown_marksVariantFailed() {
            VoluntaryPlan basePlan = buildBasePlan();
            SimulationBatchTask task = buildTask(1);
            SimulationVariant variant = buildPendingVariant();

            when(variantMapper.updateById(any())).thenReturn(1);
            when(voluntaryPlanMapper.selectById(1)).thenThrow(new RuntimeException("DB Error"));
            when(batchTaskMapper.incrementCompletedVariants(10)).thenReturn(1);

            computationService.computeVariant(task, variant, basePlan);

            assertEquals("failed", variant.getStatus());
            assertTrue(variant.getErrorMessage().contains("DB Error"));
        }

        @Test
        @DisplayName("simulationBatchTaskId正确设置")
        void setsSimulationBatchTaskIdOnPlan() {
            VoluntaryPlan basePlan = buildBasePlan();
            SimulationBatchTask task = buildTask(1);
            SimulationVariant variant = buildPendingVariant();

            when(variantMapper.updateById(any())).thenReturn(1);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(basePlan);
            when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(Collections.emptyList());
            when(voluntaryPlanMapper.insert(any())).thenAnswer(inv -> {
                VoluntaryPlan p = inv.getArgument(0);
                p.setId(100);
                return 1;
            });
            when(voluntaryPlanMapper.updateById(any())).thenReturn(1);
            when(planVersionLogMapper.insert(any())).thenReturn(1);
            when(batchTaskMapper.incrementCompletedVariants(10)).thenReturn(1);

            computationService.computeVariant(task, variant, basePlan);

            verify(voluntaryPlanMapper).insert(argThat(p -> p.getSimulationBatchTaskId() == 10));
        }
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  recalculateSchoolMetrics 测试                            ║
    // ╚══════════════════════════════════════════════════════════╝

    @Nested
    @DisplayName("指标重算测试")
    class RecalculateMetricsTests {

        @Test
        @DisplayName("有ScLiScore: 正确计算prob和rank")
        void withScLiScore_correctProbAndRank() {
            PlanSchool ps = new PlanSchool();
            ps.setSchoolId(3);
            ps.setSchoolName("四川大学");
            ps.setCategory("稳");

            when(scLiScoreMapper.selectOne(any())).thenReturn(buildScLiScore(3));
            when(planSchoolMapper.selectMajorScoresForSchool(eq(3), anyString()))
                    .thenReturn(Collections.emptyList());
            when(schoolInfoMapper.selectById(3)).thenReturn(null);

            computationService.recalculateSchoolMetrics(ps, 580, 20000, "本科一批");

            assertNotNull(ps.getAdmissionProb());
            assertNotNull(ps.getAvgRank3yr());
            assertNotNull(ps.getRankStdDev());
            assertTrue(ps.getAvgRank3yr().doubleValue() > 0);
        }

        @Test
        @DisplayName("无ScLiScore: 默认值")
        void withoutScLiScore_defaultValues() {
            PlanSchool ps = new PlanSchool();
            ps.setSchoolId(99);
            ps.setSchoolName("未知院校");
            ps.setCategory("稳");

            when(scLiScoreMapper.selectOne(any())).thenReturn(null);
            when(planSchoolMapper.selectMajorScoresForSchool(eq(99), anyString()))
                    .thenReturn(Collections.emptyList());
            when(schoolInfoMapper.selectById(99)).thenReturn(null);

            computationService.recalculateSchoolMetrics(ps, 580, 20000, "本科一批");

            assertEquals(BigDecimal.valueOf(50), ps.getAdmissionProb());
            assertEquals(BigDecimal.ZERO, ps.getAvgRank3yr());
            assertEquals(BigDecimal.ZERO, ps.getRankStdDev());
        }

        @Test
        @DisplayName("跨批次降级: null batch查询")
        void crossBatchFallback_toNullBatch() {
            PlanSchool ps = new PlanSchool();
            ps.setSchoolId(3);
            ps.setSchoolName("四川大学");
            ps.setCategory("稳");

            when(scLiScoreMapper.selectOne(any())).thenReturn(buildScLiScore(3));

            // 第一次查询(有效批次)返回空, 第二次(null批次)返回数据
            Map<String, Object> majorRow = new HashMap<>();
            majorRow.put("majorName", "计算机");
            majorRow.put("maxScore", 650);
            majorRow.put("minScore", 630);
            majorRow.put("avgScore", 640);

            when(planSchoolMapper.selectMajorScoresForSchool(3, "本科二批"))
                    .thenReturn(Collections.emptyList());
            when(planSchoolMapper.selectMajorScoresForSchool(3, null))
                    .thenReturn(List.of(majorRow));
            when(schoolInfoMapper.selectById(3)).thenReturn(null);

            computationService.recalculateSchoolMetrics(ps, 580, 20000, "本科二批");

            assertNotNull(ps.getMajorAdjustRisk());
            // 有数据时风险不应为默认50
            assertNotEquals(BigDecimal.valueOf(50), ps.getMajorAdjustRisk());
        }

        @Test
        @DisplayName("使用有效rank非基础rank")
        void usesEffectiveRank_notBaseRank() {
            PlanSchool ps = new PlanSchool();
            ps.setSchoolId(3);
            ps.setSchoolName("四川大学");
            ps.setCategory("冲");

            when(scLiScoreMapper.selectOne(any())).thenReturn(buildScLiScore(3));
            when(planSchoolMapper.selectMajorScoresForSchool(eq(3), anyString()))
                    .thenReturn(Collections.emptyList());
            when(schoolInfoMapper.selectById(3)).thenReturn(null);

            // 使用覆盖后的rank=10000 (优于基础方案20000)
            computationService.recalculateSchoolMetrics(ps, 620, 10000, "本科一批");

            // 位次10000远低于avgRank~3767, 应为冲且概率较低
            assertNotNull(ps.getAdmissionProb());
            assertTrue(ps.getAdmissionProb().doubleValue() <= 50);
            // 波动解释应提及10000
            assertTrue(ps.getRankFluctuation().contains("10000"));
        }
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  批次状态方法测试                                        ║
    // ╚══════════════════════════════════════════════════════════╝

    @Nested
    @DisplayName("批次状态方法测试")
    class BatchStatusMethodsTests {

        @Test
        @DisplayName("markBatchRunning更新状态")
        void markBatchRunning_updatesStatus() {
            when(batchTaskMapper.updateStatus(10, "running")).thenReturn(1);
            computationService.markBatchRunning(10);
            verify(batchTaskMapper).updateStatus(10, "running");
        }

        @Test
        @DisplayName("finalizeBatch设置completed")
        void finalizeBatch_setsCompleted() {
            SimulationBatchTask task = buildTask(1);
            task.setStatus("running");
            when(batchTaskMapper.selectById(10)).thenReturn(task);
            when(batchTaskMapper.updateStatus(10, "completed")).thenReturn(1);

            computationService.finalizeBatch(10);

            verify(batchTaskMapper).updateStatus(10, "completed");
        }

        @Test
        @DisplayName("finalizeBatch不覆盖stale状态")
        void finalizeBatch_doesNotOverrideStale() {
            SimulationBatchTask task = buildTask(1);
            task.setStatus("stale");
            when(batchTaskMapper.selectById(10)).thenReturn(task);

            computationService.finalizeBatch(10);

            verify(batchTaskMapper, never()).updateStatus(eq(10), eq("completed"));
        }

        @Test
        @DisplayName("markBatchFailed设置错误信息")
        void markBatchFailed_setsErrorMessage() {
            SimulationBatchTask task = buildTask(1);
            when(batchTaskMapper.selectById(10)).thenReturn(task);
            when(batchTaskMapper.updateById(any())).thenReturn(1);

            computationService.markBatchFailed(10, "计算失败");

            verify(batchTaskMapper).updateById(argThat(t ->
                    "failed".equals(t.getStatus()) && "计算失败".equals(t.getErrorMessage())));
        }
    }
}
