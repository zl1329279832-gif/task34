package com.scu.gkvr_system_backend.security;

import com.scu.gkvr_system_backend.dto.SchoolReorderDTO;
import com.scu.gkvr_system_backend.mapper.PlanSchoolMapper;
import com.scu.gkvr_system_backend.mapper.PlanVersionLogMapper;
import com.scu.gkvr_system_backend.mapper.VoluntaryPlanMapper;
import com.scu.gkvr_system_backend.pojo.PlanSchool;
import com.scu.gkvr_system_backend.pojo.VoluntaryPlan;
import com.scu.gkvr_system_backend.service.VoluntaryPlanService;
import com.scu.gkvr_system_backend.vo.PlanComparisonVO;
import com.scu.gkvr_system_backend.vo.PlanDetailVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.scu.gkvr_system_backend.service.impl.VoluntaryPlanServiceImpl;
import com.scu.gkvr_system_backend.mapper.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 用户隔离测试: 确保用户A无法操作用户B的方案
 */
@ExtendWith(MockitoExtension.class)
class VoluntaryPlanUserIsolationTest {

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

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(voluntaryPlanService, "baseMapper", voluntaryPlanMapper);
    }

    private VoluntaryPlan createPlanForUserB() {
        VoluntaryPlan plan = new VoluntaryPlan();
        plan.setId(100);
        plan.setPlanName("UserB的方案");
        plan.setUserName("userB");
        plan.setScore(600);
        plan.setUserRank(10000);
        plan.setSubjectType("理科");
        plan.setVersion(1);
        plan.setStatus(0);
        return plan;
    }

    @Test
    void userA_cannotSeeUserBPlan() {
        when(voluntaryPlanMapper.selectById(100)).thenReturn(createPlanForUserB());

        PlanDetailVO result = voluntaryPlanService.getPlanDetail(100, "userA");
        assertNull(result, "用户A不应能看到用户B的方案");
    }

    @Test
    void userA_cannotDeleteUserBPlan() {
        when(voluntaryPlanMapper.selectById(100)).thenReturn(createPlanForUserB());

        Boolean result = voluntaryPlanService.deletePlan(100, "userA");
        assertFalse(result, "用户A不应能删除用户B的方案");

        // 确保没有执行实际删除
        verify(voluntaryPlanMapper, never()).deleteById(anyInt());
    }

    @Test
    void userA_cannotCopyUserBPlan() {
        when(voluntaryPlanMapper.selectById(100)).thenReturn(createPlanForUserB());

        Integer result = voluntaryPlanService.copyPlan(100, "userA", "恶意复制");
        assertNull(result, "用户A不应能复制用户B的方案");

        verify(voluntaryPlanMapper, never()).insert(any());
    }

    @Test
    void userA_cannotReorderUserBPlan() {
        when(voluntaryPlanMapper.selectById(100)).thenReturn(createPlanForUserB());

        SchoolReorderDTO dto = new SchoolReorderDTO();
        dto.setPlanId(100);
        dto.setUserName("userA");
        SchoolReorderDTO.SchoolOrderItem item = new SchoolReorderDTO.SchoolOrderItem();
        item.setPlanSchoolId(1);
        item.setSortOrder(0);
        dto.setOrderedSchools(List.of(item));

        Boolean result = voluntaryPlanService.reorderSchools(dto);
        assertFalse(result, "用户A不应能重排用户B的方案院校");

        verify(planSchoolMapper, never()).updateSortOrder(anyInt(), anyInt());
    }

    @Test
    void userA_cannotReevaluateUserBPlan() {
        when(voluntaryPlanMapper.selectById(100)).thenReturn(createPlanForUserB());

        PlanDetailVO result = voluntaryPlanService.reevaluatePlan(100, "userA");
        assertNull(result, "用户A不应能重新评估用户B的方案");
    }

    @Test
    void userA_cannotCompareWithUserBPlan() {
        // planA属于userA, planB属于userB
        VoluntaryPlan planA = createPlanForUserB();
        planA.setId(200);
        planA.setUserName("userA");

        when(voluntaryPlanMapper.selectById(200)).thenReturn(planA);
        when(planSchoolMapper.selectByPlanIdOrdered(200)).thenReturn(Collections.emptyList());

        when(voluntaryPlanMapper.selectById(100)).thenReturn(createPlanForUserB());

        // userA尝试对比自己的方案和userB的方案
        PlanComparisonVO result = voluntaryPlanService.comparePlans(200, 100, "userA");
        assertNull(result, "用户A不应能在对比中包含用户B的方案");
    }

    @Test
    void userA_listOnlyOwnPlans() {
        // 验证listUserPlans只传userName, 由SQL过滤
        VoluntaryPlan userAPlan = new VoluntaryPlan();
        userAPlan.setUserName("userA");
        when(voluntaryPlanMapper.selectRealPlansByUserName("userA"))
                .thenReturn(List.of(userAPlan));

        List<VoluntaryPlan> result = voluntaryPlanService.listUserPlans("userA");
        assertEquals(1, result.size());
        assertEquals("userA", result.get(0).getUserName());

        // 验证没有查询userB的数据
        verify(voluntaryPlanMapper).selectRealPlansByUserName("userA");
        verify(voluntaryPlanMapper, never()).selectRealPlansByUserName("userB");
    }

    @Test
    void userA_cannotUpdateUserBPlanStatus() {
        when(voluntaryPlanMapper.selectById(100)).thenReturn(createPlanForUserB());

        Boolean result = voluntaryPlanService.updatePlanStatus(100, "userA", 1);
        assertFalse(result, "用户A不应能修改用户B的方案状态");

        verify(voluntaryPlanMapper, never()).updateById(any());
    }
}
