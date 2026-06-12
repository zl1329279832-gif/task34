package com.scu.gkvr_system_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scu.gkvr_system_backend.dto.SimulationBatchRequestDTO;
import com.scu.gkvr_system_backend.dto.SimulationVariantDTO;
import com.scu.gkvr_system_backend.service.SimulationService;
import com.scu.gkvr_system_backend.vo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * SimulationController 单元测试(MockMvc standalone)
 */
@ExtendWith(MockitoExtension.class)
class SimulationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SimulationService simulationService;

    @InjectMocks
    private SimulationController simulationController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(simulationController).build();
    }

    @Nested
    @DisplayName("批量模拟接口测试")
    class BatchEndpointTests {

        @Test
        @DisplayName("POST /batch 成功返回200")
        void submitBatch_success() throws Exception {
            SimulationBatchRequestDTO request = buildBatchRequest();
            when(simulationService.submitBatchSimulation(any())).thenReturn(1);

            mockMvc.perform(post("/simulation/batch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.batchTaskId").value(1));
        }

        @Test
        @DisplayName("POST /batch 校验失败返回400(空变体)")
        void submitBatch_emptyVariants_validationError() throws Exception {
            SimulationBatchRequestDTO request = buildBatchRequest();
            request.setVariants(Collections.emptyList());

            mockMvc.perform(post("/simulation/batch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST /batch 校验失败返回400(空用户名)")
        void submitBatch_emptyUserName_validationError() throws Exception {
            SimulationBatchRequestDTO request = buildBatchRequest();
            request.setUserName("");

            mockMvc.perform(post("/simulation/batch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST /batch 频率限制返回201")
        void submitBatch_rateLimited_returnsFail() throws Exception {
            SimulationBatchRequestDTO request = buildBatchRequest();
            when(simulationService.submitBatchSimulation(any()))
                    .thenThrow(new IllegalStateException("最多同时运行3个批量模拟"));

            mockMvc.perform(post("/simulation/batch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(201));
        }

        @Test
        @DisplayName("POST /batch 权限不足返回201")
        void submitBatch_wrongUser_returnsFail() throws Exception {
            SimulationBatchRequestDTO request = buildBatchRequest();
            when(simulationService.submitBatchSimulation(any()))
                    .thenThrow(new IllegalArgumentException("方案不存在或无权限"));

            mockMvc.perform(post("/simulation/batch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(201));
        }
    }

    @Nested
    @DisplayName("状态查询接口测试")
    class StatusEndpointTests {

        @Test
        @DisplayName("GET /status 成功返回200")
        void getStatus_success() throws Exception {
            SimulationBatchStatusVO status = new SimulationBatchStatusVO();
            status.setBatchTaskId(1);
            status.setStatus("completed");
            status.setProgressPercent(BigDecimal.valueOf(100));
            status.setVariants(Collections.emptyList());

            when(simulationService.getBatchStatus(1, "testUser")).thenReturn(status);

            mockMvc.perform(get("/simulation/status/1")
                            .param("userName", "testUser"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.status").value("completed"));
        }

        @Test
        @DisplayName("GET /status 错误用户返回201")
        void getStatus_wrongUser_returnsFail() throws Exception {
            when(simulationService.getBatchStatus(1, "wrongUser")).thenReturn(null);

            mockMvc.perform(get("/simulation/status/1")
                            .param("userName", "wrongUser"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(201));
        }
    }

    @Nested
    @DisplayName("概率解释接口测试")
    class ExplanationEndpointTests {

        @Test
        @DisplayName("GET /explanation 成功返回200")
        void getExplanation_success() throws Exception {
            SimulationExplanationVO exp = new SimulationExplanationVO();
            exp.setSimulationId(1);
            exp.setVariantName("测试变体");
            exp.setSchoolBreakdowns(Collections.emptyList());

            when(simulationService.getExplanation(1, "testUser")).thenReturn(exp);

            mockMvc.perform(get("/simulation/explanation/1")
                            .param("userName", "testUser"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.variantName").value("测试变体"));
        }

        @Test
        @DisplayName("GET /explanation 未完成返回201")
        void getExplanation_pending_returnsFail() throws Exception {
            when(simulationService.getExplanation(1, "testUser")).thenReturn(null);

            mockMvc.perform(get("/simulation/explanation/1")
                            .param("userName", "testUser"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(201));
        }
    }

    @Nested
    @DisplayName("提升与回滚接口测试")
    class PromoteAndRollbackTests {

        @Test
        @DisplayName("POST /promote 成功返回200")
        void promote_success() throws Exception {
            when(simulationService.promoteToPlan(1, "testUser", "正式方案")).thenReturn(10);

            mockMvc.perform(post("/simulation/promote")
                            .param("simulationVariantId", "1")
                            .param("userName", "testUser")
                            .param("newPlanName", "正式方案"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.planId").value(10));
        }

        @Test
        @DisplayName("POST /promote 失败返回201")
        void promote_fail_returnsFail() throws Exception {
            when(simulationService.promoteToPlan(1, "wrongUser", "正式方案")).thenReturn(null);

            mockMvc.perform(post("/simulation/promote")
                            .param("simulationVariantId", "1")
                            .param("userName", "wrongUser")
                            .param("newPlanName", "正式方案"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(201));
        }

        @Test
        @DisplayName("POST /rollback 成功返回200")
        void rollback_success() throws Exception {
            when(simulationService.rollbackToVersion(1, 2, "testUser")).thenReturn(5);

            mockMvc.perform(post("/simulation/rollback")
                            .param("planId", "1")
                            .param("targetVersion", "2")
                            .param("userName", "testUser"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.simulationVariantId").value(5));
        }
    }

    @Nested
    @DisplayName("对比接口测试")
    class CompareEndpointTests {

        @Test
        @DisplayName("GET /compare 成功返回200")
        void compare_success() throws Exception {
            SimulationCompareVO vo = new SimulationCompareVO();
            vo.setSummary("对比结果");
            vo.setDifferences(Collections.emptyList());

            when(simulationService.compareSimulations(any())).thenReturn(vo);

            mockMvc.perform(get("/simulation/compare")
                            .param("planIdA", "1")
                            .param("planIdB", "2")
                            .param("userName", "testUser"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.summary").value("对比结果"));
        }

        @Test
        @DisplayName("GET /compare 失败返回201")
        void compare_fail_returnsFail() throws Exception {
            when(simulationService.compareSimulations(any())).thenReturn(null);

            mockMvc.perform(get("/simulation/compare")
                            .param("planIdA", "1")
                            .param("planIdB", "999")
                            .param("userName", "testUser"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(201));
        }
    }

    // ── 辅助方法 ──

    private SimulationBatchRequestDTO buildBatchRequest() {
        SimulationBatchRequestDTO request = new SimulationBatchRequestDTO();
        request.setUserName("testUser");
        request.setBasePlanId(1);

        SimulationVariantDTO variant = new SimulationVariantDTO();
        variant.setVariantName("分数+20");
        variant.setScoreOverride(600);
        request.setVariants(List.of(variant));

        return request;
    }
}
