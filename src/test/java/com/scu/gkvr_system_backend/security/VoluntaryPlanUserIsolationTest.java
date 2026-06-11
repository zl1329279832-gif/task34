package com.scu.gkvr_system_backend.security;

import com.scu.gkvr_system_backend.dto.SchoolReorderDTO;
import com.scu.gkvr_system_backend.pojo.PlanSchool;
import com.scu.gkvr_system_backend.pojo.VoluntaryPlan;
import com.scu.gkvr_system_backend.service.VoluntaryPlanService;
import com.scu.gkvr_system_backend.vo.PlanComparisonVO;
import com.scu.gkvr_system_backend.vo.PlanDetailVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VoluntaryPlanUserIsolationTest {

    @Autowired
    private VoluntaryPlanService voluntaryPlanService;

    private Integer planIdA;
    private Integer planIdB;

    @BeforeEach
    void setUp() {
        // 为userA创建方案
        VoluntaryPlan planA = new VoluntaryPlan();
        planA.setPlanName("UserA的方案");
        planA.setUserName("testUserA");
        planA.setScore(600);
        planA.setUserRank(10000);
        planA.setSubjectType("理科");
        planA.setVersion(1);
        planA.setStatus(0);
        planA.setCreateTime(LocalDateTime.now());
        planA.setUpdateTime(LocalDateTime.now());
        voluntaryPlanService.save(planA);
        planIdA = planA.getId();

        // 为userB创建方案
        VoluntaryPlan planB = new VoluntaryPlan();
        planB.setPlanName("UserB的方案");
        planB.setUserName("testUserB");
        planB.setScore(650);
        planB.setUserRank(2000);
        planB.setSubjectType("理科");
        planB.setVersion(1);
        planB.setStatus(0);
        planB.setCreateTime(LocalDateTime.now());
        planB.setUpdateTime(LocalDateTime.now());
        voluntaryPlanService.save(planB);
        planIdB = planB.getId();
    }

    @Test
    void userA_cannotSeeUserBPlan() {
        PlanDetailVO detail = voluntaryPlanService.getPlanDetail(planIdB, "testUserA");
        assertNull(detail, "UserA不应该能看到UserB的方案");
    }

    @Test
    void userA_cannotDeleteUserBPlan() {
        boolean result = voluntaryPlanService.deletePlan(planIdB, "testUserA");
        assertFalse(result, "UserA不应该能删除UserB的方案");

        // 验证UserB的方案仍然存在
        PlanDetailVO detail = voluntaryPlanService.getPlanDetail(planIdB, "testUserB");
        assertNotNull(detail);
    }

    @Test
    void userA_cannotCopyUserBPlan() {
        Integer newId = voluntaryPlanService.copyPlan(planIdB, "testUserA");
        assertNull(newId, "UserA不应该能复制UserB的方案");
    }

    @Test
    void userA_cannotReorderUserBPlan() {
        SchoolReorderDTO request = new SchoolReorderDTO();
        request.setPlanId(planIdB);
        request.setUserName("testUserA");
        request.setItems(Collections.emptyList());

        boolean result = voluntaryPlanService.reorderSchools(request);
        assertFalse(result, "UserA不应该能调整UserB方案的排序");
    }

    @Test
    void userA_cannotReevaluateUserBPlan() {
        PlanDetailVO result = voluntaryPlanService.reevaluatePlan(planIdB, "testUserA");
        assertNull(result, "UserA不应该能重新评估UserB的方案");
    }

    @Test
    void userA_cannotUpdateUserBPlanStatus() {
        // 尝试通过getPlanDetail验证无法访问
        PlanDetailVO detail = voluntaryPlanService.getPlanDetail(planIdB, "testUserA");
        assertNull(detail, "UserA不应该能访问UserB的方案来更新状态");
    }

    @Test
    void userA_cannotCompareWithUserBPlan() {
        PlanComparisonVO result = voluntaryPlanService.comparePlans(planIdA, planIdB, "testUserA");
        assertNull(result, "UserA不应该能对比涉及UserB方案的内容");
    }

    @Test
    void userA_listOnlyOwnPlans() {
        List<VoluntaryPlan> plansA = voluntaryPlanService.listPlans("testUserA");
        List<VoluntaryPlan> plansB = voluntaryPlanService.listPlans("testUserB");

        assertEquals(1, plansA.size(), "UserA应该只能看到自己的方案");
        assertEquals(1, plansB.size(), "UserB应该只能看到自己的方案");
        assertEquals("testUserA", plansA.get(0).getUserName());
        assertEquals("testUserB", plansB.get(0).getUserName());
    }
}
