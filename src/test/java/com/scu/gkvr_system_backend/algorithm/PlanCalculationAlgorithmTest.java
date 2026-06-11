package com.scu.gkvr_system_backend.algorithm;

import com.scu.gkvr_system_backend.utils.PlanCalculationUtils;
import com.scu.gkvr_system_backend.vo.PlanRiskSummary;
import com.scu.gkvr_system_backend.vo.PlanSchoolVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 方案计算算法纯单元测试(无Spring上下文)
 */
class PlanCalculationAlgorithmTest {

    // ══════════════════════════════════════════
    // 录取概率测试
    // ══════════════════════════════════════════

    @Test
    void calcAdmissionProb_equalRank_stable() {
        // avgRank == userRank → P ≈ 50
        BigDecimal prob = PlanCalculationUtils.calcAdmissionProb(20000, 0, 20000, "稳");
        assertEquals(50.0, prob.doubleValue(), 1.0);
    }

    @Test
    void calcAdmissionProb_schoolBelowUser_safety() {
        // avgRank(30000) > userRank(20000) → P > 50, 保底线55
        BigDecimal prob = PlanCalculationUtils.calcAdmissionProb(30000, 0, 20000, "保");
        assertTrue(prob.doubleValue() >= 55.0, "保底院校概率应 >= 55, actual=" + prob);
    }

    @Test
    void calcAdmissionProb_schoolAboveUser_reach() {
        // avgRank(12000) < userRank(20000) → P < 50, 冲封顶50
        BigDecimal prob = PlanCalculationUtils.calcAdmissionProb(12000, 0, 20000, "冲");
        assertTrue(prob.doubleValue() <= 50.0, "冲院校概率应 <= 50, actual=" + prob);
    }

    @Test
    void calcAdmissionProb_highVariance_lowerProb() {
        // 高方差 vs 低方差, 高方差的概率应更低
        BigDecimal lowVar = PlanCalculationUtils.calcAdmissionProb(20000, 500, 20000, "稳");
        BigDecimal highVar = PlanCalculationUtils.calcAdmissionProb(20000, 5000, 20000, "稳");
        assertTrue(lowVar.doubleValue() > highVar.doubleValue(),
                "高方差院校概率应更低: lowVar=" + lowVar + ", highVar=" + highVar);
    }

    @Test
    void calcAdmissionProb_zeroVariance_stabilityFactor1() {
        // stdDev=0 → stabilityFactor=1.0
        BigDecimal prob = PlanCalculationUtils.calcAdmissionProb(20000, 0, 20000, "稳");
        assertEquals(50.0, prob.doubleValue(), 0.5);
    }

    @Test
    void calcAdmissionProb_clampedTo99() {
        // 极端比率 → 不会超过99
        BigDecimal prob = PlanCalculationUtils.calcAdmissionProb(100000, 0, 10000, "保");
        assertTrue(prob.doubleValue() <= 99.0, "概率不应超过99, actual=" + prob);
    }

    @Test
    void calcAdmissionProb_clampedTo1() {
        // 极端比率 → 不会低于1
        BigDecimal prob = PlanCalculationUtils.calcAdmissionProb(1000, 0, 100000, "冲");
        assertTrue(prob.doubleValue() >= 1.0, "概率不应低于1, actual=" + prob);
    }

    // ══════════════════════════════════════════
    // 调剂风险测试
    // ══════════════════════════════════════════

    @Test
    void calcMajorAdjustRisk_scoreAboveAllAvgs_nearZero() {
        List<Map<String, Object>> majors = List.of(
                Map.of("majorName", "CS", "maxScore", 620, "minScore", 580, "avgScore", 600),
                Map.of("majorName", "EE", "maxScore", 610, "minScore", 570, "avgScore", 590)
        );
        BigDecimal risk = PlanCalculationUtils.calcMajorAdjustRisk(majors, 650);
        assertTrue(risk.doubleValue() < 5.0, "分数远高于平均, 风险应接近0, actual=" + risk);
    }

    @Test
    void calcMajorAdjustRisk_scoreBelowAllMins_above80() {
        List<Map<String, Object>> majors = List.of(
                Map.of("majorName", "CS", "maxScore", 620, "minScore", 580, "avgScore", 600),
                Map.of("majorName", "EE", "maxScore", 610, "minScore", 570, "avgScore", 590)
        );
        BigDecimal risk = PlanCalculationUtils.calcMajorAdjustRisk(majors, 450);
        assertTrue(risk.doubleValue() > 80.0, "分数远低于最低, 风险应 > 80, actual=" + risk);
    }

    @Test
    void calcMajorAdjustRisk_noMajorData_returns50() {
        BigDecimal risk = PlanCalculationUtils.calcMajorAdjustRisk(Collections.emptyList(), 580);
        assertEquals(50.0, risk.doubleValue(), 0.01);
    }

    @Test
    void calcMajorAdjustRisk_mixed_intermediate() {
        List<Map<String, Object>> majors = List.of(
                Map.of("majorName", "CS", "maxScore", 650, "minScore", 620, "avgScore", 635),
                Map.of("majorName", "Civil", "maxScore", 590, "minScore", 560, "avgScore", 575)
        );
        BigDecimal risk = PlanCalculationUtils.calcMajorAdjustRisk(majors, 600);
        assertTrue(risk.doubleValue() > 0 && risk.doubleValue() < 80,
                "混合情况应为中间值, actual=" + risk);
    }

    // ══════════════════════════════════════════
    // 热度测试
    // ══════════════════════════════════════════

    @Test
    void calcPopularityScore_zero_monthView() {
        BigDecimal score = PlanCalculationUtils.calcPopularityScore(0, 0);
        assertEquals(0.0, score.doubleValue(), 0.01);
    }

    @Test
    void calcPopularityScore_high() {
        BigDecimal score = PlanCalculationUtils.calcPopularityScore(10000, 120000);
        assertTrue(score.doubleValue() >= 99.0, "高月浏览量应接近100, actual=" + score);
    }

    @Test
    void calcPopularityTrend_rising() {
        // monthView(5000) > avgMonthly(1000) * 1.2
        String trend = PlanCalculationUtils.calcPopularityTrend(5000, 12000);
        assertEquals("rising", trend);
    }

    @Test
    void calcPopularityTrend_declining() {
        // monthView(500) < avgMonthly(10000) * 0.8
        String trend = PlanCalculationUtils.calcPopularityTrend(500, 120000);
        assertEquals("declining", trend);
    }

    @Test
    void calcPopularityTrend_stable() {
        // monthView(1000) ≈ avgMonthly(1000)
        String trend = PlanCalculationUtils.calcPopularityTrend(1000, 12000);
        assertEquals("stable", trend);
    }

    // ══════════════════════════════════════════
    // 位次统计测试
    // ══════════════════════════════════════════

    @Test
    void calcRankStdDev_allSame_zero() {
        double stdDev = PlanCalculationUtils.calcRankStdDev(15000, 15000, 15000);
        assertEquals(0.0, stdDev, 0.001);
    }

    @Test
    void calcRankStdDev_spread() {
        double stdDev = PlanCalculationUtils.calcRankStdDev(10000, 15000, 20000);
        assertTrue(stdDev > 4000 && stdDev < 4200, "标准差应约为4082, actual=" + stdDev);
    }

    @Test
    void calcRankTrend_stable() {
        assertEquals("stable", PlanCalculationUtils.calcRankTrend(15000, 15000, 15000));
    }

    @Test
    void calcRankTrend_rising() {
        // rank2022(10000) < avg(15000) * 0.9(13500)
        assertEquals("rising", PlanCalculationUtils.calcRankTrend(15000, 15000, 10000));
    }

    @Test
    void calcRankTrend_declining() {
        // rank2022(20000) > avg(15000) * 1.1(16500)
        assertEquals("declining", PlanCalculationUtils.calcRankTrend(15000, 15000, 20000));
    }

    @Test
    void buildRankFluctuationExplanation_stable() {
        String explanation = PlanCalculationUtils.buildRankFluctuationExplanation(
                15000, 15200, 15100, 14000);
        assertTrue(explanation.contains("非常稳定"), "波动小应显示'非常稳定', actual=" + explanation);
        assertTrue(explanation.contains("优于"), "用户位次优于该校平均");
    }

    @Test
    void buildRankFluctuationExplanation_volatile() {
        String explanation = PlanCalculationUtils.buildRankFluctuationExplanation(
                10000, 15000, 20000, 15000);
        assertTrue(explanation.contains("波动较大"), "波动大应显示'波动较大', actual=" + explanation);
    }

    // ══════════════════════════════════════════
    // 风险汇总测试
    // ══════════════════════════════════════════

    @Test
    void buildRiskSummary_allSafety_lowRisk() {
        List<PlanSchoolVO> safety = createSchoolVOs(10, "保", 80.0, 20.0);
        PlanRiskSummary summary = PlanCalculationUtils.buildRiskSummary(
                Collections.emptyList(), Collections.emptyList(), safety);
        assertEquals("低风险", summary.getOverallRiskLevel());
        assertEquals(10, summary.getSafetyCount());
    }

    @Test
    void buildRiskSummary_allReach_highRisk() {
        List<PlanSchoolVO> reach = createSchoolVOs(10, "冲", 30.0, 70.0);
        PlanRiskSummary summary = PlanCalculationUtils.buildRiskSummary(
                reach, Collections.emptyList(), Collections.emptyList());
        assertTrue(summary.getOverallRiskScore().doubleValue() > 40,
                "全冲方案风险应较高, actual=" + summary.getOverallRiskScore());
    }

    @Test
    void buildRiskSummary_balanced() {
        List<PlanSchoolVO> reach = createSchoolVOs(5, "冲", 35.0, 55.0);
        List<PlanSchoolVO> match = createSchoolVOs(10, "稳", 55.0, 45.0);
        List<PlanSchoolVO> safety = createSchoolVOs(5, "保", 75.0, 30.0);
        PlanRiskSummary summary = PlanCalculationUtils.buildRiskSummary(reach, match, safety);
        // riskScore ≈ 33.4 → 中风险
        assertEquals("中风险", summary.getOverallRiskLevel());
    }

    @Test
    void buildRiskSummary_empty_noData() {
        PlanRiskSummary summary = PlanCalculationUtils.buildRiskSummary(
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        assertEquals("无数据", summary.getOverallRiskLevel());
    }

    // ══════════════════════════════════════════
    // 位次边界修正测试
    // ══════════════════════════════════════════

    @Test
    void getAdjustedRatios_topRank() {
        double[] ratios = PlanCalculationUtils.getAdjustedRatios(50);
        assertEquals(0.5, ratios[0], 0.001); // reachUpper
        assertEquals(3.0, ratios[5], 0.001); // safetyLower
    }

    @Test
    void getAdjustedRatios_bottomRank() {
        double[] ratios = PlanCalculationUtils.getAdjustedRatios(280000);
        assertEquals(0.7, ratios[0], 0.001); // reachUpper
        assertEquals(2.5, ratios[5], 0.001); // safetyLower
    }

    @Test
    void getAdjustedRatios_normalRank() {
        double[] ratios = PlanCalculationUtils.getAdjustedRatios(20000);
        assertEquals(0.6, ratios[0], 0.001);
        assertEquals(1.8, ratios[5], 0.001);
    }

    // ══════════════════════════════════════════
    // 等级判定测试
    // ══════════════════════════════════════════

    @Test
    void probLevel_high() {
        assertEquals("高", PlanCalculationUtils.probLevel(BigDecimal.valueOf(75)));
    }

    @Test
    void probLevel_mid() {
        assertEquals("中", PlanCalculationUtils.probLevel(BigDecimal.valueOf(50)));
    }

    @Test
    void probLevel_low() {
        assertEquals("低", PlanCalculationUtils.probLevel(BigDecimal.valueOf(25)));
    }

    @Test
    void riskLevel_high() {
        assertEquals("高", PlanCalculationUtils.riskLevel(BigDecimal.valueOf(70)));
    }

    @Test
    void riskLevel_mid() {
        assertEquals("中", PlanCalculationUtils.riskLevel(BigDecimal.valueOf(45)));
    }

    @Test
    void riskLevel_low() {
        assertEquals("低", PlanCalculationUtils.riskLevel(BigDecimal.valueOf(15)));
    }

    // ══════════════════════════════════════════
    // 辅助方法
    // ══════════════════════════════════════════

    private List<PlanSchoolVO> createSchoolVOs(int count, String category,
                                                 double prob, double risk) {
        List<PlanSchoolVO> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            PlanSchoolVO vo = new PlanSchoolVO();
            vo.setSchoolId(i + 1);
            vo.setSchoolName("TestSchool" + i);
            vo.setCategory(category);
            vo.setAdmissionProb(BigDecimal.valueOf(prob));
            vo.setMajorAdjustRisk(BigDecimal.valueOf(risk));
            list.add(vo);
        }
        return list;
    }
}
