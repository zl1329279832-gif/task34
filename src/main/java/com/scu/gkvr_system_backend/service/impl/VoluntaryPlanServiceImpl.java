package com.scu.gkvr_system_backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scu.gkvr_system_backend.dto.PlanGenerateRequestDTO;
import com.scu.gkvr_system_backend.dto.PlanSaveRequestDTO;
import com.scu.gkvr_system_backend.dto.PlanSchoolDTO;
import com.scu.gkvr_system_backend.dto.SchoolReorderDTO;
import com.scu.gkvr_system_backend.mapper.*;
import com.scu.gkvr_system_backend.pojo.*;
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
 * 志愿方案服务实现
 */
@Service
public class VoluntaryPlanServiceImpl extends ServiceImpl<VoluntaryPlanMapper, VoluntaryPlan>
        implements VoluntaryPlanService {

    @Autowired
    private PlanSchoolMapper planSchoolMapper;

    @Autowired
    private PlanVersionLogMapper planVersionLogMapper;

    @Autowired
    private ScLiScoreMapper scLiScoreMapper;

    @Autowired
    private ScoreRankMapper scoreRankMapper;

    @Autowired
    private SchoolInfoMapper schoolInfoMapper;

    @Autowired
    private MajorScoreMapper majorScoreMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ══════════════════════════════════════════
    // 方案生成 (核心算法)
    // ══════════════════════════════════════════

    @Override
    public Map<String, Object> generatePlans(PlanGenerateRequestDTO request) {
        Map<String, Object> result = new HashMap<>();
        int userRank = request.getUserRank();
        int score = request.getScore();

        // ── 1. 位次边界处理 ──
        double[] ratios = PlanCalculationUtils.getAdjustedRatios(userRank);

        // ── 2. 批次推导 ──
        LambdaQueryWrapper<ScoreRank> rankWrapper = new LambdaQueryWrapper<>();
        rankWrapper.eq(ScoreRank::getScore, String.valueOf(score));
        ScoreRank scoreRank = scoreRankMapper.selectOne(rankWrapper);
        String batchName = (scoreRank != null) ? scoreRank.getBatchName() : "";

        // ── 3. 解析偏好 ──
        List<String> provinces = request.getRegionPref();
        boolean require985 = false, require211 = false, requireDoubleHigh = false;
        if (request.getSchoolTier() != null) {
            require985 = request.getSchoolTier().contains("985");
            require211 = request.getSchoolTier().contains("211");
            requireDoubleHigh = request.getSchoolTier().contains("双一流");
        }

        // ── 4. 三档位次区间 ──
        int rUpper = (int) (userRank * ratios[0]);
        int rLower = (int) (userRank * ratios[1]);
        int mUpper = (int) (userRank * ratios[2]);
        int mLower = (int) (userRank * ratios[3]);
        int sUpper = (int) (userRank * ratios[4]);
        int sLower = (int) (userRank * ratios[5]);

        // ── 5. 查询候选院校 ──
        List<Map<String, Object>> reachCandidates = planSchoolMapper.selectCandidateSchools(
                rUpper, rLower, provinces, require985, require211, requireDoubleHigh);
        List<Map<String, Object>> matchCandidates = planSchoolMapper.selectCandidateSchools(
                mUpper, mLower, provinces, require985, require211, requireDoubleHigh);
        List<Map<String, Object>> safetyCandidates = planSchoolMapper.selectCandidateSchools(
                sUpper, sLower, provinces, require985, require211, requireDoubleHigh);

        // ── 6. 计算指标并构建VO ──
        List<PlanSchoolVO> reachVOs = buildSchoolVOs(reachCandidates, "冲",
                userRank, score, batchName, request.getReachCount());
        List<PlanSchoolVO> matchVOs = buildSchoolVOs(matchCandidates, "稳",
                userRank, score, batchName, request.getMatchCount());
        List<PlanSchoolVO> safetyVOs = buildSchoolVOs(safetyCandidates, "保",
                userRank, score, batchName, request.getSafetyCount());

        // ── 7. 同校冲突检测(合并解释) ──
        List<String> conflictWarnings = PlanCalculationUtils.buildMergedConflictWarnings(
                reachVOs, matchVOs, safetyVOs);

        // ── 8. 风险汇总 ──
        PlanRiskSummary summary = PlanCalculationUtils.buildRiskSummary(reachVOs, matchVOs, safetyVOs);

        result.put("reach", reachVOs);
        result.put("match", matchVOs);
        result.put("safety", safetyVOs);
        result.put("riskSummary", summary);
        result.put("conflictWarnings", conflictWarnings);
        result.put("batchName", batchName);
        result.put("scoreRank", scoreRank);
        return result;
    }

    /**
     * 为某个类别(冲/稳/保)构建院校VO列表
     */
    private List<PlanSchoolVO> buildSchoolVOs(List<Map<String, Object>> candidates,
                                               String category, int userRank, int score,
                                               String batchName, int maxCount) {
        // 排序: 冲→平均位次升序(好学校在前); 稳/保→平均位次降序(安全学校在前)
        candidates.sort((a, b) -> {
            double avgA = PlanCalculationUtils.avgRank(
                    PlanCalculationUtils.toInt(a.get("rank2020")),
                    PlanCalculationUtils.toInt(a.get("rank2021")),
                    PlanCalculationUtils.toInt(a.get("rank2022")));
            double avgB = PlanCalculationUtils.avgRank(
                    PlanCalculationUtils.toInt(b.get("rank2020")),
                    PlanCalculationUtils.toInt(b.get("rank2021")),
                    PlanCalculationUtils.toInt(b.get("rank2022")));
            return "冲".equals(category)
                    ? Double.compare(avgA, avgB)
                    : Double.compare(avgB, avgA);
        });

        List<Map<String, Object>> selected = candidates.subList(0,
                Math.min(candidates.size(), maxCount));

        List<PlanSchoolVO> vos = new ArrayList<>();
        int order = 0;
        for (Map<String, Object> row : selected) {
            PlanSchoolVO vo = new PlanSchoolVO();
            vo.setPlanSchoolId(null);
            vo.setSchoolId(PlanCalculationUtils.toInt(row.get("schoolId")));
            vo.setSchoolName(PlanCalculationUtils.toStr(row.get("schoolName")));
            vo.setCategory(category);
            vo.setSortOrder(order++);

            int r2020 = PlanCalculationUtils.toInt(row.get("rank2020"));
            int r2021 = PlanCalculationUtils.toInt(row.get("rank2021"));
            int r2022 = PlanCalculationUtils.toInt(row.get("rank2022"));
            double avgRk = PlanCalculationUtils.avgRank(r2020, r2021, r2022);
            double stdDev = PlanCalculationUtils.calcRankStdDev(r2020, r2021, r2022);

            vo.setAvgRank3yr(BigDecimal.valueOf(avgRk).setScale(2, RoundingMode.HALF_UP));
            vo.setRankStdDev(BigDecimal.valueOf(stdDev).setScale(2, RoundingMode.HALF_UP));

            // 录取概率
            vo.setAdmissionProb(PlanCalculationUtils.calcAdmissionProb(avgRk, stdDev, userRank, category));
            vo.setAdmissionProbLevel(PlanCalculationUtils.probLevel(vo.getAdmissionProb()));

            // 调剂风险 (跨批次去重)
            List<Map<String, Object>> majorRows = PlanCalculationUtils.deduplicateMajorScores(
                    planSchoolMapper.selectMajorScoresForSchool(vo.getSchoolId(), batchName));
            vo.setMajorAdjustRisk(PlanCalculationUtils.calcMajorAdjustRisk(majorRows, score));
            vo.setMajorAdjustRiskLevel(PlanCalculationUtils.riskLevel(vo.getMajorAdjustRisk()));

            // 热度
            int monthView = PlanCalculationUtils.toInt(row.get("monthView"));
            int totalView = PlanCalculationUtils.toInt(row.get("totalView"));
            vo.setPopularityScore(PlanCalculationUtils.calcPopularityScore(monthView, totalView));
            vo.setPopularityTrend(PlanCalculationUtils.calcPopularityTrend(monthView, totalView));

            // 位次波动
            vo.setRankFluctuation(PlanCalculationUtils.buildRankFluctuationExplanation(
                    r2020, r2021, r2022, userRank));
            vo.setRankTrend(PlanCalculationUtils.calcRankTrend(r2020, r2021, r2022));

            // 专业维度风险 (已去重)
            vo.setMajorDetails(buildMajorRiskDetails(majorRows, score));

            vos.add(vo);
        }
        return vos;
    }

    /**
     * 构建专业维度风险详情列表
     */
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

    // ══════════════════════════════════════════
    // 保存方案
    // ══════════════════════════════════════════

    @Override
    @Transactional(rollbackFor = Exception.class)
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

        this.baseMapper.insert(plan);

        List<PlanSchool> schoolEntities = new ArrayList<>();
        List<BigDecimal> admissionProbs = new ArrayList<>();
        List<BigDecimal> majorAdjustRisks = new ArrayList<>();
        long reachCount = 0;

        for (PlanSchoolDTO dto : request.getSchools()) {
            PlanSchool ps = new PlanSchool();
            ps.setPlanId(plan.getId());
            ps.setSchoolId(dto.getSchoolId());
            ps.setSchoolName(dto.getSchoolName());
            ps.setCategory(dto.getCategory());
            ps.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
            ps.setSelectedMajors(dto.getSelectedMajors());

            // 从 sc_li_score 获取历史数据计算指标
            LambdaQueryWrapper<ScLiScore> scWrapper = new LambdaQueryWrapper<>();
            scWrapper.eq(ScLiScore::getSchoolId, dto.getSchoolId());
            ScLiScore scLiScore = scLiScoreMapper.selectOne(scWrapper);

            if (scLiScore != null) {
                int r0 = scLiScore.getRank2020(), r1 = scLiScore.getRank2021(), r2 = scLiScore.getRank2022();
                double avg = PlanCalculationUtils.avgRank(r0, r1, r2);
                double std = PlanCalculationUtils.calcRankStdDev(r0, r1, r2);
                ps.setAvgRank3yr(BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
                ps.setRankStdDev(BigDecimal.valueOf(std).setScale(2, RoundingMode.HALF_UP));
                ps.setAdmissionProb(PlanCalculationUtils.calcAdmissionProb(
                        avg, std, request.getUserRank(), dto.getCategory()));
                ps.setRankFluctuation(PlanCalculationUtils.buildRankFluctuationExplanation(
                        r0, r1, r2, request.getUserRank()));
                ps.setRankTrend(PlanCalculationUtils.calcRankTrend(r0, r1, r2));
            } else {
                ps.setAdmissionProb(BigDecimal.valueOf(50));
                ps.setRankFluctuation("暂无历年数据");
                ps.setRankTrend("stable");
            }

            // 调剂风险 (跨批次去重)
            List<Map<String, Object>> majorRows = PlanCalculationUtils.deduplicateMajorScores(
                    planSchoolMapper.selectMajorScoresForSchool(dto.getSchoolId(), request.getBatchName()));
            ps.setMajorAdjustRisk(PlanCalculationUtils.calcMajorAdjustRisk(majorRows, request.getScore()));

            // 热度
            SchoolInfo schoolInfo = schoolInfoMapper.selectById(dto.getSchoolId());
            if (schoolInfo != null) {
                int mv = (schoolInfo.getMonthView() != null) ? schoolInfo.getMonthView() : 0;
                int tv = PlanCalculationUtils.toInt(schoolInfo.getTotalView());
                ps.setPopularityScore(PlanCalculationUtils.calcPopularityScore(mv, tv));
                ps.setPopularityTrend(PlanCalculationUtils.calcPopularityTrend(mv, tv));
            }

            planSchoolMapper.insert(ps);
            schoolEntities.add(ps);
            admissionProbs.add(ps.getAdmissionProb());
            majorAdjustRisks.add(ps.getMajorAdjustRisk());
            if ("冲".equals(dto.getCategory())) reachCount++;
        }

        // 综合风险评分
        plan.setTotalRiskScore(PlanCalculationUtils.calcTotalRiskScore(
                admissionProbs, majorAdjustRisks, reachCount, schoolEntities.size()));
        this.baseMapper.updateById(plan);

        // 创建初始版本快照
        saveVersionSnapshot(plan.getId(), 1, schoolEntities);

        return plan.getId();
    }

    // ══════════════════════════════════════════
    // 查询方案详情
    // ══════════════════════════════════════════

    @Override
    public PlanDetailVO getPlanDetail(Integer planId, String userName) {
        VoluntaryPlan plan = this.baseMapper.selectById(planId);
        if (plan == null) return null;

        // 用户隔离校验
        if (!plan.getUserName().equals(userName)) {
            return null;
        }

        PlanDetailVO vo = new PlanDetailVO();
        vo.setId(plan.getId());
        vo.setPlanName(plan.getPlanName());
        vo.setUserName(plan.getUserName());
        vo.setScore(plan.getScore());
        vo.setUserRank(plan.getUserRank());
        vo.setSubjectType(plan.getSubjectType());
        vo.setRegionPref(plan.getRegionPref());
        vo.setSchoolTier(plan.getSchoolTier());
        vo.setMajorPref(plan.getMajorPref());
        vo.setBatchName(plan.getBatchName());
        vo.setVersion(plan.getVersion());
        vo.setParentId(plan.getParentId());
        vo.setStatus(plan.getStatus());
        vo.setTotalRiskScore(plan.getTotalRiskScore());
        vo.setCreateTime(plan.getCreateTime());
        vo.setUpdateTime(plan.getUpdateTime());

        List<PlanSchool> schools = planSchoolMapper.selectByPlanIdOrdered(planId);
        List<PlanSchoolVO> reachList = new ArrayList<>();
        List<PlanSchoolVO> matchList = new ArrayList<>();
        List<PlanSchoolVO> safetyList = new ArrayList<>();

        for (PlanSchool ps : schools) {
            PlanSchoolVO svo = mapToSchoolVO(ps);
            switch (ps.getCategory()) {
                case "冲" -> reachList.add(svo);
                case "稳" -> matchList.add(svo);
                case "保" -> safetyList.add(svo);
            }
        }

        vo.setReachSchools(reachList);
        vo.setMatchSchools(matchList);
        vo.setSafetySchools(safetyList);
        vo.setRiskSummary(PlanCalculationUtils.buildRiskSummary(reachList, matchList, safetyList));
        vo.setConflictWarnings(PlanCalculationUtils.buildMergedConflictWarnings(reachList, matchList, safetyList));

        return vo;
    }

    private PlanSchoolVO mapToSchoolVO(PlanSchool ps) {
        PlanSchoolVO vo = new PlanSchoolVO();
        vo.setPlanSchoolId(ps.getId());
        vo.setSchoolId(ps.getSchoolId());
        vo.setSchoolName(ps.getSchoolName());
        vo.setCategory(ps.getCategory());
        vo.setSortOrder(ps.getSortOrder());
        vo.setAdmissionProb(ps.getAdmissionProb());
        vo.setAdmissionProbLevel(PlanCalculationUtils.probLevel(ps.getAdmissionProb()));
        vo.setMajorAdjustRisk(ps.getMajorAdjustRisk());
        vo.setMajorAdjustRiskLevel(PlanCalculationUtils.riskLevel(ps.getMajorAdjustRisk()));
        vo.setPopularityScore(ps.getPopularityScore());
        vo.setPopularityTrend(ps.getPopularityTrend());
        vo.setRankFluctuation(ps.getRankFluctuation());
        vo.setAvgRank3yr(ps.getAvgRank3yr());
        vo.setRankStdDev(ps.getRankStdDev());
        vo.setRankTrend(ps.getRankTrend());

        if (ps.getSelectedMajors() != null && !ps.getSelectedMajors().isEmpty()) {
            vo.setSelectedMajors(Arrays.asList(ps.getSelectedMajors().split(",")));
        }

        // 专业详情 (跨批次去重)
        List<Map<String, Object>> majorRows = PlanCalculationUtils.deduplicateMajorScores(
                planSchoolMapper.selectMajorScoresForSchool(ps.getSchoolId(), null));
        vo.setMajorDetails(buildMajorRiskDetails(majorRows, 0));
        return vo;
    }

    // ══════════════════════════════════════════
    // 列出用户方案
    // ══════════════════════════════════════════

    @Override
    public List<VoluntaryPlan> listUserPlans(String userName) {
        return this.baseMapper.selectByUserName(userName);
    }

    // ══════════════════════════════════════════
    // 复制方案
    // ══════════════════════════════════════════

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer copyPlan(Integer planId, String userName, String newPlanName) {
        VoluntaryPlan original = this.baseMapper.selectById(planId);
        if (original == null || !original.getUserName().equals(userName)) {
            return null;
        }

        VoluntaryPlan copy = new VoluntaryPlan();
        copy.setPlanName(newPlanName);
        copy.setUserName(userName);
        copy.setScore(original.getScore());
        copy.setUserRank(original.getUserRank());
        copy.setSubjectType(original.getSubjectType());
        copy.setRegionPref(original.getRegionPref());
        copy.setSchoolTier(original.getSchoolTier());
        copy.setMajorPref(original.getMajorPref());
        copy.setBatchName(original.getBatchName());
        copy.setVersion(1);
        copy.setParentId(original.getId());
        copy.setStatus(0);
        // 不复制旧风险评分, 后面独立重算
        this.baseMapper.insert(copy);

        // 复制院校结构并独立重算风险指标
        List<PlanSchool> origSchools = planSchoolMapper.selectByPlanIdOrdered(planId);
        List<PlanSchool> clonedSchools = new ArrayList<>();
        List<BigDecimal> admissionProbs = new ArrayList<>();
        List<BigDecimal> majorAdjustRisks = new ArrayList<>();
        long reachCount = 0;

        for (PlanSchool orig : origSchools) {
            PlanSchool cloned = new PlanSchool();
            cloned.setPlanId(copy.getId());
            cloned.setSchoolId(orig.getSchoolId());
            cloned.setSchoolName(orig.getSchoolName());
            cloned.setCategory(orig.getCategory());
            cloned.setSortOrder(orig.getSortOrder());
            cloned.setSelectedMajors(orig.getSelectedMajors());

            // 独立重算: 从数据源读取并计算, 不复用原方案的风险摘要
            recalcSchoolMetrics(cloned, copy.getUserRank(), copy.getScore(), copy.getBatchName());

            planSchoolMapper.insert(cloned);
            clonedSchools.add(cloned);
            admissionProbs.add(cloned.getAdmissionProb());
            majorAdjustRisks.add(cloned.getMajorAdjustRisk());
            if ("冲".equals(cloned.getCategory())) reachCount++;
        }

        // 独立计算综合风险评分
        copy.setTotalRiskScore(PlanCalculationUtils.calcTotalRiskScore(
                admissionProbs, majorAdjustRisks, reachCount, clonedSchools.size()));
        this.baseMapper.updateById(copy);

        // 创建独立版本快照
        saveVersionSnapshot(copy.getId(), 1, clonedSchools);

        return copy.getId();
    }

    // ══════════════════════════════════════════
    // 调整排序
    // ══════════════════════════════════════════

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean reorderSchools(SchoolReorderDTO request) {
        VoluntaryPlan plan = this.baseMapper.selectById(request.getPlanId());
        if (plan == null || !plan.getUserName().equals(request.getUserName())) {
            return false;
        }

        // 快照当前排序状态, 保证人工调序只影响当前方案版本
        List<PlanSchool> currentSchools = planSchoolMapper.selectByPlanIdOrdered(request.getPlanId());
        saveVersionSnapshot(request.getPlanId(), plan.getVersion(), currentSchools);

        int newVersion = plan.getVersion() + 1;
        plan.setVersion(newVersion);
        this.baseMapper.updateById(plan);

        for (SchoolReorderDTO.SchoolOrderItem item : request.getOrderedSchools()) {
            planSchoolMapper.updateSortOrder(item.getPlanSchoolId(), item.getSortOrder());
        }

        // 保存调序后的新版本快照
        List<PlanSchool> updatedSchools = planSchoolMapper.selectByPlanIdOrdered(request.getPlanId());
        saveVersionSnapshot(request.getPlanId(), newVersion, updatedSchools);

        return true;
    }

    // ══════════════════════════════════════════
    // 重新评估
    // ══════════════════════════════════════════

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlanDetailVO reevaluatePlan(Integer planId, String userName) {
        VoluntaryPlan plan = this.baseMapper.selectById(planId);
        if (plan == null || !plan.getUserName().equals(userName)) {
            return null;
        }

        // 快照当前版本
        List<PlanSchool> currentSchools = planSchoolMapper.selectByPlanIdOrdered(planId);
        saveVersionSnapshot(planId, plan.getVersion(), currentSchools);

        // 版本号递增
        int newVersion = plan.getVersion() + 1;
        plan.setVersion(newVersion);

        List<BigDecimal> admissionProbs = new ArrayList<>();
        List<BigDecimal> majorAdjustRisks = new ArrayList<>();
        long reachCount = 0;

        // 清理旧风险缓存并重算每所院校的指标
        for (PlanSchool ps : currentSchools) {
            // 先清除所有旧风险字段, 避免残留陈旧数据
            resetSchoolRiskFields(ps);

            // 从数据源重新计算
            recalcSchoolMetrics(ps, plan.getUserRank(), plan.getScore(), plan.getBatchName());

            planSchoolMapper.updateById(ps);
            admissionProbs.add(ps.getAdmissionProb());
            majorAdjustRisks.add(ps.getMajorAdjustRisk());
            if ("冲".equals(ps.getCategory())) reachCount++;
        }

        plan.setTotalRiskScore(PlanCalculationUtils.calcTotalRiskScore(
                admissionProbs, majorAdjustRisks, reachCount, currentSchools.size()));
        this.baseMapper.updateById(plan);

        // 保存新版本快照
        saveVersionSnapshot(planId, newVersion, currentSchools);

        return getPlanDetail(planId, userName);
    }

    // ══════════════════════════════════════════
    // 方案对比
    // ══════════════════════════════════════════

    @Override
    public PlanComparisonVO comparePlans(Integer planIdA, Integer planIdB, String userName) {
        PlanDetailVO planA = getPlanDetail(planIdA, userName);
        PlanDetailVO planB = getPlanDetail(planIdB, userName);
        if (planA == null || planB == null) return null;

        PlanComparisonVO comparison = new PlanComparisonVO();
        comparison.setPlanA(planA);
        comparison.setPlanB(planB);

        Set<Integer> schoolIdsA = new HashSet<>();
        Set<Integer> schoolIdsB = new HashSet<>();
        Map<Integer, PlanSchoolVO> mapA = new HashMap<>();
        Map<Integer, PlanSchoolVO> mapB = new HashMap<>();

        for (PlanSchoolVO vo : allSchools(planA)) {
            schoolIdsA.add(vo.getSchoolId());
            mapA.put(vo.getSchoolId(), vo);
        }
        for (PlanSchoolVO vo : allSchools(planB)) {
            schoolIdsB.add(vo.getSchoolId());
            mapB.put(vo.getSchoolId(), vo);
        }

        List<PlanComparisonVO.SchoolDiff> diffs = new ArrayList<>();

        // 仅在A中(被移除)
        for (Integer id : schoolIdsA) {
            if (!schoolIdsB.contains(id)) {
                PlanComparisonVO.SchoolDiff diff = new PlanComparisonVO.SchoolDiff();
                diff.setSchoolName(mapA.get(id).getSchoolName());
                diff.setChangeType("removed");
                diff.setDetail("方案B中已移除该院校");
                diffs.add(diff);
            }
        }

        // 仅在B中(新增)
        for (Integer id : schoolIdsB) {
            if (!schoolIdsA.contains(id)) {
                PlanComparisonVO.SchoolDiff diff = new PlanComparisonVO.SchoolDiff();
                diff.setSchoolName(mapB.get(id).getSchoolName());
                diff.setChangeType("added");
                diff.setDetail("方案B中新增该院校");
                diffs.add(diff);
            }
        }

        // 两者都有: 检查类别和概率变化
        for (Integer id : schoolIdsA) {
            if (schoolIdsB.contains(id)) {
                PlanSchoolVO a = mapA.get(id);
                PlanSchoolVO b = mapB.get(id);
                if (!a.getCategory().equals(b.getCategory())) {
                    PlanComparisonVO.SchoolDiff diff = new PlanComparisonVO.SchoolDiff();
                    diff.setSchoolName(a.getSchoolName());
                    diff.setChangeType("category_changed");
                    diff.setDetail(String.format("类别从%s变为%s", a.getCategory(), b.getCategory()));
                    diffs.add(diff);
                }
                double probDiff = b.getAdmissionProb().doubleValue()
                        - a.getAdmissionProb().doubleValue();
                if (Math.abs(probDiff) > 5) {
                    PlanComparisonVO.SchoolDiff diff = new PlanComparisonVO.SchoolDiff();
                    diff.setSchoolName(a.getSchoolName());
                    diff.setChangeType("prob_changed");
                    diff.setDetail(String.format("录取概率从%.1f%%变为%.1f%% (%.1f%%)",
                            a.getAdmissionProb().doubleValue(),
                            b.getAdmissionProb().doubleValue(), probDiff));
                    diffs.add(diff);
                }
            }
        }

        comparison.setDifferences(diffs);

        // 摘要
        long added = diffs.stream().filter(d -> "added".equals(d.getChangeType())).count();
        long removed = diffs.stream().filter(d -> "removed".equals(d.getChangeType())).count();
        comparison.setSummary(String.format("方案A(%s)与方案B(%s)对比: 共%d处差异，新增%d所院校，移除%d所院校。",
                planA.getPlanName(), planB.getPlanName(), diffs.size(), added, removed));

        return comparison;
    }

    private List<PlanSchoolVO> allSchools(PlanDetailVO plan) {
        List<PlanSchoolVO> all = new ArrayList<>();
        if (plan.getReachSchools() != null) all.addAll(plan.getReachSchools());
        if (plan.getMatchSchools() != null) all.addAll(plan.getMatchSchools());
        if (plan.getSafetySchools() != null) all.addAll(plan.getSafetySchools());
        return all;
    }

    // ══════════════════════════════════════════
    // 删除方案
    // ══════════════════════════════════════════

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deletePlan(Integer planId, String userName) {
        VoluntaryPlan plan = this.baseMapper.selectById(planId);
        if (plan == null || !plan.getUserName().equals(userName)) {
            return false;
        }

        // 删除院校记录 (FK CASCADE 也会删, 但显式删除更安全)
        LambdaQueryWrapper<PlanSchool> schoolWrapper = new LambdaQueryWrapper<>();
        schoolWrapper.eq(PlanSchool::getPlanId, planId);
        planSchoolMapper.delete(schoolWrapper);

        LambdaQueryWrapper<PlanVersionLog> versionWrapper = new LambdaQueryWrapper<>();
        versionWrapper.eq(PlanVersionLog::getPlanId, planId);
        planVersionLogMapper.delete(versionWrapper);

        return this.baseMapper.deleteById(planId) > 0;
    }

    // ══════════════════════════════════════════
    // 更新状态
    // ══════════════════════════════════════════

    @Override
    public Boolean updatePlanStatus(Integer planId, String userName, Integer status) {
        VoluntaryPlan plan = this.baseMapper.selectById(planId);
        if (plan == null || !plan.getUserName().equals(userName)) {
            return false;
        }
        plan.setStatus(status);
        return this.baseMapper.updateById(plan) > 0;
    }

    // ══════════════════════════════════════════
    // 院校风险指标重算 & 重置
    // ══════════════════════════════════════════

    /**
     * 清除院校的所有风险字段, 用于重新评估前避免残留陈旧数据.
     */
    private void resetSchoolRiskFields(PlanSchool ps) {
        ps.setAdmissionProb(BigDecimal.valueOf(50));
        ps.setMajorAdjustRisk(BigDecimal.valueOf(50));
        ps.setPopularityScore(BigDecimal.ZERO);
        ps.setPopularityTrend("stable");
        ps.setRankFluctuation("暂无历年数据");
        ps.setAvgRank3yr(null);
        ps.setRankStdDev(null);
        ps.setRankTrend("stable");
    }

    /**
     * 从数据源独立重算院校的所有风险指标.
     * 用于方案复制和重新评估, 不复用任何旧方案的缓存值.
     */
    private void recalcSchoolMetrics(PlanSchool ps, int userRank, int userScore, String batchName) {
        // 位次相关指标
        LambdaQueryWrapper<ScLiScore> scWrapper = new LambdaQueryWrapper<>();
        scWrapper.eq(ScLiScore::getSchoolId, ps.getSchoolId());
        ScLiScore sc = scLiScoreMapper.selectOne(scWrapper);

        if (sc != null) {
            int r0 = sc.getRank2020(), r1 = sc.getRank2021(), r2 = sc.getRank2022();
            double avg = PlanCalculationUtils.avgRank(r0, r1, r2);
            double std = PlanCalculationUtils.calcRankStdDev(r0, r1, r2);
            ps.setAvgRank3yr(BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
            ps.setRankStdDev(BigDecimal.valueOf(std).setScale(2, RoundingMode.HALF_UP));
            ps.setAdmissionProb(PlanCalculationUtils.calcAdmissionProb(
                    avg, std, userRank, ps.getCategory()));
            ps.setRankFluctuation(PlanCalculationUtils.buildRankFluctuationExplanation(
                    r0, r1, r2, userRank));
            ps.setRankTrend(PlanCalculationUtils.calcRankTrend(r0, r1, r2));
        } else {
            ps.setAdmissionProb(BigDecimal.valueOf(50));
            ps.setRankFluctuation("暂无历年数据");
            ps.setRankTrend("stable");
        }

        // 调剂风险 (跨批次去重)
        List<Map<String, Object>> majorRows = PlanCalculationUtils.deduplicateMajorScores(
                planSchoolMapper.selectMajorScoresForSchool(ps.getSchoolId(), batchName));
        ps.setMajorAdjustRisk(PlanCalculationUtils.calcMajorAdjustRisk(majorRows, userScore));

        // 热度
        SchoolInfo si = schoolInfoMapper.selectById(ps.getSchoolId());
        if (si != null) {
            int mv = (si.getMonthView() != null) ? si.getMonthView() : 0;
            int tv = PlanCalculationUtils.toInt(si.getTotalView());
            ps.setPopularityScore(PlanCalculationUtils.calcPopularityScore(mv, tv));
            ps.setPopularityTrend(PlanCalculationUtils.calcPopularityTrend(mv, tv));
        } else {
            ps.setPopularityScore(BigDecimal.ZERO);
            ps.setPopularityTrend("stable");
        }
    }

    // ══════════════════════════════════════════
    // 版本快照
    // ══════════════════════════════════════════

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
