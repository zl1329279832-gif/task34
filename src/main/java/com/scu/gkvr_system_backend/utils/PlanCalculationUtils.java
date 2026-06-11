package com.scu.gkvr_system_backend.utils;

import com.scu.gkvr_system_backend.pojo.PlanSchool;
import com.scu.gkvr_system_backend.vo.PlanRiskSummary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

public class PlanCalculationUtils {

    private PlanCalculationUtils() {
    }

    /**
     * 计算录取概率
     * 基于现有getReco的公式: (avgRank/userRank)*50, 增加稳定性因子
     */
    public static int calcAdmissionProb(double avgRank3yr, int userRank, double rankStdDev) {
        if (userRank <= 0) {
            return 50;
        }
        double baseProb = (avgRank3yr / userRank) * 50.0;
        double stabilityFactor = 1.0;
        if (avgRank3yr > 0) {
            stabilityFactor = 1.0 - Math.min(rankStdDev / avgRank3yr, 0.5);
        }
        double adjustedProb = baseProb * stabilityFactor;
        return Math.max(1, Math.min(99, (int) adjustedProb));
    }

    /**
     * 计算专业调剂风险
     * 无数据返回50(中等风险)
     */
    public static double calcMajorAdjustRisk(int userScore, List<Map<String, Object>> majorScoreList) {
        if (majorScoreList == null || majorScoreList.isEmpty()) {
            return 50.0;
        }
        double totalShortfall = 0.0;
        int count = 0;
        for (Map<String, Object> major : majorScoreList) {
            Object avgObj = major.get("avgScore");
            if (avgObj == null) continue;
            int avgScore = ((Number) avgObj).intValue();
            if (avgScore <= 0) continue;
            double shortfall = Math.max(0, (avgScore - userScore)) / (double) avgScore * 100.0;
            totalShortfall += shortfall;
            count++;
        }
        if (count == 0) {
            return 50.0;
        }
        return totalShortfall / count;
    }

    /**
     * 计算三年位次标准差
     */
    public static double calcRankStdDev(int rank2020, int rank2021, int rank2022) {
        double avg = (rank2020 + rank2021 + rank2022) / 3.0;
        double variance = (Math.pow(rank2020 - avg, 2)
                + Math.pow(rank2021 - avg, 2)
                + Math.pow(rank2022 - avg, 2)) / 3.0;
        return Math.sqrt(variance);
    }

    /**
     * 计算位次趋势
     * rising: 学校越来越难考(位次数值下降)
     * declining: 学校越来越容易(位次数值上升)
     */
    public static String calcRankTrend(int rank2020, int rank2021, int rank2022) {
        if (rank2020 <= 0) {
            return "stable";
        }
        if (rank2022 < rank2020 * 0.9) {
            return "rising";
        } else if (rank2022 > rank2020 * 1.1) {
            return "declining";
        }
        return "stable";
    }

    /**
     * 计算热度分数
     */
    public static double calcPopularityScore(int monthView) {
        return Math.min(monthView / 50.0, 100.0);
    }

    /**
     * 计算热度趋势
     */
    public static String calcPopularityTrend(int monthView, String totalView) {
        if (totalView == null || totalView.isEmpty()) {
            return "stable";
        }
        try {
            double total = Double.parseDouble(totalView);
            double yearlyAvg = total / 12.0;
            if (yearlyAvg <= 0) {
                return "stable";
            }
            if (monthView > yearlyAvg * 1.2) {
                return "rising";
            } else if (monthView < yearlyAvg * 0.8) {
                return "declining";
            }
            return "stable";
        } catch (NumberFormatException e) {
            return "stable";
        }
    }

    /**
     * 构建位次波动解释
     */
    public static String buildRankFluctuationExplanation(double rankStdDev, double avgRank3yr) {
        if (avgRank3yr <= 0) {
            return "数据不足，无法分析位次波动";
        }
        double cv = rankStdDev / avgRank3yr;
        long stdDevRound = Math.round(rankStdDev);
        if (cv < 0.05) {
            return "近三年位次稳定(标准差" + stdDevRound + ")，录取确定性高";
        } else if (cv < 0.15) {
            return "近三年位次小幅波动(标准差" + stdDevRound + ")，录取有一定不确定性";
        } else {
            return "近三年位次波动较大(标准差" + stdDevRound + ")，录取存在不确定性，建议谨慎";
        }
    }

    /**
     * 获取位次边界调整后的冲/稳/保分配比例
     */
    public static double[] getAdjustedRatios(int userRank) {
        if (userRank < 500) {
            return new double[]{0.2, 0.5, 0.3};
        } else if (userRank > 200000) {
            return new double[]{0.3, 0.3, 0.4};
        }
        return new double[]{0.3, 0.4, 0.3};
    }

    /**
     * 构建风险摘要
     */
    public static PlanRiskSummary buildRiskSummary(List<PlanSchool> schools) {
        PlanRiskSummary summary = new PlanRiskSummary();
        if (schools == null || schools.isEmpty()) {
            summary.setTotalRiskScore(BigDecimal.ZERO);
            summary.setRiskLevel("低风险");
            summary.setReachCount(0);
            summary.setMatchCount(0);
            summary.setSafetyCount(0);
            summary.setSuggestion("暂无院校数据");
            return summary;
        }

        int reachCount = 0, matchCount = 0, safetyCount = 0;
        double weightedSum = 0.0;
        double totalWeight = 0.0;

        for (PlanSchool school : schools) {
            double prob = school.getAdmissionProb() != null ? school.getAdmissionProb().doubleValue() : 50.0;
            double riskContribution = 100 - prob;
            switch (school.getCategory()) {
                case "冲":
                    reachCount++;
                    weightedSum += riskContribution * 1.5;
                    totalWeight += 1.5;
                    break;
                case "稳":
                    matchCount++;
                    weightedSum += riskContribution * 1.0;
                    totalWeight += 1.0;
                    break;
                case "保":
                    safetyCount++;
                    weightedSum += riskContribution * 0.5;
                    totalWeight += 0.5;
                    break;
                default:
                    break;
            }
        }

        summary.setReachCount(reachCount);
        summary.setMatchCount(matchCount);
        summary.setSafetyCount(safetyCount);

        double totalRisk = totalWeight > 0 ? weightedSum / totalWeight : 0;
        summary.setTotalRiskScore(BigDecimal.valueOf(totalRisk).setScale(2, RoundingMode.HALF_UP));

        if (totalRisk > 65) {
            summary.setRiskLevel("高风险");
        } else if (totalRisk > 35) {
            summary.setRiskLevel("中等风险");
        } else {
            summary.setRiskLevel("低风险");
        }

        if (safetyCount == 0 && reachCount > 0) {
            summary.setSuggestion("冲的比例过高，建议增加稳/保院校");
        } else if (reachCount == 0 && matchCount == 0 && safetyCount > 0) {
            summary.setSuggestion("方案过于保守，可适当增加冲的院校");
        } else if (reachCount > 0 && matchCount > 0 && safetyCount > 0) {
            summary.setSuggestion("方案分布合理，冲稳保搭配得当");
        } else {
            summary.setSuggestion("建议完善方案，确保冲稳保均有覆盖");
        }

        return summary;
    }

    /**
     * 概率等级标签
     */
    public static String probLevel(double prob) {
        if (prob >= 70) return "高";
        if (prob >= 40) return "中";
        return "低";
    }

    /**
     * 风险等级标签
     */
    public static String riskLevel(double risk) {
        if (risk >= 60) return "高";
        if (risk >= 30) return "中";
        return "低";
    }
}
