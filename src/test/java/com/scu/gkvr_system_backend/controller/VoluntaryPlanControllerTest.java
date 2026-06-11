package com.scu.gkvr_system_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scu.gkvr_system_backend.dto.PlanGenerateRequestDTO;
import com.scu.gkvr_system_backend.dto.PlanSaveRequestDTO;
import com.scu.gkvr_system_backend.dto.PlanSchoolDTO;
import com.scu.gkvr_system_backend.dto.SchoolReorderDTO;
import com.scu.gkvr_system_backend.service.VoluntaryPlanService;
import com.scu.gkvr_system_backend.vo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * VoluntaryPlanController 单元测试(MockMvc standalone)
 */
@ExtendWith(MockitoExtension.class)
class VoluntaryPlanControllerTest {

    private MockMvc mockMvc;

    @Mock
    private VoluntaryPlanService voluntaryPlanService;

    @InjectMocks
    private VoluntaryPlanController voluntaryPlanController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(voluntaryPlanController).build();
    }

    // ══════════════════════════════════════════
    // POST /voluntaryPlan/generate
    // ══════════════════════════════════════════

    @Test
    void generate_endpoint_success() throws Exception {
        PlanGenerateRequestDTO request = new PlanGenerateRequestDTO();
        request.setUserName("testUser");
        request.setScore(580);
        request.setUserRank(20000);
        request.setSubjectType("理科");

        Map<String, Object> data = new HashMap<>();
        data.put("reach", Collections.emptyList());
        data.put("match", Collections.emptyList());
        data.put("safety", Collections.emptyList());
        when(voluntaryPlanService.generatePlans(any())).thenReturn(data);

        mockMvc.perform(post("/voluntaryPlan/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.reach").isArray());
    }

    @Test
    void generate_validationError_missingScore() throws Exception {
        PlanGenerateRequestDTO request = new PlanGenerateRequestDTO();
        request.setUserName("testUser");
        // score is null
        request.setUserRank(20000);
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
        request.setScore(800); // > 750
        request.setUserRank(20000);
        request.setSubjectType("理科");

        mockMvc.perform(post("/voluntaryPlan/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generate_validationError_missingUserName() throws Exception {
        PlanGenerateRequestDTO request = new PlanGenerateRequestDTO();
        // userName is blank
        request.setScore(580);
        request.setUserRank(20000);
        request.setSubjectType("理科");

        mockMvc.perform(post("/voluntaryPlan/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ══════════════════════════════════════════
    // POST /voluntaryPlan/save
    // ══════════════════════════════════════════

    @Test
    void save_endpoint_success() throws Exception {
        PlanSaveRequestDTO request = createSaveRequest();
        when(voluntaryPlanService.savePlan(any())).thenReturn(1);

        mockMvc.perform(post("/voluntaryPlan/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.planId").value(1));
    }

    @Test
    void save_emptyPlanName_validationError() throws Exception {
        PlanSaveRequestDTO request = createSaveRequest();
        request.setPlanName("");

        mockMvc.perform(post("/voluntaryPlan/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ══════════════════════════════════════════
    // GET /voluntaryPlan/detail
    // ══════════════════════════════════════════

    @Test
    void getDetail_success() throws Exception {
        PlanDetailVO detail = new PlanDetailVO();
        detail.setId(1);
        detail.setPlanName("测试方案");
        detail.setUserName("testUser");
        detail.setReachSchools(Collections.emptyList());
        detail.setMatchSchools(Collections.emptyList());
        detail.setSafetySchools(Collections.emptyList());

        when(voluntaryPlanService.getPlanDetail(1, "testUser")).thenReturn(detail);

        mockMvc.perform(get("/voluntaryPlan/detail")
                        .param("planId", "1")
                        .param("userName", "testUser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.planName").value("测试方案"));
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

    @Test
    void getDetail_notFound() throws Exception {
        when(voluntaryPlanService.getPlanDetail(999, "testUser")).thenReturn(null);

        mockMvc.perform(get("/voluntaryPlan/detail")
                        .param("planId", "999")
                        .param("userName", "testUser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201));
    }

    // ══════════════════════════════════════════
    // GET /voluntaryPlan/list
    // ══════════════════════════════════════════

    @Test
    void listPlans_success() throws Exception {
        when(voluntaryPlanService.listUserPlans("testUser"))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/voluntaryPlan/list")
                        .param("userName", "testUser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    // ══════════════════════════════════════════
    // POST /voluntaryPlan/reorder
    // ══════════════════════════════════════════

    @Test
    void reorder_success() throws Exception {
        SchoolReorderDTO dto = new SchoolReorderDTO();
        dto.setPlanId(1);
        dto.setUserName("testUser");
        SchoolReorderDTO.SchoolOrderItem item = new SchoolReorderDTO.SchoolOrderItem();
        item.setPlanSchoolId(10);
        item.setSortOrder(0);
        dto.setOrderedSchools(List.of(item));

        when(voluntaryPlanService.reorderSchools(any())).thenReturn(true);

        mockMvc.perform(post("/voluntaryPlan/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ══════════════════════════════════════════
    // POST /voluntaryPlan/delete
    // ══════════════════════════════════════════

    @Test
    void delete_success() throws Exception {
        when(voluntaryPlanService.deletePlan(1, "testUser")).thenReturn(true);

        mockMvc.perform(post("/voluntaryPlan/delete")
                        .param("planId", "1")
                        .param("userName", "testUser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ══════════════════════════════════════════
    // POST /voluntaryPlan/copy
    // ══════════════════════════════════════════

    @Test
    void copy_success() throws Exception {
        when(voluntaryPlanService.copyPlan(1, "testUser", "新方案名")).thenReturn(2);

        mockMvc.perform(post("/voluntaryPlan/copy")
                        .param("planId", "1")
                        .param("userName", "testUser")
                        .param("newPlanName", "新方案名"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.planId").value(2));
    }

    // ══════════════════════════════════════════
    // GET /voluntaryPlan/compare
    // ══════════════════════════════════════════

    @Test
    void compare_success() throws Exception {
        PlanComparisonVO comparison = new PlanComparisonVO();
        comparison.setSummary("对比结果");
        comparison.setDifferences(Collections.emptyList());

        when(voluntaryPlanService.comparePlans(1, 2, "testUser")).thenReturn(comparison);

        mockMvc.perform(get("/voluntaryPlan/compare")
                        .param("planIdA", "1")
                        .param("planIdB", "2")
                        .param("userName", "testUser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.summary").value("对比结果"));
    }

    // ══════════════════════════════════════════
    // POST /voluntaryPlan/reevaluate
    // ══════════════════════════════════════════

    @Test
    void reevaluate_success() throws Exception {
        PlanDetailVO detail = new PlanDetailVO();
        detail.setId(1);
        detail.setVersion(2);
        detail.setReachSchools(Collections.emptyList());
        detail.setMatchSchools(Collections.emptyList());
        detail.setSafetySchools(Collections.emptyList());

        when(voluntaryPlanService.reevaluatePlan(1, "testUser")).thenReturn(detail);

        mockMvc.perform(post("/voluntaryPlan/reevaluate")
                        .param("planId", "1")
                        .param("userName", "testUser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.version").value(2));
    }

    // ══════════════════════════════════════════
    // 辅助方法
    // ══════════════════════════════════════════

    private PlanSaveRequestDTO createSaveRequest() {
        PlanSaveRequestDTO request = new PlanSaveRequestDTO();
        request.setUserName("testUser");
        request.setPlanName("测试方案");
        request.setScore(580);
        request.setUserRank(20000);
        request.setSubjectType("理科");

        PlanSchoolDTO dto = new PlanSchoolDTO();
        dto.setSchoolId(3);
        dto.setSchoolName("四川大学");
        dto.setCategory("稳");
        dto.setSortOrder(0);
        request.setSchools(List.of(dto));
        return request;
    }
}
