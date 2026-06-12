package com.scu.gkvr_system_backend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scu.gkvr_system_backend.dto.SimulationBatchRequestDTO;
import com.scu.gkvr_system_backend.dto.SimulationCompareRequestDTO;
import com.scu.gkvr_system_backend.dto.SimulationVariantDTO;
import com.scu.gkvr_system_backend.mapper.*;
import com.scu.gkvr_system_backend.pojo.*;
import com.scu.gkvr_system_backend.service.SimulationComputationService;
import com.scu.gkvr_system_backend.service.SimulationService;
import com.scu.gkvr_system_backend.utils.PlanCalculationUtils;
import com.scu.gkvr_system_backend.vo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SimulationServiceImpl extends ServiceImpl<SimulationBatchMapper, SimulationBatch>
        implements SimulationService {

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
    private PlanVersionLogMapper planVersionLogMapper;
    @Autowired
    private SimulationComputationService simulationComputationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public Integer submitBatch(SimulationBatchRequestDTO request) {
        // Validate source plan exists and belongs to user
        VoluntaryPlan sourcePlan = voluntaryPlanMapper.selectById(request.getSourcePlanId());
        if (sourcePlan == null || !sourcePlan.getUserName().equals(request.getUserName())) {
            return null;
        }

        // Create batch
        SimulationBatch batch = new SimulationBatch();
        batch.setUserName(request.getUserName());
        batch.setSourcePlanId(request.getSourcePlanId());
        batch.setBatchName(request.getBatchName());
        batch.setSequenceNumber(1);
        batch.setStatus("COMPUTING");
        batch.setTotalTasks(request.getVariants().size());
        batch.setCompletedTasks(0);
        batch.setFailedTasks(0);
        batch.setCreateTime(LocalDateTime.now());
        batch.setUpdateTime(LocalDateTime.now());
        simulationBatchMapper.insert(batch);

        // Create tasks for each variant
        List<Integer> taskIds = new ArrayList<>();
        for (SimulationVariantDTO variant : request.getVariants()) {
            SimulationTask task = new SimulationTask();
            task.setBatchId(batch.getId());
            task.setTaskLabel(variant.getTaskLabel());
            task.setStatus("PENDING");
            task.setSequenceNumber(0);
            task.setParamScore(variant.getScore());
            task.setParamUserRank(variant.getUserRank());
            task.setParamBatchName(variant.getBatchName());
            task.setParamRegionPref(variant.getRegionPref() != null
                    ? String.join(",", variant.getRegionPref()) : null);
            task.setParamSchoolTier(variant.getSchoolTier() != null
                    ? String.join(",", variant.getSchoolTier()) : null);
            task.setParamMajorPref(variant.getMajorPref() != null
                    ? String.join(",", variant.getMajorPref()) : null);
            task.setCreateTime(LocalDateTime.now());
            simulationTaskMapper.insert(task);
            taskIds.add(task.getId());
        }

        // Dispatch async computation for each task
        for (Integer taskId : taskIds) {
            simulationComputationService.computeVariantAsync(taskId);
        }

        return batch.getId();
    }

    @Override
    public SimulationBatchVO getBatchStatus(Integer batchId, String userName) {
        SimulationBatch batch = simulationBatchMapper.selectByIdAndUser(batchId, userName);
        if (batch == null) {
            return null;
        }
        return buildBatchVO(batch);
    }

    @Override
    public SimulationTaskDetailVO getTaskDetail(Integer taskId, String userName) {
        SimulationTask task = simulationTaskMapper.selectById(taskId);
        if (task == null) return null;

        SimulationBatch batch = simulationBatchMapper.selectById(task.getBatchId());
        if (batch == null || !batch.getUserName().equals(userName)) return null;

        VoluntaryPlan sourcePlan = voluntaryPlanMapper.selectById(batch.getSourcePlanId());

        SimulationTaskDetailVO vo = new SimulationTaskDetailVO();
        vo.setTaskId(task.getId());
        vo.setBatchId(task.getBatchId());
        vo.setTaskLabel(task.getTaskLabel());
        vo.setStatus(task.getStatus());

        // Effective parameters
        if (sourcePlan != null) {
            vo.setEffectiveScore(task.getParamScore() != null ? task.getParamScore() : sourcePlan.getScore());
            vo.setEffectiveUserRank(task.getParamUserRank() != null ? task.getParamUserRank() : sourcePlan.getUserRank());
            vo.setEffectiveBatchName(task.getParamBatchName() != null ? task.getParamBatchName() : sourcePlan.getBatchName());
            vo.setEffectiveRegionPref(task.getParamRegionPref() != null ? task.getParamRegionPref() : sourcePlan.getRegionPref());
            vo.setEffectiveSchoolTier(task.getParamSchoolTier() != null ? task.getParamSchoolTier() : sourcePlan.getSchoolTier());
            vo.setEffectiveMajorPref(task.getParamMajorPref() != null ? task.getParamMajorPref() : sourcePlan.getMajorPref());
        }

        // If not completed, return summary only
        if (!"COMPLETED".equals(task.getStatus())) {
            vo.setReachSchools(Collections.emptyList());
            vo.setMatchSchools(Collections.emptyList());
            vo.setSafetySchools(Collections.emptyList());
            return vo;
        }

        // Load school results
        List<SimulationTaskSchool> schools = simulationTaskSchoolMapper.selectByTaskIdOrdered(taskId);
        List<SimulationSchoolVO> reachList = new ArrayList<>();
        List<SimulationSchoolVO> matchList = new ArrayList<>();
        List<SimulationSchoolVO> safetyList = new ArrayList<>();

        for (SimulationTaskSchool sts : schools) {
            SimulationSchoolVO svo = mapToSimulationSchoolVO(sts);
            switch (sts.getCategory()) {
                case "冲" -> reachList.add(svo);
                case "稳" -> matchList.add(svo);
                case "保" -> safetyList.add(svo);
            }
        }

        vo.setReachSchools(reachList);
        vo.setMatchSchools(matchList);
        vo.setSafetySchools(safetyList);
        vo.setSnapshotTime(task.getComputeEnd());

        // Build risk summary from school VOs
        vo.setRiskSummary(buildSimulationRiskSummary(reachList, matchList, safetyList));

        // Detect cross-category conflicts
        vo.setConflictWarnings(detectConflicts(schools));

        return vo;
    }

    @Override
    public ProbabilityExplanationVO getSchoolExplanation(Integer taskId, Integer schoolId, String userName) {
        SimulationTask task = simulationTaskMapper.selectById(taskId);
        if (task == null) return null;

        SimulationBatch batch = simulationBatchMapper.selectById(task.getBatchId());
        if (batch == null || !batch.getUserName().equals(userName)) return null;

        SimulationTaskSchool sts = simulationTaskSchoolMapper.selectByTaskIdAndSchoolId(taskId, schoolId);
        if (sts == null) return null;

        ProbabilityExplanationVO vo = new ProbabilityExplanationVO();
        vo.setRankRatio(sts.getProbRankRatio());
        vo.setBaseProbability(sts.getProbBaseValue());
        vo.setStabilityFactor(sts.getProbStabilityFactor());
        vo.setCategoryAdjustment(sts.getProbCategoryAdj());
        vo.setFinalProbability(sts.getAdmissionProb());
        vo.setAvgRank3yr(sts.getAvgRank3yr());
        vo.setRankStdDev(sts.getRankStdDev());
        vo.setExplanation(sts.getProbExplanation());
        return vo;
    }

    @Override
    public SimulationComparisonVO compareTasks(SimulationCompareRequestDTO request) {
        String userName = request.getUserName();

        // Load and verify both tasks
        SimulationTask taskA = simulationTaskMapper.selectById(request.getTaskIdA());
        SimulationTask taskB = simulationTaskMapper.selectById(request.getTaskIdB());
        if (taskA == null || taskB == null) return null;

        SimulationBatch batchA = simulationBatchMapper.selectById(taskA.getBatchId());
        SimulationBatch batchB = simulationBatchMapper.selectById(taskB.getBatchId());
        if (batchA == null || batchB == null) return null;
        if (!batchA.getUserName().equals(userName) || !batchB.getUserName().equals(userName)) return null;

        if (!"COMPLETED".equals(taskA.getStatus()) || !"COMPLETED".equals(taskB.getStatus())) return null;

        // Get task details
        SimulationTaskDetailVO detailA = getTaskDetail(request.getTaskIdA(), userName);
        SimulationTaskDetailVO detailB = getTaskDetail(request.getTaskIdB(), userName);

        // Build school maps by schoolId
        Map<Integer, SimulationSchoolVO> schoolMapA = buildSchoolMap(detailA);
        Map<Integer, SimulationSchoolVO> schoolMapB = buildSchoolMap(detailB);

        Set<Integer> allSchoolIds = new HashSet<>();
        allSchoolIds.addAll(schoolMapA.keySet());
        allSchoolIds.addAll(schoolMapB.keySet());

        List<SimulationComparisonVO.SimulationSchoolDiff> diffs = new ArrayList<>();
        for (Integer sid : allSchoolIds) {
            SimulationSchoolVO sA = schoolMapA.get(sid);
            SimulationSchoolVO sB = schoolMapB.get(sid);

            if (sA == null) {
                SimulationComparisonVO.SimulationSchoolDiff diff = new SimulationComparisonVO.SimulationSchoolDiff();
                diff.setSchoolId(sid);
                diff.setSchoolName(sB.getSchoolName());
                diff.setChangeType("added");
                diff.setDetail("任务B新增院校: " + sB.getSchoolName());
                diff.setProbB(sB.getAdmissionProb());
                diff.setCategoryB(sB.getCategory());
                diffs.add(diff);
            } else if (sB == null) {
                SimulationComparisonVO.SimulationSchoolDiff diff = new SimulationComparisonVO.SimulationSchoolDiff();
                diff.setSchoolId(sid);
                diff.setSchoolName(sA.getSchoolName());
                diff.setChangeType("removed");
                diff.setDetail("任务B移除院校: " + sA.getSchoolName());
                diff.setProbA(sA.getAdmissionProb());
                diff.setCategoryA(sA.getCategory());
                diffs.add(diff);
            } else {
                // Both exist - check for changes
                if (!Objects.equals(sA.getCategory(), sB.getCategory())) {
                    SimulationComparisonVO.SimulationSchoolDiff diff = new SimulationComparisonVO.SimulationSchoolDiff();
                    diff.setSchoolId(sid);
                    diff.setSchoolName(sA.getSchoolName());
                    diff.setChangeType("category_changed");
                    diff.setDetail(String.format("%s类别: %s→%s", sA.getSchoolName(), sA.getCategory(), sB.getCategory()));
                    diff.setProbA(sA.getAdmissionProb());
                    diff.setProbB(sB.getAdmissionProb());
                    diff.setCategoryA(sA.getCategory());
                    diff.setCategoryB(sB.getCategory());
                    diffs.add(diff);
                } else if (sA.getAdmissionProb() != null && sB.getAdmissionProb() != null
                        && Math.abs(sA.getAdmissionProb().subtract(sB.getAdmissionProb()).doubleValue()) > 5) {
                    SimulationComparisonVO.SimulationSchoolDiff diff = new SimulationComparisonVO.SimulationSchoolDiff();
                    diff.setSchoolId(sid);
                    diff.setSchoolName(sA.getSchoolName());
                    diff.setChangeType("prob_changed");
                    diff.setDetail(String.format("%s概率: %.2f%%→%.2f%%",
                            sA.getSchoolName(), sA.getAdmissionProb(), sB.getAdmissionProb()));
                    diff.setProbA(sA.getAdmissionProb());
                    diff.setProbB(sB.getAdmissionProb());
                    diff.setCategoryA(sA.getCategory());
                    diff.setCategoryB(sB.getCategory());
                    diffs.add(diff);
                }
            }
        }

        // Parameter diffs
        Map<String, String[]> paramDiffs = new LinkedHashMap<>();
        addParamDiff(paramDiffs, "score",
                String.valueOf(detailA.getEffectiveScore()), String.valueOf(detailB.getEffectiveScore()));
        addParamDiff(paramDiffs, "userRank",
                String.valueOf(detailA.getEffectiveUserRank()), String.valueOf(detailB.getEffectiveUserRank()));
        addParamDiff(paramDiffs, "batchName",
                detailA.getEffectiveBatchName(), detailB.getEffectiveBatchName());
        addParamDiff(paramDiffs, "regionPref",
                detailA.getEffectiveRegionPref(), detailB.getEffectiveRegionPref());
        addParamDiff(paramDiffs, "schoolTier",
                detailA.getEffectiveSchoolTier(), detailB.getEffectiveSchoolTier());

        // Summary
        long addedCount = diffs.stream().filter(d -> "added".equals(d.getChangeType())).count();
        long removedCount = diffs.stream().filter(d -> "removed".equals(d.getChangeType())).count();
        long changedCount = diffs.stream()
                .filter(d -> "category_changed".equals(d.getChangeType()) || "prob_changed".equals(d.getChangeType()))
                .count();
        String summary = String.format("共%d处差异: %d所新增, %d所移除, %d所变化。",
                diffs.size(), addedCount, removedCount, changedCount);

        SimulationComparisonVO result = new SimulationComparisonVO();
        result.setTaskA(detailA);
        result.setTaskB(detailB);
        result.setDifferences(diffs);
        result.setSummary(summary);
        result.setParameterDiffs(paramDiffs);
        return result;
    }

    @Override
    @Transactional
    public Boolean cancelBatch(Integer batchId, String userName) {
        SimulationBatch batch = simulationBatchMapper.selectByIdAndUser(batchId, userName);
        if (batch == null) return false;

        simulationTaskMapper.cancelPendingByBatchId(batchId);
        simulationBatchMapper.updateStatus(batchId, "CANCELLED");
        return true;
    }

    @Override
    @Transactional
    public Integer promoteToPlan(Integer taskId, String userName, String planName) {
        SimulationTask task = simulationTaskMapper.selectById(taskId);
        if (task == null || !"COMPLETED".equals(task.getStatus())) return null;

        SimulationBatch batch = simulationBatchMapper.selectById(task.getBatchId());
        if (batch == null || !batch.getUserName().equals(userName)) return null;

        VoluntaryPlan sourcePlan = voluntaryPlanMapper.selectById(batch.getSourcePlanId());
        if (sourcePlan == null) return null;

        // Create new plan from task results
        VoluntaryPlan newPlan = new VoluntaryPlan();
        newPlan.setPlanName(planName);
        newPlan.setUserName(userName);
        newPlan.setScore(task.getParamScore() != null ? task.getParamScore() : sourcePlan.getScore());
        newPlan.setUserRank(task.getParamUserRank() != null ? task.getParamUserRank() : sourcePlan.getUserRank());
        newPlan.setSubjectType(sourcePlan.getSubjectType());
        newPlan.setRegionPref(task.getParamRegionPref() != null ? task.getParamRegionPref() : sourcePlan.getRegionPref());
        newPlan.setSchoolTier(task.getParamSchoolTier() != null ? task.getParamSchoolTier() : sourcePlan.getSchoolTier());
        newPlan.setMajorPref(task.getParamMajorPref() != null ? task.getParamMajorPref() : sourcePlan.getMajorPref());
        newPlan.setBatchName(task.getParamBatchName() != null ? task.getParamBatchName() : sourcePlan.getBatchName());
        newPlan.setVersion(1);
        newPlan.setParentId(sourcePlan.getId());
        newPlan.setStatus(0);
        newPlan.setTotalRiskScore(task.getTotalRiskScore());
        newPlan.setCreateTime(LocalDateTime.now());
        newPlan.setUpdateTime(LocalDateTime.now());
        voluntaryPlanMapper.insert(newPlan);

        // Copy simulation task schools to plan_school
        List<SimulationTaskSchool> taskSchools = simulationTaskSchoolMapper.selectByTaskIdOrdered(taskId);
        List<PlanSchool> planSchools = new ArrayList<>();
        for (SimulationTaskSchool sts : taskSchools) {
            PlanSchool ps = new PlanSchool();
            ps.setPlanId(newPlan.getId());
            ps.setSchoolId(sts.getSchoolId());
            ps.setSchoolName(sts.getSchoolName());
            ps.setCategory(sts.getCategory());
            ps.setSortOrder(sts.getSortOrder());
            ps.setAdmissionProb(sts.getAdmissionProb());
            ps.setMajorAdjustRisk(sts.getMajorAdjustRisk());
            ps.setPopularityScore(sts.getPopularityScore());
            ps.setPopularityTrend(sts.getPopularityTrend());
            ps.setRankFluctuation(sts.getRankFluctuation());
            ps.setAvgRank3yr(sts.getAvgRank3yr());
            ps.setRankStdDev(sts.getRankStdDev());
            ps.setRankTrend(sts.getRankTrend());
            ps.setSelectedMajors(sts.getSelectedMajors());
            ps.setCreateTime(LocalDateTime.now());
            planSchoolMapper.insert(ps);
            planSchools.add(ps);
        }

        // Save initial version snapshot
        saveVersionSnapshot(newPlan.getId(), 1, planSchools);

        return newPlan.getId();
    }

    @Override
    @Transactional
    public Boolean rollbackFromSnapshot(Integer taskId, String userName) {
        SimulationTask task = simulationTaskMapper.selectById(taskId);
        if (task == null || task.getRiskSnapshot() == null) return false;

        SimulationBatch batch = simulationBatchMapper.selectById(task.getBatchId());
        if (batch == null || !batch.getUserName().equals(userName)) return false;

        VoluntaryPlan sourcePlan = voluntaryPlanMapper.selectById(batch.getSourcePlanId());
        if (sourcePlan == null || !sourcePlan.getUserName().equals(userName)) return false;

        // Deserialize risk snapshot
        Map<String, Map<String, Object>> snapshot;
        try {
            snapshot = objectMapper.readValue(task.getRiskSnapshot(),
                    new TypeReference<Map<String, Map<String, Object>>>() {});
        } catch (Exception e) {
            return false;
        }

        // Update source plan schools from snapshot
        List<PlanSchool> currentSchools = planSchoolMapper.selectByPlanIdOrdered(sourcePlan.getId());
        for (PlanSchool ps : currentSchools) {
            Map<String, Object> schoolData = snapshot.get("school_" + ps.getSchoolId());
            if (schoolData == null) continue;

            int r2020 = toInt(schoolData.get("rank2020"));
            int r2021 = toInt(schoolData.get("rank2021"));
            int r2022 = toInt(schoolData.get("rank2022"));

            if (r2020 > 0 || r2021 > 0 || r2022 > 0) {
                double avgRank = PlanCalculationUtils.avgRank(r2020, r2021, r2022);
                double stdDevVal = PlanCalculationUtils.calcRankStdDev(r2020, r2021, r2022);
                BigDecimal admissionProb = PlanCalculationUtils.calcAdmissionProb(
                        avgRank, stdDevVal, sourcePlan.getUserRank(), ps.getCategory());
                ps.setAdmissionProb(admissionProb);
                ps.setAvgRank3yr(BigDecimal.valueOf(avgRank));
                ps.setRankStdDev(BigDecimal.valueOf(stdDevVal));
                ps.setRankFluctuation(PlanCalculationUtils.buildRankFluctuationExplanation(
                        r2020, r2021, r2022, sourcePlan.getUserRank()) + "(快照回滚)");
                ps.setRankTrend(PlanCalculationUtils.calcRankTrend(r2020, r2021, r2022));
                planSchoolMapper.updateById(ps);
            }
        }

        // Increment version and save snapshot
        int newVersion = sourcePlan.getVersion() + 1;
        sourcePlan.setVersion(newVersion);
        sourcePlan.setUpdateTime(LocalDateTime.now());
        voluntaryPlanMapper.updateById(sourcePlan);

        List<PlanSchool> updatedSchools = planSchoolMapper.selectByPlanIdOrdered(sourcePlan.getId());
        saveVersionSnapshot(sourcePlan.getId(), newVersion, updatedSchools);

        return true;
    }

    @Override
    public List<SimulationBatchVO> listUserBatches(String userName) {
        List<SimulationBatch> batches = simulationBatchMapper.selectByUserName(userName);
        return batches.stream().map(this::buildBatchVO).collect(Collectors.toList());
    }

    // ══════════════════════════════════════════════
    // Private helper methods
    // ══════════════════════════════════════════════

    private SimulationBatchVO buildBatchVO(SimulationBatch batch) {
        SimulationBatchVO vo = new SimulationBatchVO();
        vo.setBatchId(batch.getId());
        vo.setBatchName(batch.getBatchName());
        vo.setSourcePlanId(batch.getSourcePlanId());
        vo.setStatus(batch.getStatus());
        vo.setSequenceNumber(batch.getSequenceNumber());
        vo.setTotalTasks(batch.getTotalTasks());
        vo.setCompletedTasks(batch.getCompletedTasks());
        vo.setFailedTasks(batch.getFailedTasks());
        vo.setCreateTime(batch.getCreateTime());

        // Source plan name
        VoluntaryPlan sourcePlan = voluntaryPlanMapper.selectById(batch.getSourcePlanId());
        vo.setSourcePlanName(sourcePlan != null ? sourcePlan.getPlanName() : "已删除方案");

        // Task summaries
        List<SimulationTask> tasks = simulationTaskMapper.selectByBatchId(batch.getId());
        List<SimulationTaskSummaryVO> taskSummaries = new ArrayList<>();
        for (SimulationTask task : tasks) {
            SimulationTaskSummaryVO tsvo = new SimulationTaskSummaryVO();
            tsvo.setTaskId(task.getId());
            tsvo.setTaskLabel(task.getTaskLabel());
            tsvo.setStatus(task.getStatus());
            tsvo.setTotalRiskScore(task.getTotalRiskScore());
            tsvo.setReachCount(task.getReachCount());
            tsvo.setMatchCount(task.getMatchCount());
            tsvo.setSafetyCount(task.getSafetyCount());
            tsvo.setErrorMessage(task.getErrorMessage());
            tsvo.setComputeStart(task.getComputeStart());
            tsvo.setComputeEnd(task.getComputeEnd());

            // Effective parameters
            if (sourcePlan != null) {
                tsvo.setEffectiveScore(task.getParamScore() != null ? task.getParamScore() : sourcePlan.getScore());
                tsvo.setEffectiveUserRank(task.getParamUserRank() != null ? task.getParamUserRank() : sourcePlan.getUserRank());
                tsvo.setEffectiveBatchName(task.getParamBatchName() != null ? task.getParamBatchName() : sourcePlan.getBatchName());
            }
            taskSummaries.add(tsvo);
        }
        vo.setTasks(taskSummaries);
        return vo;
    }

    private SimulationSchoolVO mapToSimulationSchoolVO(SimulationTaskSchool sts) {
        SimulationSchoolVO svo = new SimulationSchoolVO();
        svo.setTaskSchoolId(sts.getId());
        svo.setSchoolId(sts.getSchoolId());
        svo.setSchoolName(sts.getSchoolName());
        svo.setCategory(sts.getCategory());
        svo.setSortOrder(sts.getSortOrder());
        svo.setAdmissionProb(sts.getAdmissionProb());
        svo.setAdmissionProbLevel(sts.getAdmissionProbLevel());
        svo.setMajorAdjustRisk(sts.getMajorAdjustRisk());
        svo.setMajorAdjustRiskLevel(sts.getMajorAdjustRiskLevel());
        svo.setPopularityScore(sts.getPopularityScore());
        svo.setPopularityTrend(sts.getPopularityTrend());
        svo.setRankFluctuation(sts.getRankFluctuation());
        svo.setAvgRank3yr(sts.getAvgRank3yr());
        svo.setRankStdDev(sts.getRankStdDev());
        svo.setRankTrend(sts.getRankTrend());
        svo.setSourceAdmissionProb(sts.getSourceAdmissionProb());
        svo.setSourceCategory(sts.getSourceCategory());
        svo.setProbDelta(sts.getProbDelta());
        svo.setCategoryChanged(sts.getCategoryChanged() != null && sts.getCategoryChanged() == 1);

        // Selected majors
        if (sts.getSelectedMajors() != null && !sts.getSelectedMajors().isBlank()) {
            svo.setSelectedMajors(Arrays.asList(sts.getSelectedMajors().split(",")));
        } else {
            svo.setSelectedMajors(Collections.emptyList());
        }

        // Probability explanation
        ProbabilityExplanationVO probExpl = new ProbabilityExplanationVO();
        probExpl.setRankRatio(sts.getProbRankRatio());
        probExpl.setBaseProbability(sts.getProbBaseValue());
        probExpl.setStabilityFactor(sts.getProbStabilityFactor());
        probExpl.setCategoryAdjustment(sts.getProbCategoryAdj());
        probExpl.setFinalProbability(sts.getAdmissionProb());
        probExpl.setAvgRank3yr(sts.getAvgRank3yr());
        probExpl.setRankStdDev(sts.getRankStdDev());
        probExpl.setExplanation(sts.getProbExplanation());
        svo.setProbabilityExplanation(probExpl);

        return svo;
    }

    private Map<Integer, SimulationSchoolVO> buildSchoolMap(SimulationTaskDetailVO detail) {
        Map<Integer, SimulationSchoolVO> map = new LinkedHashMap<>();
        for (SimulationSchoolVO s : allSchools(detail)) {
            map.put(s.getSchoolId(), s);
        }
        return map;
    }

    private List<SimulationSchoolVO> allSchools(SimulationTaskDetailVO detail) {
        List<SimulationSchoolVO> all = new ArrayList<>();
        if (detail.getReachSchools() != null) all.addAll(detail.getReachSchools());
        if (detail.getMatchSchools() != null) all.addAll(detail.getMatchSchools());
        if (detail.getSafetySchools() != null) all.addAll(detail.getSafetySchools());
        return all;
    }

    private PlanRiskSummary buildSimulationRiskSummary(
            List<SimulationSchoolVO> reach, List<SimulationSchoolVO> match, List<SimulationSchoolVO> safety) {
        PlanRiskSummary summary = new PlanRiskSummary();
        summary.setReachCount(reach.size());
        summary.setMatchCount(match.size());
        summary.setSafetyCount(safety.size());

        List<SimulationSchoolVO> all = new ArrayList<>();
        all.addAll(reach);
        all.addAll(match);
        all.addAll(safety);

        if (all.isEmpty()) {
            summary.setOverallRiskScore(BigDecimal.ZERO);
            summary.setOverallRiskLevel("无数据");
            summary.setAvgAdmissionProb(BigDecimal.ZERO);
            summary.setAvgMajorAdjustRisk(BigDecimal.ZERO);
            summary.setRecommendation("未找到匹配的院校。");
            return summary;
        }

        double avgProb = all.stream()
                .mapToDouble(v -> v.getAdmissionProb() != null ? v.getAdmissionProb().doubleValue() : 50)
                .average().orElse(50);
        double avgRisk = all.stream()
                .mapToDouble(v -> v.getMajorAdjustRisk() != null ? v.getMajorAdjustRisk().doubleValue() : 50)
                .average().orElse(50);

        summary.setAvgAdmissionProb(BigDecimal.valueOf(avgProb).setScale(2, java.math.RoundingMode.HALF_UP));
        summary.setAvgMajorAdjustRisk(BigDecimal.valueOf(avgRisk).setScale(2, java.math.RoundingMode.HALF_UP));

        double reachRatio = (double) reach.size() / all.size();
        double riskScore = (100 - avgProb) * 0.4 + avgRisk * 0.3 + reachRatio * 30 * 0.3;
        riskScore = Math.max(0, Math.min(100, riskScore));
        summary.setOverallRiskScore(BigDecimal.valueOf(riskScore).setScale(2, java.math.RoundingMode.HALF_UP));

        if (riskScore <= 30) summary.setOverallRiskLevel("低风险");
        else if (riskScore <= 60) summary.setOverallRiskLevel("中风险");
        else summary.setOverallRiskLevel("高风险");

        StringBuilder rec = new StringBuilder();
        rec.append(String.format("模拟方案包含%d冲%d稳%d保，共%d所院校。",
                reach.size(), match.size(), safety.size(), all.size()));
        if (reachRatio > 0.5) rec.append("冲一冲院校占比偏高，建议增加稳妥和保底院校。");
        if (avgRisk > 60) rec.append("专业调剂风险较高，建议勾选服从专业调剂。");
        if (avgProb >= 60 && reachRatio <= 0.4) rec.append("方案整体较为稳健，录取概率良好。");
        summary.setRecommendation(rec.toString());

        return summary;
    }

    private List<String> detectConflicts(List<SimulationTaskSchool> schools) {
        Map<Integer, List<String>> schoolCategories = new LinkedHashMap<>();
        for (SimulationTaskSchool s : schools) {
            schoolCategories.computeIfAbsent(s.getSchoolId(), k -> new ArrayList<>())
                    .add(s.getCategory());
        }
        List<String> warnings = new ArrayList<>();
        for (Map.Entry<Integer, List<String>> entry : schoolCategories.entrySet()) {
            if (entry.getValue().size() > 1) {
                String schoolName = schools.stream()
                        .filter(s -> s.getSchoolId().equals(entry.getKey()))
                        .findFirst().map(SimulationTaskSchool::getSchoolName).orElse("未知");
                warnings.add(String.format("院校[%s]同时出现在%s类别中，存在冲突",
                        schoolName, String.join("、", entry.getValue())));
            }
        }
        return warnings;
    }

    private void addParamDiff(Map<String, String[]> paramDiffs, String key, String valueA, String valueB) {
        if (!Objects.equals(valueA, valueB)) {
            paramDiffs.put(key, new String[]{valueA, valueB});
        }
    }

    private void saveVersionSnapshot(Integer planId, int version, List<PlanSchool> schools) {
        PlanVersionLog log = new PlanVersionLog();
        log.setPlanId(planId);
        log.setVersion(version);
        try {
            log.setSnapshotData(objectMapper.writeValueAsString(schools));
        } catch (Exception e) {
            log.setSnapshotData("[]");
        }
        log.setCreateTime(LocalDateTime.now());
        planVersionLogMapper.insert(log);
    }

    private int toInt(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(obj.toString());
        } catch (Exception e) {
            return 0;
        }
    }
}
