package com.scu.gkvr_system_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scu.gkvr_system_backend.dto.SimulationBatchRequestDTO;
import com.scu.gkvr_system_backend.dto.SimulationCompareRequestDTO;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("模拟批处理控制器测试")
class SimulationControllerTest {

    @InjectMocks
    private SimulationController simulationController;

    @Mock
    private SimulationService simulationService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(simulationController).build();
    }

    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("提交批次接口测试")
    class BatchEndpointTests {

        @Test
        @DisplayName("提交成功返回200")
        void submitBatch_success_returns200() throws Exception {
            when(simulationService.submitBatch(any())).thenReturn(1);

            SimulationBatchRequestDTO dto = buildValidRequest();
            mockMvc.perform(post("/simulation/submitBatch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.batchId").value(1));
        }

        @Test
        @DisplayName("缺少用户名返回400")
        void submitBatch_missingUserName_returns400() throws Exception {
            SimulationBatchRequestDTO dto = buildValidRequest();
            dto.setUserName(null);

            mockMvc.perform(post("/simulation/submitBatch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("空变体列表返回400")
        void submitBatch_emptyVariants_returns400() throws Exception {
            SimulationBatchRequestDTO dto = buildValidRequest();
            dto.setVariants(new ArrayList<>());

            mockMvc.perform(post("/simulation/submitBatch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("列出批次成功")
        void listBatches_success_returnsArray() throws Exception {
            when(simulationService.listUserBatches("userA")).thenReturn(List.of());

            mockMvc.perform(get("/simulation/listBatches")
                            .param("userName", "userA"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("状态查询接口测试")
    class StatusEndpointTests {

        @Test
        @DisplayName("批次状态查询成功")
        void batchStatus_success_returns200() throws Exception {
            SimulationBatchVO vo = new SimulationBatchVO();
            vo.setBatchId(1);
            vo.setStatus("COMPLETED");
            when(simulationService.getBatchStatus(1, "userA")).thenReturn(vo);

            mockMvc.perform(get("/simulation/batchStatus")
                            .param("batchId", "1")
                            .param("userName", "userA"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        }

        @Test
        @DisplayName("批次不存在返回失败")
        void batchStatus_notFound_returns201() throws Exception {
            when(simulationService.getBatchStatus(999, "userA")).thenReturn(null);

            mockMvc.perform(get("/simulation/batchStatus")
                            .param("batchId", "999")
                            .param("userName", "userA"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(201));
        }

        @Test
        @DisplayName("任务详情成功")
        void taskDetail_success_returns200() throws Exception {
            SimulationTaskDetailVO vo = new SimulationTaskDetailVO();
            vo.setTaskId(1);
            vo.setStatus("COMPLETED");
            when(simulationService.getTaskDetail(1, "userA")).thenReturn(vo);

            mockMvc.perform(get("/simulation/taskDetail")
                            .param("taskId", "1")
                            .param("userName", "userA"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("任务不存在返回失败")
        void taskDetail_notFound_returns201() throws Exception {
            when(simulationService.getTaskDetail(999, "userA")).thenReturn(null);

            mockMvc.perform(get("/simulation/taskDetail")
                            .param("taskId", "999")
                            .param("userName", "userA"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(201));
        }
    }

    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("概率解释接口测试")
    class ExplanationEndpointTests {

        @Test
        @DisplayName("解释查询成功")
        void explanation_success_returns200() throws Exception {
            ProbabilityExplanationVO vo = new ProbabilityExplanationVO();
            vo.setFinalProbability(BigDecimal.valueOf(65));
            vo.setExplanation("测试解释");
            when(simulationService.getSchoolExplanation(1, 3, "userA")).thenReturn(vo);

            mockMvc.perform(get("/simulation/explanation")
                            .param("taskId", "1")
                            .param("schoolId", "3")
                            .param("userName", "userA"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.explanation").value("测试解释"));
        }

        @Test
        @DisplayName("解释不存在返回失败")
        void explanation_notFound_returns201() throws Exception {
            when(simulationService.getSchoolExplanation(1, 999, "userA")).thenReturn(null);

            mockMvc.perform(get("/simulation/explanation")
                            .param("taskId", "1")
                            .param("schoolId", "999")
                            .param("userName", "userA"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(201));
        }
    }

    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("对比和操作接口测试")
    class CompareAndOperationTests {

        @Test
        @DisplayName("对比成功")
        void compare_success_returns200() throws Exception {
            SimulationComparisonVO vo = new SimulationComparisonVO();
            vo.setSummary("共0处差异");
            vo.setDifferences(List.of());
            when(simulationService.compareTasks(any())).thenReturn(vo);

            SimulationCompareRequestDTO dto = new SimulationCompareRequestDTO();
            dto.setUserName("userA");
            dto.setTaskIdA(1);
            dto.setTaskIdB(2);

            mockMvc.perform(post("/simulation/compare")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("提升为方案成功")
        void promote_success_returns200() throws Exception {
            when(simulationService.promoteToPlan(1, "userA", "新方案")).thenReturn(100);

            mockMvc.perform(post("/simulation/promote")
                            .param("taskId", "1")
                            .param("userName", "userA")
                            .param("planName", "新方案"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.planId").value(100));
        }

        @Test
        @DisplayName("取消批次成功")
        void cancel_success_returns200() throws Exception {
            when(simulationService.cancelBatch(1, "userA")).thenReturn(true);

            mockMvc.perform(post("/simulation/cancel")
                            .param("batchId", "1")
                            .param("userName", "userA"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("回滚成功")
        void rollback_success_returns200() throws Exception {
            when(simulationService.rollbackFromSnapshot(1, "userA")).thenReturn(true);

            mockMvc.perform(post("/simulation/rollback")
                            .param("taskId", "1")
                            .param("userName", "userA"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ══════════════════════════════════════════════
    // Helper
    // ══════════════════════════════════════════════

    private SimulationBatchRequestDTO buildValidRequest() {
        SimulationBatchRequestDTO dto = new SimulationBatchRequestDTO();
        dto.setUserName("userA");
        dto.setSourcePlanId(1);
        dto.setBatchName("测试批次");
        List<SimulationVariantDTO> variants = new ArrayList<>();
        SimulationVariantDTO v = new SimulationVariantDTO();
        v.setTaskLabel("分数+10");
        v.setScore(610);
        variants.add(v);
        dto.setVariants(variants);
        return dto;
    }
}
