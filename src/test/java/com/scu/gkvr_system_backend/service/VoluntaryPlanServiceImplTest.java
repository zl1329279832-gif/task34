package com.scu.gkvr_system_backend.service;

import com.scu.gkvr_system_backend.dto.*;
import com.scu.gkvr_system_backend.mapper.*;
import com.scu.gkvr_system_backend.pojo.*;
import com.scu.gkvr_system_backend.service.impl.VoluntaryPlanServiceImpl;
import com.scu.gkvr_system_backend.vo.PlanComparisonVO;
import com.scu.gkvr_system_backend.vo.PlanDetailVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * VoluntaryPlanServiceImpl 单元测试(Mockito)
 */
@ExtendWith(MockitoExtension.class)
class VoluntaryPlanServiceImplTest {

    @InjectMocks
    private VoluntaryPlanServiceImpl voluntaryPlanService;

    @Mock
    private VoluntaryPlanMapper voluntaryPlanMapper;

    @Mock
    private PlanSchoolMapper planSchoolMapper;

    @Mock
    private PlanVersionLogMapper planVersionLogMapper;

    @Mock
    private ScLiScoreMapper scLiScoreMapper;

    @Mock
    private ScoreRankMapper scoreRankMapper;

    @Mock
    private SchoolInfoMapper schoolInfoMapper;

    @Mock
    private MajorScoreMapper majorScoreMapper;

    private PlanGenerateRequestDTO generateRequest;

    @BeforeEach
    void setUp() {
        // MyBatis-Plus ServiceImpl 的 baseMapper 需手动注入
        ReflectionTestUtils.setField(voluntaryPlanService, "baseMapper", voluntaryPlanMapper);

        generateRequest = new PlanGenerateRequestDTO();
        generateRequest.setUserName("testUser");
        generateRequest.setScore(580);
        generateRequest.setUserRank(20000);
        generateRequest.setSubjectType("理科");
        generateRequest.setReachCount(3);
        generateRequest.setMatchCount(5);
        generateRequest.setSafetyCount(3);
    }

    // ══════════════════════════════════════════
    // generatePlans
    // ══════════════════════════════════════════

    @Test
    void generatePlans_normalScore_returnsAllCategories() {
        ScoreRank sr = new ScoreRank();
        sr.setBatchName("本科一批");
        when(scoreRankMapper.selectOne(any())).thenReturn(sr);

        // 返回一些候选院校
        List<Map<String, Object>> candidates = createCandidateRows(5);
        when(planSchoolMapper.selectCandidateSchools(anyInt(), anyInt(), any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(candidates);
        when(planSchoolMapper.selectMajorScoresForSchool(anyInt(), any()))
                .thenReturn(Collections.emptyList());

        Map<String, Object> result = voluntaryPlanService.generatePlans(generateRequest);

        assertNotNull(result);
        assertNotNull(result.get("reach"));
        assertNotNull(result.get("match"));
        assertNotNull(result.get("safety"));
        assertNotNull(result.get("riskSummary"));
    }

    @Test
    void generatePlans_topRank_adjustedRatios() {
        generateRequest.setScore(700);
        generateRequest.setUserRank(50);

        ScoreRank sr = new ScoreRank();
        sr.setBatchName("本科一批");
        when(scoreRankMapper.selectOne(any())).thenReturn(sr);
        when(planSchoolMapper.selectCandidateSchools(anyInt(), anyInt(), any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(Collections.emptyList());

        Map<String, Object> result = voluntaryPlanService.generatePlans(generateRequest);
        assertNotNull(result);
    }

    @Test
    void generatePlans_bottomRank_adjustedRatios() {
        generateRequest.setScore(200);
        generateRequest.setUserRank(280000);

        when(scoreRankMapper.selectOne(any())).thenReturn(null);
        when(planSchoolMapper.selectCandidateSchools(anyInt(), anyInt(), any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(Collections.emptyList());

        Map<String, Object> result = voluntaryPlanService.generatePlans(generateRequest);
        assertNotNull(result);
    }

    @Test
    void generatePlans_noMatchingSchools_emptyLists() {
        generateRequest.setRegionPref(List.of("不存在的省"));
        generateRequest.setSchoolTier(List.of("985"));

        when(scoreRankMapper.selectOne(any())).thenReturn(null);
        when(planSchoolMapper.selectCandidateSchools(anyInt(), anyInt(), any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(Collections.emptyList());

        Map<String, Object> result = voluntaryPlanService.generatePlans(generateRequest);
        assertNotNull(result);
        assertTrue(((List<?>) result.get("reach")).isEmpty());
        assertTrue(((List<?>) result.get("match")).isEmpty());
        assertTrue(((List<?>) result.get("safety")).isEmpty());
    }

    @Test
    void generatePlans_withFilters_parsedCorrectly() {
        generateRequest.setRegionPref(List.of("四川", "北京"));
        generateRequest.setSchoolTier(List.of("985", "211"));

        when(scoreRankMapper.selectOne(any())).thenReturn(null);
        when(planSchoolMapper.selectCandidateSchools(anyInt(), anyInt(),
                eq(List.of("四川", "北京")), eq(true), eq(true), eq(false)))
                .thenReturn(Collections.emptyList());

        voluntaryPlanService.generatePlans(generateRequest);

        // 验证传入了正确的过滤参数
        verify(planSchoolMapper, times(3)).selectCandidateSchools(
                anyInt(), anyInt(), eq(List.of("四川", "北京")), eq(true), eq(true), eq(false));
    }

    // ══════════════════════════════════════════
    // savePlan
    // ══════════════════════════════════════════

    @Test
    void savePlan_validInput_returnsPlanId() {
        PlanSaveRequestDTO request = createSaveRequest();

        when(voluntaryPlanMapper.insert(any(VoluntaryPlan.class))).thenAnswer(invocation -> {
            VoluntaryPlan plan = invocation.getArgument(0);
            plan.setId(1);
            return 1;
        });
        when(scLiScoreMapper.selectOne(any())).thenReturn(null);
        when(planSchoolMapper.selectMajorScoresForSchool(anyInt(), any()))
                .thenReturn(Collections.emptyList());
        when(schoolInfoMapper.selectById(anyInt())).thenReturn(null);
        when(planSchoolMapper.insert(any(PlanSchool.class))).thenReturn(1);
        when(planVersionLogMapper.insert(any(PlanVersionLog.class))).thenReturn(1);
        when(voluntaryPlanMapper.updateById(any())).thenReturn(1);

        Integer planId = voluntaryPlanService.savePlan(request);
        assertEquals(1, planId);

        verify(voluntaryPlanMapper).insert(any(VoluntaryPlan.class));
        verify(planSchoolMapper, times(2)).insert(any(PlanSchool.class));
        verify(planVersionLogMapper).insert(any(PlanVersionLog.class));
    }

    // ══════════════════════════════════════════
    // copyPlan
    // ══════════════════════════════════════════

    @Test
    void copyPlan_success_returnsNewId() {
        VoluntaryPlan original = createPlan("userA");
        original.setId(1);
        when(voluntaryPlanMapper.selectById(1)).thenReturn(original);
        when(voluntaryPlanMapper.insert(any())).thenAnswer(inv -> {
            ((VoluntaryPlan) inv.getArgument(0)).setId(2);
            return 1;
        });

        PlanSchool ps = new PlanSchool();
        ps.setSchoolId(1);
        ps.setSchoolName("Test");
        ps.setCategory("稳");
        ps.setAdmissionProb(BigDecimal.valueOf(50));
        ps.setMajorAdjustRisk(BigDecimal.valueOf(30));
        when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(List.of(ps));
        when(planSchoolMapper.insert(any())).thenReturn(1);
        when(scLiScoreMapper.selectOne(any())).thenReturn(null);
        when(planSchoolMapper.selectMajorScoresForSchool(anyInt(), any()))
                .thenReturn(Collections.emptyList());
        when(schoolInfoMapper.selectById(anyInt())).thenReturn(null);
        when(voluntaryPlanMapper.updateById(any())).thenReturn(1);
        when(planVersionLogMapper.insert(any())).thenReturn(1);

        Integer newId = voluntaryPlanService.copyPlan(1, "userA", "副本方案");
        assertEquals(2, newId);
    }

    @Test
    void copyPlan_wrongUser_returnsNull() {
        VoluntaryPlan original = createPlan("userA");
        original.setId(1);
        when(voluntaryPlanMapper.selectById(1)).thenReturn(original);

        Integer newId = voluntaryPlanService.copyPlan(1, "userB", "副本方案");
        assertNull(newId);
    }

    // ══════════════════════════════════════════
    // reorderSchools
    // ══════════════════════════════════════════

    @Test
    void reorderSchools_success() {
        VoluntaryPlan plan = createPlan("userA");
        plan.setId(1);
        when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);
        when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(Collections.emptyList());
        when(planSchoolMapper.updateSortOrder(anyInt(), anyInt())).thenReturn(1);
        when(voluntaryPlanMapper.updateById(any())).thenReturn(1);
        when(planVersionLogMapper.insert(any())).thenReturn(1);

        SchoolReorderDTO dto = new SchoolReorderDTO();
        dto.setPlanId(1);
        dto.setUserName("userA");
        SchoolReorderDTO.SchoolOrderItem item = new SchoolReorderDTO.SchoolOrderItem();
        item.setPlanSchoolId(10);
        item.setSortOrder(0);
        dto.setOrderedSchools(List.of(item));

        Boolean result = voluntaryPlanService.reorderSchools(dto);
        assertTrue(result);
    }

    @Test
    void reorderSchools_wrongUser_returnsFalse() {
        VoluntaryPlan plan = createPlan("userA");
        plan.setId(1);
        when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);

        SchoolReorderDTO dto = new SchoolReorderDTO();
        dto.setPlanId(1);
        dto.setUserName("userB");
        dto.setOrderedSchools(List.of(new SchoolReorderDTO.SchoolOrderItem()));

        Boolean result = voluntaryPlanService.reorderSchools(dto);
        assertFalse(result);
    }

    // ══════════════════════════════════════════
    // deletePlan
    // ══════════════════════════════════════════

    @Test
    void deletePlan_success() {
        VoluntaryPlan plan = createPlan("userA");
        plan.setId(1);
        when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);
        when(planSchoolMapper.delete(any())).thenReturn(2);
        when(planVersionLogMapper.delete(any())).thenReturn(1);
        when(voluntaryPlanMapper.deleteById(1)).thenReturn(1);

        Boolean result = voluntaryPlanService.deletePlan(1, "userA");
        assertTrue(result);
    }

    @Test
    void deletePlan_wrongUser_returnsFalse() {
        VoluntaryPlan plan = createPlan("userA");
        plan.setId(1);
        when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);

        Boolean result = voluntaryPlanService.deletePlan(1, "userB");
        assertFalse(result);
    }

    // ══════════════════════════════════════════
    // reevaluatePlan
    // ══════════════════════════════════════════

    @Test
    void reevaluatePlan_versionIncremented() {
        VoluntaryPlan plan = createPlan("userA");
        plan.setId(1);
        plan.setVersion(1);
        plan.setUserRank(20000);
        plan.setScore(580);
        when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);

        PlanSchool ps = new PlanSchool();
        ps.setId(10);
        ps.setPlanId(1);
        ps.setSchoolId(3);
        ps.setCategory("稳");
        ps.setAdmissionProb(BigDecimal.valueOf(50));
        ps.setMajorAdjustRisk(BigDecimal.valueOf(30));
        when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(List.of(ps));
        when(scLiScoreMapper.selectOne(any())).thenReturn(null);
        when(planSchoolMapper.selectMajorScoresForSchool(anyInt(), any()))
                .thenReturn(Collections.emptyList());
        when(schoolInfoMapper.selectById(anyInt())).thenReturn(null);
        when(planSchoolMapper.updateById(any())).thenReturn(1);
        when(voluntaryPlanMapper.updateById(any())).thenReturn(1);
        when(planVersionLogMapper.insert(any())).thenReturn(1);

        // getPlanDetail mock
        when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);

        PlanDetailVO result = voluntaryPlanService.reevaluatePlan(1, "userA");

        // version should be incremented
        assertEquals(2, plan.getVersion());
        // 2 snapshots: old version + new version
        verify(planVersionLogMapper, times(2)).insert(any(PlanVersionLog.class));
    }

    // ══════════════════════════════════════════
    // comparePlans
    // ══════════════════════════════════════════

    @Test
    void comparePlans_samePlanVsSelf_emptyDiffs() {
        // 同一个方案和自身对比 → 差异为空
        VoluntaryPlan plan = createPlan("userA");
        plan.setId(1);
        when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);
        when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(Collections.emptyList());

        PlanComparisonVO result = voluntaryPlanService.comparePlans(1, 1, "userA");
        assertNotNull(result);
        assertTrue(result.getDifferences().isEmpty());
    }

    @Test
    void comparePlans_wrongUser_returnsNull() {
        VoluntaryPlan plan = createPlan("userA");
        plan.setId(1);
        when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);

        PlanComparisonVO result = voluntaryPlanService.comparePlans(1, 1, "userB");
        assertNull(result);
    }

    // ══════════════════════════════════════════
    // getPlanDetail
    // ══════════════════════════════════════════

    @Test
    void getPlanDetail_userIsolation_returnsNull() {
        VoluntaryPlan plan = createPlan("userA");
        plan.setId(1);
        when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);

        PlanDetailVO result = voluntaryPlanService.getPlanDetail(1, "userB");
        assertNull(result);
    }

    // ══════════════════════════════════════════
    // 辅助方法
    // ══════════════════════════════════════════

    private List<Map<String, Object>> createCandidateRows(int count) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, Object> row = new HashMap<>();
            row.put("schoolId", i + 1);
            row.put("schoolName", "School" + i);
            row.put("rank2020", 15000 + i * 1000);
            row.put("rank2021", 15500 + i * 1000);
            row.put("rank2022", 16000 + i * 1000);
            row.put("monthView", 500 + i * 100);
            row.put("totalView", "6000");
            row.put("provinceName", "四川");
            row.put("is985", 0);
            row.put("is211", 0);
            row.put("doublehigh", 0);
            rows.add(row);
        }
        return rows;
    }

    private PlanSaveRequestDTO createSaveRequest() {
        PlanSaveRequestDTO request = new PlanSaveRequestDTO();
        request.setUserName("testUser");
        request.setPlanName("测试方案");
        request.setScore(580);
        request.setUserRank(20000);
        request.setSubjectType("理科");

        PlanSchoolDTO dto1 = new PlanSchoolDTO();
        dto1.setSchoolId(3);
        dto1.setSchoolName("四川大学");
        dto1.setCategory("稳");
        dto1.setSortOrder(0);

        PlanSchoolDTO dto2 = new PlanSchoolDTO();
        dto2.setSchoolId(6);
        dto2.setSchoolName("成都理工大学");
        dto2.setCategory("保");
        dto2.setSortOrder(1);

        request.setSchools(List.of(dto1, dto2));
        return request;
    }

    private VoluntaryPlan createPlan(String userName) {
        VoluntaryPlan plan = new VoluntaryPlan();
        plan.setPlanName("测试方案");
        plan.setUserName(userName);
        plan.setScore(580);
        plan.setUserRank(20000);
        plan.setSubjectType("理科");
        plan.setVersion(1);
        plan.setStatus(0);
        return plan;
    }
}
