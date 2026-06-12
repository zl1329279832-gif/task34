package com.scu.gkvr_system_backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scu.gkvr_system_backend.dto.SimulationBatchRequestDTO;
import com.scu.gkvr_system_backend.dto.SimulationCompareRequestDTO;
import com.scu.gkvr_system_backend.dto.SimulationVariantDTO;
import com.scu.gkvr_system_backend.mapper.*;
import com.scu.gkvr_system_backend.pojo.*;
import com.scu.gkvr_system_backend.service.SimulationService;
import com.scu.gkvr_system_backend.service.VoluntaryPlanService;
import com.scu.gkvr_system_backend.utils.PlanCalculationUtils;
import com.scu.gkvr_system_backend.vo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 模拟批处理服务实现
 */
@Service
public class SimulationServiceImpl implements SimulationService {

    @Autowired
    private SimulationBatchTaskMapper batchTaskMapper;

    @Autowired
    private SimulationVariantMapper variantMapper;

    @Autowired
    private VoluntaryPlanMapper voluntaryPlanMapper;

    @Autowired
    private PlanSchoolMapper planSchoolMapper;

    @Autowired
    private PlanVersionLogMapper planVersionLogMapper;

    @Autowired
    private VoluntaryPlanService voluntaryPlanService;

    @Autowired
    private SimulationAsyncExecutor simulationAsyncExecutor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ══════════════════════════════════════════
    // 提交批量模拟
    // ══════════════════════════════════════════

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer submitBatchSimulation(SimulationBatchRequestDTO request) {
        // 1. 校验基础方案所有权
        VoluntaryPlan basePlan = voluntaryPlanMapper.selectById(request.getBasePlanId());
        if (basePlan == null || !basePlan.getUserName().equals(request.getUserName())) {
            throw new IllegalArgumentException("方案不存在或无权限");
        }

        // 2. 频率限制: 最多3个活跃批次/用户
        long activeCount = batchTaskMapper.selectRecentByUserName(request.getUserName())
                .stream()
                .filter(t -> "pending".equals(t.getStatus()) || "running".equals(t.getStatus()))
                .count();
        if (activeCount >= 3) {
            throw new IllegalStateException("最多同时运行3个批量模拟");
        }

        // 3. 创建批次任务(捕获版本号用于过时检测)
        SimulationBatchTask task = new SimulationBatchTask();
        task.setUserName(request.getUserName());
        task.setBasePlanId(request.getBasePlanId());
        task.setBasePlanVersion(basePlan.getVersion());
        task.setStatus("pending");
        task.setTotalVariants(request.getVariants().size());
        task.setCompletedVariants(0);
        task.setProgressPercent(BigDecimal.ZERO);
        batchTaskMapper.insert(task);

        // 4. 创建变体记录
        List<SimulationVariant> variants = new ArrayList<>();
        int idx = 0;
        for (SimulationVariantDTO dto : request.getVariants()) {
            SimulationVariant v = new SimulationVariant();
            v.setBatchTaskId(task.getId());
            v.setVariantName(dto.getVariantName());
            v.setVariantIndex(idx++);
            v.setScoreOverride(dto.getScoreOverride());
            v.setRankOverride(dto.getRankOverride());
            v.setBatchNameOverride(dto.getBatchNameOverride());
            v.setRegionPrefOverride(dto.getRegionPrefOverride());
            v.setSchoolTierOverride(dto.getSchoolTierOverride());
            v.setMajorPrefOverride(dto.getMajorPrefOverride());
            v.setStatus("pending");
            variantMapper.insert(v);
            variants.add(v);
        }

        // 5. 异步执行
        simulationAsyncExecutor.executeBatch(task, variants, basePlan);

        return task.getId();
    }

    // ══════════════════════════════════════════
    // 查询批次状态
    // ══════════════════════════════════════════

    @Override
    public SimulationBatchStatusVO getBatchStatus(Integer batchTaskId, String userName) {
        SimulationBatchTask task = batchTaskMapper.selectById(batchTaskId);
        if (task == null || !task.getUserName().equals(userName)) {
            return null;
        }

        // 过时检测
        checkAndMarkStaleness(task);

        SimulationBatchStatusVO vo = new SimulationBatchStatusVO();
        vo.setBatchTaskId(task.getId());
        vo.setStatus(task.getStatus());
        vo.setProgressPercent(task.getProgressPercent());
        vo.setTotalVariants(task.getTotalVariants());
        vo.setCompletedVariants(task.getCompletedVariants());
        vo.setErrorMessage(task.getErrorMessage());
        vo.setCreateTime(task.getCreateTime());
        vo.setUpdateTime(task.getUpdateTime());

        // 变体列表
        List<SimulationVariant> variants = variantMapper.selectByBatchTaskId(batchTaskId);
        List<SimulationVariantStatusVO> variantStatuses = new ArrayList<>();
        for (SimulationVariant v : variants) {
            SimulationVariantStatusVO sv = new SimulationVariantStatusVO();
            sv.setVariantId(v.getId());
            sv.setVariantName(v.getVariantName());
            sv.setVariantIndex(v.getVariantIndex());
            sv.setStatus(v.getStatus());
            sv.setSimulatedPlanId(v.getSimulatedPlanId());
            sv.setErrorMessage(v.getErrorMessage());
            sv.setComputationTimeMs(v.getComputationTimeMs());
            variantStatuses.add(sv);
        }
        vo.setVariants(variantStatuses);

        return vo;
    }

    // ══════════════════════════════════════════
    // 概率解释明细
    // ══════════════════════════════════════════

    @Override
    public SimulationExplanationVO getExplanation(Integer simulationVariantId, String userName) {
        SimulationVariant variant = variantMapper.selectById(simulationVariantId);
        if (variant == null || !"completed".equals(variant.getStatus())
                || variant.getSimulatedPlanId() == null) {
            return null;
        }

        // 用户隔离
        SimulationBatchTask task = batchTaskMapper.selectById(variant.getBatchTaskId());
        if (task == null || !task.getUserName().equals(userName)) {
            return null;
        }

        VoluntaryPlan simPlan = voluntaryPlanMapper.selectById(variant.getSimulatedPlanId());
        if (simPlan == null) return null;

        SimulationExplanationVO vo = new SimulationExplanationVO();
        vo.setSimulationId(variant.getId());
        vo.setVariantName(variant.getVariantName());
        vo.setSimulatedPlanId(simPlan.getId());
        vo.setEffectiveScore(simPlan.getScore());
        vo.setEffectiveRank(simPlan.getUserRank());
        vo.setEffectiveBatchName(simPlan.getBatchName());

        // 逐校分解
        List<PlanSchool> simSchools = planSchoolMapper.selectByPlanIdOrdered(simPlan.getId());
        List<PlanSchool> baseSchools = planSchoolMapper.selectByPlanIdOrdered(task.getBasePlanId());

        // 基础方案院校 map (按 schoolId)
        Map<Integer, PlanSchool> baseSchoolMap = baseSchools.stream()
                .collect(Collectors.toMap(PlanSchool::getSchoolId, ps -> ps, (a, b) -> a));

        List<SchoolProbabilityBreakdownVO> breakdowns = new ArrayList<>();
        List<PlanSchoolVO> reachVOs = new ArrayList<>();
        List<PlanSchoolVO> matchVOs = new ArrayList<>();
        List<PlanSchoolVO> safetyVOs = new ArrayList<>();

        for (PlanSchool ps : simSchools) {
            SchoolProbabilityBreakdownVO bd = new SchoolProbabilityBreakdownVO();
            bd.setSchoolId(ps.getSchoolId());
            bd.setSchoolName(ps.getSchoolName());
            bd.setCategory(ps.getCategory());
            bd.setAdmissionProb(ps.getAdmissionProb() != null ? ps.getAdmissionProb() : BigDecimal.valueOf(50));
            bd.setAdmissionProbLevel(PlanCalculationUtils.probLevel(bd.getAdmissionProb()));
            bd.setAvgRank3yr(ps.getAvgRank3yr());
            bd.setRankStdDev(ps.getRankStdDev());
            bd.setRankTrend(ps.getRankTrend());
            bd.setRankFluctuation(ps.getRankFluctuation());
            bd.setMajorAdjustRisk(ps.getMajorAdjustRisk() != null ? ps.getMajorAdjustRisk() : BigDecimal.valueOf(50));
            bd.setMajorAdjustRiskLevel(PlanCalculationUtils.riskLevel(bd.getMajorAdjustRisk()));

            // 专业详情
            List<Map<String, Object>> majorRows = planSchoolMapper.selectMajorScoresForSchool(
                    ps.getSchoolId(), simPlan.getBatchName());
            bd.setMajorDetails(buildMajorRiskDetails(majorRows, simPlan.getScore()));

            // 计算变化 delta
            PlanSchool basePs = baseSchoolMap.get(ps.getSchoolId());
            if (basePs != null) {
                BigDecimal baseProb = basePs.getAdmissionProb() != null
                        ? basePs.getAdmissionProb() : BigDecimal.valueOf(50);
                BigDecimal baseRisk = basePs.getMajorAdjustRisk() != null
                        ? basePs.getMajorAdjustRisk() : BigDecimal.valueOf(50);
                bd.setProbDelta(bd.getAdmissionProb().subtract(baseProb)
                        .setScale(2, RoundingMode.HALF_UP));
                bd.setRiskDelta(bd.getMajorAdjustRisk().subtract(baseRisk)
                        .setScale(2, RoundingMode.HALF_UP));
                bd.setChangeReason(buildChangeReason(basePs, ps, task, simPlan));
            } else {
                bd.setProbDelta(BigDecimal.ZERO);
                bd.setRiskDelta(BigDecimal.ZERO);
                bd.setChangeReason("新增院校, 无基础方案对比");
            }

            breakdowns.add(bd);

            // 构建 PlanSchoolVO 用于风险汇总
            PlanSchoolVO svo = mapToSchoolVO(ps);
            switch (ps.getCategory()) {
                case "冲" -> reachVOs.add(svo);
                case "稳" -> matchVOs.add(svo);
                case "保" -> safetyVOs.add(svo);
            }
        }

        vo.setSchoolBreakdowns(breakdowns);
        vo.setRiskSummary(PlanCalculationUtils.buildRiskSummary(reachVOs, matchVOs, safetyVOs));

        // 变化解释
        vo.setChangeExplanation(buildChangeExplanation(simPlan, task));

        return vo;
    }

    // ══════════════════════════════════════════
    // 风险快照
    // ══════════════════════════════════════════

    @Override
    public SimulationSnapshotVO getSnapshot(Integer simulationVariantId, String userName) {
        SimulationVariant variant = variantMapper.selectById(simulationVariantId);
        if (variant == null || !"completed".equals(variant.getStatus())
                || variant.getSimulatedPlanId() == null) {
            return null;
        }

        SimulationBatchTask task = batchTaskMapper.selectById(variant.getBatchTaskId());
        if (task == null || !task.getUserName().equals(userName)) {
            return null;
        }

        VoluntaryPlan simPlan = voluntaryPlanMapper.selectById(variant.getSimulatedPlanId());
        if (simPlan == null) return null;

        List<PlanSchool> schools = planSchoolMapper.selectByPlanIdOrdered(simPlan.getId());
        List<PlanSchoolVO> reachVOs = new ArrayList<>();
        List<PlanSchoolVO> matchVOs = new ArrayList<>();
        List<PlanSchoolVO> safetyVOs = new ArrayList<>();

        for (PlanSchool ps : schools) {
            PlanSchoolVO svo = mapToSchoolVO(ps);
            switch (ps.getCategory()) {
                case "冲" -> reachVOs.add(svo);
                case "稳" -> matchVOs.add(svo);
                case "保" -> safetyVOs.add(svo);
            }
        }

        SimulationSnapshotVO vo = new SimulationSnapshotVO();
        vo.setSimulationId(variant.getId());
        vo.setVariantName(variant.getVariantName());
        vo.setSimulatedPlanId(simPlan.getId());
        vo.setTotalRiskScore(simPlan.getTotalRiskScore());
        vo.setRiskSummary(PlanCalculationUtils.buildRiskSummary(reachVOs, matchVOs, safetyVOs));

        List<PlanSchoolVO> allSchools = new ArrayList<>();
        allSchools.addAll(reachVOs);
        allSchools.addAll(matchVOs);
        allSchools.addAll(safetyVOs);
        vo.setAllSchools(allSchools);
        vo.setComputedAt(variant.getUpdateTime());

        return vo;
    }

    // ══════════════════════════════════════════
    // 版本对比
    // ══════════════════════════════════════════

    @Override
    public SimulationCompareVO compareSimulations(SimulationCompareRequestDTO request) {
        // 解析 planIdA
        Integer planIdA = resolvePlanId(request.getSimulationIdA(), request.getPlanIdA(), request.getUserName());
        Integer planIdB = resolvePlanId(request.getSimulationIdB(), request.getPlanIdB(), request.getUserName());

        if (planIdA == null || planIdB == null) return null;

        PlanComparisonVO comparison = voluntaryPlanService.comparePlans(planIdA, planIdB, request.getUserName());
        if (comparison == null) return null;

        SimulationCompareVO vo = new SimulationCompareVO();
        vo.setPlanA(comparison.getPlanA());
        vo.setPlanB(comparison.getPlanB());
        vo.setDifferences(comparison.getDifferences());
        vo.setSummary(comparison.getSummary());

        // 附加变化解释
        vo.setChangeExplanation(buildCompareChangeExplanation(comparison.getPlanA(), comparison.getPlanB()));

        return vo;
    }

    // ══════════════════════════════════════════
    // 提升为正式方案
    // ══════════════════════════════════════════

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer promoteToPlan(Integer simulationVariantId, String userName, String newPlanName) {
        SimulationVariant variant = variantMapper.selectById(simulationVariantId);
        if (variant == null || !"completed".equals(variant.getStatus())
                || variant.getSimulatedPlanId() == null) {
            return null;
        }

        SimulationBatchTask task = batchTaskMapper.selectById(variant.getBatchTaskId());
        if (task == null || !task.getUserName().equals(userName)) {
            return null;
        }

        VoluntaryPlan simPlan = voluntaryPlanMapper.selectById(variant.getSimulatedPlanId());
        if (simPlan == null) return null;

        // 清除模拟标记, 设为正式方案 (使用自定义SQL显式设置null)
        voluntaryPlanMapper.promoteSimulation(simPlan.getId(), newPlanName, task.getBasePlanId());

        return simPlan.getId();
    }

    // ══════════════════════════════════════════
    // 历史版本回滚
    // ══════════════════════════════════════════

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer rollbackToVersion(Integer planId, Integer targetVersion, String userName) {
        VoluntaryPlan plan = voluntaryPlanMapper.selectById(planId);
        if (plan == null || !plan.getUserName().equals(userName)) {
            return null;
        }

        // 查询版本快照(使用list取最新一条, 避免多版本冲突)
        LambdaQueryWrapper<PlanVersionLog> vlWrapper = new LambdaQueryWrapper<>();
        vlWrapper.eq(PlanVersionLog::getPlanId, planId)
                .eq(PlanVersionLog::getVersion, targetVersion)
                .orderByDesc(PlanVersionLog::getId);
        List<PlanVersionLog> versionLogs = planVersionLogMapper.selectList(vlWrapper);
        if (versionLogs.isEmpty()) return null;
        PlanVersionLog versionLog = versionLogs.get(0);

        // 反序列化快照中的院校列表
        List<PlanSchool> snapshotSchools;
        try {
            snapshotSchools = objectMapper.readValue(versionLog.getSnapshotData(),
                    new TypeReference<List<PlanSchool>>() {});
        } catch (Exception e) {
            return null;
        }
        if (snapshotSchools == null) return null;

        // 创建单变体批次
        SimulationBatchTask task = new SimulationBatchTask();
        task.setUserName(userName);
        task.setBasePlanId(planId);
        task.setBasePlanVersion(plan.getVersion());
        task.setStatus("pending");
        task.setTotalVariants(1);
        task.setCompletedVariants(0);
        task.setProgressPercent(BigDecimal.ZERO);
        batchTaskMapper.insert(task);

        SimulationVariant variant = new SimulationVariant();
        variant.setBatchTaskId(task.getId());
        variant.setVariantName("回滚到版本" + targetVersion);
        variant.setVariantIndex(0);
        // 不覆盖任何参数, 使用基础方案当前参数
        variant.setStatus("pending");
        variantMapper.insert(variant);

        // 创建回滚方案(直接克隆快照数据)
        VoluntaryPlan rollbackPlan = new VoluntaryPlan();
        rollbackPlan.setPlanName(plan.getPlanName() + " [回滚v" + targetVersion + "]");
        rollbackPlan.setUserName(userName);
        rollbackPlan.setScore(plan.getScore());
        rollbackPlan.setUserRank(plan.getUserRank());
        rollbackPlan.setSubjectType(plan.getSubjectType());
        rollbackPlan.setRegionPref(plan.getRegionPref());
        rollbackPlan.setSchoolTier(plan.getSchoolTier());
        rollbackPlan.setMajorPref(plan.getMajorPref());
        rollbackPlan.setBatchName(plan.getBatchName());
        rollbackPlan.setVersion(1);
        rollbackPlan.setParentId(planId);
        rollbackPlan.setStatus(0);
        rollbackPlan.setSimulationBatchTaskId(task.getId());
        voluntaryPlanMapper.insert(rollbackPlan);

        // 克隆快照中的院校(保留原始指标)
        List<BigDecimal> admissionProbs = new ArrayList<>();
        List<BigDecimal> majorAdjustRisks = new ArrayList<>();
        long reachCount = 0;

        for (PlanSchool orig : snapshotSchools) {
            PlanSchool ps = new PlanSchool();
            ps.setPlanId(rollbackPlan.getId());
            ps.setSchoolId(orig.getSchoolId());
            ps.setSchoolName(orig.getSchoolName());
            ps.setCategory(orig.getCategory());
            ps.setSortOrder(orig.getSortOrder());
            ps.setAdmissionProb(orig.getAdmissionProb());
            ps.setMajorAdjustRisk(orig.getMajorAdjustRisk());
            ps.setPopularityScore(orig.getPopularityScore());
            ps.setPopularityTrend(orig.getPopularityTrend());
            ps.setRankFluctuation(orig.getRankFluctuation());
            ps.setAvgRank3yr(orig.getAvgRank3yr());
            ps.setRankStdDev(orig.getRankStdDev());
            ps.setRankTrend(orig.getRankTrend());
            ps.setSelectedMajors(orig.getSelectedMajors());
            planSchoolMapper.insert(ps);

            admissionProbs.add(ps.getAdmissionProb() != null ? ps.getAdmissionProb() : BigDecimal.valueOf(50));
            majorAdjustRisks.add(ps.getMajorAdjustRisk() != null ? ps.getMajorAdjustRisk() : BigDecimal.valueOf(50));
            if ("冲".equals(ps.getCategory())) reachCount++;
        }

        rollbackPlan.setTotalRiskScore(PlanCalculationUtils.calcTotalRiskScore(
                admissionProbs, majorAdjustRisks, reachCount, snapshotSchools.size()));
        voluntaryPlanMapper.updateById(rollbackPlan);

        // 标记变体和批次为已完成
        variant.setStatus("completed");
        variant.setSimulatedPlanId(rollbackPlan.getId());
        variantMapper.updateById(variant);

        task.setStatus("completed");
        task.setCompletedVariants(1);
        task.setProgressPercent(BigDecimal.valueOf(100));
        batchTaskMapper.updateById(task);

        return variant.getId();
    }

    // ══════════════════════════════════════════
    // 私有辅助方法
    // ══════════════════════════════════════════

    /**
     * 过时检测: 检查基础方案版本是否已前进
     */
    private void checkAndMarkStaleness(SimulationBatchTask task) {
        if ("completed".equals(task.getStatus()) || "failed".equals(task.getStatus())
                || "stale".equals(task.getStatus())) {
            return;
        }
        VoluntaryPlan basePlan = voluntaryPlanMapper.selectById(task.getBasePlanId());
        if (basePlan != null && basePlan.getVersion() > task.getBasePlanVersion()) {
            batchTaskMapper.updateStatus(task.getId(), "stale");
            task.setStatus("stale");
        }
    }

    /**
     * 解析 planId: 优先 simulationId → simulatedPlanId, 否则直接使用 planId
     */
    private Integer resolvePlanId(Integer simulationId, Integer planId, String userName) {
        if (simulationId != null) {
            SimulationVariant variant = variantMapper.selectById(simulationId);
            if (variant == null || !"completed".equals(variant.getStatus())
                    || variant.getSimulatedPlanId() == null) {
                return null;
            }
            SimulationBatchTask task = batchTaskMapper.selectById(variant.getBatchTaskId());
            if (task == null || !task.getUserName().equals(userName)) {
                return null;
            }
            return variant.getSimulatedPlanId();
        }
        if (planId != null) {
            VoluntaryPlan plan = voluntaryPlanMapper.selectById(planId);
            if (plan == null || !plan.getUserName().equals(userName)) {
                return null;
            }
            return planId;
        }
        return null;
    }

    private String buildChangeReason(PlanSchool basePs, PlanSchool simPs,
                                      SimulationBatchTask task, VoluntaryPlan simPlan) {
        VoluntaryPlan basePlan = voluntaryPlanMapper.selectById(task.getBasePlanId());
        List<String> reasons = new ArrayList<>();

        if (!Objects.equals(simPlan.getScore(), basePlan.getScore())) {
            reasons.add(String.format("分数变化(%d→%d)", basePlan.getScore(), simPlan.getScore()));
        }
        if (!Objects.equals(simPlan.getUserRank(), basePlan.getUserRank())) {
            reasons.add(String.format("位次变化(%d→%d)", basePlan.getUserRank(), simPlan.getUserRank()));
        }
        if (!Objects.equals(simPlan.getBatchName(), basePlan.getBatchName())) {
            reasons.add(String.format("批次变化(%s→%s)",
                    basePlan.getBatchName(), simPlan.getBatchName()));
        }
        if (reasons.isEmpty()) {
            return "参数未变化, 指标保持一致";
        }
        return String.join("; ", reasons);
    }

    private SimulationChangeExplanationVO buildChangeExplanation(VoluntaryPlan simPlan,
                                                                   SimulationBatchTask task) {
        VoluntaryPlan basePlan = voluntaryPlanMapper.selectById(task.getBasePlanId());
        if (basePlan == null) return null;

        List<PlanSchool> simSchools = planSchoolMapper.selectByPlanIdOrdered(simPlan.getId());
        List<PlanSchool> baseSchools = planSchoolMapper.selectByPlanIdOrdered(task.getBasePlanId());

        Map<Integer, PlanSchool> baseMap = baseSchools.stream()
                .collect(Collectors.toMap(PlanSchool::getSchoolId, ps -> ps, (a, b) -> a));

        double simAvgProb = simSchools.stream()
                .mapToDouble(ps -> ps.getAdmissionProb() != null ? ps.getAdmissionProb().doubleValue() : 50)
                .average().orElse(50);
        double baseAvgProb = baseSchools.stream()
                .mapToDouble(ps -> ps.getAdmissionProb() != null ? ps.getAdmissionProb().doubleValue() : 50)
                .average().orElse(50);
        double simAvgRisk = simSchools.stream()
                .mapToDouble(ps -> ps.getMajorAdjustRisk() != null ? ps.getMajorAdjustRisk().doubleValue() : 50)
                .average().orElse(50);
        double baseAvgRisk = baseSchools.stream()
                .mapToDouble(ps -> ps.getMajorAdjustRisk() != null ? ps.getMajorAdjustRisk().doubleValue() : 50)
                .average().orElse(50);

        BigDecimal baseRiskScore = basePlan.getTotalRiskScore() != null
                ? basePlan.getTotalRiskScore() : BigDecimal.ZERO;
        BigDecimal simRiskScore = simPlan.getTotalRiskScore() != null
                ? simPlan.getTotalRiskScore() : BigDecimal.ZERO;

        SimulationChangeExplanationVO vo = new SimulationChangeExplanationVO();
        vo.setTotalRiskScoreDelta(simRiskScore.subtract(baseRiskScore).setScale(2, RoundingMode.HALF_UP));
        vo.setAvgProbDelta(BigDecimal.valueOf(simAvgProb - baseAvgProb).setScale(2, RoundingMode.HALF_UP));
        vo.setAvgMajorRiskDelta(BigDecimal.valueOf(simAvgRisk - baseAvgRisk).setScale(2, RoundingMode.HALF_UP));

        StringBuilder overall = new StringBuilder();
        overall.append(String.format("综合风险评分变化%.2f, ", vo.getTotalRiskScoreDelta().doubleValue()));
        overall.append(String.format("平均录取概率变化%.2f%%, ", vo.getAvgProbDelta().doubleValue()));
        overall.append(String.format("平均调剂风险变化%.2f.", vo.getAvgMajorRiskDelta().doubleValue()));
        vo.setOverallExplanation(overall.toString());

        List<String> notable = new ArrayList<>();
        for (PlanSchool simPs : simSchools) {
            PlanSchool basePs = baseMap.get(simPs.getSchoolId());
            if (basePs != null) {
                BigDecimal baseProb = basePs.getAdmissionProb() != null
                        ? basePs.getAdmissionProb() : BigDecimal.valueOf(50);
                BigDecimal curProb = simPs.getAdmissionProb() != null
                        ? simPs.getAdmissionProb() : BigDecimal.valueOf(50);
                double probDelta = curProb.doubleValue() - baseProb.doubleValue();
                if (Math.abs(probDelta) > 10) {
                    notable.add(String.format("%s(%s)录取概率%s%.1f%%",
                            simPs.getSchoolName(), simPs.getCategory(),
                            probDelta > 0 ? "提升" : "下降", Math.abs(probDelta)));
                }
            }
        }
        vo.setNotableChanges(notable);

        return vo;
    }

    private SimulationChangeExplanationVO buildCompareChangeExplanation(PlanDetailVO planA, PlanDetailVO planB) {
        if (planA == null || planB == null) return null;

        SimulationChangeExplanationVO vo = new SimulationChangeExplanationVO();

        BigDecimal riskA = planA.getTotalRiskScore() != null ? planA.getTotalRiskScore() : BigDecimal.ZERO;
        BigDecimal riskB = planB.getTotalRiskScore() != null ? planB.getTotalRiskScore() : BigDecimal.ZERO;
        vo.setTotalRiskScoreDelta(riskB.subtract(riskA).setScale(2, RoundingMode.HALF_UP));

        PlanRiskSummary sumA = planA.getRiskSummary();
        PlanRiskSummary sumB = planB.getRiskSummary();
        if (sumA != null && sumB != null) {
            BigDecimal avgProbA = sumA.getAvgAdmissionProb() != null ? sumA.getAvgAdmissionProb() : BigDecimal.ZERO;
            BigDecimal avgProbB = sumB.getAvgAdmissionProb() != null ? sumB.getAvgAdmissionProb() : BigDecimal.ZERO;
            vo.setAvgProbDelta(avgProbB.subtract(avgProbA).setScale(2, RoundingMode.HALF_UP));

            BigDecimal avgRiskA = sumA.getAvgMajorAdjustRisk() != null ? sumA.getAvgMajorAdjustRisk() : BigDecimal.ZERO;
            BigDecimal avgRiskB = sumB.getAvgMajorAdjustRisk() != null ? sumB.getAvgMajorAdjustRisk() : BigDecimal.ZERO;
            vo.setAvgMajorRiskDelta(avgRiskB.subtract(avgRiskA).setScale(2, RoundingMode.HALF_UP));
        }

        vo.setOverallExplanation(String.format("综合风险评分变化%.2f, 平均录取概率变化%.2f%%.",
                vo.getTotalRiskScoreDelta().doubleValue(), vo.getAvgProbDelta().doubleValue()));
        vo.setNotableChanges(new ArrayList<>());

        return vo;
    }

    private PlanSchoolVO mapToSchoolVO(PlanSchool ps) {
        PlanSchoolVO vo = new PlanSchoolVO();
        vo.setPlanSchoolId(ps.getId());
        vo.setSchoolId(ps.getSchoolId());
        vo.setSchoolName(ps.getSchoolName());
        vo.setCategory(ps.getCategory());
        vo.setSortOrder(ps.getSortOrder());

        BigDecimal prob = ps.getAdmissionProb() != null ? ps.getAdmissionProb() : BigDecimal.valueOf(50);
        BigDecimal risk = ps.getMajorAdjustRisk() != null ? ps.getMajorAdjustRisk() : BigDecimal.valueOf(50);

        vo.setAdmissionProb(prob);
        vo.setAdmissionProbLevel(PlanCalculationUtils.probLevel(prob));
        vo.setMajorAdjustRisk(risk);
        vo.setMajorAdjustRiskLevel(PlanCalculationUtils.riskLevel(risk));
        vo.setPopularityScore(ps.getPopularityScore());
        vo.setPopularityTrend(ps.getPopularityTrend());
        vo.setRankFluctuation(ps.getRankFluctuation());
        vo.setAvgRank3yr(ps.getAvgRank3yr());
        vo.setRankStdDev(ps.getRankStdDev());
        vo.setRankTrend(ps.getRankTrend());

        if (ps.getSelectedMajors() != null && !ps.getSelectedMajors().isEmpty()) {
            vo.setSelectedMajors(Arrays.asList(ps.getSelectedMajors().split(",")));
        }

        List<Map<String, Object>> majorRows = planSchoolMapper.selectMajorScoresForSchool(
                ps.getSchoolId(), null);
        vo.setMajorDetails(buildMajorRiskDetails(majorRows, 0));
        return vo;
    }

    private List<MajorRiskVO> buildMajorRiskDetails(List<Map<String, Object>> majorRows, int userScore) {
        List<MajorRiskVO> riskVOs = new ArrayList<>();
        for (Map<String, Object> row : majorRows) {
            MajorRiskVO mvo = new MajorRiskVO();
            mvo.setMajorName(PlanCalculationUtils.toStr(row.get("majorName")));
            mvo.setHistoricalMax(PlanCalculationUtils.toInt(row.get("maxScore")));
            mvo.setHistoricalMin(PlanCalculationUtils.toInt(row.get("minScore")));
            mvo.setHistoricalAvg(PlanCalculationUtils.toInt(row.get("avgScore")));
            if (userScore > 0 && mvo.getHistoricalAvg() != null && mvo.getHistoricalMin() != null) {
                double matchScore = PlanCalculationUtils.calcMajorMatchScore(
                        userScore, mvo.getHistoricalMin(), mvo.getHistoricalAvg());
                mvo.setMatchScore(BigDecimal.valueOf(matchScore).setScale(2, RoundingMode.HALF_UP));
            }
            riskVOs.add(mvo);
        }
        return riskVOs;
    }
}
