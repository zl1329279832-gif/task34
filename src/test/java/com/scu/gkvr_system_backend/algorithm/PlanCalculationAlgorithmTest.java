package com.scu.gkvr_system_backend.algorithm;

import com.scu.gkvr_system_backend.pojo.PlanSchool;
import com.scu.gkvr_system_backend.utils.PlanCalculationUtils;
import com.scu.gkvr_system_backend.vo.PlanRiskSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PlanCalculationAlgorithmTest {

    // ===== calcAdmissionProb =====

    @Test
    void calcAdmissionProb_equalRank_stable() {
        // avgRank == userRank, stdDev=0 => baseProb=50, stability=1.0 => 50
        int result = PlanCalculationUtils.calcAdmissionProb(10000, 10000, 0);
        assertEquals(50, result);
    }

    @Test
    void calcAdmissionProb_schoolAboveUser_reach() {
        // avgRank < userRank => prob < 50 (harder school)
        int result = PlanCalculationUtils.calcAdmissionProb(5000, 10000, 0);
        assertTrue(result < 50);
    }

    @Test
    void calcAdmissionProb_schoolBelowUser_safety() {
        // avgRank > userRank => prob > 50 (easier school)
        int result = PlanCalculationUtils.calcAdmissionProb(15000, 10000, 0);
        assertTrue(result > 50);
    }

    @Test
    void calcAdmissionProb_highVariance_lowerProb() {
        int stableResult = PlanCalculationUtils.calcAdmissionProb(10000, 10000, 0);
        int volatileResult = PlanCalculationUtils.calcAdmissionProb(10000, 10000, 3000);
        assertTrue(volatileResult < stableResult);
    }

    @Test
    void calcAdmissionProb_zeroVariance_stabilityFactor1() {
        int result = PlanCalculationUtils.calcAdmissionProb(10000, 10000, 0);
        assertEquals(50, result);
    }

    @Test
    void calcAdmissionProb_clampedTo99() {
        // Very easy school: avgRank >> userRank
        int result = PlanCalculationUtils.calcAdmissionProb(50000, 10000, 0);
        assertEquals(99, result);
    }

    @Test
    void calcAdmissionProb_clampedTo1() {
        // Very hard school: avgRank << userRank
        int result = PlanCalculationUtils.calcAdmissionProb(100, 10000, 0);
        assertEquals(1, result);
    }

    // ===== calcMajorAdjustRisk =====

    @Test
    void calcMajorAdjustRisk_noMajorData_returns50() {
        double result = PlanCalculationUtils.calcMajorAdjustRisk(600, null);
        assertEquals(50.0, result);

        result = PlanCalculationUtils.calcMajorAdjustRisk(600, Collections.emptyList());
        assertEquals(50.0, result);
    }

    @Test
    void calcMajorAdjustRisk_scoreAboveAllAvgs_nearZero() {
        List<Map<String, Object>> majors = List.of(
                Map.of("avgScore", 600),
                Map.of("avgScore", 580)
        );
        double result = PlanCalculationUtils.calcMajorAdjustRisk(650, majors);
        assertEquals(0.0, result, 0.01);
    }

    @Test
    void calcMajorAdjustRisk_scoreBelowAllMins_above80() {
        List<Map<String, Object>> majors = List.of(
                Map.of("avgScore", 650),
                Map.of("avgScore", 660)
        );
        double result = PlanCalculationUtils.calcMajorAdjustRisk(500, majors);
        assertTrue(result > 20);
    }

    @Test
    void calcMajorAdjustRisk_mixed_intermediate() {
        List<Map<String, Object>> majors = List.of(
                Map.of("avgScore", 600),
                Map.of("avgScore", 650)
        );
        double result = PlanCalculationUtils.calcMajorAdjustRisk(620, majors);
        assertTrue(result > 0 && result < 50);
    }

    // ===== calcRankStdDev =====

    @Test
    void calcRankStdDev_allSame_zero() {
        double result = PlanCalculationUtils.calcRankStdDev(5000, 5000, 5000);
        assertEquals(0.0, result, 0.01);
    }

    @Test
    void calcRankStdDev_spread() {
        double result = PlanCalculationUtils.calcRankStdDev(4000, 5000, 6000);
        assertTrue(result > 0);
        // std dev of {4000,5000,6000} = sqrt(((−1000)²+(0)²+(1000)²)/3) ≈ 816.5
        assertEquals(816.5, result, 1.0);
    }

    // ===== calcRankTrend =====

    @Test
    void calcRankTrend_rising() {
        // rank2022 < rank2020 * 0.9 => school getting harder
        String result = PlanCalculationUtils.calcRankTrend(10000, 8000, 7000);
        assertEquals("rising", result);
    }

    @Test
    void calcRankTrend_declining() {
        // rank2022 > rank2020 * 1.1 => school getting easier
        String result = PlanCalculationUtils.calcRankTrend(10000, 11000, 12000);
        assertEquals("declining", result);
    }

    @Test
    void calcRankTrend_stable() {
        String result = PlanCalculationUtils.calcRankTrend(10000, 10200, 10100);
        assertEquals("stable", result);
    }

    // ===== calcPopularityScore =====

    @Test
    void calcPopularityScore_high() {
        double result = PlanCalculationUtils.calcPopularityScore(5000);
        assertEquals(100.0, result);
    }

    @Test
    void calcPopularityScore_zero_monthView() {
        double result = PlanCalculationUtils.calcPopularityScore(0);
        assertEquals(0.0, result);
    }

    // ===== calcPopularityTrend =====

    @Test
    void calcPopularityTrend_rising() {
        // monthView=5000, totalView="30000" => yearlyAvg=2500, 5000 > 2500*1.2
        String result = PlanCalculationUtils.calcPopularityTrend(5000, "30000");
        assertEquals("rising", result);
    }

    @Test
    void calcPopularityTrend_stable() {
        // monthView=2500, totalView="30000" => yearlyAvg=2500, within 20%
        String result = PlanCalculationUtils.calcPopularityTrend(2500, "30000");
        assertEquals("stable", result);
    }

    @Test
    void calcPopularityTrend_declining() {
        // monthView=1000, totalView="30000" => yearlyAvg=2500, 1000 < 2500*0.8
        String result = PlanCalculationUtils.calcPopularityTrend(1000, "30000");
        assertEquals("declining", result);
    }

    // ===== buildRankFluctuationExplanation =====

    @Test
    void buildRankFluctuationExplanation_stable() {
        // CV < 0.05
        String result = PlanCalculationUtils.buildRankFluctuationExplanation(100, 10000);
        assertTrue(result.contains("稳定"));
    }

    @Test
    void buildRankFluctuationExplanation_volatile() {
        // CV >= 0.15
        String result = PlanCalculationUtils.buildRankFluctuationExplanation(2000, 10000);
        assertTrue(result.contains("波动较大"));
    }

    // ===== buildRiskSummary =====

    @Test
    void buildRiskSummary_balanced() {
        List<PlanSchool> schools = new ArrayList<>();
        schools.add(createSchool("冲", 30));
        schools.add(createSchool("稳", 60));
        schools.add(createSchool("保", 85));
        PlanRiskSummary summary = PlanCalculationUtils.buildRiskSummary(schools);
        assertEquals(1, summary.getReachCount());
        assertEquals(1, summary.getMatchCount());
        assertEquals(1, summary.getSafetyCount());
        assertNotNull(summary.getRiskLevel());
    }

    @Test
    void buildRiskSummary_allReach_highRisk() {
        List<PlanSchool> schools = new ArrayList<>();
        schools.add(createSchool("冲", 20));
        schools.add(createSchool("冲", 25));
        schools.add(createSchool("冲", 15));
        PlanRiskSummary summary = PlanCalculationUtils.buildRiskSummary(schools);
        assertEquals("高风险", summary.getRiskLevel());
        assertTrue(summary.getSuggestion().contains("冲的比例过高"));
    }

    @Test
    void buildRiskSummary_allSafety_lowRisk() {
        List<PlanSchool> schools = new ArrayList<>();
        schools.add(createSchool("保", 90));
        schools.add(createSchool("保", 85));
        PlanRiskSummary summary = PlanCalculationUtils.buildRiskSummary(schools);
        assertEquals("低风险", summary.getRiskLevel());
        assertTrue(summary.getSuggestion().contains("保守"));
    }

    @Test
    void buildRiskSummary_empty_noData() {
        PlanRiskSummary summary = PlanCalculationUtils.buildRiskSummary(Collections.emptyList());
        assertEquals(BigDecimal.ZERO, summary.getTotalRiskScore());
        assertEquals("低风险", summary.getRiskLevel());
    }

    // ===== probLevel =====

    @Test
    void probLevel_high() {
        assertEquals("高", PlanCalculationUtils.probLevel(75));
    }

    @Test
    void probLevel_mid() {
        assertEquals("中", PlanCalculationUtils.probLevel(50));
    }

    @Test
    void probLevel_low() {
        assertEquals("低", PlanCalculationUtils.probLevel(20));
    }

    // ===== riskLevel =====

    @Test
    void riskLevel_high() {
        assertEquals("高", PlanCalculationUtils.riskLevel(70));
    }

    @Test
    void riskLevel_mid() {
        assertEquals("中", PlanCalculationUtils.riskLevel(45));
    }

    @Test
    void riskLevel_low() {
        assertEquals("低", PlanCalculationUtils.riskLevel(10));
    }

    // ===== getAdjustedRatios =====

    @Test
    void getAdjustedRatios_normalRank() {
        double[] ratios = PlanCalculationUtils.getAdjustedRatios(10000);
        assertArrayEquals(new double[]{0.3, 0.4, 0.3}, ratios);
    }

    @Test
    void getAdjustedRatios_topRank() {
        double[] ratios = PlanCalculationUtils.getAdjustedRatios(100);
        assertArrayEquals(new double[]{0.2, 0.5, 0.3}, ratios);
    }

    @Test
    void getAdjustedRatios_bottomRank() {
        double[] ratios = PlanCalculationUtils.getAdjustedRatios(250000);
        assertArrayEquals(new double[]{0.3, 0.3, 0.4}, ratios);
    }

    // ===== Helper =====

    private PlanSchool createSchool(String category, int admissionProb) {
        PlanSchool ps = new PlanSchool();
        ps.setCategory(category);
        ps.setAdmissionProb(BigDecimal.valueOf(admissionProb));
        return ps;
    }
}
