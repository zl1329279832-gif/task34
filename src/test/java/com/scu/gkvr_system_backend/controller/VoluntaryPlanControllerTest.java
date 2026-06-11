package com.scu.gkvr_system_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scu.gkvr_system_backend.dto.PlanGenerateRequestDTO;
import com.scu.gkvr_system_backend.dto.PlanSaveRequestDTO;
import com.scu.gkvr_system_backend.dto.PlanSchoolDTO;
import com.scu.gkvr_system_backend.dto.SchoolReorderDTO;
import com.scu.gkvr_system_backend.pojo.VoluntaryPlan;
import com.scu.gkvr_system_backend.service.VoluntaryPlanService;
import com.scu.gkvr_system_backend.vo.PlanComparisonVO;
import com.scu.gkvr_system_backend.vo.PlanDetailVO;
import com.scu.gkvr_system_backend.vo.PlanRiskSummary;
import com.scu.gkvr_system_backend.vo.PlanSchoolVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VoluntaryPlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VoluntaryPlanService voluntaryPlanService;

    @Autowired
    private ObjectMapper objectMapper;

    // ===== generate =====

    @Test
    void generate_success() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("reachSchools", Collections.emptyList());
        data.put("matchSchools", Collections.emptyList());
        data.put("safetySchools", Collections.emptyList());
        data.put("riskSummary", new PlanRiskSummary());
        when(voluntaryPlanService.generatePlans(any())).thenReturn(data);

        PlanGenerateRequestDTO request = new PlanGenerateRequestDTO();
        request.setUserName("testUser");
        request.setScore(600);
        request.setSubjectType("理科");

        mockMvc.perform(post("/voluntaryPlan/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void generate_validationError_missingUserName() throws Exception {
        PlanGenerateRequestDTO request = new PlanGenerateRequestDTO();
        request.setScore(600);
        request.setSubjectType("理科");

        mockMvc.perform(post("/voluntaryPlan/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generate_validationError_missingScore() throws Exception {
        PlanGenerateRequestDTO request = new PlanGenerateRequestDTO();
        request.setUserName("testUser");
        request.setSubjectType("理科");

        mockMvc.perform(post("/voluntaryPlan/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generate_validationError_scoreOutOfRange() throws Exception {
        PlanGenerateRequestDTO request = new PlanGenerateRequestDTO();
        request.setUserName("testUser");
        request.setScore(800);
        request.setSubjectType("理科");

        mockMvc.perform(post("/voluntaryPlan/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ===== save =====

    @Test
    void save_success() throws Exception {
        when(voluntaryPlanService.savePlan(any())).thenReturn(1);

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
        request.setSchools(List.of(school));

        mockMvc.perform(post("/voluntaryPlan/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.planId").value(1));
    }

    @Test
    void save_emptyPlanName_validationError() throws Exception {
        PlanSaveRequestDTO request = new PlanSaveRequestDTO();
        request.setPlanName("");
        request.setUserName("testUser");
        request.setScore(600);
        request.setUserRank(10000);
        request.setSubjectType("理科");
        PlanSchoolDTO school = new PlanSchoolDTO();
        school.setSchoolId(3);
        school.setSchoolName("四川大学");
        school.setCategory("稳");
        request.setSchools(List.of(school));

        mockMvc.perform(post("/voluntaryPlan/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ===== detail =====

    @Test
    void getDetail_success() throws Exception {
        PlanDetailVO detail = new PlanDetailVO();
        detail.setPlanId(1);
        detail.setPlanName("测试方案");
        detail.setReachSchools(Collections.emptyList());
        detail.setMatchSchools(Collections.emptyList());
        detail.setSafetySchools(Collections.emptyList());
        when(voluntaryPlanService.getPlanDetail(1, "testUser")).thenReturn(detail);

        mockMvc.perform(get("/voluntaryPlan/detail")
                        .param("planId", "1")
                        .param("userName", "testUser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.plan.planId").value(1));
    }

    @Test
    void getDetail_notFound() throws Exception {
        when(voluntaryPlanService.getPlanDetail(999, "testUser")).thenReturn(null);

        mockMvc.perform(get("/voluntaryPlan/detail")
                        .param("planId", "999")
                        .param("userName", "testUser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201));
    }

    @Test
    void getDetail_wrongUser_returnsFail() throws Exception {
        when(voluntaryPlanService.getPlanDetail(1, "wrongUser")).thenReturn(null);

        mockMvc.perform(get("/voluntaryPlan/detail")
                        .param("planId", "1")
                        .param("userName", "wrongUser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201));
    }

    // ===== list =====

    @Test
    void list_success() throws Exception {
        when(voluntaryPlanService.listPlans("testUser")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/voluntaryPlan/list")
                        .param("userName", "testUser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.plans").isArray());
    }

    // ===== delete =====

    @Test
    void delete_success() throws Exception {
        when(voluntaryPlanService.deletePlan(1, "testUser")).thenReturn(true);

        mockMvc.perform(post("/voluntaryPlan/delete")
                        .param("planId", "1")
                        .param("userName", "testUser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ===== copy =====

    @Test
    void copy_success() throws Exception {
        when(voluntaryPlanService.copyPlan(1, "testUser")).thenReturn(2);

        mockMvc.perform(post("/voluntaryPlan/copy")
                        .param("planId", "1")
                        .param("userName", "testUser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.planId").value(2));
    }

    // ===== reorder =====

    @Test
    void reorder_success() throws Exception {
        when(voluntaryPlanService.reorderSchools(any())).thenReturn(true);

        SchoolReorderDTO request = new SchoolReorderDTO();
        request.setPlanId(1);
        request.setUserName("testUser");
        SchoolReorderDTO.SchoolOrderItem item = new SchoolReorderDTO.SchoolOrderItem();
        item.setPlanSchoolId(10);
        item.setSortOrder(1);
        request.setItems(List.of(item));

        mockMvc.perform(post("/voluntaryPlan/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ===== reevaluate =====

    @Test
    void reevaluate_success() throws Exception {
        PlanDetailVO detail = new PlanDetailVO();
        detail.setPlanId(1);
        detail.setVersion(2);
        detail.setReachSchools(Collections.emptyList());
        detail.setMatchSchools(Collections.emptyList());
        detail.setSafetySchools(Collections.emptyList());
        when(voluntaryPlanService.reevaluatePlan(1, "testUser")).thenReturn(detail);

        mockMvc.perform(post("/voluntaryPlan/reevaluate")
                        .param("planId", "1")
                        .param("userName", "testUser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ===== compare =====

    @Test
    void compare_success() throws Exception {
        PlanComparisonVO comparison = new PlanComparisonVO();
        comparison.setPlanIdA(1);
        comparison.setPlanIdB(2);
        comparison.setDiffs(Collections.emptyList());
        when(voluntaryPlanService.comparePlans(1, 2, "testUser")).thenReturn(comparison);

        mockMvc.perform(get("/voluntaryPlan/compare")
                        .param("planIdA", "1")
                        .param("planIdB", "2")
                        .param("userName", "testUser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
