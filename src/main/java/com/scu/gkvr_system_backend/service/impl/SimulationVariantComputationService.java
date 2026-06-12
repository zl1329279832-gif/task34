package com.scu.gkvr_system_backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scu.gkvr_system_backend.mapper.*;
import com.scu.gkvr_system_backend.pojo.*;
import com.scu.gkvr_system_backend.utils.PlanCalculationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 模拟变体计算服务.
 * 负责单个变体的方案克隆 + 指标重算.
 */
@Service
public class SimulationVariantComputationService {

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
    private ScLiScoreMapper scLiScoreMapper;

    @Autowired
    private SchoolInfoMapper schoolInfoMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 标记批次为运行中
     */
    public void markBatchRunning(Integer batchTaskId) {
        batchTaskMapper.updateStatus(batchTaskId, "running");
    }

    /**
     * 完成批次(所有变体均已处理)
     */
    public void finalizeBatch(Integer batchTaskId) {
        SimulationBatchTask task = batchTaskMapper.selectById(batchTaskId);
        if (task != null && !"stale".equals(task.getStatus()) && !"failed".equals(task.getStatus())) {
            batchTaskMapper.updateStatus(batchTaskId, "completed");
        }
    }

    /**
     * 标记批次失败
     */
    public void markBatchFailed(Integer batchTaskId, String message) {
        SimulationBatchTask task = batchTaskMapper.selectById(batchTaskId);
        if (task != null) {
            task.setStatus("failed");
            task.setErrorMessage(message != null && message.length() > 1000
                    ? message.substring(0, 1000) : message);
            batchTaskMapper.updateById(task);
        }
    }

    /**
     * 计算单个变体: 克隆方案 + 重算所有指标.
     */
    @Transactional(rollbackFor = Exception.class)
    public void computeVariant(SimulationBatchTask task, SimulationVariant variant, VoluntaryPlan basePlan) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. 标记运行中
            variant.setStatus("running");
            variantMapper.updateById(variant);

            // 2. 过时检测: 重读基础方案版本
            VoluntaryPlan currentBase = voluntaryPlanMapper.selectById(task.getBasePlanId());
            if (currentBase != null && currentBase.getVersion() > task.getBasePlanVersion()) {
                variant.setStatus("failed");
                variant.setErrorMessage("基础方案已更新, 模拟结果已过时");
                variant.setComputationTimeMs(System.currentTimeMillis() - startTime);
                variantMapper.updateById(variant);
                batchTaskMapper.incrementCompletedVariants(task.getId());
                return;
            }

            // 3. 确定有效参数
            int effectiveScore = variant.getScoreOverride() != null
                    ? variant.getScoreOverride() : basePlan.getScore();
            int effectiveRank = variant.getRankOverride() != null
                    ? variant.getRankOverride() : basePlan.getUserRank();
            String effectiveBatch = variant.getBatchNameOverride() != null
                    ? variant.getBatchNameOverride() : basePlan.getBatchName();
            String effectiveRegionPref = variant.getRegionPrefOverride() != null
                    ? variant.getRegionPrefOverride() : basePlan.getRegionPref();
            String effectiveSchoolTier = variant.getSchoolTierOverride() != null
                    ? variant.getSchoolTierOverride() : basePlan.getSchoolTier();
            String effectiveMajorPref = variant.getMajorPrefOverride() != null
                    ? variant.getMajorPrefOverride() : basePlan.getMajorPref();

            // 4. 克隆 VoluntaryPlan
            VoluntaryPlan simPlan = new VoluntaryPlan();
            simPlan.setPlanName(basePlan.getPlanName() + " [" + variant.getVariantName() + "]");
            simPlan.setUserName(basePlan.getUserName());
            simPlan.setScore(effectiveScore);
            simPlan.setUserRank(effectiveRank);
            simPlan.setSubjectType(basePlan.getSubjectType());
            simPlan.setRegionPref(effectiveRegionPref);
            simPlan.setSchoolTier(effectiveSchoolTier);
            simPlan.setMajorPref(effectiveMajorPref);
            simPlan.setBatchName(effectiveBatch);
            simPlan.setVersion(1);
            simPlan.setParentId(basePlan.getId());
            simPlan.setStatus(0);
            simPlan.setSimulationBatchTaskId(task.getId());
            voluntaryPlanMapper.insert(simPlan);

            // 5. 克隆院校并重算指标
            List<PlanSchool> baseSchools = planSchoolMapper.selectByPlanIdOrdered(task.getBasePlanId());
            List<PlanSchool> newSchools = new ArrayList<>();
            List<BigDecimal> admissionProbs = new ArrayList<>();
            List<BigDecimal> majorAdjustRisks = new ArrayList<>();
            long reachCount = 0;

            for (PlanSchool basePs : baseSchools) {
                PlanSchool ps = new PlanSchool();
                ps.setPlanId(simPlan.getId());
                ps.setSchoolId(basePs.getSchoolId());
                ps.setSchoolName(basePs.getSchoolName());
                ps.setCategory(basePs.getCategory());
                ps.setSortOrder(basePs.getSortOrder());
                ps.setSelectedMajors(basePs.getSelectedMajors());

                // 重算录取概率和位次统计
                recalculateSchoolMetrics(ps, effectiveScore, effectiveRank, effectiveBatch);

                planSchoolMapper.insert(ps);
                newSchools.add(ps);
                admissionProbs.add(ps.getAdmissionProb());
                majorAdjustRisks.add(ps.getMajorAdjustRisk());
                if ("冲".equals(ps.getCategory())) reachCount++;
            }

            // 6. 计算综合风险评分
            simPlan.setTotalRiskScore(PlanCalculationUtils.calcTotalRiskScore(
                    admissionProbs, majorAdjustRisks, reachCount, newSchools.size()));
            voluntaryPlanMapper.updateById(simPlan);

            // 7. 保存版本快照
            saveVersionSnapshot(simPlan.getId(), 1, newSchools);

            // 8. 标记完成
            variant.setStatus("completed");
            variant.setSimulatedPlanId(simPlan.getId());
            variant.setComputationTimeMs(System.currentTimeMillis() - startTime);
            variantMapper.updateById(variant);

            // 9. 原子递增完成数
            batchTaskMapper.incrementCompletedVariants(task.getId());

        } catch (Exception e) {
            variant.setStatus("failed");
            String errMsg = e.getMessage() != null ? e.getMessage() : "计算异常";
            variant.setErrorMessage(errMsg.length() > 1000 ? errMsg.substring(0, 1000) : errMsg);
            variant.setComputationTimeMs(System.currentTimeMillis() - startTime);
            variantMapper.updateById(variant);
            batchTaskMapper.incrementCompletedVariants(task.getId());
        }
    }

    /**
     * 重算单校指标: 录取概率, 调剂风险, 热度, 位次统计.
     * 直接重用 PlanCalculationUtils 静态方法.
     */
    public void recalculateSchoolMetrics(PlanSchool ps, int score, int rank, String batch) {
        // 1. 位次统计 (从 sc_li_score 获取)
        LambdaQueryWrapper<ScLiScore> scWrapper = new LambdaQueryWrapper<>();
        scWrapper.eq(ScLiScore::getSchoolId, ps.getSchoolId());
        ScLiScore sc = scLiScoreMapper.selectOne(scWrapper);

        if (sc != null) {
            int r0 = sc.getRank2020(), r1 = sc.getRank2021(), r2 = sc.getRank2022();
            double avg = PlanCalculationUtils.avgRank(r0, r1, r2);
            double std = PlanCalculationUtils.calcRankStdDev(r0, r1, r2);
            ps.setAvgRank3yr(BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
            ps.setRankStdDev(BigDecimal.valueOf(std).setScale(2, RoundingMode.HALF_UP));
            ps.setAdmissionProb(PlanCalculationUtils.calcAdmissionProb(avg, std, rank, ps.getCategory()));
            ps.setRankFluctuation(PlanCalculationUtils.buildRankFluctuationExplanation(r0, r1, r2, rank));
            ps.setRankTrend(PlanCalculationUtils.calcRankTrend(r0, r1, r2));
        } else {
            ps.setAdmissionProb(BigDecimal.valueOf(50));
            ps.setAvgRank3yr(BigDecimal.ZERO);
            ps.setRankStdDev(BigDecimal.ZERO);
            ps.setRankFluctuation("暂无历年数据");
            ps.setRankTrend("stable");
        }

        // 2. 调剂风险 — 跨批次三级降级
        List<Map<String, Object>> majorRows = planSchoolMapper.selectMajorScoresForSchool(
                ps.getSchoolId(), batch);
        if (majorRows == null || majorRows.isEmpty()) {
            // 降级: 不限批次查询
            majorRows = planSchoolMapper.selectMajorScoresForSchool(ps.getSchoolId(), null);
        }
        // PlanCalculationUtils 无数据时默认返回50
        ps.setMajorAdjustRisk(PlanCalculationUtils.calcMajorAdjustRisk(majorRows, score));

        // 3. 热度
        SchoolInfo si = schoolInfoMapper.selectById(ps.getSchoolId());
        if (si != null) {
            int mv = (si.getMonthView() != null) ? si.getMonthView() : 0;
            int tv = PlanCalculationUtils.toInt(si.getTotalView());
            ps.setPopularityScore(PlanCalculationUtils.calcPopularityScore(mv, tv));
            ps.setPopularityTrend(PlanCalculationUtils.calcPopularityTrend(mv, tv));
        }
    }

    private void saveVersionSnapshot(Integer planId, int version, List<PlanSchool> schools) {
        PlanVersionLog log = new PlanVersionLog();
        log.setPlanId(planId);
        log.setVersion(version);
        try {
            log.setSnapshotData(objectMapper.writeValueAsString(schools));
        } catch (JsonProcessingException e) {
            log.setSnapshotData("[]");
        }
        planVersionLogMapper.insert(log);
    }
}
