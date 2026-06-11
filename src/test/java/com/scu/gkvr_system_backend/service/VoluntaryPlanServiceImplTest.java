package com.scu.gkvr_system_backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scu.gkvr_system_backend.dto.PlanGenerateRequestDTO;
import com.scu.gkvr_system_backend.dto.PlanSaveRequestDTO;
import com.scu.gkvr_system_backend.dto.PlanSchoolDTO;
import com.scu.gkvr_system_backend.dto.SchoolReorderDTO;
import com.scu.gkvr_system_backend.mapper.PlanSchoolMapper;
import com.scu.gkvr_system_backend.mapper.PlanVersionLogMapper;
import com.scu.gkvr_system_backend.mapper.ScoreRankMapper;
import com.scu.gkvr_system_backend.mapper.VoluntaryPlanMapper;
import com.scu.gkvr_system_backend.pojo.PlanSchool;
import com.scu.gkvr_system_backend.pojo.ScoreRank;
import com.scu.gkvr_system_backend.pojo.VoluntaryPlan;
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
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoluntaryPlanServiceImplTest {

    @Mock
    private VoluntaryPlanMapper voluntaryPlanMapper;

    @Mock
    private PlanSchoolMapper planSchoolMapper;

    @Mock
    private PlanVersionLogMapper planVersionLogMapper;

    @Mock
    private ScoreRankMapper scoreRankMapper;

    @InjectMocks
    private VoluntaryPlanServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", voluntaryPlanMapper);
    }

    // ===== generatePlans =====

    @Test
    void generatePlans_normalScore_returnsAllCategories() {
        PlanGenerateRequestDTO request = new PlanGenerateRequestDTO();
        request.setUserName("testUser");
        request.setScore(600);
        request.setSubjectType("理科");

        ScoreRank sr = new ScoreRank();
        sr.setScore("600");
        sr.setRank(10000);
        sr.setBatchName("本科一批");
        when(scoreRankMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(sr);
        when(planSchoolMapper.selectCandidateSchools(anyInt(), anyInt(), any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(new ArrayList<>());

        Map<String, Object> result = service.generatePlans(request);

        assertNotNull(result.get("reachSchools"));
        assertNotNull(result.get("matchSchools"));
        assertNotNull(result.get("safetySchools"));
        assertNotNull(result.get("riskSummary"));
        assertFalse(result.containsKey("error"));
    }

    @Test
    void generatePlans_noMatchingSchools_emptyLists() {
        PlanGenerateRequestDTO request = new PlanGenerateRequestDTO();
        request.setUserName("testUser");
        request.setScore(600);
        request.setSubjectType("理科");

        ScoreRank sr = new ScoreRank();
        sr.setScore("600");
        sr.setRank(10000);
        sr.setBatchName("本科一批");
        when(scoreRankMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(sr);
        when(planSchoolMapper.selectCandidateSchools(anyInt(), anyInt(), any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(Collections.emptyList());

        Map<String, Object> result = service.generatePlans(request);

        assertTrue(((List<?>) result.get("reachSchools")).isEmpty());
        assertTrue(((List<?>) result.get("matchSchools")).isEmpty());
        assertTrue(((List<?>) result.get("safetySchools")).isEmpty());
    }

    @Test
    void generatePlans_withFilters_parsedCorrectly() {
        PlanGenerateRequestDTO request = new PlanGenerateRequestDTO();
        request.setUserName("testUser");
        request.setScore(600);
        request.setSubjectType("理科");
        request.setSchoolTier("985");
        request.setRegionPref(List.of("四川", "北京"));

        ScoreRank sr = new ScoreRank();
        sr.setScore("600");
        sr.setRank(10000);
        sr.setBatchName("本科一批");
        when(scoreRankMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(sr);
        when(planSchoolMapper.selectCandidateSchools(anyInt(), anyInt(), any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(new ArrayList<>());

        service.generatePlans(request);

        verify(planSchoolMapper, times(3)).selectCandidateSchools(
                anyInt(), anyInt(), eq(List.of("四川", "北京")), eq(true), eq(false), eq(false));
    }

    @Test
    void generatePlans_scoreNotFound_returnsError() {
        PlanGenerateRequestDTO request = new PlanGenerateRequestDTO();
        request.setUserName("testUser");
        request.setScore(999);
        request.setSubjectType("理科");

        when(scoreRankMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        Map<String, Object> result = service.generatePlans(request);
        assertTrue(result.containsKey("error"));
    }

    @Test
    void generatePlans_withUserRank_usesProvided() {
        PlanGenerateRequestDTO request = new PlanGenerateRequestDTO();
        request.setUserName("testUser");
        request.setScore(600);
        request.setUserRank(8000);
        request.setSubjectType("理科");

        ScoreRank sr = new ScoreRank();
        sr.setScore("600");
        sr.setRank(10000);
        sr.setBatchName("本科一批");
        when(scoreRankMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(sr);
        when(planSchoolMapper.selectCandidateSchools(anyInt(), anyInt(), any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(new ArrayList<>());

        Map<String, Object> result = service.generatePlans(request);
        assertEquals(8000, result.get("userRank"));
    }

    // ===== savePlan =====

    @Test
    void savePlan_validInput_returnsPlanId() {
        PlanSaveRequestDTO request = new PlanSaveRequestDTO();
        request.setPlanName("我的方案");
        request.setUserName("testUser");
        request.setScore(600);
        request.setUserRank(10000);
        request.setSubjectType("理科");

        PlanSchoolDTO school = new PlanSchoolDTO();
        school.setSchoolId(3);
        school.setSchoolName("四川大学");
        school.setCategory("稳");
        school.setAdmissionProb(BigDecimal.valueOf(55));
        school.setMajorAdjustRisk(BigDecimal.valueOf(30));
        request.setSchools(List.of(school));

        when(voluntaryPlanMapper.insert(any(VoluntaryPlan.class))).thenAnswer(inv -> {
            VoluntaryPlan p = inv.getArgument(0);
            p.setId(1);
            return 1;
        });
        when(planSchoolMapper.insert(any(PlanSchool.class))).thenReturn(1);
        when(planSchoolMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(voluntaryPlanMapper.updateById(any(VoluntaryPlan.class))).thenReturn(1);

        Integer planId = service.savePlan(request);
        assertEquals(1, planId);
    }

    // ===== getPlanDetail =====

    @Test
    void getPlanDetail_userIsolation_returnsNull() {
        when(voluntaryPlanMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        PlanDetailVO result = service.getPlanDetail(1, "wrongUser");
        assertNull(result);
    }

    // ===== deletePlan =====

    @Test
    void deletePlan_success() {
        VoluntaryPlan plan = createTestPlan(1, "testUser");
        when(voluntaryPlanMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(plan);
        when(planSchoolMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(0);
        when(planVersionLogMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(0);
        when(voluntaryPlanMapper.deleteById(1)).thenReturn(1);

        assertTrue(service.deletePlan(1, "testUser"));
    }

    @Test
    void deletePlan_wrongUser_returnsFalse() {
        when(voluntaryPlanMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertFalse(service.deletePlan(1, "wrongUser"));
    }

    // ===== copyPlan =====

    @Test
    void copyPlan_success_returnsNewId() {
        VoluntaryPlan source = createTestPlan(1, "testUser");
        when(voluntaryPlanMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(source);
        when(voluntaryPlanMapper.insert(any(VoluntaryPlan.class))).thenAnswer(inv -> {
            VoluntaryPlan p = inv.getArgument(0);
            p.setId(2);
            return 1;
        });
        when(planSchoolMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        Integer newId = service.copyPlan(1, "testUser");
        assertEquals(2, newId);
    }

    @Test
    void copyPlan_wrongUser_returnsNull() {
        when(voluntaryPlanMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertNull(service.copyPlan(1, "wrongUser"));
    }

    // ===== reorderSchools =====

    @Test
    void reorderSchools_success() {
        VoluntaryPlan plan = createTestPlan(1, "testUser");
        when(voluntaryPlanMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(plan);

        PlanSchool ps = new PlanSchool();
        ps.setId(10);
        ps.setPlanId(1);
        ps.setSortOrder(0);
        when(planSchoolMapper.selectById(10)).thenReturn(ps);
        when(planSchoolMapper.updateById(any(PlanSchool.class))).thenReturn(1);
        when(voluntaryPlanMapper.updateById(any(VoluntaryPlan.class))).thenReturn(1);

        SchoolReorderDTO request = new SchoolReorderDTO();
        request.setPlanId(1);
        request.setUserName("testUser");
        SchoolReorderDTO.SchoolOrderItem item = new SchoolReorderDTO.SchoolOrderItem();
        item.setPlanSchoolId(10);
        item.setSortOrder(5);
        request.setItems(List.of(item));

        assertTrue(service.reorderSchools(request));
    }

    @Test
    void reorderSchools_wrongUser_returnsFalse() {
        when(voluntaryPlanMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        SchoolReorderDTO request = new SchoolReorderDTO();
        request.setPlanId(1);
        request.setUserName("wrongUser");
        request.setItems(Collections.emptyList());

        assertFalse(service.reorderSchools(request));
    }

    // ===== reevaluatePlan =====

    @Test
    void reevaluatePlan_versionIncremented() {
        VoluntaryPlan plan = createTestPlan(1, "testUser");
        plan.setVersion(1);
        when(voluntaryPlanMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(plan);
        when(planSchoolMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(planVersionLogMapper.insert(any())).thenReturn(1);
        when(voluntaryPlanMapper.updateById(any(VoluntaryPlan.class))).thenReturn(1);

        PlanDetailVO result = service.reevaluatePlan(1, "testUser");
        assertNotNull(result);
        assertEquals(2, plan.getVersion());
    }

    // ===== comparePlans =====

    @Test
    void comparePlans_wrongUser_returnsNull() {
        when(voluntaryPlanMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertNull(service.comparePlans(1, 2, "wrongUser"));
    }

    @Test
    void comparePlans_samePlanVsSelf_noDiffs() {
        VoluntaryPlan plan = createTestPlan(1, "testUser");
        plan.setTotalRiskScore(BigDecimal.valueOf(40));
        // selectOne will be called twice with different queries
        when(voluntaryPlanMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(plan);
        when(planSchoolMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        PlanComparisonVO result = service.comparePlans(1, 1, "testUser");
        assertNotNull(result);
        assertTrue(result.getDiffs().isEmpty());
    }

    // ===== Helpers =====

    private VoluntaryPlan createTestPlan(int id, String userName) {
        VoluntaryPlan plan = new VoluntaryPlan();
        plan.setId(id);
        plan.setPlanName("测试方案");
        plan.setUserName(userName);
        plan.setScore(600);
        plan.setUserRank(10000);
        plan.setSubjectType("理科");
        plan.setVersion(1);
        plan.setStatus(0);
        plan.setCreateTime(LocalDateTime.now());
        plan.setUpdateTime(LocalDateTime.now());
        return plan;
    }
}
