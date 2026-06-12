package com.scu.gkvr_system_backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scu.gkvr_system_backend.mapper.*;
import com.scu.gkvr_system_backend.pojo.*;
import com.scu.gkvr_system_backend.service.SimulationComputationService;
import com.scu.gkvr_system_backend.utils.PlanCalculationUtils;
import com.scu.gkvr_system_backend.vo.ProbabilityExplanationVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SimulationComputationServiceImpl implements SimulationComputationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationComputationServiceImpl.class);

    @Autowired
    private SimulationBatchMapper simulationBatchMapper;
    @Autowired
    private SimulationTaskMapper simulationTaskMapper;
    @Autowired
    private SimulationTaskSchoolMapper simulationTaskSchoolMapper;
    @Autowired
    private VoluntaryPlanMapper voluntaryPlanMapper;
    @Autowired
    private PlanSchoolMapper planSchoolMapper;
    @Autowired
    private ScLiScoreMapper scLiScoreMapper;
    @Autowired
    private SchoolInfoMapper schoolInfoMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Async("simulationTaskExecutor")
    @Override
    public void computeVariantAsync(Integer taskId) {
        SimulationTask task = simulationTaskMapper.selectById(taskId);
        if (task == null || !"PENDING".equals(task.getStatus())) {
            return; // idempotent guard
        }

        // CAS: mark COMPUTING
        int updated = simulationTaskMapper.markComputing(taskId);
        if (updated == 0) {
            return; // another thread already claimed
        }

        // Reload to get updated sequence_number
        task = simulationTaskMapper.selectById(taskId);
        int currentSeq = task.getSequenceNumber();

        try {
            // Load batch and source plan
            SimulationBatch batch = simulationBatchMapper.selectById(task.getBatchId());
            VoluntaryPlan sourcePlan = voluntaryPlanMapper.selectById(batch.getSourcePlanId());

            // Merge effective parameters
            int effectiveScore = task.getParamScore() != null ? task.getParamScore() : sourcePlan.getScore();
            int effectiveUserRank = task.getParamUserRank() != null ? task.getParamUserRank() : sourcePlan.getUserRank();
            String effectiveBatchName = task.getParamBatchName() != null ? task.getParamBatchName() : sourcePlan.getBatchName();

            // Load source plan schools
            List<PlanSchool> sourceSchools = planSchoolMapper.selectByPlanIdOrdered(sourcePlan.getId());

            // Build risk snapshot and compute metrics
            Map<String, Object> riskSnapshotMap = new LinkedHashMap<>();
            List<SimulationTaskSchool> resultSchools = new ArrayList<>();
            List<BigDecimal> admissionProbs = new ArrayList<>();
            List<BigDecimal> majorAdjustRisks = new ArrayList<>();
            int reachCount = 0;
            int matchCount = 0;
            int safetyCount = 0;

            // Map source schools by schoolId for delta computation
            Map<Integer, PlanSchool> sourceSchoolMap = sourceSchools.stream()
                    .collect(Collectors.toMap(PlanSchool::getSchoolId, s -> s, (a, b) -> a));

            int sortOrder = 0;
            for (PlanSchool sourceSchool : sourceSchools) {
                sortOrder++;
                int schoolId = sourceSchool.getSchoolId();

                // Freeze risk factor inputs
                Map<String, Object> schoolSnapshot = new LinkedHashMap<>();
                schoolSnapshot.put("schoolId", schoolId);
                schoolSnapshot.put("schoolName", sourceSchool.getSchoolName());

                // Query ScLiScore for historical ranks
                com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ScLiScore> wrapper =
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
                wrapper.eq(ScLiScore::getSchoolId, schoolId);
                ScLiScore scLiScore = scLiScoreMapper.selectOne(wrapper);

                int r2020 = 0, r2021 = 0, r2022 = 0;
                double avgRank = 0, stdDev = 0;
                ProbabilityExplanationVO probExplanation;

                if (scLiScore != null) {
                    r2020 = scLiScore.getRank2020();
                    r2021 = scLiScore.getRank2021();
                    r2022 = scLiScore.getRank2022();
                    avgRank = PlanCalculationUtils.avgRank(r2020, r2021, r2022);
                    stdDev = PlanCalculationUtils.calcRankStdDev(r2020, r2021, r2022);

                    schoolSnapshot.put("rank2020", r2020);
                    schoolSnapshot.put("rank2021", r2021);
                    schoolSnapshot.put("rank2022", r2022);

                    probExplanation = PlanCalculationUtils.buildProbabilityExplanation(
                            avgRank, stdDev, effectiveUserRank, sourceSchool.getCategory(),
                            r2020, r2021, r2022);
                } else {
                    probExplanation = PlanCalculationUtils.buildMissingDataExplanation(
                            sourceSchool.getSchoolName(), "暂无历年录取位次数据");
                }

                BigDecimal admissionProb = probExplanation.getFinalProbability();

                // Major adjust risk
                List<Map<String, Object>> majorRows = planSchoolMapper.selectMajorScoresForSchool(
                        schoolId, effectiveBatchName);
                BigDecimal majorAdjustRisk;
                String majorMissingNote = null;
                if (majorRows == null || majorRows.isEmpty()) {
                    majorAdjustRisk = BigDecimal.valueOf(50);
                    majorMissingNote = "该批次无专业分数数据,使用默认风险值50";
                } else {
                    majorAdjustRisk = PlanCalculationUtils.calcMajorAdjustRisk(majorRows, effectiveScore);
                }

                schoolSnapshot.put("majorRows", majorRows);

                // Popularity
                SchoolInfo schoolInfo = schoolInfoMapper.selectById(schoolId);
                BigDecimal popularityScore = BigDecimal.ZERO;
                String popularityTrend = "stable";
                if (schoolInfo != null) {
                    int mv = (schoolInfo.getMonthView() != null) ? schoolInfo.getMonthView() : 0;
                    int tv = PlanCalculationUtils.toInt(schoolInfo.getTotalView());
                    popularityScore = PlanCalculationUtils.calcPopularityScore(mv, tv);
                    popularityTrend = PlanCalculationUtils.calcPopularityTrend(mv, tv);
                    schoolSnapshot.put("monthView", mv);
                    schoolSnapshot.put("totalView", tv);
                }

                // Rank fluctuation and trend
                String rankFluctuation = scLiScore != null
                        ? PlanCalculationUtils.buildRankFluctuationExplanation(r2020, r2021, r2022, effectiveUserRank)
                        : "暂无历年数据(已重新计算)";
                String rankTrend = scLiScore != null
                        ? PlanCalculationUtils.calcRankTrend(r2020, r2021, r2022)
                        : "stable";

                // Source plan deltas
                PlanSchool sourceRef = sourceSchoolMap.get(schoolId);
                BigDecimal sourceProb = sourceRef != null && sourceRef.getAdmissionProb() != null
                        ? sourceRef.getAdmissionProb() : BigDecimal.valueOf(50);
                String sourceCategory = sourceRef != null ? sourceRef.getCategory() : sourceSchool.getCategory();
                BigDecimal probDelta = admissionProb.subtract(sourceProb);
                int categoryChangedFlag = !sourceSchool.getCategory().equals(sourceCategory) ? 1 : 0;

                // Count categories
                switch (sourceSchool.getCategory()) {
                    case "冲" -> reachCount++;
                    case "稳" -> matchCount++;
                    case "保" -> safetyCount++;
                }

                // Build SimulationTaskSchool
                SimulationTaskSchool sts = new SimulationTaskSchool();
                sts.setTaskId(taskId);
                sts.setSchoolId(schoolId);
                sts.setSchoolName(sourceSchool.getSchoolName());
                sts.setCategory(sourceSchool.getCategory());
                sts.setSortOrder(sortOrder);
                sts.setAdmissionProb(admissionProb);
                sts.setAdmissionProbLevel(PlanCalculationUtils.probLevel(admissionProb));
                sts.setMajorAdjustRisk(majorAdjustRisk);
                sts.setMajorAdjustRiskLevel(PlanCalculationUtils.riskLevel(majorAdjustRisk));
                sts.setPopularityScore(popularityScore);
                sts.setPopularityTrend(popularityTrend);
                sts.setRankFluctuation(rankFluctuation);
                sts.setAvgRank3yr(BigDecimal.valueOf(avgRank).setScale(2, RoundingMode.HALF_UP));
                sts.setRankStdDev(BigDecimal.valueOf(stdDev).setScale(2, RoundingMode.HALF_UP));
                sts.setRankTrend(rankTrend);
                sts.setSelectedMajors(sourceSchool.getSelectedMajors());

                // Probability explanation factors
                sts.setProbRankRatio(probExplanation.getRankRatio());
                sts.setProbStabilityFactor(probExplanation.getStabilityFactor());
                sts.setProbCategoryAdj(probExplanation.getCategoryAdjustment()
                        + (majorMissingNote != null ? "; " + majorMissingNote : ""));
                sts.setProbBaseValue(probExplanation.getBaseProbability());
                sts.setProbExplanation(probExplanation.getExplanation());

                // Deltas
                sts.setSourceAdmissionProb(sourceProb);
                sts.setSourceCategory(sourceCategory);
                sts.setProbDelta(probDelta);
                sts.setCategoryChanged(categoryChangedFlag);
                sts.setCreateTime(LocalDateTime.now());

                resultSchools.add(sts);
                admissionProbs.add(admissionProb);
                majorAdjustRisks.add(majorAdjustRisk);

                riskSnapshotMap.put("school_" + schoolId, schoolSnapshot);
            }

            // Persist SimulationTaskSchool rows
            for (SimulationTaskSchool sts : resultSchools) {
                simulationTaskSchoolMapper.insert(sts);
            }

            // Compute total risk score
            BigDecimal totalRiskScore = PlanCalculationUtils.calcTotalRiskScore(
                    admissionProbs, majorAdjustRisks, reachCount, resultSchools.size());

            // Serialize result_data and risk_snapshot
            String resultDataJson;
            String riskSnapshotJson;
            try {
                resultDataJson = objectMapper.writeValueAsString(resultSchools);
                riskSnapshotJson = objectMapper.writeValueAsString(riskSnapshotMap);
            } catch (Exception e) {
                resultDataJson = "[]";
                riskSnapshotJson = "{}";
            }

            // CAS-complete
            int completed = simulationTaskMapper.completeTask(
                    taskId, resultDataJson, riskSnapshotJson, totalRiskScore,
                    reachCount, matchCount, safetyCount, currentSeq);

            if (completed == 0) {
                log.warn("Stale write detected for taskId={}, seq={}", taskId, currentSeq);
            }

            // Update batch counters
            simulationBatchMapper.incrementCompletedTasks(task.getBatchId());

        } catch (Exception e) {
            log.error("Simulation task {} failed", taskId, e);
            String errMsg = e.getMessage();
            if (errMsg != null && errMsg.length() > 500) {
                errMsg = errMsg.substring(0, 500);
            }
            simulationTaskMapper.failTask(taskId, errMsg);
            simulationBatchMapper.incrementFailedTasks(task.getBatchId());
        }
    }
}
