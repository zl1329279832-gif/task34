package com.scu.gkvr_system_backend.utils;

import com.scu.gkvr_system_backend.vo.MajorRiskVO;
import com.scu.gkvr_system_backend.vo.PlanRiskSummary;
import com.scu.gkvr_system_backend.vo.PlanSchoolVO;
import com.scu.gkvr_system_backend.vo.ProbabilityExplanationVO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 志愿方案计算工具类.
 * 包含录取概率、调剂风险、热度评分、位次波动等核心算法.
 */
public final class PlanCalculationUtils {

    private PlanCalculationUtils() {}

    // ── 位次区间比例常量 ──
    public static final double REACH_UPPER_RATIO = 0.6;
    public static final double REACH_LOWER_RATIO = 0.9;
    public static final double MATCH_UPPER_RATIO = 0.9;
    public static final double MATCH_LOWER_RATIO = 1.2;
    public static final double SAFETY_UPPER_RATIO = 1.2;
    public static final double SAFETY_LOWER_RATIO = 1.8;

    // ── 边界位次阈值 ──
    private static final int TOP_RANK_THRESHOLD = 100;
    private static final int BOTTOM_RANK_THRESHOLD = 200000;

    // ══════════════════════════════════════════════
    // 录取概率
    // ══════════════════════════════════════════════

    /**
     * 计算录取概率.
     *
     * 基础公式: P_base = (avgRank / userRank) * 50
     *   - avgRank == userRank → P = 50
     *   - avgRank > userRank (院校在用户下方) → P > 50
     *   - avgRank < userRank (院校在用户上方) → P < 50
     *
     * 稳定性修正: P_adj = P_base * stabilityFactor
     *   - stabilityFactor = 1.0 - (stdDev / avgRank) * 0.5, 下限 0.5
     *
     * 类别修正:
     *   - 冲: 封顶 50
     *   - 保: 底线 55
     *
     * 最终钳位 [1, 99]
     */
    public static BigDecimal calcAdmissionProb(double avgRank, double stdDev,
                                                int userRank, String category) {
        double ratio = avgRank / userRank;
        double pBase = ratio * 50.0;

        double cv = (avgRank > 0) ? (stdDev / avgRank) : 0;
        double stabilityFactor = 1.0 - (cv * 0.5);
        stabilityFactor = Math.max(stabilityFactor, 0.5);
        double pAdj = pBase * stabilityFactor;

        double finalProb;
        switch (category) {
            case "冲":
                finalProb = Math.min(pAdj, 50.0);
                break;
            case "保":
                finalProb = Math.max(pAdj, 55.0);
                break;
            default:
                finalProb = pAdj;
                break;
        }

        finalProb = Math.max(1.0, Math.min(99.0, finalProb));
        return BigDecimal.valueOf(finalProb).setScale(2, RoundingMode.HALF_UP);
    }

    // ══════════════════════════════════════════════
    // 专业调剂风险
    // ══════════════════════════════════════════════

    /**
     * 计算专业调剂风险.
     *
     * 逐专业计算:
     *   - score >= major.avg → risk = 0
     *   - major.min <= score < major.avg → risk = (avg - score) / (avg - min) * 50
     *   - score < major.min → risk = 80 + (min - score) / min * 20
     *
     * 最终取所有专业的平均风险, 钳位 [0, 99].
     * 无专业数据时默认返回 50.
     */
    public static BigDecimal calcMajorAdjustRisk(List<Map<String, Object>> majorRows, int userScore) {
        if (majorRows == null || majorRows.isEmpty()) {
            return BigDecimal.valueOf(50.0);
        }

        double totalRisk = 0;
        for (Map<String, Object> row : majorRows) {
            int min = toInt(row.get("minScore"));
            int avg = toInt(row.get("avgScore"));
            if (avg == 0) avg = min;

            if (userScore >= avg) {
                totalRisk += 0;
            } else if (userScore >= min) {
                int range = avg - min;
                double contribution = (range > 0) ? ((double) (avg - userScore) / range) * 50 : 25;
                totalRisk += contribution;
            } else {
                double contribution = 80 + (min > 0 ? ((double) (min - userScore) / min) * 20 : 19);
                totalRisk += Math.min(contribution, 99);
            }
        }

        double avgRisk = totalRisk / majorRows.size();
        avgRisk = Math.max(0, Math.min(99, avgRisk));
        return BigDecimal.valueOf(avgRisk).setScale(2, RoundingMode.HALF_UP);
    }

    // ══════════════════════════════════════════════
    // 院校热度
    // ══════════════════════════════════════════════

    /**
     * 院校热度评分.
     * popularityScore = min(100, log10(monthView + 1) / log10(10001) * 100)
     */
    public static BigDecimal calcPopularityScore(int monthView, int totalView) {
        double refMax = 10000.0;
        double score = Math.min(100, Math.log10(monthView + 1) / Math.log10(refMax + 1) * 100);
        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 院校热度趋势.
     * monthView > avgMonthly * 1.2 → "rising"
     * monthView < avgMonthly * 0.8 → "declining"
     * else → "stable"
     */
    public static String calcPopularityTrend(int monthView, int totalView) {
        if (totalView <= 0) return "stable";
        double avgMonthly = totalView / 12.0;
        if (monthView > avgMonthly * 1.2) return "rising";
        if (monthView < avgMonthly * 0.8) return "declining";
        return "stable";
    }

    // ══════════════════════════════════════════════
    // 位次统计
    // ══════════════════════════════════════════════

    /** 三年平均位次 */
    public static double avgRank(int r2020, int r2021, int r2022) {
        return (r2020 + r2021 + r2022) / 3.0;
    }

    /** 三年位次标准差 */
    public static double calcRankStdDev(int r0, int r1, int r2) {
        double mean = (r0 + r1 + r2) / 3.0;
        double variance = (Math.pow(r0 - mean, 2) + Math.pow(r1 - mean, 2) + Math.pow(r2 - mean, 2)) / 3.0;
        return Math.sqrt(variance);
    }

    /**
     * 位次趋势.
     * rank2022 < avg * 0.9 → "rising" (位次数字变小=院校变难)
     * rank2022 > avg * 1.1 → "declining" (院校变容易)
     * else → "stable"
     */
    public static String calcRankTrend(int r2020, int r2021, int r2022) {
        double avg = (r2020 + r2021 + r2022) / 3.0;
        if (avg == 0) return "stable";
        if (r2022 < avg * 0.9) return "rising";
        if (r2022 > avg * 1.1) return "declining";
        return "stable";
    }

    /**
     * 位次波动解释文本.
     * 例: "2020年位次12000→2021年位次11500→2022年位次11800，三年波动±500名，整体非常稳定。您的位次11000优于该校平均位次。"
     */
    public static String buildRankFluctuationExplanation(int r2020, int r2021, int r2022, int userRank) {
        double avg = (r2020 + r2021 + r2022) / 3.0;
        int maxR = Math.max(r2020, Math.max(r2021, r2022));
        int minR = Math.min(r2020, Math.min(r2021, r2022));
        int fluctuation = maxR - minR;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("2020年位次%d→2021年位次%d→2022年位次%d，", r2020, r2021, r2022));

        if (fluctuation <= 1000) {
            sb.append(String.format("三年波动%d名，整体非常稳定。", fluctuation));
        } else if (fluctuation <= 5000) {
            sb.append(String.format("三年波动%d名，有一定波动。", fluctuation));
        } else {
            sb.append(String.format("三年波动%d名，波动较大，需谨慎参考。", fluctuation));
        }

        if (userRank < avg) {
            sb.append(String.format("您的位次%d优于该校平均位次%.0f，有一定优势。", userRank, avg));
        } else {
            sb.append(String.format("您的位次%d低于该校平均位次%.0f，存在一定风险。", userRank, avg));
        }

        return sb.toString();
    }

    // ══════════════════════════════════════════════
    // 风险汇总
    // ══════════════════════════════════════════════

    /**
     * 构建方案总体风险摘要.
     *
     * Risk = (100 - avgProb) * 0.4 + avgMajorRisk * 0.3 + reachRatio * 30 * 0.3
     */
    public static PlanRiskSummary buildRiskSummary(List<PlanSchoolVO> reach,
                                                    List<PlanSchoolVO> match,
                                                    List<PlanSchoolVO> safety) {
        PlanRiskSummary summary = new PlanRiskSummary();
        summary.setReachCount(reach.size());
        summary.setMatchCount(match.size());
        summary.setSafetyCount(safety.size());

        List<PlanSchoolVO> all = new ArrayList<>();
        all.addAll(reach);
        all.addAll(match);
        all.addAll(safety);

        if (all.isEmpty()) {
            summary.setOverallRiskScore(BigDecimal.ZERO);
            summary.setOverallRiskLevel("无数据");
            summary.setAvgAdmissionProb(BigDecimal.ZERO);
            summary.setAvgMajorAdjustRisk(BigDecimal.ZERO);
            summary.setRecommendation("未找到匹配的院校，请调整筛选条件。");
            return summary;
        }

        double avgProb = all.stream()
                .mapToDouble(v -> v.getAdmissionProb().doubleValue())
                .average().orElse(50);
        double avgRisk = all.stream()
                .mapToDouble(v -> v.getMajorAdjustRisk().doubleValue())
                .average().orElse(50);

        summary.setAvgAdmissionProb(
                BigDecimal.valueOf(avgProb).setScale(2, RoundingMode.HALF_UP));
        summary.setAvgMajorAdjustRisk(
                BigDecimal.valueOf(avgRisk).setScale(2, RoundingMode.HALF_UP));

        double reachRatio = (double) reach.size() / all.size();
        double riskScore = (100 - avgProb) * 0.4 + avgRisk * 0.3 + reachRatio * 30 * 0.3;
        riskScore = Math.max(0, Math.min(100, riskScore));
        summary.setOverallRiskScore(
                BigDecimal.valueOf(riskScore).setScale(2, RoundingMode.HALF_UP));

        if (riskScore <= 30) {
            summary.setOverallRiskLevel("低风险");
        } else if (riskScore <= 60) {
            summary.setOverallRiskLevel("中风险");
        } else {
            summary.setOverallRiskLevel("高风险");
        }

        StringBuilder rec = new StringBuilder();
        rec.append(String.format("该方案包含%d冲%d稳%d保，共%d所院校。",
                reach.size(), match.size(), safety.size(), all.size()));
        if (reachRatio > 0.5) {
            rec.append("冲一冲院校占比偏高，建议增加稳妥和保底院校。");
        }
        if (avgRisk > 60) {
            rec.append("专业调剂风险较高，建议勾选服从专业调剂。");
        }
        if (avgProb >= 60 && reachRatio <= 0.4) {
            rec.append("方案整体较为稳健，录取概率良好。");
        }
        summary.setRecommendation(rec.toString());

        return summary;
    }

    // ══════════════════════════════════════════════
    // 专业匹配度
    // ══════════════════════════════════════════════

    /**
     * 专业分数匹配度(0-100).
     */
    public static double calcMajorMatchScore(int userScore, int majorMin, int majorAvg) {
        if (userScore >= majorAvg) return Math.min(100, 70 + (userScore - majorAvg) * 0.5);
        if (userScore >= majorMin) {
            int range = majorAvg - majorMin;
            return (range > 0) ? ((double) (userScore - majorMin) / range) * 70 : 35;
        }
        return Math.max(0, 30 - (majorMin - userScore) * 0.5);
    }

    // ══════════════════════════════════════════════
    // 等级判定
    // ══════════════════════════════════════════════

    public static String probLevel(BigDecimal prob) {
        double p = prob.doubleValue();
        if (p >= 70) return "高";
        if (p >= 40) return "中";
        return "低";
    }

    public static String riskLevel(BigDecimal risk) {
        double r = risk.doubleValue();
        if (r >= 60) return "高";
        if (r >= 30) return "中";
        return "低";
    }

    // ══════════════════════════════════════════════
    // 位次边界修正
    // ══════════════════════════════════════════════

    /**
     * 根据用户位次返回修正后的冲/稳/保位次区间比例.
     * 返回 double[6]: {reachUpper, reachLower, matchUpper, matchLower, safetyUpper, safetyLower}
     */
    public static double[] getAdjustedRatios(int userRank) {
        if (userRank <= TOP_RANK_THRESHOLD) {
            return new double[]{0.5, 0.8, 0.8, 1.5, 1.5, 3.0};
        } else if (userRank >= BOTTOM_RANK_THRESHOLD) {
            return new double[]{0.7, 0.95, 0.95, 1.3, 1.3, 2.5};
        } else {
            return new double[]{
                    REACH_UPPER_RATIO, REACH_LOWER_RATIO,
                    MATCH_UPPER_RATIO, MATCH_LOWER_RATIO,
                    SAFETY_UPPER_RATIO, SAFETY_LOWER_RATIO
            };
        }
    }

    // ══════════════════════════════════════════════
    // 综合风险评分 (方案级)
    // ══════════════════════════════════════════════

    /**
     * 计算方案的综合风险评分.
     */
    public static BigDecimal calcTotalRiskScore(List<BigDecimal> admissionProbs,
                                                 List<BigDecimal> majorAdjustRisks,
                                                 long reachCount, int totalCount) {
        if (totalCount == 0) return BigDecimal.ZERO;

        double avgProb = admissionProbs.stream()
                .mapToDouble(BigDecimal::doubleValue).average().orElse(50);
        double avgRisk = majorAdjustRisks.stream()
                .mapToDouble(BigDecimal::doubleValue).average().orElse(50);
        double reachRatio = (double) reachCount / totalCount;

        double score = (100 - avgProb) * 0.4 + avgRisk * 0.3 + reachRatio * 30 * 0.3;
        return BigDecimal.valueOf(Math.max(0, Math.min(100, score)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    // ══════════════════════════════════════════════
    // 工具方法
    // ══════════════════════════════════════════════

    public static int toInt(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(obj.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    public static String toStr(Object obj) {
        return (obj != null) ? obj.toString() : "";
    }

    // ══════════════════════════════════════════════
    // 概率解释构建
    // ══════════════════════════════════════════════

    /**
     * 构建录取概率的逐步解释.
     * 逻辑与 calcAdmissionProb 完全一致,但捕获每个中间值用于可解释性展示.
     */
    public static ProbabilityExplanationVO buildProbabilityExplanation(
            double avgRank, double stdDev, int userRank, String category,
            int r2020, int r2021, int r2022) {

        ProbabilityExplanationVO vo = new ProbabilityExplanationVO();
        vo.setRank2020(r2020);
        vo.setRank2021(r2021);
        vo.setRank2022(r2022);
        vo.setAvgRank3yr(BigDecimal.valueOf(avgRank).setScale(2, RoundingMode.HALF_UP));
        vo.setRankStdDev(BigDecimal.valueOf(stdDev).setScale(2, RoundingMode.HALF_UP));

        // Step 1: rank ratio
        double rankRatio = (userRank > 0) ? avgRank / userRank : 1.0;
        vo.setRankRatio(BigDecimal.valueOf(rankRatio).setScale(4, RoundingMode.HALF_UP));

        // Step 2: base probability
        double baseProbability = rankRatio * 50;
        vo.setBaseProbability(BigDecimal.valueOf(baseProbability).setScale(2, RoundingMode.HALF_UP));

        // Step 3: coefficient of variation
        double cv = (avgRank > 0) ? stdDev / avgRank : 0;
        vo.setCoefficientOfVariation(BigDecimal.valueOf(cv).setScale(4, RoundingMode.HALF_UP));

        // Step 4: stability factor
        double stabilityFactor = Math.max(0.5, 1.0 - cv * 0.5);
        vo.setStabilityFactor(BigDecimal.valueOf(stabilityFactor).setScale(4, RoundingMode.HALF_UP));

        // Step 5: adjusted probability
        double adjustedProbability = baseProbability * stabilityFactor;
        vo.setAdjustedProbability(BigDecimal.valueOf(adjustedProbability).setScale(2, RoundingMode.HALF_UP));

        // Step 6: category adjustment
        double finalProb = adjustedProbability;
        String categoryAdj;
        if ("冲".equals(category)) {
            finalProb = Math.min(finalProb, 50);
            categoryAdj = "冲一冲: 概率上限50%";
        } else if ("保".equals(category)) {
            finalProb = Math.max(finalProb, 55);
            categoryAdj = "保一保: 概率下限55%";
        } else {
            categoryAdj = "稳一稳: 无额外调整";
        }
        vo.setCategoryAdjustment(categoryAdj);

        // Step 7: clamp to [1, 99]
        finalProb = Math.max(1, Math.min(99, finalProb));
        vo.setFinalProbability(BigDecimal.valueOf(finalProb).setScale(2, RoundingMode.HALF_UP));

        // Build explanation text
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("该校近三年录取位次: %d(2020)、%d(2021)、%d(2022)，", r2020, r2021, r2022));
        sb.append(String.format("平均位次%.0f，标准差%.0f。", avgRank, stdDev));
        sb.append(String.format("您的位次%d，位次比=%.4f，基础概率=%.2f%%。", userRank, rankRatio, baseProbability));
        sb.append(String.format("稳定性因子=%.4f(波动%s)，调整后概率=%.2f%%。",
                stabilityFactor, cv <= 0.05 ? "很小" : cv <= 0.15 ? "适中" : "较大", adjustedProbability));
        sb.append(String.format("%s，最终录取概率=%.2f%%。", categoryAdj, finalProb));
        vo.setExplanation(sb.toString());

        return vo;
    }

    /**
     * 当历史数据缺失时构建默认概率解释.
     */
    public static ProbabilityExplanationVO buildMissingDataExplanation(String schoolName, String reason) {
        ProbabilityExplanationVO vo = new ProbabilityExplanationVO();
        vo.setRankRatio(BigDecimal.ONE);
        vo.setBaseProbability(BigDecimal.valueOf(50));
        vo.setCoefficientOfVariation(BigDecimal.ZERO);
        vo.setStabilityFactor(BigDecimal.ONE);
        vo.setAdjustedProbability(BigDecimal.valueOf(50));
        vo.setCategoryAdjustment("无历史数据,使用默认值");
        vo.setFinalProbability(BigDecimal.valueOf(50));
        vo.setRank2020(0);
        vo.setRank2021(0);
        vo.setRank2022(0);
        vo.setAvgRank3yr(BigDecimal.ZERO);
        vo.setRankStdDev(BigDecimal.ZERO);
        vo.setExplanation(String.format("%s: %s。使用默认录取概率50%%。", schoolName, reason));
        return vo;
    }
}
