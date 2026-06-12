package com.scu.gkvr_system_backend.service;

import com.scu.gkvr_system_backend.dto.SimulationBatchRequestDTO;
import com.scu.gkvr_system_backend.dto.SimulationCompareRequestDTO;
import com.scu.gkvr_system_backend.dto.SimulationVariantDTO;
import com.scu.gkvr_system_backend.mapper.*;
import com.scu.gkvr_system_backend.pojo.*;
import com.scu.gkvr_system_backend.service.impl.SimulationServiceImpl;
import com.scu.gkvr_system_backend.vo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("模拟批处理服务测试")
class SimulationServiceImplTest {

    @InjectMocks
    private SimulationServiceImpl simulationService;

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
    private PlanVersionLogMapper planVersionLogMapper;
    @Mock
    private SimulationComputationService simulationComputationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(simulationService, "baseMapper", simulationBatchMapper);
    }

    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("提交批次测试")
    class SubmitBatchTests {

        @Test
        @DisplayName("有效请求返回批次ID")
        void submitBatch_validRequest_returnsBatchId() {
            VoluntaryPlan sourcePlan = buildPlan(1, "userA");
            when(voluntaryPlanMapper.selectById(1)).thenReturn(sourcePlan);
            when(simulationBatchMapper.insert(any(SimulationBatch.class))).thenAnswer(inv -> {
                SimulationBatch b = inv.getArgument(0);
                b.setId(100);
                return 1;
            });
            when(simulationTaskMapper.insert(any(SimulationTask.class))).thenAnswer(inv -> {
                SimulationTask t = inv.getArgument(0);
                t.setId(200);
                return 1;
            });

            SimulationBatchRequestDTO request = buildBatchRequest("userA", 1, 2);
            Integer batchId = simulationService.submitBatch(request);

            assertNotNull(batchId, "应返回批次ID");
            assertEquals(100, batchId);
            verify(simulationTaskMapper, times(2)).insert(any(SimulationTask.class));
            verify(simulationComputationService, times(2)).computeVariantAsync(anyInt());
        }

        @Test
        @DisplayName("源方案不存在返回null")
        void submitBatch_sourcePlanNotFound_returnsNull() {
            when(voluntaryPlanMapper.selectById(999)).thenReturn(null);

            SimulationBatchRequestDTO request = buildBatchRequest("userA", 999, 1);
            Integer result = simulationService.submitBatch(request);

            assertNull(result, "源方案不存在应返回null");
            verify(simulationBatchMapper, never()).insert(any());
        }

        @Test
        @DisplayName("非方案拥有者返回null")
        void submitBatch_wrongUser_returnsNull() {
            VoluntaryPlan plan = buildPlan(1, "userB");
            when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);

            SimulationBatchRequestDTO request = buildBatchRequest("userA", 1, 1);
            Integer result = simulationService.submitBatch(request);

            assertNull(result, "非拥有者应返回null");
        }

        @Test
        @DisplayName("多变体正确创建所有任务")
        void submitBatch_multipleVariants_createsAllTasks() {
            VoluntaryPlan sourcePlan = buildPlan(1, "userA");
            when(voluntaryPlanMapper.selectById(1)).thenReturn(sourcePlan);
            when(simulationBatchMapper.insert(any())).thenAnswer(inv -> {
                ((SimulationBatch) inv.getArgument(0)).setId(10);
                return 1;
            });
            when(simulationTaskMapper.insert(any())).thenAnswer(inv -> {
                ((SimulationTask) inv.getArgument(0)).setId(1);
                return 1;
            });

            SimulationBatchRequestDTO request = buildBatchRequest("userA", 1, 5);
            simulationService.submitBatch(request);

            ArgumentCaptor<SimulationTask> captor = ArgumentCaptor.forClass(SimulationTask.class);
            verify(simulationTaskMapper, times(5)).insert(captor.capture());
            List<SimulationTask> tasks = captor.getAllValues();
            assertEquals(5, tasks.size());
            assertTrue(tasks.stream().allMatch(t -> t.getBatchId().equals(10)));
        }
    }

    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("查询批次状态测试")
    class GetBatchStatusTests {

        @Test
        @DisplayName("正常返回批次状态")
        void getBatchStatus_existingBatch_returnsFull() {
            SimulationBatch batch = buildBatch(1, "userA", "COMPUTING");
            when(simulationBatchMapper.selectByIdAndUser(1, "userA")).thenReturn(batch);
            when(voluntaryPlanMapper.selectById(100)).thenReturn(buildPlan(100, "userA"));
            when(simulationTaskMapper.selectByBatchId(1)).thenReturn(List.of(buildTask(1, 1, "COMPLETED")));

            SimulationBatchVO vo = simulationService.getBatchStatus(1, "userA");

            assertNotNull(vo);
            assertEquals("COMPUTING", vo.getStatus());
            assertEquals(1, vo.getTasks().size());
        }

        @Test
        @DisplayName("非拥有者返回null")
        void getBatchStatus_wrongUser_returnsNull() {
            when(simulationBatchMapper.selectByIdAndUser(1, "userA")).thenReturn(null);

            SimulationBatchVO vo = simulationService.getBatchStatus(1, "userA");
            assertNull(vo);
        }

        @Test
        @DisplayName("不存在的批次返回null")
        void getBatchStatus_nonExistent_returnsNull() {
            when(simulationBatchMapper.selectByIdAndUser(999, "userA")).thenReturn(null);
            assertNull(simulationService.getBatchStatus(999, "userA"));
        }
    }

    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("概率解释测试")
    class GetExplanationTests {

        @Test
        @DisplayName("已完成任务返回解释")
        void getExplanation_completedTask_returnsExplanation() {
            SimulationTask task = buildTask(1, 10, "COMPLETED");
            SimulationBatch batch = buildBatch(10, "userA", "COMPLETED");
            SimulationTaskSchool sts = buildTaskSchool(1, 3, "test大学");
            sts.setProbRankRatio(BigDecimal.valueOf(1.2));
            sts.setProbStabilityFactor(BigDecimal.valueOf(0.95));
            sts.setProbCategoryAdj("稳一稳: 无额外调整");
            sts.setProbBaseValue(BigDecimal.valueOf(60));
            sts.setProbExplanation("解释文本");

            when(simulationTaskMapper.selectById(1)).thenReturn(task);
            when(simulationBatchMapper.selectById(10)).thenReturn(batch);
            when(simulationTaskSchoolMapper.selectByTaskIdAndSchoolId(1, 3)).thenReturn(sts);

            ProbabilityExplanationVO vo = simulationService.getSchoolExplanation(1, 3, "userA");

            assertNotNull(vo);
            assertEquals(BigDecimal.valueOf(1.2), vo.getRankRatio());
            assertEquals("解释文本", vo.getExplanation());
        }

        @Test
        @DisplayName("非拥有者返回null")
        void getExplanation_wrongUser_returnsNull() {
            SimulationTask task = buildTask(1, 10, "COMPLETED");
            SimulationBatch batch = buildBatch(10, "userB", "COMPLETED");

            when(simulationTaskMapper.selectById(1)).thenReturn(task);
            when(simulationBatchMapper.selectById(10)).thenReturn(batch);

            assertNull(simulationService.getSchoolExplanation(1, 3, "userA"));
        }

        @Test
        @DisplayName("院校不存在返回null")
        void getExplanation_missingSchool_returnsNull() {
            SimulationTask task = buildTask(1, 10, "COMPLETED");
            SimulationBatch batch = buildBatch(10, "userA", "COMPLETED");

            when(simulationTaskMapper.selectById(1)).thenReturn(task);
            when(simulationBatchMapper.selectById(10)).thenReturn(batch);
            when(simulationTaskSchoolMapper.selectByTaskIdAndSchoolId(1, 999)).thenReturn(null);

            assertNull(simulationService.getSchoolExplanation(1, 999, "userA"));
        }
    }

    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("任务对比测试")
    class CompareTests {

        @Test
        @DisplayName("两个已完成任务返回差异")
        void compareTasks_bothCompleted_returnsDiffs() {
            SimulationTask taskA = buildTask(1, 10, "COMPLETED");
            SimulationTask taskB = buildTask(2, 10, "COMPLETED");
            SimulationBatch batch = buildBatch(10, "userA", "COMPLETED");
            VoluntaryPlan plan = buildPlan(100, "userA");

            when(simulationTaskMapper.selectById(1)).thenReturn(taskA);
            when(simulationTaskMapper.selectById(2)).thenReturn(taskB);
            when(simulationBatchMapper.selectById(10)).thenReturn(batch);
            when(voluntaryPlanMapper.selectById(100)).thenReturn(plan);

            // Task A schools
            SimulationTaskSchool sA1 = buildTaskSchool(1, 3, "川大");
            sA1.setCategory("冲");
            sA1.setAdmissionProb(BigDecimal.valueOf(35));
            when(simulationTaskSchoolMapper.selectByTaskIdOrdered(1)).thenReturn(List.of(sA1));

            // Task B schools: same school but different prob
            SimulationTaskSchool sB1 = buildTaskSchool(2, 3, "川大");
            sB1.setCategory("冲");
            sB1.setAdmissionProb(BigDecimal.valueOf(50));
            when(simulationTaskSchoolMapper.selectByTaskIdOrdered(2)).thenReturn(List.of(sB1));

            SimulationCompareRequestDTO request = new SimulationCompareRequestDTO();
            request.setUserName("userA");
            request.setTaskIdA(1);
            request.setTaskIdB(2);

            SimulationComparisonVO vo = simulationService.compareTasks(request);

            assertNotNull(vo);
            assertFalse(vo.getDifferences().isEmpty(), "应检测到概率变化");
            assertEquals("prob_changed", vo.getDifferences().get(0).getChangeType());
        }

        @Test
        @DisplayName("未完成的任务返回null")
        void compareTasks_oneNotCompleted_returnsNull() {
            SimulationTask taskA = buildTask(1, 10, "COMPLETED");
            SimulationTask taskB = buildTask(2, 10, "COMPUTING");
            SimulationBatch batch = buildBatch(10, "userA", "COMPUTING");

            when(simulationTaskMapper.selectById(1)).thenReturn(taskA);
            when(simulationTaskMapper.selectById(2)).thenReturn(taskB);
            when(simulationBatchMapper.selectById(10)).thenReturn(batch);

            SimulationCompareRequestDTO request = new SimulationCompareRequestDTO();
            request.setUserName("userA");
            request.setTaskIdA(1);
            request.setTaskIdB(2);

            assertNull(simulationService.compareTasks(request));
        }

        @Test
        @DisplayName("跨用户对比返回null")
        void compareTasks_crossUser_returnsNull() {
            SimulationTask taskA = buildTask(1, 10, "COMPLETED");
            SimulationTask taskB = buildTask(2, 20, "COMPLETED");
            SimulationBatch batchA = buildBatch(10, "userA", "COMPLETED");
            SimulationBatch batchB = buildBatch(20, "userB", "COMPLETED");

            when(simulationTaskMapper.selectById(1)).thenReturn(taskA);
            when(simulationTaskMapper.selectById(2)).thenReturn(taskB);
            when(simulationBatchMapper.selectById(10)).thenReturn(batchA);
            when(simulationBatchMapper.selectById(20)).thenReturn(batchB);

            SimulationCompareRequestDTO request = new SimulationCompareRequestDTO();
            request.setUserName("userA");
            request.setTaskIdA(1);
            request.setTaskIdB(2);

            assertNull(simulationService.compareTasks(request));
        }

        @Test
        @DisplayName("院校新增被检测")
        void compareTasks_schoolAdded_detected() {
            SimulationTask taskA = buildTask(1, 10, "COMPLETED");
            SimulationTask taskB = buildTask(2, 10, "COMPLETED");
            SimulationBatch batch = buildBatch(10, "userA", "COMPLETED");
            VoluntaryPlan plan = buildPlan(100, "userA");

            when(simulationTaskMapper.selectById(1)).thenReturn(taskA);
            when(simulationTaskMapper.selectById(2)).thenReturn(taskB);
            when(simulationBatchMapper.selectById(10)).thenReturn(batch);
            when(voluntaryPlanMapper.selectById(100)).thenReturn(plan);

            // Task A: empty
            when(simulationTaskSchoolMapper.selectByTaskIdOrdered(1)).thenReturn(List.of());
            // Task B: one school
            SimulationTaskSchool sB = buildTaskSchool(2, 5, "电子科大");
            sB.setCategory("稳");
            sB.setAdmissionProb(BigDecimal.valueOf(60));
            when(simulationTaskSchoolMapper.selectByTaskIdOrdered(2)).thenReturn(List.of(sB));

            SimulationCompareRequestDTO request = new SimulationCompareRequestDTO();
            request.setUserName("userA");
            request.setTaskIdA(1);
            request.setTaskIdB(2);

            SimulationComparisonVO vo = simulationService.compareTasks(request);
            assertNotNull(vo);
            assertEquals(1, vo.getDifferences().size());
            assertEquals("added", vo.getDifferences().get(0).getChangeType());
        }
    }

    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("提升为方案测试")
    class PromoteToPlanTests {

        @Test
        @DisplayName("已完成任务成功提升")
        void promoteToPlan_completedTask_createsNewPlan() {
            SimulationTask task = buildTask(1, 10, "COMPLETED");
            task.setTotalRiskScore(BigDecimal.valueOf(35));
            SimulationBatch batch = buildBatch(10, "userA", "COMPLETED");
            VoluntaryPlan sourcePlan = buildPlan(100, "userA");

            when(simulationTaskMapper.selectById(1)).thenReturn(task);
            when(simulationBatchMapper.selectById(10)).thenReturn(batch);
            when(voluntaryPlanMapper.selectById(100)).thenReturn(sourcePlan);
            when(voluntaryPlanMapper.insert(any())).thenAnswer(inv -> {
                ((VoluntaryPlan) inv.getArgument(0)).setId(200);
                return 1;
            });

            SimulationTaskSchool sts = buildTaskSchool(1, 3, "川大");
            when(simulationTaskSchoolMapper.selectByTaskIdOrdered(1)).thenReturn(List.of(sts));

            Integer planId = simulationService.promoteToPlan(1, "userA", "新方案");
            assertNotNull(planId);
            assertEquals(200, planId);
            verify(planSchoolMapper, times(1)).insert(any(PlanSchool.class));
            verify(planVersionLogMapper, times(1)).insert(any(PlanVersionLog.class));
        }

        @Test
        @DisplayName("未完成任务返回null")
        void promoteToPlan_incompleteTask_returnsNull() {
            SimulationTask task = buildTask(1, 10, "COMPUTING");
            when(simulationTaskMapper.selectById(1)).thenReturn(task);

            assertNull(simulationService.promoteToPlan(1, "userA", "方案"));
        }

        @Test
        @DisplayName("非拥有者返回null")
        void promoteToPlan_wrongUser_returnsNull() {
            SimulationTask task = buildTask(1, 10, "COMPLETED");
            SimulationBatch batch = buildBatch(10, "userB", "COMPLETED");

            when(simulationTaskMapper.selectById(1)).thenReturn(task);
            when(simulationBatchMapper.selectById(10)).thenReturn(batch);

            assertNull(simulationService.promoteToPlan(1, "userA", "方案"));
        }
    }

    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("快照回滚测试")
    class RollbackTests {

        @Test
        @DisplayName("有效快照成功回滚")
        void rollbackFromSnapshot_validSnapshot_restoresData() {
            SimulationTask task = buildTask(1, 10, "COMPLETED");
            task.setRiskSnapshot("{\"school_3\":{\"rank2020\":5000,\"rank2021\":5200,\"rank2022\":4800}}");
            SimulationBatch batch = buildBatch(10, "userA", "COMPLETED");
            VoluntaryPlan plan = buildPlan(100, "userA");
            plan.setVersion(2);

            PlanSchool ps = new PlanSchool();
            ps.setId(1);
            ps.setPlanId(100);
            ps.setSchoolId(3);
            ps.setCategory("稳");

            when(simulationTaskMapper.selectById(1)).thenReturn(task);
            when(simulationBatchMapper.selectById(10)).thenReturn(batch);
            when(voluntaryPlanMapper.selectById(100)).thenReturn(plan);
            when(planSchoolMapper.selectByPlanIdOrdered(100)).thenReturn(List.of(ps));

            Boolean result = simulationService.rollbackFromSnapshot(1, "userA");

            assertTrue(result);
            verify(planSchoolMapper).updateById(any(PlanSchool.class));
            verify(voluntaryPlanMapper).updateById(any(VoluntaryPlan.class));
            verify(planVersionLogMapper).insert(any(PlanVersionLog.class));
        }

        @Test
        @DisplayName("无快照返回false")
        void rollbackFromSnapshot_noSnapshot_returnsFalse() {
            SimulationTask task = buildTask(1, 10, "COMPLETED");
            task.setRiskSnapshot(null);
            when(simulationTaskMapper.selectById(1)).thenReturn(task);

            assertFalse(simulationService.rollbackFromSnapshot(1, "userA"));
        }
    }

    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("取消批次测试")
    class CancelBatchTests {

        @Test
        @DisplayName("取消成功")
        void cancelBatch_success() {
            SimulationBatch batch = buildBatch(1, "userA", "COMPUTING");
            when(simulationBatchMapper.selectByIdAndUser(1, "userA")).thenReturn(batch);

            assertTrue(simulationService.cancelBatch(1, "userA"));
            verify(simulationTaskMapper).cancelPendingByBatchId(1);
            verify(simulationBatchMapper).updateStatus(1, "CANCELLED");
        }

        @Test
        @DisplayName("非拥有者取消失败")
        void cancelBatch_wrongUser_returnsFalse() {
            when(simulationBatchMapper.selectByIdAndUser(1, "userA")).thenReturn(null);
            assertFalse(simulationService.cancelBatch(1, "userA"));
        }
    }

    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("用户隔离测试")
    class UserIsolationTests {

        @Test
        @DisplayName("用户A无法访问用户B的批次")
        void userA_cannotAccessUserBBatch() {
            when(simulationBatchMapper.selectByIdAndUser(1, "userA")).thenReturn(null);
            assertNull(simulationService.getBatchStatus(1, "userA"));
        }

        @Test
        @DisplayName("用户A无法访问用户B的任务")
        void userA_cannotAccessUserBTask() {
            SimulationTask task = buildTask(1, 10, "COMPLETED");
            SimulationBatch batch = buildBatch(10, "userB", "COMPLETED");

            when(simulationTaskMapper.selectById(1)).thenReturn(task);
            when(simulationBatchMapper.selectById(10)).thenReturn(batch);

            assertNull(simulationService.getTaskDetail(1, "userA"));
        }

        @Test
        @DisplayName("用户A无法取消用户B的批次")
        void userA_cannotCancelUserBBatch() {
            when(simulationBatchMapper.selectByIdAndUser(1, "userA")).thenReturn(null);
            assertFalse(simulationService.cancelBatch(1, "userA"));
            verify(simulationTaskMapper, never()).cancelPendingByBatchId(anyInt());
        }

        @Test
        @DisplayName("列出批次只返回本人数据")
        void listBatches_onlyOwnData() {
            SimulationBatch batch = buildBatch(1, "userA", "COMPLETED");
            when(simulationBatchMapper.selectByUserName("userA")).thenReturn(List.of(batch));
            when(voluntaryPlanMapper.selectById(anyInt())).thenReturn(buildPlan(100, "userA"));
            when(simulationTaskMapper.selectByBatchId(1)).thenReturn(List.of());

            List<SimulationBatchVO> result = simulationService.listUserBatches("userA");
            assertEquals(1, result.size());
            verify(simulationBatchMapper, never()).selectByUserName("userB");
        }
    }

    // ══════════════════════════════════════════════
    // Helper builders
    // ══════════════════════════════════════════════

    private VoluntaryPlan buildPlan(int id, String userName) {
        VoluntaryPlan plan = new VoluntaryPlan();
        plan.setId(id);
        plan.setUserName(userName);
        plan.setPlanName("测试方案");
        plan.setScore(600);
        plan.setUserRank(10000);
        plan.setSubjectType("理科");
        plan.setBatchName("ben ke yi pi");
        plan.setVersion(1);
        plan.setStatus(0);
        plan.setCreateTime(LocalDateTime.now());
        plan.setUpdateTime(LocalDateTime.now());
        return plan;
    }

    private SimulationBatch buildBatch(int id, String userName, String status) {
        SimulationBatch batch = new SimulationBatch();
        batch.setId(id);
        batch.setUserName(userName);
        batch.setSourcePlanId(100);
        batch.setBatchName("测试批次");
        batch.setSequenceNumber(1);
        batch.setStatus(status);
        batch.setTotalTasks(2);
        batch.setCompletedTasks(status.equals("COMPLETED") ? 2 : 0);
        batch.setFailedTasks(0);
        batch.setCreateTime(LocalDateTime.now());
        batch.setUpdateTime(LocalDateTime.now());
        return batch;
    }

    private SimulationTask buildTask(int id, int batchId, String status) {
        SimulationTask task = new SimulationTask();
        task.setId(id);
        task.setBatchId(batchId);
        task.setTaskLabel("测试任务");
        task.setStatus(status);
        task.setSequenceNumber(1);
        task.setCreateTime(LocalDateTime.now());
        if ("COMPLETED".equals(status)) {
            task.setTotalRiskScore(BigDecimal.valueOf(40));
            task.setReachCount(2);
            task.setMatchCount(3);
            task.setSafetyCount(2);
            task.setComputeEnd(LocalDateTime.now());
        }
        return task;
    }

    private SimulationTaskSchool buildTaskSchool(int taskId, int schoolId, String schoolName) {
        SimulationTaskSchool sts = new SimulationTaskSchool();
        sts.setId(taskId * 100 + schoolId);
        sts.setTaskId(taskId);
        sts.setSchoolId(schoolId);
        sts.setSchoolName(schoolName);
        sts.setCategory("稳");
        sts.setSortOrder(1);
        sts.setAdmissionProb(BigDecimal.valueOf(60));
        sts.setAdmissionProbLevel("中");
        sts.setMajorAdjustRisk(BigDecimal.valueOf(40));
        sts.setMajorAdjustRiskLevel("中");
        sts.setPopularityScore(BigDecimal.valueOf(55));
        sts.setPopularityTrend("stable");
        sts.setRankFluctuation("测试波动");
        sts.setAvgRank3yr(BigDecimal.valueOf(10000));
        sts.setRankStdDev(BigDecimal.valueOf(500));
        sts.setRankTrend("stable");
        sts.setCategoryChanged(0);
        sts.setCreateTime(LocalDateTime.now());
        return sts;
    }

    private SimulationBatchRequestDTO buildBatchRequest(String userName, int sourcePlanId, int variantCount) {
        SimulationBatchRequestDTO dto = new SimulationBatchRequestDTO();
        dto.setUserName(userName);
        dto.setSourcePlanId(sourcePlanId);
        dto.setBatchName("测试模拟批次");
        List<SimulationVariantDTO> variants = new ArrayList<>();
        for (int i = 0; i < variantCount; i++) {
            SimulationVariantDTO v = new SimulationVariantDTO();
            v.setTaskLabel("变体" + (i + 1));
            v.setScore(600 + i * 10);
            variants.add(v);
        }
        dto.setVariants(variants);
        return dto;
    }
}
