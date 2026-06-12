package com.scu.gkvr_system_backend.service;

import com.scu.gkvr_system_backend.mapper.*;
import com.scu.gkvr_system_backend.pojo.*;
import com.scu.gkvr_system_backend.service.impl.SimulationComputationServiceImpl;
import com.scu.gkvr_system_backend.utils.PlanCalculationUtils;
import com.scu.gkvr_system_backend.vo.ProbabilityExplanationVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("模拟任务计算服务测试")
class SimulationComputationServiceImplTest {

    @InjectMocks
    private SimulationComputationServiceImpl computationService;

    @Mock
    private SimulationBatchMapper simulationBatchMapper;
    @Mock
    private SimulationTaskMapper simulationTaskMapper;
    @Mock
    private SimulationTaskSchoolMapper simulationTaskSchoolMapper;
    @Mock
    private VoluntaryPlanMapper voluntaryPlanMapper;
    @Mock
    private PlanSchoolMapper planSchoolMapper;
    @Mock
    private ScLiScoreMapper scLiScoreMapper;
    @Mock
    private SchoolInfoMapper schoolInfoMapper;

    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("计算变体测试")
    class ComputeVariantTests {

        @Test
        @DisplayName("正常计算完成")
        void computeVariant_normalCase_completesSuccessfully() {
            SimulationTask task = buildTask(1, 10, "PENDING");
            SimulationBatch batch = buildBatch(10, "userA");
            VoluntaryPlan plan = buildPlan(100);
            PlanSchool ps = buildPlanSchool(1, 100, 3, "稳");

            when(simulationTaskMapper.selectById(1)).thenReturn(task)
                    .thenReturn(taskWithSeq(task, 1));
            when(simulationTaskMapper.markComputing(1)).thenReturn(1);
            when(simulationBatchMapper.selectById(10)).thenReturn(batch);
            when(voluntaryPlanMapper.selectById(100)).thenReturn(plan);
            when(planSchoolMapper.selectByPlanIdOrdered(100)).thenReturn(List.of(ps));

            ScLiScore score = buildScLiScore(3, 8000, 8500, 7800);
            when(scLiScoreMapper.selectOne(any())).thenReturn(score);

            SchoolInfo info = new SchoolInfo();
            info.setMonthView(500);
            info.setTotalView("6000");
            when(schoolInfoMapper.selectById(3)).thenReturn(info);
            when(planSchoolMapper.selectMajorScoresForSchool(eq(3), any())).thenReturn(buildMajorRows());
            when(simulationTaskMapper.completeTask(eq(1), any(), any(), any(), anyInt(), anyInt(), anyInt(), eq(1)))
                    .thenReturn(1);

            computationService.computeVariantAsync(1);

            verify(simulationTaskSchoolMapper, times(1)).insert(any(SimulationTaskSchool.class));
            verify(simulationBatchMapper).incrementCompletedTasks(10);
        }

        @Test
        @DisplayName("使用覆盖分数")
        void computeVariant_withScoreOverride_usesOverriddenScore() {
            SimulationTask task = buildTask(1, 10, "PENDING");
            task.setParamScore(650);
            SimulationBatch batch = buildBatch(10, "userA");
            VoluntaryPlan plan = buildPlan(100);
            PlanSchool ps = buildPlanSchool(1, 100, 3, "稳");

            when(simulationTaskMapper.selectById(1)).thenReturn(task)
                    .thenReturn(taskWithSeq(task, 1));
            when(simulationTaskMapper.markComputing(1)).thenReturn(1);
            when(simulationBatchMapper.selectById(10)).thenReturn(batch);
            when(voluntaryPlanMapper.selectById(100)).thenReturn(plan);
            when(planSchoolMapper.selectByPlanIdOrdered(100)).thenReturn(List.of(ps));
            when(scLiScoreMapper.selectOne(any())).thenReturn(buildScLiScore(3, 8000, 8500, 7800));
            when(schoolInfoMapper.selectById(3)).thenReturn(null);
            when(planSchoolMapper.selectMajorScoresForSchool(eq(3), any())).thenReturn(buildMajorRows());
            when(simulationTaskMapper.completeTask(eq(1), any(), any(), any(), anyInt(), anyInt(), anyInt(), eq(1)))
                    .thenReturn(1);

            computationService.computeVariantAsync(1);

            ArgumentCaptor<SimulationTaskSchool> captor = ArgumentCaptor.forClass(SimulationTaskSchool.class);
            verify(simulationTaskSchoolMapper).insert(captor.capture());
            // Major adjust risk uses effectiveScore=650 (not 600)
            assertNotNull(captor.getValue().getMajorAdjustRisk());
        }

        @Test
        @DisplayName("无历史数据使用默认值")
        void computeVariant_noScLiScore_usesDefaults() {
            SimulationTask task = buildTask(1, 10, "PENDING");
            SimulationBatch batch = buildBatch(10, "userA");
            VoluntaryPlan plan = buildPlan(100);
            PlanSchool ps = buildPlanSchool(1, 100, 3, "稳");

            when(simulationTaskMapper.selectById(1)).thenReturn(task)
                    .thenReturn(taskWithSeq(task, 1));
            when(simulationTaskMapper.markComputing(1)).thenReturn(1);
            when(simulationBatchMapper.selectById(10)).thenReturn(batch);
            when(voluntaryPlanMapper.selectById(100)).thenReturn(plan);
            when(planSchoolMapper.selectByPlanIdOrdered(100)).thenReturn(List.of(ps));
            when(scLiScoreMapper.selectOne(any())).thenReturn(null);
            when(schoolInfoMapper.selectById(3)).thenReturn(null);
            when(planSchoolMapper.selectMajorScoresForSchool(eq(3), any())).thenReturn(List.of());
            when(simulationTaskMapper.completeTask(eq(1), any(), any(), any(), anyInt(), anyInt(), anyInt(), eq(1)))
                    .thenReturn(1);

            computationService.computeVariantAsync(1);

            ArgumentCaptor<SimulationTaskSchool> captor = ArgumentCaptor.forClass(SimulationTaskSchool.class);
            verify(simulationTaskSchoolMapper).insert(captor.capture());
            SimulationTaskSchool result = captor.getValue();
            assertEquals(BigDecimal.valueOf(50).setScale(2), result.getAdmissionProb().setScale(2));
            assertEquals(BigDecimal.valueOf(50), result.getMajorAdjustRisk());
        }

        @Test
        @DisplayName("跨批次专业缺失使用默认风险50")
        void computeVariant_crossBatchMajorMissing_gracefulDefault() {
            SimulationTask task = buildTask(1, 10, "PENDING");
            task.setParamBatchName("ben ke er pi");
            SimulationBatch batch = buildBatch(10, "userA");
            VoluntaryPlan plan = buildPlan(100);
            PlanSchool ps = buildPlanSchool(1, 100, 3, "稳");

            when(simulationTaskMapper.selectById(1)).thenReturn(task)
                    .thenReturn(taskWithSeq(task, 1));
            when(simulationTaskMapper.markComputing(1)).thenReturn(1);
            when(simulationBatchMapper.selectById(10)).thenReturn(batch);
            when(voluntaryPlanMapper.selectById(100)).thenReturn(plan);
            when(planSchoolMapper.selectByPlanIdOrdered(100)).thenReturn(List.of(ps));
            when(scLiScoreMapper.selectOne(any())).thenReturn(buildScLiScore(3, 8000, 8500, 7800));
            when(schoolInfoMapper.selectById(3)).thenReturn(null);
            // Return empty for cross-batch query
            when(planSchoolMapper.selectMajorScoresForSchool(3, "ben ke er pi")).thenReturn(List.of());
            when(simulationTaskMapper.completeTask(eq(1), any(), any(), any(), anyInt(), anyInt(), anyInt(), eq(1)))
                    .thenReturn(1);

            computationService.computeVariantAsync(1);

            ArgumentCaptor<SimulationTaskSchool> captor = ArgumentCaptor.forClass(SimulationTaskSchool.class);
            verify(simulationTaskSchoolMapper).insert(captor.capture());
            assertEquals(BigDecimal.valueOf(50), captor.getValue().getMajorAdjustRisk());
            assertTrue(captor.getValue().getProbCategoryAdj().contains("该批次无专业分数数据"));
        }

        @Test
        @DisplayName("计算异常标记任务失败")
        void computeVariant_exceptionDuringCompute_marksTaskFailed() {
            SimulationTask task = buildTask(1, 10, "PENDING");

            when(simulationTaskMapper.selectById(1)).thenReturn(task)
                    .thenReturn(taskWithSeq(task, 1));
            when(simulationTaskMapper.markComputing(1)).thenReturn(1);
            when(simulationBatchMapper.selectById(10)).thenThrow(new RuntimeException("DB error"));

            computationService.computeVariantAsync(1);

            verify(simulationTaskMapper).failTask(eq(1), contains("DB error"));
            verify(simulationBatchMapper).incrementFailedTasks(10);
        }

        @Test
        @DisplayName("已在计算中的任务被跳过")
        void computeVariant_alreadyComputing_skips() {
            SimulationTask task = buildTask(1, 10, "COMPUTING");
            when(simulationTaskMapper.selectById(1)).thenReturn(task);

            computationService.computeVariantAsync(1);

            verify(simulationTaskMapper, never()).markComputing(anyInt());
            verify(simulationTaskSchoolMapper, never()).insert(any());
        }

        @Test
        @DisplayName("CAS竞争失败时跳过")
        void computeVariant_casFailure_skips() {
            SimulationTask task = buildTask(1, 10, "PENDING");
            when(simulationTaskMapper.selectById(1)).thenReturn(task);
            when(simulationTaskMapper.markComputing(1)).thenReturn(0); // another thread won

            computationService.computeVariantAsync(1);

            verify(simulationBatchMapper, never()).selectById(anyInt());
        }

        @Test
        @DisplayName("概率解释因子完整填充")
        void computeVariant_probExplanation_allFactorsPopulated() {
            SimulationTask task = buildTask(1, 10, "PENDING");
            SimulationBatch batch = buildBatch(10, "userA");
            VoluntaryPlan plan = buildPlan(100);
            PlanSchool ps = buildPlanSchool(1, 100, 3, "冲");

            when(simulationTaskMapper.selectById(1)).thenReturn(task)
                    .thenReturn(taskWithSeq(task, 1));
            when(simulationTaskMapper.markComputing(1)).thenReturn(1);
            when(simulationBatchMapper.selectById(10)).thenReturn(batch);
            when(voluntaryPlanMapper.selectById(100)).thenReturn(plan);
            when(planSchoolMapper.selectByPlanIdOrdered(100)).thenReturn(List.of(ps));
            when(scLiScoreMapper.selectOne(any())).thenReturn(buildScLiScore(3, 8000, 8500, 7800));
            when(schoolInfoMapper.selectById(3)).thenReturn(null);
            when(planSchoolMapper.selectMajorScoresForSchool(eq(3), any())).thenReturn(List.of());
            when(simulationTaskMapper.completeTask(eq(1), any(), any(), any(), anyInt(), anyInt(), anyInt(), eq(1)))
                    .thenReturn(1);

            computationService.computeVariantAsync(1);

            ArgumentCaptor<SimulationTaskSchool> captor = ArgumentCaptor.forClass(SimulationTaskSchool.class);
            verify(simulationTaskSchoolMapper).insert(captor.capture());
            SimulationTaskSchool result = captor.getValue();
            assertNotNull(result.getProbRankRatio(), "rankRatio应被填充");
            assertNotNull(result.getProbStabilityFactor(), "stabilityFactor应被填充");
            assertNotNull(result.getProbCategoryAdj(), "categoryAdj应被填充");
            assertNotNull(result.getProbBaseValue(), "baseValue应被填充");
            assertNotNull(result.getProbExplanation(), "explanation应被填充");
        }

        @Test
        @DisplayName("源方案对比增量计算正确")
        void computeVariant_sourceDeltas_computed() {
            SimulationTask task = buildTask(1, 10, "PENDING");
            task.setParamUserRank(5000); // better rank
            SimulationBatch batch = buildBatch(10, "userA");
            VoluntaryPlan plan = buildPlan(100);
            PlanSchool ps = buildPlanSchool(1, 100, 3, "稳");
            ps.setAdmissionProb(BigDecimal.valueOf(60));

            when(simulationTaskMapper.selectById(1)).thenReturn(task)
                    .thenReturn(taskWithSeq(task, 1));
            when(simulationTaskMapper.markComputing(1)).thenReturn(1);
            when(simulationBatchMapper.selectById(10)).thenReturn(batch);
            when(voluntaryPlanMapper.selectById(100)).thenReturn(plan);
            when(planSchoolMapper.selectByPlanIdOrdered(100)).thenReturn(List.of(ps));
            when(scLiScoreMapper.selectOne(any())).thenReturn(buildScLiScore(3, 8000, 8500, 7800));
            when(schoolInfoMapper.selectById(3)).thenReturn(null);
            when(planSchoolMapper.selectMajorScoresForSchool(eq(3), any())).thenReturn(List.of());
            when(simulationTaskMapper.completeTask(eq(1), any(), any(), any(), anyInt(), anyInt(), anyInt(), eq(1)))
                    .thenReturn(1);

            computationService.computeVariantAsync(1);

            ArgumentCaptor<SimulationTaskSchool> captor = ArgumentCaptor.forClass(SimulationTaskSchool.class);
            verify(simulationTaskSchoolMapper).insert(captor.capture());
            SimulationTaskSchool result = captor.getValue();
            assertEquals(BigDecimal.valueOf(60), result.getSourceAdmissionProb());
            assertNotNull(result.getProbDelta(), "probDelta应被计算");
        }
    }

    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("批次状态更新测试")
    class BatchStatusTests {

        @Test
        @DisplayName("完成后调用incrementCompletedTasks")
        void incrementCompletedTasks_updatesCount() {
            SimulationTask task = buildTask(1, 10, "PENDING");
            SimulationBatch batch = buildBatch(10, "userA");
            VoluntaryPlan plan = buildPlan(100);

            when(simulationTaskMapper.selectById(1)).thenReturn(task)
                    .thenReturn(taskWithSeq(task, 1));
            when(simulationTaskMapper.markComputing(1)).thenReturn(1);
            when(simulationBatchMapper.selectById(10)).thenReturn(batch);
            when(voluntaryPlanMapper.selectById(100)).thenReturn(plan);
            when(planSchoolMapper.selectByPlanIdOrdered(100)).thenReturn(List.of());
            when(simulationTaskMapper.completeTask(eq(1), any(), any(), any(), anyInt(), anyInt(), anyInt(), eq(1)))
                    .thenReturn(1);

            computationService.computeVariantAsync(1);

            verify(simulationBatchMapper).incrementCompletedTasks(10);
            verify(simulationBatchMapper, never()).incrementFailedTasks(anyInt());
        }

        @Test
        @DisplayName("失败后调用incrementFailedTasks")
        void allTasksFailed_batchStatusFailed() {
            SimulationTask task = buildTask(1, 10, "PENDING");
            when(simulationTaskMapper.selectById(1)).thenReturn(task)
                    .thenReturn(taskWithSeq(task, 1));
            when(simulationTaskMapper.markComputing(1)).thenReturn(1);
            when(simulationBatchMapper.selectById(10)).thenThrow(new RuntimeException("Error"));

            computationService.computeVariantAsync(1);

            verify(simulationBatchMapper).incrementFailedTasks(10);
            verify(simulationBatchMapper, never()).incrementCompletedTasks(anyInt());
        }
    }

    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("PlanCalculationUtils概率解释测试")
    class ProbabilityExplanationUtilsTests {

        @Test
        @DisplayName("正常数据构建完整解释")
        void buildProbabilityExplanation_normalData_complete() {
            ProbabilityExplanationVO vo = PlanCalculationUtils.buildProbabilityExplanation(
                    10000, 500, 10000, "稳", 9500, 10200, 10300);

            assertNotNull(vo);
            assertEquals(0, BigDecimal.ONE.compareTo(vo.getRankRatio())); // avgRank/userRank = 1.0
            assertNotNull(vo.getBaseProbability());
            assertNotNull(vo.getStabilityFactor());
            assertTrue(vo.getStabilityFactor().doubleValue() >= 0.5);
            assertTrue(vo.getStabilityFactor().doubleValue() <= 1.0);
            assertEquals("稳一稳: 无额外调整", vo.getCategoryAdjustment());
            assertTrue(vo.getFinalProbability().doubleValue() >= 1);
            assertTrue(vo.getFinalProbability().doubleValue() <= 99);
            assertNotNull(vo.getExplanation());
            assertFalse(vo.getExplanation().isEmpty());
        }

        @Test
        @DisplayName("冲类别概率上限50")
        void buildProbabilityExplanation_reachCategory_cappedAt50() {
            // avgRank much higher than userRank -> high prob, but capped
            ProbabilityExplanationVO vo = PlanCalculationUtils.buildProbabilityExplanation(
                    20000, 100, 10000, "冲", 20000, 20000, 20000);

            assertTrue(vo.getFinalProbability().doubleValue() <= 50,
                    "冲类别概率应不超过50，实际: " + vo.getFinalProbability());
        }

        @Test
        @DisplayName("保类别概率下限55")
        void buildProbabilityExplanation_safetyCategory_flooredAt55() {
            // avgRank much lower than userRank -> low prob, but floored
            ProbabilityExplanationVO vo = PlanCalculationUtils.buildProbabilityExplanation(
                    5000, 100, 10000, "保", 5000, 5000, 5000);

            assertTrue(vo.getFinalProbability().doubleValue() >= 55,
                    "保类别概率应不低于55，实际: " + vo.getFinalProbability());
        }

        @Test
        @DisplayName("缺失数据解释构建正确")
        void buildMissingDataExplanation_defaultValues() {
            ProbabilityExplanationVO vo = PlanCalculationUtils.buildMissingDataExplanation(
                    "测试大学", "暂无历年数据");

            assertEquals(BigDecimal.valueOf(50), vo.getFinalProbability());
            assertTrue(vo.getExplanation().contains("测试大学"));
            assertTrue(vo.getExplanation().contains("暂无历年数据"));
        }
    }

    // ══════════════════════════════════════════════
    // Helper builders
    // ══════════════════════════════════════════════

    private SimulationTask buildTask(int id, int batchId, String status) {
        SimulationTask task = new SimulationTask();
        task.setId(id);
        task.setBatchId(batchId);
        task.setStatus(status);
        task.setSequenceNumber(0);
        task.setTaskLabel("测试变体");
        task.setCreateTime(LocalDateTime.now());
        return task;
    }

    private SimulationTask taskWithSeq(SimulationTask original, int seq) {
        SimulationTask task = new SimulationTask();
        task.setId(original.getId());
        task.setBatchId(original.getBatchId());
        task.setStatus("COMPUTING");
        task.setSequenceNumber(seq);
        task.setTaskLabel(original.getTaskLabel());
        task.setParamScore(original.getParamScore());
        task.setParamUserRank(original.getParamUserRank());
        task.setParamBatchName(original.getParamBatchName());
        task.setCreateTime(original.getCreateTime());
        return task;
    }

    private SimulationBatch buildBatch(int id, String userName) {
        SimulationBatch batch = new SimulationBatch();
        batch.setId(id);
        batch.setUserName(userName);
        batch.setSourcePlanId(100);
        batch.setBatchName("测试批次");
        batch.setTotalTasks(1);
        batch.setCompletedTasks(0);
        batch.setFailedTasks(0);
        batch.setStatus("COMPUTING");
        return batch;
    }

    private VoluntaryPlan buildPlan(int id) {
        VoluntaryPlan plan = new VoluntaryPlan();
        plan.setId(id);
        plan.setUserName("userA");
        plan.setScore(600);
        plan.setUserRank(10000);
        plan.setBatchName("ben ke yi pi");
        plan.setSubjectType("理科");
        return plan;
    }

    private PlanSchool buildPlanSchool(int id, int planId, int schoolId, String category) {
        PlanSchool ps = new PlanSchool();
        ps.setId(id);
        ps.setPlanId(planId);
        ps.setSchoolId(schoolId);
        ps.setSchoolName("测试大学" + schoolId);
        ps.setCategory(category);
        ps.setSortOrder(1);
        ps.setAdmissionProb(BigDecimal.valueOf(60));
        ps.setMajorAdjustRisk(BigDecimal.valueOf(40));
        ps.setSelectedMajors("计算机科学,软件工程");
        return ps;
    }

    private ScLiScore buildScLiScore(int schoolId, int r2020, int r2021, int r2022) {
        ScLiScore score = new ScLiScore();
        score.setSchoolId(schoolId);
        score.setRank2020(r2020);
        score.setRank2021(r2021);
        score.setRank2022(r2022);
        return score;
    }

    private List<Map<String, Object>> buildMajorRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("majorName", "计算机科学");
        row.put("max", 650);
        row.put("min", 610);
        row.put("average", 630);
        rows.add(row);
        return rows;
    }
}
