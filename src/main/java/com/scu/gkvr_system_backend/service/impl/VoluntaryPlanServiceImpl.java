package com.scu.gkvr_system_backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scu.gkvr_system_backend.dto.PlanGenerateRequestDTO;
import com.scu.gkvr_system_backend.dto.PlanSaveRequestDTO;
import com.scu.gkvr_system_backend.dto.PlanSchoolDTO;
import com.scu.gkvr_system_backend.dto.SchoolReorderDTO;
import com.scu.gkvr_system_backend.mapper.PlanSchoolMapper;
import com.scu.gkvr_system_backend.mapper.PlanVersionLogMapper;
import com.scu.gkvr_system_backend.mapper.ScoreRankMapper;
import com.scu.gkvr_system_backend.mapper.VoluntaryPlanMapper;
import com.scu.gkvr_system_backend.pojo.PlanSchool;
import com.scu.gkvr_system_backend.pojo.PlanVersionLog;
import com.scu.gkvr_system_backend.pojo.ScoreRank;
import com.scu.gkvr_system_backend.pojo.VoluntaryPlan;
import com.scu.gkvr_system_backend.service.VoluntaryPlanService;
import com.scu.gkvr_system_backend.utils.PlanCalculationUtils;
import com.scu.gkvr_system_backend.vo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class VoluntaryPlanServiceImpl extends ServiceImpl<VoluntaryPlanMapper, VoluntaryPlan>
        implements VoluntaryPlanService {

    @Autowired
    private PlanSchoolMapper planSchoolMapper;

    @Autowired
    private PlanVersionLogMapper planVersionLogMapper;

    @Autowired
    private ScoreRankMapper scoreRankMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> generatePlans(PlanGenerateRequestDTO request) {
        Map<String, Object> result = new HashMap<>();

        // 1. 查询位次
        LambdaQueryWrapper<ScoreRank> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScoreRank::getScore, String.valueOf(request.getScore()));
        ScoreRank scoreRank = scoreRankMapper.selectOne(wrapper);

        if (scoreRank == null) {
            result.put("error", "未找到对应的分数排名信息");
            return result;
        }

        int userRank = request.getUserRank() != null ? request.getUserRank() : scoreRank.getRank();
        String batchName = request.getBatchName() != null ? request.getBatchName() : scoreRank.getBatchName();

        // 2. 解析筛选条件
        boolean require985 = "985".equals(request.getSchoolTier());
        boolean require211 = "211".equals(request.getSchoolTier());
        boolean requireDoubleHigh = "双一流".equals(request.getSchoolTier());
        List<String> provinces = request.getRegionPref();

        // 3. 三档位次区间
        int reachUpper = (int) (userRank * 0.6);
        int reachLower = (int) (userRank * 0.9);
        int matchUpper = (int) (userRank * 0.9);
        int matchLower = (int) (userRank * 1.2);
        int safetyUpper = (int) (userRank * 1.2);
        int safetyLower = (int) (userRank * 1.8);

        // 4. 查询各档候选院校
        List<Map<String, Object>> reachCandidates = planSchoolMapper.selectCandidateSchools(
                reachUpper, reachLower, provinces, require985, require211, requireDoubleHigh);
        List<Map<String, Object>> matchCandidates = planSchoolMapper.selectCandidateSchools(
                matchUpper, matchLower, provinces, require985, require211, requireDoubleHigh);
        List<Map<String, Object>> safetyCandidates = planSchoolMapper.selectCandidateSchools(
                safetyUpper, safetyLower, provinces, require985, require211, requireDoubleHigh);

        // 5. 同校去重: 保留最优档位(冲 > 稳 > 保)
        Set<Integer> seenSchoolIds = new HashSet<>();
        reachCandidates = deduplicateSchools(reachCandidates, seenSchoolIds);
        matchCandidates = deduplicateSchools(matchCandidates, seenSchoolIds);
        safetyCandidates = deduplicateSchools(safetyCandidates, seenSchoolIds);

        // 6. 逐校计算指标
        List<PlanSchoolVO> reachSchools = buildSchoolVOs(reachCandidates, "冲", userRank, request.getScore(), batchName, request.getMajorPref());
        List<PlanSchoolVO> matchSchools = buildSchoolVOs(matchCandidates, "稳", userRank, request.getScore(), batchName, request.getMajorPref());
        List<PlanSchoolVO> safetySchools = buildSchoolVOs(safetyCandidates, "保", userRank, request.getScore(), batchName, request.getMajorPref());

        // 7. 构建风险摘要
        List<PlanSchool> allSchoolEntities = new ArrayList<>();
        addToEntityList(allSchoolEntities, reachSchools);
        addToEntityList(allSchoolEntities, matchSchools);
        addToEntityList(allSchoolEntities, safetySchools);
        PlanRiskSummary riskSummary = PlanCalculationUtils.buildRiskSummary(allSchoolEntities);

        result.put("reachSchools", reachSchools);
        result.put("matchSchools", matchSchools);
        result.put("safetySchools", safetySchools);
        result.put("riskSummary", riskSummary);
        result.put("scoreRank", scoreRank);
        result.put("userRank", userRank);
        result.put("batchName", batchName);
        return result;
    }

    @Override
    @Transactional
    public Integer savePlan(PlanSaveRequestDTO request) {
        VoluntaryPlan plan = new VoluntaryPlan();
        plan.setPlanName(request.getPlanName());
        plan.setUserName(request.getUserName());
        plan.setScore(request.getScore());
        plan.setUserRank(request.getUserRank());
        plan.setSubjectType(request.getSubjectType());
        plan.setRegionPref(request.getRegionPref());
        plan.setSchoolTier(request.getSchoolTier());
        plan.setMajorPref(request.getMajorPref());
        plan.setBatchName(request.getBatchName());
        plan.setVersion(1);
        plan.setStatus(0);
        plan.setCreateTime(LocalDateTime.now());
        plan.setUpdateTime(LocalDateTime.now());
        this.baseMapper.insert(plan);

        int sortOrder = 0;
        for (PlanSchoolDTO dto : request.getSchools()) {
            PlanSchool ps = new PlanSchool();
            ps.setPlanId(plan.getId());
            ps.setSchoolId(dto.getSchoolId());
            ps.setSchoolName(dto.getSchoolName());
            ps.setCategory(dto.getCategory());
            ps.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : sortOrder++);
            ps.setAdmissionProb(dto.getAdmissionProb() != null ? dto.getAdmissionProb() : BigDecimal.ZERO);
            ps.setMajorAdjustRisk(dto.getMajorAdjustRisk() != null ? dto.getMajorAdjustRisk() : BigDecimal.ZERO);
            ps.setPopularityScore(dto.getPopularityScore());
            ps.setPopularityTrend(dto.getPopularityTrend());
            ps.setRankFluctuation(dto.getRankFluctuation());
            ps.setAvgRank3yr(dto.getAvgRank3yr());
            ps.setRankStdDev(dto.getRankStdDev());
            ps.setRankTrend(dto.getRankTrend());
            ps.setSelectedMajors(dto.getSelectedMajors() != null ? String.join(",", dto.getSelectedMajors()) : null);
            ps.setCreateTime(LocalDateTime.now());
            planSchoolMapper.insert(ps);
        }

        // 计算总风险分
        List<PlanSchool> allSchools = queryPlanSchools(plan.getId());
        PlanRiskSummary summary = PlanCalculationUtils.buildRiskSummary(allSchools);
        plan.setTotalRiskScore(summary.getTotalRiskScore());
        this.baseMapper.updateById(plan);

        return plan.getId();
    }

    @Override
    public PlanDetailVO getPlanDetail(Integer planId, String userName) {
        VoluntaryPlan plan = getPlanWithOwnerCheck(planId, userName);
        if (plan == null) {
            return null;
        }
        return buildPlanDetailVO(plan);
    }

    @Override
    public List<VoluntaryPlan> listPlans(String userName) {
        LambdaQueryWrapper<VoluntaryPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VoluntaryPlan::getUserName, userName)
                .orderByDesc(VoluntaryPlan::getUpdateTime);
        return this.baseMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public boolean deletePlan(Integer planId, String userName) {
        VoluntaryPlan plan = getPlanWithOwnerCheck(planId, userName);
        if (plan == null) {
            return false;
        }
        // 先删子表（plan_school, plan_version_log），再删主表
        LambdaQueryWrapper<PlanSchool> schoolWrapper = new LambdaQueryWrapper<>();
        schoolWrapper.eq(PlanSchool::getPlanId, planId);
        planSchoolMapper.delete(schoolWrapper);

        LambdaQueryWrapper<PlanVersionLog> logWrapper = new LambdaQueryWrapper<>();
        logWrapper.eq(PlanVersionLog::getPlanId, planId);
        planVersionLogMapper.delete(logWrapper);

        this.baseMapper.deleteById(planId);
        return true;
    }

    @Override
    @Transactional
    public Integer copyPlan(Integer planId, String userName) {
        VoluntaryPlan source = getPlanWithOwnerCheck(planId, userName);
        if (source == null) {
            return null;
        }

        // 深拷贝方案
        VoluntaryPlan copy = new VoluntaryPlan();
        copy.setPlanName(source.getPlanName() + "(副本)");
        copy.setUserName(source.getUserName());
        copy.setScore(source.getScore());
        copy.setUserRank(source.getUserRank());
        copy.setSubjectType(source.getSubjectType());
        copy.setRegionPref(source.getRegionPref());
        copy.setSchoolTier(source.getSchoolTier());
        copy.setMajorPref(source.getMajorPref());
        copy.setBatchName(source.getBatchName());
        copy.setVersion(1);
        copy.setParentId(source.getId());
        copy.setStatus(0);
        copy.setTotalRiskScore(source.getTotalRiskScore());
        copy.setCreateTime(LocalDateTime.now());
        copy.setUpdateTime(LocalDateTime.now());
        this.baseMapper.insert(copy);

        // 深拷贝院校
        List<PlanSchool> sourceSchools = queryPlanSchools(planId);
        for (PlanSchool ps : sourceSchools) {
            PlanSchool cloned = new PlanSchool();
            cloned.setPlanId(copy.getId());
            cloned.setSchoolId(ps.getSchoolId());
            cloned.setSchoolName(ps.getSchoolName());
            cloned.setCategory(ps.getCategory());
            cloned.setSortOrder(ps.getSortOrder());
            cloned.setAdmissionProb(ps.getAdmissionProb());
            cloned.setMajorAdjustRisk(ps.getMajorAdjustRisk());
            cloned.setPopularityScore(ps.getPopularityScore());
            cloned.setPopularityTrend(ps.getPopularityTrend());
            cloned.setRankFluctuation(ps.getRankFluctuation());
            cloned.setAvgRank3yr(ps.getAvgRank3yr());
            cloned.setRankStdDev(ps.getRankStdDev());
            cloned.setRankTrend(ps.getRankTrend());
            cloned.setSelectedMajors(ps.getSelectedMajors());
            cloned.setCreateTime(LocalDateTime.now());
            planSchoolMapper.insert(cloned);
        }

        return copy.getId();
    }

    @Override
    @Transactional
    public boolean reorderSchools(SchoolReorderDTO request) {
        VoluntaryPlan plan = getPlanWithOwnerCheck(request.getPlanId(), request.getUserName());
        if (plan == null) {
            return false;
        }
        for (SchoolReorderDTO.SchoolOrderItem item : request.getItems()) {
            PlanSchool ps = planSchoolMapper.selectById(item.getPlanSchoolId());
            if (ps != null && ps.getPlanId().equals(request.getPlanId())) {
                ps.setSortOrder(item.getSortOrder());
                planSchoolMapper.updateById(ps);
            }
        }
        plan.setUpdateTime(LocalDateTime.now());
        this.baseMapper.updateById(plan);
        return true;
    }

    @Override
    @Transactional
    public PlanDetailVO reevaluatePlan(Integer planId, String userName) {
        VoluntaryPlan plan = getPlanWithOwnerCheck(planId, userName);
        if (plan == null) {
            return null;
        }

        // 1. 保存当前版本快照
        saveVersionSnapshot(plan);

        // 2. 加载院校列表并重新计算
        List<PlanSchool> schools = queryPlanSchools(planId);
        for (PlanSchool ps : schools) {
            recalculateSchoolMetrics(ps, plan.getScore(), plan.getUserRank(), plan.getBatchName());
            planSchoolMapper.updateById(ps);
        }

        // 3. 更新方案
        plan.setVersion(plan.getVersion() + 1);
        PlanRiskSummary summary = PlanCalculationUtils.buildRiskSummary(schools);
        plan.setTotalRiskScore(summary.getTotalRiskScore());
        plan.setUpdateTime(LocalDateTime.now());
        this.baseMapper.updateById(plan);

        return buildPlanDetailVO(plan);
    }

    @Override
    public PlanComparisonVO comparePlans(Integer planIdA, Integer planIdB, String userName) {
        VoluntaryPlan planA = getPlanWithOwnerCheck(planIdA, userName);
        VoluntaryPlan planB = getPlanWithOwnerCheck(planIdB, userName);
        if (planA == null || planB == null) {
            return null;
        }

        List<PlanSchool> schoolsA = queryPlanSchools(planIdA);
        List<PlanSchool> schoolsB = queryPlanSchools(planIdB);

        Map<Integer, PlanSchool> mapA = schoolsA.stream()
                .collect(Collectors.toMap(PlanSchool::getSchoolId, s -> s, (a, b) -> a));
        Map<Integer, PlanSchool> mapB = schoolsB.stream()
                .collect(Collectors.toMap(PlanSchool::getSchoolId, s -> s, (a, b) -> a));

        Set<Integer> allIds = new HashSet<>();
        allIds.addAll(mapA.keySet());
        allIds.addAll(mapB.keySet());

        List<PlanComparisonVO.SchoolDiff> diffs = new ArrayList<>();
        for (Integer schoolId : allIds) {
            PlanSchool inA = mapA.get(schoolId);
            PlanSchool inB = mapB.get(schoolId);
            PlanComparisonVO.SchoolDiff diff = new PlanComparisonVO.SchoolDiff();
            diff.setSchoolId(schoolId);

            if (inA != null && inB == null) {
                diff.setSchoolName(inA.getSchoolName());
                diff.setCategoryA(inA.getCategory());
                diff.setAdmissionProbA(inA.getAdmissionProb());
                diff.setChangeType("removed");
            } else if (inA == null && inB != null) {
                diff.setSchoolName(inB.getSchoolName());
                diff.setCategoryB(inB.getCategory());
                diff.setAdmissionProbB(inB.getAdmissionProb());
                diff.setChangeType("added");
            } else {
                diff.setSchoolName(inA.getSchoolName());
                diff.setCategoryA(inA.getCategory());
                diff.setCategoryB(inB.getCategory());
                diff.setAdmissionProbA(inA.getAdmissionProb());
                diff.setAdmissionProbB(inB.getAdmissionProb());
                diff.setChangeType("changed");
            }
            diffs.add(diff);
        }

        PlanComparisonVO comparison = new PlanComparisonVO();
        comparison.setPlanIdA(planIdA);
        comparison.setPlanIdB(planIdB);
        comparison.setVersionA(planA.getVersion());
        comparison.setVersionB(planB.getVersion());
        comparison.setRiskScoreA(planA.getTotalRiskScore());
        comparison.setRiskScoreB(planB.getTotalRiskScore());
        comparison.setDiffs(diffs);
        return comparison;
    }

    // ===== 私有辅助方法 =====

    private VoluntaryPlan getPlanWithOwnerCheck(Integer planId, String userName) {
        LambdaQueryWrapper<VoluntaryPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VoluntaryPlan::getId, planId)
                .eq(VoluntaryPlan::getUserName, userName);
        return this.baseMapper.selectOne(wrapper);
    }

    private List<PlanSchool> queryPlanSchools(Integer planId) {
        LambdaQueryWrapper<PlanSchool> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PlanSchool::getPlanId, planId)
                .orderByAsc(PlanSchool::getSortOrder);
        return planSchoolMapper.selectList(wrapper);
    }

    private List<Map<String, Object>> deduplicateSchools(List<Map<String, Object>> candidates, Set<Integer> seen) {
        List<Map<String, Object>> unique = new ArrayList<>();
        for (Map<String, Object> c : candidates) {
            Integer schoolId = ((Number) c.get("schoolId")).intValue();
            if (seen.add(schoolId)) {
                unique.add(c);
            }
        }
        return unique;
    }

    private List<PlanSchoolVO> buildSchoolVOs(List<Map<String, Object>> candidates, String category,
                                              int userRank, int score, String batchName,
                                              List<String> majorPref) {
        List<PlanSchoolVO> voList = new ArrayList<>();
        int sortOrder = 0;
        for (Map<String, Object> c : candidates) {
            int schoolId = ((Number) c.get("schoolId")).intValue();
            int rank2020 = getIntValue(c, "rank2020");
            int rank2021 = getIntValue(c, "rank2021");
            int rank2022 = getIntValue(c, "rank2022");
            int monthView = getIntValue(c, "monthView");
            String totalView = c.get("totalView") != null ? c.get("totalView").toString() : "0";

            double avgRank = (rank2020 + rank2021 + rank2022) / 3.0;
            double stdDev = PlanCalculationUtils.calcRankStdDev(rank2020, rank2021, rank2022);
            int admProb = PlanCalculationUtils.calcAdmissionProb(avgRank, userRank, stdDev);

            List<Map<String, Object>> majorScores = planSchoolMapper.selectMajorScoresForSchool(schoolId, batchName);

            // 专业偏好过滤
            if (majorPref != null && !majorPref.isEmpty() && majorScores != null) {
                majorScores = majorScores.stream()
                        .filter(m -> {
                            String name = (String) m.get("majorName");
                            return name != null && majorPref.stream().anyMatch(name::contains);
                        })
                        .collect(Collectors.toList());
            }

            double majRisk = PlanCalculationUtils.calcMajorAdjustRisk(score, majorScores);
            double popScore = PlanCalculationUtils.calcPopularityScore(monthView);
            String popTrend = PlanCalculationUtils.calcPopularityTrend(monthView, totalView);
            String rankTrend = PlanCalculationUtils.calcRankTrend(rank2020, rank2021, rank2022);
            String fluctuation = PlanCalculationUtils.buildRankFluctuationExplanation(stdDev, avgRank);

            List<String> selectedMajors = null;
            if (majorScores != null && !majorScores.isEmpty()) {
                selectedMajors = majorScores.stream()
                        .map(m -> (String) m.get("majorName"))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }

            PlanSchoolVO vo = new PlanSchoolVO();
            vo.setSchoolId(schoolId);
            vo.setSchoolName((String) c.get("schoolName"));
            vo.setCategory(category);
            vo.setSortOrder(sortOrder++);
            vo.setAdmissionProb(BigDecimal.valueOf(admProb));
            vo.setAdmissionProbLevel(PlanCalculationUtils.probLevel(admProb));
            vo.setMajorAdjustRisk(BigDecimal.valueOf(majRisk).setScale(2, RoundingMode.HALF_UP));
            vo.setMajorAdjustRiskLevel(PlanCalculationUtils.riskLevel(majRisk));
            vo.setPopularityScore(BigDecimal.valueOf(popScore).setScale(2, RoundingMode.HALF_UP));
            vo.setPopularityTrend(popTrend);
            vo.setRankFluctuation(fluctuation);
            vo.setAvgRank3yr(BigDecimal.valueOf(avgRank).setScale(2, RoundingMode.HALF_UP));
            vo.setRankStdDev(BigDecimal.valueOf(stdDev).setScale(2, RoundingMode.HALF_UP));
            vo.setRankTrend(rankTrend);
            vo.setSelectedMajors(selectedMajors);
            voList.add(vo);
        }
        return voList;
    }

    private void addToEntityList(List<PlanSchool> entities, List<PlanSchoolVO> voList) {
        for (PlanSchoolVO vo : voList) {
            PlanSchool ps = new PlanSchool();
            ps.setCategory(vo.getCategory());
            ps.setAdmissionProb(vo.getAdmissionProb());
            entities.add(ps);
        }
    }

    private PlanDetailVO buildPlanDetailVO(VoluntaryPlan plan) {
        List<PlanSchool> schools = queryPlanSchools(plan.getId());

        List<PlanSchoolVO> reach = new ArrayList<>();
        List<PlanSchoolVO> match = new ArrayList<>();
        List<PlanSchoolVO> safety = new ArrayList<>();

        for (PlanSchool ps : schools) {
            PlanSchoolVO vo = convertToVO(ps);
            switch (ps.getCategory()) {
                case "冲" -> reach.add(vo);
                case "稳" -> match.add(vo);
                case "保" -> safety.add(vo);
            }
        }

        PlanRiskSummary riskSummary = PlanCalculationUtils.buildRiskSummary(schools);

        PlanDetailVO detail = new PlanDetailVO();
        detail.setPlanId(plan.getId());
        detail.setPlanName(plan.getPlanName());
        detail.setScore(plan.getScore());
        detail.setUserRank(plan.getUserRank());
        detail.setSubjectType(plan.getSubjectType());
        detail.setBatchName(plan.getBatchName());
        detail.setVersion(plan.getVersion());
        detail.setStatus(plan.getStatus());
        detail.setRiskSummary(riskSummary);
        detail.setReachSchools(reach);
        detail.setMatchSchools(match);
        detail.setSafetySchools(safety);
        detail.setCreateTime(plan.getCreateTime());
        detail.setUpdateTime(plan.getUpdateTime());
        return detail;
    }

    private PlanSchoolVO convertToVO(PlanSchool ps) {
        PlanSchoolVO vo = new PlanSchoolVO();
        vo.setId(ps.getId());
        vo.setSchoolId(ps.getSchoolId());
        vo.setSchoolName(ps.getSchoolName());
        vo.setCategory(ps.getCategory());
        vo.setSortOrder(ps.getSortOrder());
        vo.setAdmissionProb(ps.getAdmissionProb());
        vo.setAdmissionProbLevel(PlanCalculationUtils.probLevel(
                ps.getAdmissionProb() != null ? ps.getAdmissionProb().doubleValue() : 0));
        vo.setMajorAdjustRisk(ps.getMajorAdjustRisk());
        vo.setMajorAdjustRiskLevel(PlanCalculationUtils.riskLevel(
                ps.getMajorAdjustRisk() != null ? ps.getMajorAdjustRisk().doubleValue() : 0));
        vo.setPopularityScore(ps.getPopularityScore());
        vo.setPopularityTrend(ps.getPopularityTrend());
        vo.setRankFluctuation(ps.getRankFluctuation());
        vo.setAvgRank3yr(ps.getAvgRank3yr());
        vo.setRankStdDev(ps.getRankStdDev());
        vo.setRankTrend(ps.getRankTrend());
        if (ps.getSelectedMajors() != null && !ps.getSelectedMajors().isEmpty()) {
            vo.setSelectedMajors(Arrays.asList(ps.getSelectedMajors().split(",")));
        }
        return vo;
    }

    private void recalculateSchoolMetrics(PlanSchool ps, int score, int userRank, String batchName) {
        // 查询最新数据
        List<Map<String, Object>> candidates = planSchoolMapper.selectCandidateSchools(
                0, Integer.MAX_VALUE, null, false, false, false);

        Map<String, Object> schoolData = null;
        for (Map<String, Object> c : candidates) {
            if (((Number) c.get("schoolId")).intValue() == ps.getSchoolId()) {
                schoolData = c;
                break;
            }
        }

        if (schoolData == null) {
            return;
        }

        int rank2020 = getIntValue(schoolData, "rank2020");
        int rank2021 = getIntValue(schoolData, "rank2021");
        int rank2022 = getIntValue(schoolData, "rank2022");
        int monthView = getIntValue(schoolData, "monthView");
        String totalView = schoolData.get("totalView") != null ? schoolData.get("totalView").toString() : "0";

        double avgRank = (rank2020 + rank2021 + rank2022) / 3.0;
        double stdDev = PlanCalculationUtils.calcRankStdDev(rank2020, rank2021, rank2022);
        int admProb = PlanCalculationUtils.calcAdmissionProb(avgRank, userRank, stdDev);

        List<Map<String, Object>> majorScores = planSchoolMapper.selectMajorScoresForSchool(ps.getSchoolId(), batchName);
        double majRisk = PlanCalculationUtils.calcMajorAdjustRisk(score, majorScores);
        double popScore = PlanCalculationUtils.calcPopularityScore(monthView);

        ps.setAdmissionProb(BigDecimal.valueOf(admProb));
        ps.setMajorAdjustRisk(BigDecimal.valueOf(majRisk).setScale(2, RoundingMode.HALF_UP));
        ps.setPopularityScore(BigDecimal.valueOf(popScore).setScale(2, RoundingMode.HALF_UP));
        ps.setPopularityTrend(PlanCalculationUtils.calcPopularityTrend(monthView, totalView));
        ps.setAvgRank3yr(BigDecimal.valueOf(avgRank).setScale(2, RoundingMode.HALF_UP));
        ps.setRankStdDev(BigDecimal.valueOf(stdDev).setScale(2, RoundingMode.HALF_UP));
        ps.setRankTrend(PlanCalculationUtils.calcRankTrend(rank2020, rank2021, rank2022));
        ps.setRankFluctuation(PlanCalculationUtils.buildRankFluctuationExplanation(stdDev, avgRank));
    }

    private void saveVersionSnapshot(VoluntaryPlan plan) {
        List<PlanSchool> schools = queryPlanSchools(plan.getId());
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("plan", plan);
        snapshot.put("schools", schools);

        PlanVersionLog log = new PlanVersionLog();
        log.setPlanId(plan.getId());
        log.setVersion(plan.getVersion());
        log.setCreateTime(LocalDateTime.now());
        try {
            log.setSnapshotData(objectMapper.writeValueAsString(snapshot));
        } catch (JsonProcessingException e) {
            log.setSnapshotData("{}");
        }
        planVersionLogMapper.insert(log);
    }

    private int getIntValue(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return 0;
        return ((Number) val).intValue();
    }
}
