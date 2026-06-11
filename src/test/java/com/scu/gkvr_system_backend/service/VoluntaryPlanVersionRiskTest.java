package com.scu.gkvr_system_backend.service;

import com.scu.gkvr_system_backend.dto.SchoolReorderDTO;
import com.scu.gkvr_system_backend.mapper.*;
import com.scu.gkvr_system_backend.pojo.*;
import com.scu.gkvr_system_backend.service.impl.VoluntaryPlanServiceImpl;
import com.scu.gkvr_system_backend.utils.PlanCalculationUtils;
import com.scu.gkvr_system_backend.vo.PlanComparisonVO;
import com.scu.gkvr_system_backend.vo.PlanDetailVO;
import com.scu.gkvr_system_backend.vo.PlanSchoolVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 方案版本隔离与风险重算综合测试.
 * 覆盖: 位次边界、跨批次筛选、复制后重算、同校冲突、用户隔离、历史版本、调序隔离.
 */
@ExtendWith(MockitoExtension.class)
class VoluntaryPlanVersionRiskTest {

    @InjectMocks
    private VoluntaryPlanServiceImpl service;

    @Mock
    private VoluntaryPlanMapper voluntaryPlanMapper;
    @Mock
    private PlanSchoolMapper planSchoolMapper;
    @Mock
    private PlanVersionLogMapper planVersionLogMapper;
    @Mock
    private ScLiScoreMapper scLiScoreMapper;
    @Mock
    private ScoreRankMapper scoreRankMapper;
    @Mock
    private SchoolInfoMapper schoolInfoMapper;
    @Mock
    private MajorScoreMapper majorScoreMapper;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", voluntaryPlanMapper);
    }

    // ══════════════════════════════════════════
    // 1. 位次边界测试
    // ══════════════════════════════════════════

    @Nested
    class RankBoundaryTests {

        @Test
        void topRank_exactBoundary_usesWidenedRatios() {
            double[] ratios = PlanCalculationUtils.getAdjustedRatios(100);
            assertEquals(0.5, ratios[0], 0.001, "top rank reachUpper should be 0.5");
            assertEquals(3.0, ratios[5], 0.001, "top rank safetyLower should be 3.0");
        }

        @Test
        void topRank_rank1_usesWidenedRatios() {
            double[] ratios = PlanCalculationUtils.getAdjustedRatios(1);
            assertEquals(0.5, ratios[0], 0.001);
            assertEquals(0.8, ratios[1], 0.001);
            assertEquals(3.0, ratios[5], 0.001);
        }

        @Test
        void bottomRank_exactBoundary_usesNarrowedRatios() {
            double[] ratios = PlanCalculationUtils.getAdjustedRatios(200000);
            assertEquals(0.7, ratios[0], 0.001, "bottom rank reachUpper should be 0.7");
            assertEquals(2.5, ratios[5], 0.001, "bottom rank safetyLower should be 2.5");
        }

        @Test
        void bottomRank_extremeHigh_usesNarrowedRatios() {
            double[] ratios = PlanCalculationUtils.getAdjustedRatios(500000);
            assertEquals(0.7, ratios[0], 0.001);
            assertEquals(0.95, ratios[1], 0.001);
        }

        @Test
        void normalRank_justAboveTop_usesDefaultRatios() {
            double[] ratios = PlanCalculationUtils.getAdjustedRatios(101);
            assertEquals(PlanCalculationUtils.REACH_UPPER_RATIO, ratios[0], 0.001);
            assertEquals(PlanCalculationUtils.SAFETY_LOWER_RATIO, ratios[5], 0.001);
        }

        @Test
        void normalRank_justBelowBottom_usesDefaultRatios() {
            double[] ratios = PlanCalculationUtils.getAdjustedRatios(199999);
            assertEquals(PlanCalculationUtils.REACH_UPPER_RATIO, ratios[0], 0.001);
            assertEquals(PlanCalculationUtils.SAFETY_LOWER_RATIO, ratios[5], 0.001);
        }

        @Test
        void admissionProb_topRankReach_cappedAt50() {
            BigDecimal prob = PlanCalculationUtils.calcAdmissionProb(50, 10, 80, "冲");
            assertTrue(prob.doubleValue() <= 50.0,
                    "冲类别概率应封顶50, actual=" + prob);
        }

        @Test
        void admissionProb_bottomRankSafety_flooredAt55() {
            BigDecimal prob = PlanCalculationUtils.calcAdmissionProb(250000, 5000, 200000, "保");
            assertTrue(prob.doubleValue() >= 55.0,
                    "保类别概率应底线55, actual=" + prob);
        }
    }

    // ══════════════════════════════════════════
    // 2. 跨批次筛选测试
    // ══════════════════════════════════════════

    @Nested
    class CrossBatchFilterTests {

        @Test
        void deduplicateMajorScores_removeDuplicates_keepsHigherAvg() {
            List<Map<String, Object>> rows = new ArrayList<>();
            rows.add(createMajorRow("计算机科学", 620, 580, 600, "本科一批"));
            rows.add(createMajorRow("计算机科学", 610, 570, 590, "本科二批")); // dup, lower avg
            rows.add(createMajorRow("电子工程", 600, 560, 580, "本科一批"));

            List<Map<String, Object>> deduped = PlanCalculationUtils.deduplicateMajorScores(rows);

            assertEquals(2, deduped.size(), "应去重为2条");
            assertEquals(600, PlanCalculationUtils.toInt(deduped.get(0).get("avgScore")),
                    "计算机科学应保留avgScore=600的记录");
            assertEquals("电子工程", PlanCalculationUtils.toStr(deduped.get(1).get("majorName")));
        }

        @Test
        void deduplicateMajorScores_duplicateKeepsHigher() {
            List<Map<String, Object>> rows = new ArrayList<>();
            rows.add(createMajorRow("软件工程", 590, 550, 570, "本科二批")); // lower avg first
            rows.add(createMajorRow("软件工程", 620, 580, 600, "本科一批")); // higher avg second

            List<Map<String, Object>> deduped = PlanCalculationUtils.deduplicateMajorScores(rows);

            assertEquals(1, deduped.size());
            assertEquals(600, PlanCalculationUtils.toInt(deduped.get(0).get("avgScore")),
                    "应保留avgScore更高的600, 而非570");
        }

        @Test
        void deduplicateMajorScores_emptyList_returnsEmpty() {
            List<Map<String, Object>> result = PlanCalculationUtils.deduplicateMajorScores(
                    Collections.emptyList());
            assertTrue(result.isEmpty());
        }

        @Test
        void deduplicateMajorScores_nullList_returnsNull() {
            assertNull(PlanCalculationUtils.deduplicateMajorScores(null));
        }

        @Test
        void deduplicateMajorScores_allUnique_noChange() {
            List<Map<String, Object>> rows = new ArrayList<>();
            rows.add(createMajorRow("计算机", 620, 580, 600, "本科一批"));
            rows.add(createMajorRow("数学", 600, 560, 580, "本科一批"));
            rows.add(createMajorRow("物理", 590, 550, 570, "本科一批"));

            List<Map<String, Object>> deduped = PlanCalculationUtils.deduplicateMajorScores(rows);
            assertEquals(3, deduped.size(), "无重复应保留所有记录");
        }

        @Test
        void majorAdjustRisk_withDuplicates_inflatesRisk() {
            // 不去重: 同专业两条记录会拉低平均风险(都在分数之上)
            List<Map<String, Object>> withDups = new ArrayList<>();
            withDups.add(createMajorRow("CS", 650, 620, 635, "一批"));
            withDups.add(createMajorRow("CS", 640, 610, 625, "二批"));
            withDups.add(createMajorRow("EE", 600, 560, 580, "一批"));

            BigDecimal riskWithDups = PlanCalculationUtils.calcMajorAdjustRisk(withDups, 600);

            // 去重后
            List<Map<String, Object>> deduped = PlanCalculationUtils.deduplicateMajorScores(withDups);
            BigDecimal riskDeduped = PlanCalculationUtils.calcMajorAdjustRisk(deduped, 600);

            assertNotEquals(riskWithDups.doubleValue(), riskDeduped.doubleValue(),
                    "去重前后风险值应不同, 证明去重有效");
        }

        private Map<String, Object> createMajorRow(String name, int max, int min, int avg, String batch) {
            Map<String, Object> row = new HashMap<>();
            row.put("majorName", name);
            row.put("maxScore", max);
            row.put("minScore", min);
            row.put("avgScore", avg);
            row.put("batch", batch);
            return row;
        }
    }

    // ══════════════════════════════════════════
    // 3. 方案复制后风险重算测试
    // ══════════════════════════════════════════

    @Nested
    class CopyAndReevaluateTests {

        @Test
        void copyPlan_recalcMetrics_notCopyOldValues() {
            VoluntaryPlan original = createPlan("userA", 1, 580, 20000);
            original.setTotalRiskScore(BigDecimal.valueOf(42.5));
            when(voluntaryPlanMapper.selectById(1)).thenReturn(original);

            // 原方案有一个院校, 旧风险值很高
            PlanSchool origSchool = new PlanSchool();
            origSchool.setId(10);
            origSchool.setPlanId(1);
            origSchool.setSchoolId(3);
            origSchool.setSchoolName("四川大学");
            origSchool.setCategory("稳");
            origSchool.setAdmissionProb(BigDecimal.valueOf(88.0)); // 旧值
            origSchool.setMajorAdjustRisk(BigDecimal.valueOf(15.0)); // 旧值
            origSchool.setRankFluctuation("旧的波动描述");
            origSchool.setSelectedMajors("计算机,软件工程");
            when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(List.of(origSchool));

            // 新方案ID
            when(voluntaryPlanMapper.insert(any())).thenAnswer(inv -> {
                ((VoluntaryPlan) inv.getArgument(0)).setId(2);
                return 1;
            });

            // 数据源返回ScLiScore用于独立计算
            ScLiScore sc = new ScLiScore();
            sc.setRank2020(18000);
            sc.setRank2021(19000);
            sc.setRank2022(20000);
            when(scLiScoreMapper.selectOne(any())).thenReturn(sc);
            when(planSchoolMapper.selectMajorScoresForSchool(eq(3), any()))
                    .thenReturn(Collections.emptyList());
            when(schoolInfoMapper.selectById(3)).thenReturn(null);
            when(planSchoolMapper.insert(any())).thenReturn(1);
            when(voluntaryPlanMapper.updateById(any())).thenReturn(1);
            when(planVersionLogMapper.insert(any())).thenReturn(1);

            Integer newId = service.copyPlan(1, "userA", "副本");
            assertEquals(2, newId);

            // 验证插入的PlanSchool不是直接复制旧值
            ArgumentCaptor<PlanSchool> captor = ArgumentCaptor.forClass(PlanSchool.class);
            verify(planSchoolMapper).insert(captor.capture());
            PlanSchool cloned = captor.getValue();

            assertEquals(2, cloned.getPlanId(), "应关联到新方案");
            assertNotEquals(BigDecimal.valueOf(88.0), cloned.getAdmissionProb(),
                    "录取概率应独立重算, 不应等于旧值88.0");
            assertNotEquals("旧的波动描述", cloned.getRankFluctuation(),
                    "位次波动应独立重算, 不应等于旧描述");
        }

        @Test
        void copyPlan_independentVersion_startsAt1() {
            VoluntaryPlan original = createPlan("userA", 1, 580, 20000);
            original.setVersion(5); // 原方案已经到第5版
            when(voluntaryPlanMapper.selectById(1)).thenReturn(original);
            when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(Collections.emptyList());
            when(voluntaryPlanMapper.insert(any())).thenAnswer(inv -> {
                ((VoluntaryPlan) inv.getArgument(0)).setId(2);
                return 1;
            });
            when(voluntaryPlanMapper.updateById(any())).thenReturn(1);
            when(planVersionLogMapper.insert(any())).thenReturn(1);

            service.copyPlan(1, "userA", "副本");

            ArgumentCaptor<VoluntaryPlan> captor = ArgumentCaptor.forClass(VoluntaryPlan.class);
            verify(voluntaryPlanMapper).insert(captor.capture());
            assertEquals(1, captor.getValue().getVersion(), "复制方案版本应从1开始");
            assertEquals(1, captor.getValue().getParentId(), "parentId应指向原方案");
        }

        @Test
        void copyPlan_totalRiskScore_independentlyCalculated() {
            VoluntaryPlan original = createPlan("userA", 1, 580, 20000);
            original.setTotalRiskScore(BigDecimal.valueOf(99.99));
            when(voluntaryPlanMapper.selectById(1)).thenReturn(original);
            when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(Collections.emptyList());
            when(voluntaryPlanMapper.insert(any())).thenAnswer(inv -> {
                ((VoluntaryPlan) inv.getArgument(0)).setId(2);
                return 1;
            });
            when(voluntaryPlanMapper.updateById(any())).thenReturn(1);
            when(planVersionLogMapper.insert(any())).thenReturn(1);

            service.copyPlan(1, "userA", "副本");

            // 验证updateById时的totalRiskScore不是99.99
            ArgumentCaptor<VoluntaryPlan> captor = ArgumentCaptor.forClass(VoluntaryPlan.class);
            verify(voluntaryPlanMapper).updateById(captor.capture());
            assertNotEquals(BigDecimal.valueOf(99.99), captor.getValue().getTotalRiskScore(),
                    "总风险评分应独立计算, 不应复制原方案的99.99");
        }

        @Test
        void reevaluatePlan_afterCopy_freshMetrics() {
            VoluntaryPlan plan = createPlan("userA", 2, 600, 18000);
            plan.setVersion(1);
            plan.setBatchName("本科一批");
            when(voluntaryPlanMapper.selectById(2)).thenReturn(plan);

            PlanSchool ps = new PlanSchool();
            ps.setId(20);
            ps.setPlanId(2);
            ps.setSchoolId(5);
            ps.setSchoolName("Test");
            ps.setCategory("冲");
            ps.setAdmissionProb(BigDecimal.valueOf(30)); // 旧值
            ps.setMajorAdjustRisk(BigDecimal.valueOf(60)); // 旧值
            ps.setRankFluctuation("旧描述");
            ps.setPopularityScore(BigDecimal.valueOf(80));
            ps.setPopularityTrend("rising");
            when(planSchoolMapper.selectByPlanIdOrdered(2)).thenReturn(List.of(ps));

            // ScLiScore返回null → 应重置为默认值
            when(scLiScoreMapper.selectOne(any())).thenReturn(null);
            when(planSchoolMapper.selectMajorScoresForSchool(anyInt(), any()))
                    .thenReturn(Collections.emptyList());
            when(schoolInfoMapper.selectById(anyInt())).thenReturn(null);
            when(planSchoolMapper.updateById(any())).thenReturn(1);
            when(voluntaryPlanMapper.updateById(any())).thenReturn(1);
            when(planVersionLogMapper.insert(any())).thenReturn(1);

            service.reevaluatePlan(2, "userA");

            // 验证旧值被清理
            ArgumentCaptor<PlanSchool> captor = ArgumentCaptor.forClass(PlanSchool.class);
            verify(planSchoolMapper).updateById(captor.capture());
            PlanSchool updated = captor.getValue();

            assertEquals(BigDecimal.valueOf(50), updated.getAdmissionProb(),
                    "无ScLiScore时应重置为默认50");
            assertEquals("暂无历年数据", updated.getRankFluctuation(),
                    "无ScLiScore时应重置为默认描述");
            assertEquals(50.0, updated.getMajorAdjustRisk().doubleValue(), 0.01,
                    "无专业数据时调剂风险应为默认50");
            assertEquals(BigDecimal.ZERO, updated.getPopularityScore(),
                    "无SchoolInfo时热度应为0");
            assertEquals("stable", updated.getPopularityTrend(),
                    "无SchoolInfo时趋势应为stable");
        }
    }

    // ══════════════════════════════════════════
    // 4. 同校多专业冲突测试
    // ══════════════════════════════════════════

    @Nested
    class SameSchoolConflictTests {

        @Test
        void buildMergedConflictWarnings_sameSchoolMultiCategory_mergedWarning() {
            PlanSchoolVO reach = createVO(1, "清华大学", "冲", List.of("计算机", "软件"));
            PlanSchoolVO match = createVO(1, "清华大学", "稳", List.of("数学"));

            List<String> warnings = PlanCalculationUtils.buildMergedConflictWarnings(
                    List.of(reach), List.of(match), Collections.emptyList());

            assertEquals(1, warnings.size(), "同校应合并为一条警告");
            assertTrue(warnings.get(0).contains("清华大学"), "应包含校名");
            assertTrue(warnings.get(0).contains("冲") && warnings.get(0).contains("稳"),
                    "应包含所有类别");
            assertTrue(warnings.get(0).contains("建议仅保留一个类别"),
                    "应包含去重建议");
        }

        @Test
        void buildMergedConflictWarnings_threeCategories_singleWarning() {
            PlanSchoolVO r = createVO(5, "北京大学", "冲", null);
            PlanSchoolVO m = createVO(5, "北京大学", "稳", null);
            PlanSchoolVO s = createVO(5, "北京大学", "保", null);

            List<String> warnings = PlanCalculationUtils.buildMergedConflictWarnings(
                    List.of(r), List.of(m), List.of(s));

            assertEquals(1, warnings.size());
            assertTrue(warnings.get(0).contains("冲") && warnings.get(0).contains("稳")
                    && warnings.get(0).contains("保"));
        }

        @Test
        void buildMergedConflictWarnings_allUnique_noWarnings() {
            PlanSchoolVO r = createVO(1, "清华", "冲", null);
            PlanSchoolVO m = createVO(2, "北大", "稳", null);
            PlanSchoolVO s = createVO(3, "复旦", "保", null);

            List<String> warnings = PlanCalculationUtils.buildMergedConflictWarnings(
                    List.of(r), List.of(m), List.of(s));

            assertTrue(warnings.isEmpty(), "无冲突时应返回空列表");
        }

        @Test
        void buildMergedConflictWarnings_withMajorInfo_includesMajorDetail() {
            PlanSchoolVO reach = createVO(1, "Test大学", "冲", List.of("计算机"));
            PlanSchoolVO safety = createVO(1, "Test大学", "保", List.of("数学", "物理"));

            List<String> warnings = PlanCalculationUtils.buildMergedConflictWarnings(
                    List.of(reach), Collections.emptyList(), List.of(safety));

            assertEquals(1, warnings.size());
            assertTrue(warnings.get(0).contains("专业分布"), "有专业信息时应包含专业分布");
            assertTrue(warnings.get(0).contains("计算机"), "应包含冲档专业");
            assertTrue(warnings.get(0).contains("数学"), "应包含保档专业");
        }

        @Test
        void buildMergedConflictWarnings_multipleConflicts_separateWarnings() {
            PlanSchoolVO r1 = createVO(1, "清华", "冲", null);
            PlanSchoolVO r2 = createVO(2, "北大", "冲", null);
            PlanSchoolVO m1 = createVO(1, "清华", "稳", null);
            PlanSchoolVO m2 = createVO(2, "北大", "稳", null);

            List<String> warnings = PlanCalculationUtils.buildMergedConflictWarnings(
                    List.of(r1, r2), List.of(m1, m2), Collections.emptyList());

            assertEquals(2, warnings.size(), "两组冲突应产生两条警告");
        }

        @Test
        void getPlanDetail_includesConflictWarnings() {
            VoluntaryPlan plan = createPlan("userA", 1, 580, 20000);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);

            // 同一院校出现在冲和稳
            PlanSchool ps1 = createPlanSchool(10, 1, 3, "四川大学", "冲");
            PlanSchool ps2 = createPlanSchool(11, 1, 3, "四川大学", "稳");
            when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(List.of(ps1, ps2));
            when(planSchoolMapper.selectMajorScoresForSchool(anyInt(), any()))
                    .thenReturn(Collections.emptyList());

            PlanDetailVO detail = service.getPlanDetail(1, "userA");

            assertNotNull(detail.getConflictWarnings(), "应包含conflictWarnings");
            assertFalse(detail.getConflictWarnings().isEmpty(), "同校跨类别应产生警告");
            assertTrue(detail.getConflictWarnings().get(0).contains("四川大学"));
        }

        private PlanSchoolVO createVO(int schoolId, String name, String category, List<String> majors) {
            PlanSchoolVO vo = new PlanSchoolVO();
            vo.setSchoolId(schoolId);
            vo.setSchoolName(name);
            vo.setCategory(category);
            vo.setAdmissionProb(BigDecimal.valueOf(50));
            vo.setMajorAdjustRisk(BigDecimal.valueOf(30));
            vo.setSelectedMajors(majors);
            return vo;
        }
    }

    // ══════════════════════════════════════════
    // 5. 用户隔离扩展测试
    // ══════════════════════════════════════════

    @Nested
    class UserIsolationExtendedTests {

        @Test
        void copyPlan_otherUser_returnsNull() {
            VoluntaryPlan plan = createPlan("userA", 1, 580, 20000);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);

            assertNull(service.copyPlan(1, "userB", "偷方案"),
                    "非所有者不能复制方案");
            verify(voluntaryPlanMapper, never()).insert(any());
        }

        @Test
        void reorderSchools_otherUser_returnsFalse() {
            VoluntaryPlan plan = createPlan("userA", 1, 580, 20000);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);

            SchoolReorderDTO dto = new SchoolReorderDTO();
            dto.setPlanId(1);
            dto.setUserName("userB");
            dto.setOrderedSchools(List.of());

            assertFalse(service.reorderSchools(dto),
                    "非所有者不能调序");
            verify(planSchoolMapper, never()).updateSortOrder(anyInt(), anyInt());
        }

        @Test
        void reevaluatePlan_otherUser_returnsNull() {
            VoluntaryPlan plan = createPlan("userA", 1, 580, 20000);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);

            assertNull(service.reevaluatePlan(1, "userB"),
                    "非所有者不能重新评估");
            verify(planSchoolMapper, never()).updateById(any());
        }

        @Test
        void comparePlans_crossUser_returnsNull() {
            VoluntaryPlan planA = createPlan("userA", 1, 580, 20000);
            VoluntaryPlan planB = createPlan("userB", 2, 600, 18000);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(planA);
            when(voluntaryPlanMapper.selectById(2)).thenReturn(planB);

            // userA尝试对比: 自己的方案和userB的方案
            PlanComparisonVO result = service.comparePlans(1, 2, "userA");
            assertNull(result, "不能对比其他用户的方案");
        }

        @Test
        void deletePlan_otherUser_returnsFalse() {
            VoluntaryPlan plan = createPlan("userA", 1, 580, 20000);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);

            assertFalse(service.deletePlan(1, "userB"),
                    "非所有者不能删除方案");
        }

        @Test
        void updatePlanStatus_otherUser_returnsFalse() {
            VoluntaryPlan plan = createPlan("userA", 1, 580, 20000);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);

            assertFalse(service.updatePlanStatus(1, "userB", 1),
                    "非所有者不能更新状态");
        }

        @Test
        void getPlanDetail_nonexistentPlan_returnsNull() {
            when(voluntaryPlanMapper.selectById(999)).thenReturn(null);
            assertNull(service.getPlanDetail(999, "userA"));
        }

        @Test
        void copyPlan_nonexistentPlan_returnsNull() {
            when(voluntaryPlanMapper.selectById(999)).thenReturn(null);
            assertNull(service.copyPlan(999, "userA", "不存在"));
        }
    }

    // ══════════════════════════════════════════
    // 6. 历史版本对比测试
    // ══════════════════════════════════════════

    @Nested
    class HistoricalVersionComparisonTests {

        @Test
        void reevaluatePlan_createsOldAndNewSnapshots() {
            VoluntaryPlan plan = createPlan("userA", 1, 580, 20000);
            plan.setVersion(3);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);

            PlanSchool ps = createPlanSchool(10, 1, 3, "Test", "稳");
            when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(List.of(ps));
            when(scLiScoreMapper.selectOne(any())).thenReturn(null);
            when(planSchoolMapper.selectMajorScoresForSchool(anyInt(), any()))
                    .thenReturn(Collections.emptyList());
            when(schoolInfoMapper.selectById(anyInt())).thenReturn(null);
            when(planSchoolMapper.updateById(any())).thenReturn(1);
            when(voluntaryPlanMapper.updateById(any())).thenReturn(1);
            when(planVersionLogMapper.insert(any())).thenReturn(1);

            service.reevaluatePlan(1, "userA");

            // 应有2次快照: 旧版本(v3) + 新版本(v4)
            ArgumentCaptor<PlanVersionLog> captor = ArgumentCaptor.forClass(PlanVersionLog.class);
            verify(planVersionLogMapper, times(2)).insert(captor.capture());

            List<PlanVersionLog> snapshots = captor.getAllValues();
            assertEquals(3, snapshots.get(0).getVersion(), "第一次快照应为旧版本3");
            assertEquals(4, snapshots.get(1).getVersion(), "第二次快照应为新版本4");
            assertEquals(4, plan.getVersion(), "方案版本应递增到4");
        }

        @Test
        void comparePlans_detectsAddedAndRemoved() {
            VoluntaryPlan planA = createPlan("userA", 1, 580, 20000);
            VoluntaryPlan planB = createPlan("userA", 2, 580, 20000);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(planA);
            when(voluntaryPlanMapper.selectById(2)).thenReturn(planB);

            // Plan A has school 3, Plan B has school 5
            PlanSchool psA = createPlanSchool(10, 1, 3, "四川大学", "稳");
            PlanSchool psB = createPlanSchool(20, 2, 5, "成都理工", "保");
            when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(List.of(psA));
            when(planSchoolMapper.selectByPlanIdOrdered(2)).thenReturn(List.of(psB));
            when(planSchoolMapper.selectMajorScoresForSchool(anyInt(), any()))
                    .thenReturn(Collections.emptyList());

            PlanComparisonVO result = service.comparePlans(1, 2, "userA");

            assertNotNull(result);
            assertEquals(2, result.getDifferences().size(), "应有2处差异: 1个removed + 1个added");

            long removed = result.getDifferences().stream()
                    .filter(d -> "removed".equals(d.getChangeType())).count();
            long added = result.getDifferences().stream()
                    .filter(d -> "added".equals(d.getChangeType())).count();
            assertEquals(1, removed);
            assertEquals(1, added);
        }

        @Test
        void comparePlans_detectsCategoryChange() {
            VoluntaryPlan planA = createPlan("userA", 1, 580, 20000);
            VoluntaryPlan planB = createPlan("userA", 2, 580, 20000);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(planA);
            when(voluntaryPlanMapper.selectById(2)).thenReturn(planB);

            PlanSchool psA = createPlanSchool(10, 1, 3, "四川大学", "冲");
            PlanSchool psB = createPlanSchool(20, 2, 3, "四川大学", "保");
            when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(List.of(psA));
            when(planSchoolMapper.selectByPlanIdOrdered(2)).thenReturn(List.of(psB));
            when(planSchoolMapper.selectMajorScoresForSchool(anyInt(), any()))
                    .thenReturn(Collections.emptyList());

            PlanComparisonVO result = service.comparePlans(1, 2, "userA");

            boolean hasCategoryChange = result.getDifferences().stream()
                    .anyMatch(d -> "category_changed".equals(d.getChangeType()));
            assertTrue(hasCategoryChange, "应检测到类别从冲变为保");
        }

        @Test
        void comparePlans_summaryText_containsCounts() {
            VoluntaryPlan planA = createPlan("userA", 1, 580, 20000);
            VoluntaryPlan planB = createPlan("userA", 2, 580, 20000);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(planA);
            when(voluntaryPlanMapper.selectById(2)).thenReturn(planB);
            when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(Collections.emptyList());
            when(planSchoolMapper.selectByPlanIdOrdered(2)).thenReturn(Collections.emptyList());

            PlanComparisonVO result = service.comparePlans(1, 2, "userA");

            assertNotNull(result.getSummary());
            assertTrue(result.getSummary().contains("方案A"), "摘要应包含方案A标识");
            assertTrue(result.getSummary().contains("方案B"), "摘要应包含方案B标识");
        }
    }

    // ══════════════════════════════════════════
    // 7. 人工调序隔离测试
    // ══════════════════════════════════════════

    @Nested
    class ManualReorderIsolationTests {

        @Test
        void reorderSchools_createsVersionSnapshot() {
            VoluntaryPlan plan = createPlan("userA", 1, 580, 20000);
            plan.setVersion(2);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);

            PlanSchool ps = createPlanSchool(10, 1, 3, "Test", "稳");
            when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(List.of(ps));
            when(planSchoolMapper.updateSortOrder(anyInt(), anyInt())).thenReturn(1);
            when(voluntaryPlanMapper.updateById(any())).thenReturn(1);
            when(planVersionLogMapper.insert(any())).thenReturn(1);

            SchoolReorderDTO dto = new SchoolReorderDTO();
            dto.setPlanId(1);
            dto.setUserName("userA");
            SchoolReorderDTO.SchoolOrderItem item = new SchoolReorderDTO.SchoolOrderItem();
            item.setPlanSchoolId(10);
            item.setSortOrder(5);
            dto.setOrderedSchools(List.of(item));

            Boolean result = service.reorderSchools(dto);
            assertTrue(result);

            // 验证: 调序前快照(v2) + 调序后快照(v3) = 2次
            ArgumentCaptor<PlanVersionLog> captor = ArgumentCaptor.forClass(PlanVersionLog.class);
            verify(planVersionLogMapper, times(2)).insert(captor.capture());
            List<PlanVersionLog> logs = captor.getAllValues();
            assertEquals(2, logs.get(0).getVersion(), "旧版本快照应为v2");
            assertEquals(3, logs.get(1).getVersion(), "新版本快照应为v3");
        }

        @Test
        void reorderSchools_incrementsVersion() {
            VoluntaryPlan plan = createPlan("userA", 1, 580, 20000);
            plan.setVersion(1);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);

            PlanSchool ps = createPlanSchool(10, 1, 3, "Test", "稳");
            when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(List.of(ps));
            when(planSchoolMapper.updateSortOrder(anyInt(), anyInt())).thenReturn(1);
            when(voluntaryPlanMapper.updateById(any())).thenReturn(1);
            when(planVersionLogMapper.insert(any())).thenReturn(1);

            SchoolReorderDTO dto = new SchoolReorderDTO();
            dto.setPlanId(1);
            dto.setUserName("userA");
            SchoolReorderDTO.SchoolOrderItem item = new SchoolReorderDTO.SchoolOrderItem();
            item.setPlanSchoolId(10);
            item.setSortOrder(0);
            dto.setOrderedSchools(List.of(item));

            service.reorderSchools(dto);

            assertEquals(2, plan.getVersion(), "调序后版本应从1递增到2");
            verify(voluntaryPlanMapper).updateById(plan);
        }

        @Test
        void reorderSchools_onlyAffectsTargetPlan() {
            VoluntaryPlan plan = createPlan("userA", 1, 580, 20000);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);

            PlanSchool ps = createPlanSchool(10, 1, 3, "Test", "稳");
            when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(List.of(ps));
            when(planSchoolMapper.updateSortOrder(anyInt(), anyInt())).thenReturn(1);
            when(voluntaryPlanMapper.updateById(any())).thenReturn(1);
            when(planVersionLogMapper.insert(any())).thenReturn(1);

            SchoolReorderDTO dto = new SchoolReorderDTO();
            dto.setPlanId(1);
            dto.setUserName("userA");
            SchoolReorderDTO.SchoolOrderItem item = new SchoolReorderDTO.SchoolOrderItem();
            item.setPlanSchoolId(10);
            item.setSortOrder(99);
            dto.setOrderedSchools(List.of(item));

            service.reorderSchools(dto);

            // 验证只更新了指定的planSchoolId
            verify(planSchoolMapper).updateSortOrder(10, 99);
            verify(planSchoolMapper, times(1)).updateSortOrder(anyInt(), anyInt());

            // 不应触及其他方案
            verify(voluntaryPlanMapper, never()).selectById(2);
        }

        @Test
        void reorderSchools_multipleItems_allUpdated() {
            VoluntaryPlan plan = createPlan("userA", 1, 580, 20000);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);

            PlanSchool ps1 = createPlanSchool(10, 1, 3, "A大学", "冲");
            PlanSchool ps2 = createPlanSchool(11, 1, 5, "B大学", "稳");
            when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(List.of(ps1, ps2));
            when(planSchoolMapper.updateSortOrder(anyInt(), anyInt())).thenReturn(1);
            when(voluntaryPlanMapper.updateById(any())).thenReturn(1);
            when(planVersionLogMapper.insert(any())).thenReturn(1);

            SchoolReorderDTO dto = new SchoolReorderDTO();
            dto.setPlanId(1);
            dto.setUserName("userA");
            SchoolReorderDTO.SchoolOrderItem item1 = new SchoolReorderDTO.SchoolOrderItem();
            item1.setPlanSchoolId(10);
            item1.setSortOrder(1); // 从0改为1
            SchoolReorderDTO.SchoolOrderItem item2 = new SchoolReorderDTO.SchoolOrderItem();
            item2.setPlanSchoolId(11);
            item2.setSortOrder(0); // 从1改为0
            dto.setOrderedSchools(List.of(item1, item2));

            service.reorderSchools(dto);

            verify(planSchoolMapper).updateSortOrder(10, 1);
            verify(planSchoolMapper).updateSortOrder(11, 0);
        }
    }

    // ══════════════════════════════════════════
    // 公共辅助方法
    // ══════════════════════════════════════════

    private VoluntaryPlan createPlan(String userName, int id, int score, int rank) {
        VoluntaryPlan plan = new VoluntaryPlan();
        plan.setId(id);
        plan.setPlanName("测试方案");
        plan.setUserName(userName);
        plan.setScore(score);
        plan.setUserRank(rank);
        plan.setSubjectType("理科");
        plan.setVersion(1);
        plan.setStatus(0);
        return plan;
    }

    private PlanSchool createPlanSchool(int id, int planId, int schoolId,
                                         String name, String category) {
        PlanSchool ps = new PlanSchool();
        ps.setId(id);
        ps.setPlanId(planId);
        ps.setSchoolId(schoolId);
        ps.setSchoolName(name);
        ps.setCategory(category);
        ps.setSortOrder(0);
        ps.setAdmissionProb(BigDecimal.valueOf(50));
        ps.setMajorAdjustRisk(BigDecimal.valueOf(30));
        ps.setPopularityScore(BigDecimal.valueOf(50));
        ps.setPopularityTrend("stable");
        ps.setRankFluctuation("测试波动");
        ps.setRankTrend("stable");
        return ps;
    }
}
