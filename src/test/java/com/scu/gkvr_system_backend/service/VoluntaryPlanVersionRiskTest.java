package com.scu.gkvr_system_backend.service;

import com.scu.gkvr_system_backend.dto.*;
import com.scu.gkvr_system_backend.mapper.*;
import com.scu.gkvr_system_backend.pojo.*;
import com.scu.gkvr_system_backend.service.impl.VoluntaryPlanServiceImpl;
import com.scu.gkvr_system_backend.utils.PlanCalculationUtils;
import com.scu.gkvr_system_backend.vo.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * 志愿方案版本隔离与风险重算综合测试.
 *
 * 覆盖六大场景:
 *   1. 位次边界条件          (ScoreRankService / PlanCalculationUtils)
 *   2. 跨批次筛选            (MajorScoreService / batchName 过滤)
 *   3. 方案复制后风险重算     (VoluntaryPlanServiceImpl.copyPlan + reevaluatePlan)
 *   4. 同校多专业冲突        (PlanCalculationUtils + detectCrossCategoryConflicts)
 *   5. 用户隔离              (UserVoluntaryMapper + userName 校验)
 *   6. 历史版本对比          (PlanVersionLogMapper + comparePlans)
 *
 * 同时覆盖:
 *   - 方案复制后必须生成独立版本
 *   - 人工调序只影响当前方案
 *   - 重新评估要清理旧风险缓存
 *   - 同校多专业冲突要合并解释
 */
@ExtendWith(MockitoExtension.class)
class VoluntaryPlanVersionRiskTest {

    @InjectMocks
    private VoluntaryPlanServiceImpl voluntaryPlanService;

    @Mock private VoluntaryPlanMapper voluntaryPlanMapper;
    @Mock private PlanSchoolMapper planSchoolMapper;
    @Mock private PlanVersionLogMapper planVersionLogMapper;
    @Mock private ScLiScoreMapper scLiScoreMapper;
    @Mock private ScoreRankMapper scoreRankMapper;
    @Mock private SchoolInfoMapper schoolInfoMapper;
    @Mock private MajorScoreMapper majorScoreMapper;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(voluntaryPlanService, "baseMapper", voluntaryPlanMapper);
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  第一部分: 位次边界条件 (ScoreRankService / Utils)       ║
    // ╚══════════════════════════════════════════════════════════╝

    @Nested
    @DisplayName("位次边界条件测试")
    class RankBoundaryTests {

        @Test
        @DisplayName("顶尖位次(rank=1) → 区间极度收窄")
        void topRank_rank1_extremeNarrowing() {
            double[] ratios = PlanCalculationUtils.getAdjustedRatios(1);
            // 顶尖考生: 冲一冲区间 0.5~0.8, 保底 1.5~3.0
            assertEquals(0.5, ratios[0], 0.001);
            assertEquals(0.8, ratios[1], 0.001);
            assertEquals(3.0, ratios[5], 0.001);

            // 验证区间计算: 冲上界=0, 冲下界=0 → 几乎没有冲的院校
            int rUpper = (int)(1 * ratios[0]); // 0
            int rLower = (int)(1 * ratios[1]); // 0
            assertTrue(rUpper <= rLower, "冲区间上界应不大于下界");
        }

        @Test
        @DisplayName("顶尖边界(rank=100) → 使用收窄比例")
        void topRank_boundary100_usesNarrowedRatios() {
            double[] ratios = PlanCalculationUtils.getAdjustedRatios(100);
            assertEquals(0.5, ratios[0], 0.001, "rank=100 恰好等于阈值, 应走顶尖分支");
            assertEquals(3.0, ratios[5], 0.001);
        }

        @Test
        @DisplayName("刚好越过顶尖边界(rank=101) → 使用标准比例")
        void justPastTopBoundary_usesStandardRatios() {
            double[] ratios = PlanCalculationUtils.getAdjustedRatios(101);
            assertEquals(PlanCalculationUtils.REACH_UPPER_RATIO, ratios[0], 0.001,
                    "rank=101 超过阈值, 应走标准分支");
            assertEquals(PlanCalculationUtils.SAFETY_LOWER_RATIO, ratios[5], 0.001);
        }

        @Test
        @DisplayName("底部位次(rank=200000) → 使用放宽比例")
        void bottomRank_boundary_usesWidenedRatios() {
            double[] ratios = PlanCalculationUtils.getAdjustedRatios(200000);
            assertEquals(0.7, ratios[0], 0.001, "rank=200000 恰好等于底部阈值");
            assertEquals(2.5, ratios[5], 0.001);
        }

        @Test
        @DisplayName("刚好越过底部位次(rank=200001) → 使用放宽比例")
        void justPastBottomBoundary() {
            double[] ratios = PlanCalculationUtils.getAdjustedRatios(200001);
            assertEquals(0.7, ratios[0], 0.001, "rank>200000 走底部宽松分支");
        }

        @Test
        @DisplayName("标准位次(rank=20000) → 使用默认比例")
        void normalRank_usesDefaultRatios() {
            double[] ratios = PlanCalculationUtils.getAdjustedRatios(20000);
            assertEquals(0.6, ratios[0], 0.001);
            assertEquals(0.9, ratios[1], 0.001);
            assertEquals(1.2, ratios[4], 0.001);
            assertEquals(1.8, ratios[5], 0.001);
        }

        @ParameterizedTest
        @CsvSource({
            "50, 0.5, 3.0",     // 顶尖
            "10000, 0.6, 1.8",  // 标准
            "280000, 0.7, 2.5"  // 底部
        })
        @DisplayName("参数化: 不同位次段的比例区间一致性")
        void parameterized_ratiosConsistency(int rank, double expectedReachUpper, double expectedSafetyLower) {
            double[] ratios = PlanCalculationUtils.getAdjustedRatios(rank);
            assertEquals(expectedReachUpper, ratios[0], 0.001);
            assertEquals(expectedSafetyLower, ratios[5], 0.001);
            // 区间单调性: 冲上界 <= 稳上界 <= 保上界
            assertTrue(ratios[0] <= ratios[2], "冲上界应 <= 稳上界");
            assertTrue(ratios[2] <= ratios[4], "稳上界应 <= 保上界");
        }

        @Test
        @DisplayName("ScoreRank查询返回null → batchName为空串, 不阻断流程")
        void scoreRankNull_batchNameEmpty_noBlockage() {
            PlanGenerateRequestDTO request = buildGenerateRequest(200, 280000);
            when(scoreRankMapper.selectOne(any())).thenReturn(null);
            when(planSchoolMapper.selectCandidateSchools(anyInt(), anyInt(), any(),
                    anyBoolean(), anyBoolean(), anyBoolean()))
                    .thenReturn(Collections.emptyList());

            Map<String, Object> result = voluntaryPlanService.generatePlans(request);

            assertNotNull(result);
            assertEquals("", result.get("batchName"), "ScoreRank为null时batchName应为空串");
            assertNull(result.get("scoreRank"), "scoreRank对象应为null");
        }

        @Test
        @DisplayName("极高分数(750分) → 位次极小, 区间几乎为空")
        void extremeHighScore_rankTooSmall_emptyIntervals() {
            PlanGenerateRequestDTO request = buildGenerateRequest(750, 1);
            when(scoreRankMapper.selectOne(any())).thenReturn(buildScoreRank("本科一批"));
            when(planSchoolMapper.selectCandidateSchools(anyInt(), anyInt(), any(),
                    anyBoolean(), anyBoolean(), anyBoolean()))
                    .thenReturn(Collections.emptyList());

            Map<String, Object> result = voluntaryPlanService.generatePlans(request);
            assertNotNull(result);
            // 位次=1时, 冲上界=0, 所有区间都极窄
        }

        @Test
        @DisplayName("极低分数(100分) → 位次极大, 保底区间极宽")
        void extremeLowScore_rankVeryLarge_wideIntervals() {
            PlanGenerateRequestDTO request = buildGenerateRequest(100, 350000);
            when(scoreRankMapper.selectOne(any())).thenReturn(null);
            when(planSchoolMapper.selectCandidateSchools(anyInt(), anyInt(), any(),
                    anyBoolean(), anyBoolean(), anyBoolean()))
                    .thenReturn(Collections.emptyList());

            Map<String, Object> result = voluntaryPlanService.generatePlans(request);
            assertNotNull(result);
            // 底部区间: 冲上界 = 350000 * 0.7 = 245000
            // 冲下界 = 350000 * 0.95 = 332500
        }

        @Test
        @DisplayName("录取概率边界: avgRank/userRank 极端比率 → 钳位[1,99]")
        void admissionProb_boundary_clampTest() {
            // 极小比率 → 钳位到1
            BigDecimal minProb = PlanCalculationUtils.calcAdmissionProb(1, 0, 100000, "冲");
            assertTrue(minProb.doubleValue() >= 1.0, "概率下限应为1, actual=" + minProb);

            // 极大比率 → 钳位到99
            BigDecimal maxProb = PlanCalculationUtils.calcAdmissionProb(1000000, 0, 100, "保");
            assertTrue(maxProb.doubleValue() <= 99.0, "概率上限应为99, actual=" + maxProb);
        }

        @Test
        @DisplayName("位次标准差: 三年完全相同 → 标准差为0")
        void rankStdDev_allSameYears_zeroStdDev() {
            double stdDev = PlanCalculationUtils.calcRankStdDev(15000, 15000, 15000);
            assertEquals(0.0, stdDev, 0.001);
        }

        @Test
        @DisplayName("位次标准差: 三年差异极大 → 高方差导致概率下调")
        void rankStdDev_highVariance_lowersProb() {
            BigDecimal stableProb = PlanCalculationUtils.calcAdmissionProb(20000, 100, 20000, "稳");
            BigDecimal volatileProb = PlanCalculationUtils.calcAdmissionProb(20000, 8000, 20000, "稳");
            assertTrue(stableProb.doubleValue() > volatileProb.doubleValue(),
                    String.format("稳定院校概率(%.2f)应高于波动院校(%.2f)",
                            stableProb.doubleValue(), volatileProb.doubleValue()));
        }
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  第二部分: 跨批次筛选 (MajorScoreService / batchName)    ║
    // ╚══════════════════════════════════════════════════════════╝

    @Nested
    @DisplayName("跨批次筛选测试")
    class CrossBatchFilterTests {

        @Test
        @DisplayName("savePlan: batchName传入selectMajorScoresForSchool用于专业过滤")
        void savePlan_batchNamePassedToMapper_filtersMajors() {
            PlanSaveRequestDTO request = buildSaveRequest("userA", "本科一批");

            when(voluntaryPlanMapper.insert(any(VoluntaryPlan.class))).thenAnswer(inv -> {
                ((VoluntaryPlan) inv.getArgument(0)).setId(1);
                return 1;
            });
            when(scLiScoreMapper.selectOne(any())).thenReturn(null);
            when(schoolInfoMapper.selectById(anyInt())).thenReturn(null);
            when(planSchoolMapper.insert(any(PlanSchool.class))).thenReturn(1);
            when(planVersionLogMapper.insert(any(PlanVersionLog.class))).thenReturn(1);
            when(voluntaryPlanMapper.updateById(any())).thenReturn(1);

            // 本科一批的专业数据
            List<Map<String, Object>> batch1Majors = List.of(
                    Map.of("majorName", "计算机", "maxScore", 620, "minScore", 580, "avgScore", 600)
            );
            when(planSchoolMapper.selectMajorScoresForSchool(anyInt(), eq("本科一批")))
                    .thenReturn(batch1Majors);

            voluntaryPlanService.savePlan(request);

            // 验证mapper使用了正确的batchName, 而非null
            verify(planSchoolMapper, times(2)).selectMajorScoresForSchool(anyInt(), eq("本科一批"));
        }

        @Test
        @DisplayName("reevaluatePlan: 使用plan当前batchName重算, 非null")
        void reevaluatePlan_usesCurrentBatchName() {
            VoluntaryPlan plan = buildPlan(1, "userA", "本科一批");
            plan.setVersion(1);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);

            PlanSchool ps = buildPlanSchool(10, 1, 3, "稳");
            when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(List.of(ps));
            when(scLiScoreMapper.selectOne(any())).thenReturn(buildScLiScore(3, 15000, 15500, 16000));
            when(schoolInfoMapper.selectById(anyInt())).thenReturn(null);
            when(planSchoolMapper.updateById(any())).thenReturn(1);
            when(voluntaryPlanMapper.updateById(any())).thenReturn(1);
            when(planVersionLogMapper.insert(any())).thenReturn(1);

            List<Map<String, Object>> batch1Majors = List.of(
                    Map.of("majorName", "软件工程", "maxScore", 610, "minScore", 570, "avgScore", 590)
            );
            when(planSchoolMapper.selectMajorScoresForSchool(eq(3), eq("本科一批")))
                    .thenReturn(batch1Majors);

            voluntaryPlanService.reevaluatePlan(1, "userA");

            verify(planSchoolMapper).selectMajorScoresForSchool(eq(3), eq("本科一批"));
        }

        @Test
        @DisplayName("同校不同批次专业: 只取当前batchName下的专业计算调剂风险")
        void sameSchoolDifferentBatches_onlyCurrentBatchMajorsUsed() {
            // 场景: 四川大学在本科一批和本科二批都有招生
            // 方案选择本科一批, 调剂风险应只基于一本专业计算
            PlanSaveRequestDTO request = buildSaveRequest("userA", "本科一批");

            when(voluntaryPlanMapper.insert(any())).thenAnswer(inv -> {
                ((VoluntaryPlan) inv.getArgument(0)).setId(1);
                return 1;
            });
            when(scLiScoreMapper.selectOne(any())).thenReturn(buildScLiScore(3, 15000, 15500, 16000));
            when(schoolInfoMapper.selectById(anyInt())).thenReturn(null);
            when(planSchoolMapper.insert(any())).thenReturn(1);
            when(planVersionLogMapper.insert(any())).thenReturn(1);
            when(voluntaryPlanMapper.updateById(any())).thenReturn(1);

            // 一本专业分数高 → 调剂风险低
            when(planSchoolMapper.selectMajorScoresForSchool(anyInt(), eq("本科一批")))
                    .thenReturn(List.of(
                            Map.of("majorName", "计算机", "maxScore", 620, "minScore", 590, "avgScore", 605)
                    ));

            Integer planId = voluntaryPlanService.savePlan(request);
            assertNotNull(planId);

            // 验证传入的是"本科一批"
            verify(planSchoolMapper, atLeastOnce()).selectMajorScoresForSchool(anyInt(), eq("本科一批"));
        }

        @Test
        @DisplayName("generatePlans: 不同batchName产生不同的ScoreRank查询")
        void generatePlans_differentScores_differentBatchLookups() {
            // 高分 → 本科一批
            PlanGenerateRequestDTO req1 = buildGenerateRequest(600, 10000);
            ScoreRank sr1 = buildScoreRank("本科一批");
            when(scoreRankMapper.selectOne(any())).thenReturn(sr1);
            when(planSchoolMapper.selectCandidateSchools(anyInt(), anyInt(), any(),
                    anyBoolean(), anyBoolean(), anyBoolean()))
                    .thenReturn(Collections.emptyList());

            Map<String, Object> result1 = voluntaryPlanService.generatePlans(req1);
            assertEquals("本科一批", result1.get("batchName"));
        }

        @Test
        @DisplayName("batchName为null时: selectMajorScoresForSchool传入null → 返回所有批次混合数据")
        void nullBatchName_passesNullToMapper_returnsMixedData() {
            PlanSaveRequestDTO request = buildSaveRequest("userA", null);

            when(voluntaryPlanMapper.insert(any())).thenAnswer(inv -> {
                ((VoluntaryPlan) inv.getArgument(0)).setId(1);
                return 1;
            });
            when(scLiScoreMapper.selectOne(any())).thenReturn(null);
            when(schoolInfoMapper.selectById(anyInt())).thenReturn(null);
            when(planSchoolMapper.insert(any())).thenReturn(1);
            when(planVersionLogMapper.insert(any())).thenReturn(1);
            when(voluntaryPlanMapper.updateById(any())).thenReturn(1);

            // null批次 → 返回混合批次数据
            when(planSchoolMapper.selectMajorScoresForSchool(anyInt(), isNull()))
                    .thenReturn(List.of(
                            Map.of("majorName", "一本CS", "maxScore", 620, "minScore", 590, "avgScore", 605),
                            Map.of("majorName", "二本土木", "maxScore", 540, "minScore", 500, "avgScore", 520)
                    ));

            voluntaryPlanService.savePlan(request);

            // 调剂风险应基于混合数据计算(包含一本+二本), 风险值会偏高
            verify(planSchoolMapper, atLeastOnce()).selectMajorScoresForSchool(anyInt(), isNull());
        }
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  第三部分: 方案复制后风险重算                             ║
    // ╚══════════════════════════════════════════════════════════╝

    @Nested
    @DisplayName("方案复制后风险重算测试")
    class CopyAndReevaluateTests {

        @Test
        @DisplayName("copyPlan: 生成独立ID, 版本号重置为1")
        void copyPlan_independentId_versionReset() {
            VoluntaryPlan original = buildPlan(1, "userA", "本科一批");
            original.setVersion(3); // 原方案已经过多次评估
            original.setTotalRiskScore(BigDecimal.valueOf(42.5));
            when(voluntaryPlanMapper.selectById(1)).thenReturn(original);
            when(voluntaryPlanMapper.insert(any())).thenAnswer(inv -> {
                VoluntaryPlan p = inv.getArgument(0);
                p.setId(2);
                return 1;
            });

            PlanSchool ps = buildPlanSchool(10, 1, 3, "稳");
            ps.setAdmissionProb(BigDecimal.valueOf(65.5));
            ps.setMajorAdjustRisk(BigDecimal.valueOf(25.0));
            when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(List.of(ps));
            when(planSchoolMapper.insert(any())).thenReturn(1);
            when(planSchoolMapper.selectByPlanIdOrdered(2)).thenReturn(List.of(ps));
            when(planVersionLogMapper.insert(any())).thenReturn(1);

            Integer newId = voluntaryPlanService.copyPlan(1, "userA", "副本方案");

            assertEquals(2, newId);

            // 验证新方案属性
            ArgumentCaptor<VoluntaryPlan> planCaptor = ArgumentCaptor.forClass(VoluntaryPlan.class);
            verify(voluntaryPlanMapper).insert(planCaptor.capture());
            VoluntaryPlan copied = planCaptor.getValue();
            assertEquals(1, copied.getVersion(), "复制方案版本应重置为1");
            assertEquals(1, copied.getParentId(), "parentId应指向原方案ID");
            assertEquals("副本方案", copied.getPlanName());
            assertEquals("userA", copied.getUserName());
            assertEquals(0, copied.getStatus(), "复制方案应为草稿状态");
        }

        @Test
        @DisplayName("copyPlan: PlanSchool记录完全独立(新planSchoolId, 新planId)")
        void copyPlan_planSchoolRecords_independent() {
            VoluntaryPlan original = buildPlan(1, "userA", "本科一批");
            when(voluntaryPlanMapper.selectById(1)).thenReturn(original);
            when(voluntaryPlanMapper.insert(any())).thenAnswer(inv -> {
                ((VoluntaryPlan) inv.getArgument(0)).setId(2);
                return 1;
            });

            PlanSchool ps1 = buildPlanSchool(10, 1, 3, "冲");
            PlanSchool ps2 = buildPlanSchool(11, 1, 5, "稳");
            ps1.setAdmissionProb(BigDecimal.valueOf(35));
            ps1.setMajorAdjustRisk(BigDecimal.valueOf(60));
            ps2.setAdmissionProb(BigDecimal.valueOf(55));
            ps2.setMajorAdjustRisk(BigDecimal.valueOf(40));
            when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(List.of(ps1, ps2));
            when(planSchoolMapper.insert(any())).thenReturn(1);
            when(planSchoolMapper.selectByPlanIdOrdered(2)).thenReturn(List.of(ps1, ps2));
            when(planVersionLogMapper.insert(any())).thenReturn(1);

            voluntaryPlanService.copyPlan(1, "userA", "副本");

            // 验证插入了2条新的PlanSchool记录
            ArgumentCaptor<PlanSchool> schoolCaptor = ArgumentCaptor.forClass(PlanSchool.class);
            verify(planSchoolMapper, times(2)).insert(schoolCaptor.capture());
            List<PlanSchool> copiedSchools = schoolCaptor.getAllValues();

            for (PlanSchool cs : copiedSchools) {
                assertEquals(2, cs.getPlanId(), "复制的PlanSchool应关联到新方案ID=2");
                // 复制时不设ID, 由数据库自增
            }
            assertEquals(3, copiedSchools.get(0).getSchoolId());
            assertEquals(5, copiedSchools.get(1).getSchoolId());
            assertEquals("冲", copiedSchools.get(0).getCategory());
            assertEquals("稳", copiedSchools.get(1).getCategory());
        }

        @Test
        @DisplayName("copyPlan: 复制风险指标值(分数/位次未变)")
        void copyPlan_riskValuesCopied_scoreUnchanged() {
            VoluntaryPlan original = buildPlan(1, "userA", "本科一批");
            original.setTotalRiskScore(BigDecimal.valueOf(38.5));
            when(voluntaryPlanMapper.selectById(1)).thenReturn(original);
            when(voluntaryPlanMapper.insert(any())).thenAnswer(inv -> {
                ((VoluntaryPlan) inv.getArgument(0)).setId(2);
                return 1;
            });

            PlanSchool ps = buildPlanSchool(10, 1, 3, "稳");
            ps.setAdmissionProb(BigDecimal.valueOf(65.5));
            ps.setMajorAdjustRisk(BigDecimal.valueOf(25.3));
            ps.setAvgRank3yr(BigDecimal.valueOf(15500));
            ps.setRankStdDev(BigDecimal.valueOf(816));
            ps.setRankFluctuation("三年波动1000名");
            ps.setRankTrend("stable");
            ps.setPopularityScore(BigDecimal.valueOf(72.5));
            ps.setPopularityTrend("rising");
            ps.setSelectedMajors("计算机,软件工程");
            when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(List.of(ps));
            when(planSchoolMapper.insert(any())).thenReturn(1);
            when(planSchoolMapper.selectByPlanIdOrdered(2)).thenReturn(List.of(ps));
            when(planVersionLogMapper.insert(any())).thenReturn(1);

            voluntaryPlanService.copyPlan(1, "userA", "副本");

            ArgumentCaptor<PlanSchool> captor = ArgumentCaptor.forClass(PlanSchool.class);
            verify(planSchoolMapper).insert(captor.capture());
            PlanSchool copied = captor.getValue();

            // 所有风险指标应从原方案精确复制
            assertEquals(BigDecimal.valueOf(65.5), copied.getAdmissionProb());
            assertEquals(BigDecimal.valueOf(25.3), copied.getMajorAdjustRisk());
            assertEquals(BigDecimal.valueOf(15500), copied.getAvgRank3yr());
            assertEquals(BigDecimal.valueOf(816), copied.getRankStdDev());
            assertEquals("三年波动1000名", copied.getRankFluctuation());
            assertEquals("stable", copied.getRankTrend());
            assertEquals(BigDecimal.valueOf(72.5), copied.getPopularityScore());
            assertEquals("rising", copied.getPopularityTrend());
            assertEquals("计算机,软件工程", copied.getSelectedMajors());
        }

        @Test
        @DisplayName("copyPlan: 创建独立版本快照(新planId, version=1)")
        void copyPlan_createsIndependentSnapshot() {
            VoluntaryPlan original = buildPlan(1, "userA", "本科一批");
            when(voluntaryPlanMapper.selectById(1)).thenReturn(original);
            when(voluntaryPlanMapper.insert(any())).thenAnswer(inv -> {
                ((VoluntaryPlan) inv.getArgument(0)).setId(2);
                return 1;
            });
            when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(Collections.emptyList());
            when(planSchoolMapper.selectByPlanIdOrdered(2)).thenReturn(Collections.emptyList());
            when(planVersionLogMapper.insert(any())).thenReturn(1);

            voluntaryPlanService.copyPlan(1, "userA", "副本");

            ArgumentCaptor<PlanVersionLog> logCaptor = ArgumentCaptor.forClass(PlanVersionLog.class);
            verify(planVersionLogMapper).insert(logCaptor.capture());
            PlanVersionLog log = logCaptor.getValue();
            assertEquals(2, log.getPlanId(), "快照应关联到新方案ID=2");
            assertEquals(1, log.getVersion(), "快照版本应为1");
        }

        @Test
        @DisplayName("复制后重新评估: 版本号递增, 所有指标重算")
        void copyThenReevaluate_versionIncremented_metricsRecalculated() {
            // 先复制
            VoluntaryPlan original = buildPlan(1, "userA", "本科一批");
            original.setVersion(1);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(original);
            when(voluntaryPlanMapper.insert(any())).thenAnswer(inv -> {
                ((VoluntaryPlan) inv.getArgument(0)).setId(2);
                return 1;
            });
            when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(Collections.emptyList());
            when(planSchoolMapper.selectByPlanIdOrdered(2)).thenReturn(Collections.emptyList());
            when(planVersionLogMapper.insert(any())).thenReturn(1);

            Integer copyId = voluntaryPlanService.copyPlan(1, "userA", "副本");
            assertEquals(2, copyId);

            // 然后对副本重新评估
            VoluntaryPlan copy = buildPlan(2, "userA", "本科一批");
            copy.setVersion(1);
            copy.setUserRank(25000); // 用户修改了位次
            copy.setScore(560);     // 用户修改了分数
            when(voluntaryPlanMapper.selectById(2)).thenReturn(copy);

            PlanSchool ps = buildPlanSchool(20, 2, 3, "稳");
            ps.setAdmissionProb(BigDecimal.valueOf(65)); // 旧值(来自原方案)
            ps.setMajorAdjustRisk(BigDecimal.valueOf(25)); // 旧值
            when(planSchoolMapper.selectByPlanIdOrdered(2)).thenReturn(List.of(ps));

            // 重新评估时使用新位次(25000)重算
            ScLiScore sc = buildScLiScore(3, 15000, 15500, 16000);
            when(scLiScoreMapper.selectOne(any())).thenReturn(sc);
            when(planSchoolMapper.selectMajorScoresForSchool(anyInt(), any()))
                    .thenReturn(List.of(
                            Map.of("majorName", "CS", "maxScore", 600, "minScore", 560, "avgScore", 580)
                    ));
            when(schoolInfoMapper.selectById(anyInt())).thenReturn(null);
            when(planSchoolMapper.updateById(any())).thenReturn(1);
            when(voluntaryPlanMapper.updateById(any())).thenReturn(1);

            PlanDetailVO result = voluntaryPlanService.reevaluatePlan(2, "userA");

            // 版本应递增到2
            assertEquals(2, copy.getVersion());
            // 3次快照: 1次来自copyPlan + 2次来自reevaluatePlan(旧版本 + 新版本)
            verify(planVersionLogMapper, times(3)).insert(any(PlanVersionLog.class));

            // 录取概率应基于新位次(25000)重算, 不等于旧值65
            ArgumentCaptor<PlanSchool> schoolCaptor = ArgumentCaptor.forClass(PlanSchool.class);
            verify(planSchoolMapper).updateById(schoolCaptor.capture());
            PlanSchool updated = schoolCaptor.getValue();
            assertNotEquals(BigDecimal.valueOf(65), updated.getAdmissionProb(),
                    "重新评估后录取概率应基于新位次重算, 不应等于旧值65");
        }

        @Test
        @DisplayName("重新评估清理旧缓存: ScLiScore不存在时, 位次字段被重置而非保留旧值")
        void reevaluate_noScLiScore_clearsStaleData() {
            VoluntaryPlan plan = buildPlan(1, "userA", "本科一批");
            plan.setVersion(1);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);

            // 原方案有位次数据(从之前的评估中保留)
            PlanSchool ps = buildPlanSchool(10, 1, 3, "稳");
            ps.setAvgRank3yr(BigDecimal.valueOf(15500)); // 旧值
            ps.setRankStdDev(BigDecimal.valueOf(816));   // 旧值
            ps.setAdmissionProb(BigDecimal.valueOf(65)); // 旧值
            ps.setRankFluctuation("旧的解释文本");
            when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(List.of(ps));

            // ScLiScore不存在 → 应清理旧缓存
            when(scLiScoreMapper.selectOne(any())).thenReturn(null);
            when(planSchoolMapper.selectMajorScoresForSchool(anyInt(), any()))
                    .thenReturn(Collections.emptyList());
            when(schoolInfoMapper.selectById(anyInt())).thenReturn(null);
            when(planSchoolMapper.updateById(any())).thenReturn(1);
            when(voluntaryPlanMapper.updateById(any())).thenReturn(1);
            when(planVersionLogMapper.insert(any())).thenReturn(1);

            voluntaryPlanService.reevaluatePlan(1, "userA");

            ArgumentCaptor<PlanSchool> captor = ArgumentCaptor.forClass(PlanSchool.class);
            verify(planSchoolMapper).updateById(captor.capture());
            PlanSchool updated = captor.getValue();

            // 旧值应被清除, 而非保留
            assertEquals(BigDecimal.ZERO, updated.getAvgRank3yr(),
                    "ScLiScore不存在时avgRank3yr应重置为0");
            assertEquals(BigDecimal.ZERO, updated.getRankStdDev(),
                    "ScLiScore不存在时rankStdDev应重置为0");
            assertEquals(BigDecimal.valueOf(50), updated.getAdmissionProb(),
                    "ScLiScore不存在时录取概率应重置为50");
            assertTrue(updated.getRankFluctuation().contains("已重新评估"),
                    "波动解释应标记为已重新评估");
        }

        @Test
        @DisplayName("原方案和副本互不影响: 修改副本排序不影响原方案")
        void originalAndCopy_independent_reorderCopyNoEffectOnOriginal() {
            // 原方案 planId=1, 副本 planId=2
            VoluntaryPlan original = buildPlan(1, "userA", "本科一批");
            when(voluntaryPlanMapper.selectById(1)).thenReturn(original);

            // 对原方案进行排序 → 不应影响副本
            SchoolReorderDTO dto = new SchoolReorderDTO();
            dto.setPlanId(1);
            dto.setUserName("userA");
            SchoolReorderDTO.SchoolOrderItem item = new SchoolReorderDTO.SchoolOrderItem();
            item.setPlanSchoolId(10);
            item.setSortOrder(5);
            dto.setOrderedSchools(List.of(item));

            when(planSchoolMapper.updateSortOrder(10, 5)).thenReturn(1);

            Boolean result = voluntaryPlanService.reorderSchools(dto);
            assertTrue(result);

            // 验证只更新了planSchoolId=10(属于planId=1)的排序
            verify(planSchoolMapper).updateSortOrder(10, 5);
            // 没有对副本(planId=2)的任何操作
            verify(planSchoolMapper, never()).updateSortOrder(eq(20), anyInt());
        }

        @Test
        @DisplayName("复制方案不存在 → 返回null")
        void copyPlan_nonExistentPlan_returnsNull() {
            when(voluntaryPlanMapper.selectById(999)).thenReturn(null);

            Integer result = voluntaryPlanService.copyPlan(999, "userA", "副本");
            assertNull(result);
            verify(voluntaryPlanMapper, never()).insert(any());
        }

        @Test
        @DisplayName("复制方案属于其他用户 → 返回null(用户隔离)")
        void copyPlan_otherUsersPlan_returnsNull() {
            VoluntaryPlan otherPlan = buildPlan(1, "userB", "本科一批");
            when(voluntaryPlanMapper.selectById(1)).thenReturn(otherPlan);

            Integer result = voluntaryPlanService.copyPlan(1, "userA", "恶意复制");
            assertNull(result);
            verify(voluntaryPlanMapper, never()).insert(any());
        }
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  第四部分: 同校多专业冲突                                 ║
    // ╚══════════════════════════════════════════════════════════╝

    @Nested
    @DisplayName("同校多专业冲突测试")
    class SameSchoolConflictTests {

        @Test
        @DisplayName("同校出现在冲和稳两个类别 → 生成冲突警告")
        void sameSchoolInReachAndMatch_generatesConflictWarning() {
            PlanGenerateRequestDTO request = buildGenerateRequest(580, 20000);
            when(scoreRankMapper.selectOne(any())).thenReturn(buildScoreRank("本科一批"));
            when(planSchoolMapper.selectMajorScoresForSchool(anyInt(), any()))
                    .thenReturn(Collections.emptyList());

            // 构造: 院校ID=1 同时出现在冲和稳的候选列表中
            // 注意: buildSchoolVOs 内部会调用 sort(), 必须使用可变列表
            List<Map<String, Object>> reachCandidates = new ArrayList<>(List.of(
                    buildCandidateRow(1, "四川大学", 18000, 17500, 17000)
            ));
            List<Map<String, Object>> matchCandidates = new ArrayList<>(List.of(
                    buildCandidateRow(1, "四川大学", 18000, 17500, 17000), // 同校!
                    buildCandidateRow(2, "电子科大", 20000, 19500, 19000)
            ));
            List<Map<String, Object>> safetyCandidates = new ArrayList<>(List.of(
                    buildCandidateRow(3, "成都理工", 25000, 24500, 24000)
            ));

            // 使用Answer让mock按参数返回不同结果
            when(planSchoolMapper.selectCandidateSchools(anyInt(), anyInt(), any(),
                    anyBoolean(), anyBoolean(), anyBoolean()))
                    .thenReturn(reachCandidates)     // 第一次: 冲
                    .thenReturn(matchCandidates)     // 第二次: 稳
                    .thenReturn(safetyCandidates);   // 第三次: 保

            Map<String, Object> result = voluntaryPlanService.generatePlans(request);

            @SuppressWarnings("unchecked")
            List<String> warnings = (List<String>) result.get("conflictWarnings");
            assertNotNull(warnings);
            assertFalse(warnings.isEmpty(), "应检测到同校跨类别冲突");
            assertTrue(warnings.get(0).contains("院校ID 1"),
                    "警告应包含冲突院校ID, actual=" + warnings.get(0));
        }

        @Test
        @DisplayName("同校出现在三个类别 → 生成一条包含三个类别的警告")
        void sameSchoolInAllThreeCategories_singleWarning() {
            PlanGenerateRequestDTO request = buildGenerateRequest(580, 20000);
            when(scoreRankMapper.selectOne(any())).thenReturn(buildScoreRank("本科一批"));
            when(planSchoolMapper.selectMajorScoresForSchool(anyInt(), any()))
                    .thenReturn(Collections.emptyList());

            Map<String, Object> school1Row = buildCandidateRow(1, "四川大学", 18000, 17500, 17000);
            when(planSchoolMapper.selectCandidateSchools(anyInt(), anyInt(), any(),
                    anyBoolean(), anyBoolean(), anyBoolean()))
                    .thenReturn(new ArrayList<>(List.of(school1Row)))   // 冲
                    .thenReturn(new ArrayList<>(List.of(school1Row)))   // 稳
                    .thenReturn(new ArrayList<>(List.of(school1Row)));  // 保

            Map<String, Object> result = voluntaryPlanService.generatePlans(request);

            @SuppressWarnings("unchecked")
            List<String> warnings = (List<String>) result.get("conflictWarnings");
            assertEquals(1, warnings.size(), "同校出现在三个类别应只生成一条警告");
            assertTrue(warnings.get(0).contains("冲") && warnings.get(0).contains("稳")
                            && warnings.get(0).contains("保"),
                    "警告应包含所有三个类别名");
        }

        @Test
        @DisplayName("所有院校不重复 → 无冲突警告")
        void noDuplicateSchools_noConflictWarnings() {
            PlanGenerateRequestDTO request = buildGenerateRequest(580, 20000);
            when(scoreRankMapper.selectOne(any())).thenReturn(buildScoreRank("本科一批"));
            when(planSchoolMapper.selectMajorScoresForSchool(anyInt(), any()))
                    .thenReturn(Collections.emptyList());

            when(planSchoolMapper.selectCandidateSchools(anyInt(), anyInt(), any(),
                    anyBoolean(), anyBoolean(), anyBoolean()))
                    .thenReturn(new ArrayList<>(List.of(buildCandidateRow(1, "A校", 17000, 17500, 18000))))
                    .thenReturn(new ArrayList<>(List.of(buildCandidateRow(2, "B校", 20000, 20000, 20000))))
                    .thenReturn(new ArrayList<>(List.of(buildCandidateRow(3, "C校", 25000, 25000, 25000))));

            Map<String, Object> result = voluntaryPlanService.generatePlans(request);

            @SuppressWarnings("unchecked")
            List<String> warnings = (List<String>) result.get("conflictWarnings");
            assertTrue(warnings.isEmpty(), "无重复院校应无冲突警告");
        }

        @Test
        @DisplayName("多所院校各自冲突 → 各自生成独立警告")
        void multipleSchoolsConflicts_separateWarnings() {
            PlanGenerateRequestDTO request = buildGenerateRequest(580, 20000);
            when(scoreRankMapper.selectOne(any())).thenReturn(buildScoreRank("本科一批"));
            when(planSchoolMapper.selectMajorScoresForSchool(anyInt(), any()))
                    .thenReturn(Collections.emptyList());

            // 院校1和院校2都同时出现在冲和稳中
            List<Map<String, Object>> reachCandidates = new ArrayList<>(List.of(
                    buildCandidateRow(1, "四川大学", 17000, 17500, 18000),
                    buildCandidateRow(2, "电子科大", 17500, 18000, 18500)
            ));
            List<Map<String, Object>> matchCandidates = new ArrayList<>(List.of(
                    buildCandidateRow(1, "四川大学", 17000, 17500, 18000),
                    buildCandidateRow(2, "电子科大", 17500, 18000, 18500)
            ));
            when(planSchoolMapper.selectCandidateSchools(anyInt(), anyInt(), any(),
                    anyBoolean(), anyBoolean(), anyBoolean()))
                    .thenReturn(reachCandidates)
                    .thenReturn(matchCandidates)
                    .thenReturn(new ArrayList<>());

            Map<String, Object> result = voluntaryPlanService.generatePlans(request);

            @SuppressWarnings("unchecked")
            List<String> warnings = (List<String>) result.get("conflictWarnings");
            assertEquals(2, warnings.size(), "两所冲突院校应各生成一条警告");
        }

        @Test
        @DisplayName("同校多专业: 专业维度风险正确合并(非重复计入)")
        void sameSchoolMultipleMajors_riskMerged_notDuplicated() {
            // 场景: 四川大学有3个专业, 调剂风险应为3个专业的平均值, 而非3倍
            List<Map<String, Object>> majorRows = List.of(
                    Map.of("majorName", "计算机", "maxScore", 630, "minScore", 600, "avgScore", 615),
                    Map.of("majorName", "软件工程", "maxScore", 620, "minScore", 590, "avgScore", 605),
                    Map.of("majorName", "电子信息", "maxScore", 610, "minScore", 580, "avgScore", 595)
            );

            BigDecimal risk = PlanCalculationUtils.calcMajorAdjustRisk(majorRows, 600);

            // 600分: 计算机(avg=615, min=600) → 风险在0~50之间; 软件工程(avg=605) → 低风险; 电子信息(avg=595) → 0
            assertTrue(risk.doubleValue() > 0 && risk.doubleValue() < 50,
                    "多专业调剂风险应为平均值, actual=" + risk);
        }

        @Test
        @DisplayName("同校跨批次: 不同批次的专业分别计算(批次过滤有效)")
        void sameSchoolCrossBatch_filteredByBatch() {
            // 一本专业: 分数高
            List<Map<String, Object>> batch1Majors = List.of(
                    Map.of("majorName", "计算机一本", "maxScore", 640, "minScore", 610, "avgScore", 625)
            );
            // 二本专业: 分数低
            List<Map<String, Object>> batch2Majors = List.of(
                    Map.of("majorName", "土木二本", "maxScore", 540, "minScore", 500, "avgScore", 520)
            );

            BigDecimal risk1 = PlanCalculationUtils.calcMajorAdjustRisk(batch1Majors, 580);
            BigDecimal risk2 = PlanCalculationUtils.calcMajorAdjustRisk(batch2Majors, 580);

            // 580分对一本专业(avg=625)风险较高, 对二本专业(avg=520)风险很低
            assertTrue(risk1.doubleValue() > risk2.doubleValue(),
                    String.format("一本专业调剂风险(%.2f)应高于二本(%.2f)",
                            risk1.doubleValue(), risk2.doubleValue()));
        }
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  第五部分: 用户隔离                                      ║
    // ╚══════════════════════════════════════════════════════════╝

    @Nested
    @DisplayName("用户隔离增强测试")
    class UserIsolationExtendedTests {

        @Test
        @DisplayName("userA重新评估userB方案 → 返回null")
        void userA_reevaluateUserBPlan_returnsNull() {
            VoluntaryPlan planB = buildPlan(100, "userB", "本科一批");
            when(voluntaryPlanMapper.selectById(100)).thenReturn(planB);

            PlanDetailVO result = voluntaryPlanService.reevaluatePlan(100, "userA");
            assertNull(result, "userA不应能重新评估userB的方案");
            verify(planSchoolMapper, never()).selectByPlanIdOrdered(anyInt());
            verify(scLiScoreMapper, never()).selectOne(any());
        }

        @Test
        @DisplayName("userA修改userB方案状态 → 返回false")
        void userA_updateUserBPlanStatus_returnsFalse() {
            VoluntaryPlan planB = buildPlan(100, "userB", "本科一批");
            when(voluntaryPlanMapper.selectById(100)).thenReturn(planB);

            Boolean result = voluntaryPlanService.updatePlanStatus(100, "userA", 1);
            assertFalse(result);
            verify(voluntaryPlanMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("getPlanDetail: userName匹配 → 正常返回")
        void getPlanDetail_matchingUser_returnsDetail() {
            VoluntaryPlan plan = buildPlan(1, "userA", "本科一批");
            when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);
            when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(Collections.emptyList());

            PlanDetailVO result = voluntaryPlanService.getPlanDetail(1, "userA");
            assertNotNull(result, "用户查看自己的方案应正常返回");
            assertEquals("userA", result.getUserName());
        }

        @Test
        @DisplayName("getPlanDetail: PlanSchool admissionProb为null → 不NPE, 使用默认值50")
        void getPlanDetail_nullAdmissionProb_noNPE() {
            VoluntaryPlan plan = buildPlan(1, "userA", "本科一批");
            when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);

            PlanSchool ps = buildPlanSchool(10, 1, 3, "稳");
            ps.setAdmissionProb(null); // 未初始化的场景
            ps.setMajorAdjustRisk(null);
            when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(List.of(ps));
            when(planSchoolMapper.selectMajorScoresForSchool(anyInt(), any()))
                    .thenReturn(Collections.emptyList());

            // 不应抛NPE
            PlanDetailVO result = assertDoesNotThrow(() ->
                    voluntaryPlanService.getPlanDetail(1, "userA"));
            assertNotNull(result);
            // 默认值应为50
            assertEquals(BigDecimal.valueOf(50), result.getMatchSchools().get(0).getAdmissionProb());
            assertEquals(BigDecimal.valueOf(50), result.getMatchSchools().get(0).getMajorAdjustRisk());
        }

        @Test
        @DisplayName("listUserPlans: 只查询指定用户, SQL层隔离")
        void listUserPlans_onlyQueriesSpecifiedUser() {
            when(voluntaryPlanMapper.selectRealPlansByUserName("userA"))
                    .thenReturn(List.of(buildPlan(1, "userA", "本科一批")));

            List<VoluntaryPlan> result = voluntaryPlanService.listUserPlans("userA");
            assertEquals(1, result.size());

            // 验证只查询了userA, 未查询其他用户
            verify(voluntaryPlanMapper).selectRealPlansByUserName("userA");
            verify(voluntaryPlanMapper, never()).selectRealPlansByUserName("userB");
            verify(voluntaryPlanMapper, never()).selectRealPlansByUserName("userC");
        }

        @Test
        @DisplayName("deletePlan: userA删userB方案 → 无副作用")
        void deletePlan_wrongUser_noSideEffects() {
            VoluntaryPlan planB = buildPlan(100, "userB", "本科一批");
            when(voluntaryPlanMapper.selectById(100)).thenReturn(planB);

            Boolean result = voluntaryPlanService.deletePlan(100, "userA");
            assertFalse(result);

            // 确保没有任何删除操作
            verify(planSchoolMapper, never()).delete(any());
            verify(planVersionLogMapper, never()).delete(any());
            verify(voluntaryPlanMapper, never()).deleteById(anyInt());
        }

        @Test
        @DisplayName("comparePlans: 跨用户对比 → 返回null")
        void comparePlans_crossUser_returnsNull() {
            VoluntaryPlan planA = buildPlan(1, "userA", "本科一批");
            VoluntaryPlan planB = buildPlan(2, "userB", "本科一批");
            when(voluntaryPlanMapper.selectById(1)).thenReturn(planA);
            when(voluntaryPlanMapper.selectById(2)).thenReturn(planB);

            PlanComparisonVO result = voluntaryPlanService.comparePlans(1, 2, "userA");
            assertNull(result, "跨用户对比应返回null");
        }
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  第六部分: 历史版本对比                                  ║
    // ╚══════════════════════════════════════════════════════════╝

    @Nested
    @DisplayName("历史版本对比测试")
    class HistoricalVersionComparisonTests {

        @Test
        @DisplayName("首次保存创建version=1快照")
        void savePlan_createsVersion1Snapshot() {
            PlanSaveRequestDTO request = buildSaveRequest("userA", "本科一批");

            when(voluntaryPlanMapper.insert(any())).thenAnswer(inv -> {
                ((VoluntaryPlan) inv.getArgument(0)).setId(1);
                return 1;
            });
            when(scLiScoreMapper.selectOne(any())).thenReturn(null);
            when(planSchoolMapper.selectMajorScoresForSchool(anyInt(), any()))
                    .thenReturn(Collections.emptyList());
            when(schoolInfoMapper.selectById(anyInt())).thenReturn(null);
            when(planSchoolMapper.insert(any())).thenReturn(1);
            when(planVersionLogMapper.insert(any())).thenReturn(1);
            when(voluntaryPlanMapper.updateById(any())).thenReturn(1);

            voluntaryPlanService.savePlan(request);

            ArgumentCaptor<PlanVersionLog> captor = ArgumentCaptor.forClass(PlanVersionLog.class);
            verify(planVersionLogMapper).insert(captor.capture());
            assertEquals(1, captor.getValue().getVersion(), "首次保存快照版本应为1");
            assertEquals(1, captor.getValue().getPlanId());
            assertNotNull(captor.getValue().getSnapshotData());
        }

        @Test
        @DisplayName("重新评估: 保存两个快照(旧版本 + 新版本)")
        void reevaluatePlan_savesTwoSnapshots() {
            VoluntaryPlan plan = buildPlan(1, "userA", "本科一批");
            plan.setVersion(2);
            when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);

            PlanSchool ps = buildPlanSchool(10, 1, 3, "稳");
            ps.setAdmissionProb(BigDecimal.valueOf(55));
            ps.setMajorAdjustRisk(BigDecimal.valueOf(30));
            when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(List.of(ps));
            when(scLiScoreMapper.selectOne(any())).thenReturn(null);
            when(planSchoolMapper.selectMajorScoresForSchool(anyInt(), any()))
                    .thenReturn(Collections.emptyList());
            when(schoolInfoMapper.selectById(anyInt())).thenReturn(null);
            when(planSchoolMapper.updateById(any())).thenReturn(1);
            when(voluntaryPlanMapper.updateById(any())).thenReturn(1);
            when(planVersionLogMapper.insert(any())).thenReturn(1);

            voluntaryPlanService.reevaluatePlan(1, "userA");

            // 验证版本号递增
            assertEquals(3, plan.getVersion(), "version应从2递增到3");

            // 验证两个快照
            ArgumentCaptor<PlanVersionLog> captor = ArgumentCaptor.forClass(PlanVersionLog.class);
            verify(planVersionLogMapper, times(2)).insert(captor.capture());
            List<PlanVersionLog> logs = captor.getAllValues();
            assertEquals(2, logs.get(0).getVersion(), "第一个快照应为旧版本号2");
            assertEquals(3, logs.get(1).getVersion(), "第二个快照应为新版本号3");
            assertEquals(1, logs.get(0).getPlanId());
            assertEquals(1, logs.get(1).getPlanId());
        }

        @Test
        @DisplayName("多版本演进: v1→v2→v3 版本号递增正确")
        void multiVersionEvolution_versionIncrementsCorrectly() {
            VoluntaryPlan plan = buildPlan(1, "userA", "本科一批");

            // v1 → v2
            plan.setVersion(1);
            lenient().when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);
            lenient().when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(Collections.emptyList());
            lenient().when(scLiScoreMapper.selectOne(any())).thenReturn(null);
            lenient().when(planSchoolMapper.selectMajorScoresForSchool(anyInt(), any()))
                    .thenReturn(Collections.emptyList());
            lenient().when(schoolInfoMapper.selectById(anyInt())).thenReturn(null);
            lenient().when(planSchoolMapper.updateById(any())).thenReturn(1);
            lenient().when(voluntaryPlanMapper.updateById(any())).thenReturn(1);
            lenient().when(planVersionLogMapper.insert(any())).thenReturn(1);

            voluntaryPlanService.reevaluatePlan(1, "userA");
            assertEquals(2, plan.getVersion(), "第一次评估后version=2");

            // v2 → v3
            voluntaryPlanService.reevaluatePlan(1, "userA");
            assertEquals(3, plan.getVersion(), "第二次评估后version=3");
        }

        @Test
        @DisplayName("comparePlans: 同一方案的不同版本对比(新增/移除院校)")
        void comparePlans_schoolAddedAndRemoved_detectsDiffs() {
            VoluntaryPlan planA = buildPlan(1, "userA", "本科一批");
            VoluntaryPlan planB = buildPlan(2, "userA", "本科一批");
            planA.setParentId(null);
            planB.setParentId(1); // planB是planA的副本

            when(voluntaryPlanMapper.selectById(1)).thenReturn(planA);
            when(voluntaryPlanMapper.selectById(2)).thenReturn(planB);

            // planA有院校3和5
            PlanSchool psA1 = buildPlanSchool(10, 1, 3, "冲");
            psA1.setAdmissionProb(BigDecimal.valueOf(35));
            psA1.setMajorAdjustRisk(BigDecimal.valueOf(60));
            PlanSchool psA2 = buildPlanSchool(11, 1, 5, "稳");
            psA2.setAdmissionProb(BigDecimal.valueOf(55));
            psA2.setMajorAdjustRisk(BigDecimal.valueOf(40));
            when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(List.of(psA1, psA2));

            // planB移除了院校5, 新增了院校7
            PlanSchool psB1 = buildPlanSchool(20, 2, 3, "冲");
            psB1.setAdmissionProb(BigDecimal.valueOf(35));
            psB1.setMajorAdjustRisk(BigDecimal.valueOf(60));
            PlanSchool psB2 = buildPlanSchool(21, 2, 7, "保");
            psB2.setAdmissionProb(BigDecimal.valueOf(80));
            psB2.setMajorAdjustRisk(BigDecimal.valueOf(15));
            when(planSchoolMapper.selectByPlanIdOrdered(2)).thenReturn(List.of(psB1, psB2));
            when(planSchoolMapper.selectMajorScoresForSchool(anyInt(), any()))
                    .thenReturn(Collections.emptyList());

            PlanComparisonVO result = voluntaryPlanService.comparePlans(1, 2, "userA");

            assertNotNull(result);
            assertNotNull(result.getDifferences());

            // 应有2个差异: 院校5被移除 + 院校7被新增
            long added = result.getDifferences().stream()
                    .filter(d -> "added".equals(d.getChangeType())).count();
            long removed = result.getDifferences().stream()
                    .filter(d -> "removed".equals(d.getChangeType())).count();
            assertEquals(1, added, "应检测到1所新增院校");
            assertEquals(1, removed, "应检测到1所移除院校");
        }

        @Test
        @DisplayName("comparePlans: 类别变更检测(冲→稳)")
        void comparePlans_categoryChanged_detected() {
            VoluntaryPlan planA = buildPlan(1, "userA", "本科一批");
            VoluntaryPlan planB = buildPlan(2, "userA", "本科一批");
            when(voluntaryPlanMapper.selectById(1)).thenReturn(planA);
            when(voluntaryPlanMapper.selectById(2)).thenReturn(planB);

            // planA: 院校3是"冲"
            PlanSchool psA = buildPlanSchool(10, 1, 3, "冲");
            psA.setSchoolName("四川大学");
            psA.setAdmissionProb(BigDecimal.valueOf(40));
            psA.setMajorAdjustRisk(BigDecimal.valueOf(55));
            when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(List.of(psA));

            // planB: 院校3改为"稳"
            PlanSchool psB = buildPlanSchool(20, 2, 3, "稳");
            psB.setSchoolName("四川大学");
            psB.setAdmissionProb(BigDecimal.valueOf(55));
            psB.setMajorAdjustRisk(BigDecimal.valueOf(55));
            when(planSchoolMapper.selectByPlanIdOrdered(2)).thenReturn(List.of(psB));
            when(planSchoolMapper.selectMajorScoresForSchool(anyInt(), any()))
                    .thenReturn(Collections.emptyList());

            PlanComparisonVO result = voluntaryPlanService.comparePlans(1, 2, "userA");

            long categoryChanged = result.getDifferences().stream()
                    .filter(d -> "category_changed".equals(d.getChangeType())).count();
            assertEquals(1, categoryChanged, "应检测到类别变更(冲→稳)");
        }

        @Test
        @DisplayName("comparePlans: 录取概率显著变化(>5%) → 生成prob_changed差异")
        void comparePlans_significantProbChange_detected() {
            VoluntaryPlan planA = buildPlan(1, "userA", "本科一批");
            VoluntaryPlan planB = buildPlan(2, "userA", "本科一批");
            when(voluntaryPlanMapper.selectById(1)).thenReturn(planA);
            when(voluntaryPlanMapper.selectById(2)).thenReturn(planB);

            // planA: 录取概率40%
            PlanSchool psA = buildPlanSchool(10, 1, 3, "稳");
            psA.setSchoolName("四川大学");
            psA.setAdmissionProb(BigDecimal.valueOf(40));
            psA.setMajorAdjustRisk(BigDecimal.valueOf(50));
            when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(List.of(psA));

            // planB: 录取概率变为70%(重新评估后)
            PlanSchool psB = buildPlanSchool(20, 2, 3, "稳");
            psB.setSchoolName("四川大学");
            psB.setAdmissionProb(BigDecimal.valueOf(70));
            psB.setMajorAdjustRisk(BigDecimal.valueOf(50));
            when(planSchoolMapper.selectByPlanIdOrdered(2)).thenReturn(List.of(psB));
            when(planSchoolMapper.selectMajorScoresForSchool(anyInt(), any()))
                    .thenReturn(Collections.emptyList());

            PlanComparisonVO result = voluntaryPlanService.comparePlans(1, 2, "userA");

            long probChanged = result.getDifferences().stream()
                    .filter(d -> "prob_changed".equals(d.getChangeType())).count();
            assertEquals(1, probChanged, "录取概率变化>5%应被检测到");
        }

        @Test
        @DisplayName("comparePlans: 录取概率微小变化(<=5%) → 不生成差异")
        void comparePlans_minorProbChange_notDetected() {
            VoluntaryPlan planA = buildPlan(1, "userA", "本科一批");
            VoluntaryPlan planB = buildPlan(2, "userA", "本科一批");
            when(voluntaryPlanMapper.selectById(1)).thenReturn(planA);
            when(voluntaryPlanMapper.selectById(2)).thenReturn(planB);

            PlanSchool psA = buildPlanSchool(10, 1, 3, "稳");
            psA.setSchoolName("四川大学");
            psA.setAdmissionProb(BigDecimal.valueOf(50));
            psA.setMajorAdjustRisk(BigDecimal.valueOf(30));
            when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(List.of(psA));

            PlanSchool psB = buildPlanSchool(20, 2, 3, "稳");
            psB.setSchoolName("四川大学");
            psB.setAdmissionProb(BigDecimal.valueOf(53)); // 仅差3%
            psB.setMajorAdjustRisk(BigDecimal.valueOf(30));
            when(planSchoolMapper.selectByPlanIdOrdered(2)).thenReturn(List.of(psB));
            when(planSchoolMapper.selectMajorScoresForSchool(anyInt(), any()))
                    .thenReturn(Collections.emptyList());

            PlanComparisonVO result = voluntaryPlanService.comparePlans(1, 2, "userA");

            long probChanged = result.getDifferences().stream()
                    .filter(d -> "prob_changed".equals(d.getChangeType())).count();
            assertEquals(0, probChanged, "录取概率变化<=5%不应生成差异");
        }

        @Test
        @DisplayName("comparePlans: 对比摘要格式正确")
        void comparePlans_summaryFormat() {
            VoluntaryPlan planA = buildPlan(1, "userA", "方案A");
            VoluntaryPlan planB = buildPlan(2, "userA", "方案B");
            when(voluntaryPlanMapper.selectById(1)).thenReturn(planA);
            when(voluntaryPlanMapper.selectById(2)).thenReturn(planB);
            when(planSchoolMapper.selectByPlanIdOrdered(1)).thenReturn(Collections.emptyList());
            when(planSchoolMapper.selectByPlanIdOrdered(2)).thenReturn(Collections.emptyList());

            PlanComparisonVO result = voluntaryPlanService.comparePlans(1, 2, "userA");

            assertNotNull(result.getSummary());
            assertTrue(result.getSummary().contains("方案A"));
            assertTrue(result.getSummary().contains("方案B"));
            assertTrue(result.getSummary().contains("0处差异"));
        }

        @Test
        @DisplayName("deletePlan: 同时清理版本日志和院校记录")
        void deletePlan_cleansUpVersionLogsAndSchools() {
            VoluntaryPlan plan = buildPlan(1, "userA", "本科一批");
            when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);
            when(planSchoolMapper.delete(any())).thenReturn(3);
            when(planVersionLogMapper.delete(any())).thenReturn(2);
            when(voluntaryPlanMapper.deleteById(1)).thenReturn(1);

            Boolean result = voluntaryPlanService.deletePlan(1, "userA");
            assertTrue(result);

            verify(planSchoolMapper).delete(any());
            verify(planVersionLogMapper).delete(any());
            verify(voluntaryPlanMapper).deleteById(1);
        }
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  第七部分: 人工调序隔离                                  ║
    // ╚══════════════════════════════════════════════════════════╝

    @Nested
    @DisplayName("人工调序隔离测试")
    class ManualReorderIsolationTests {

        @Test
        @DisplayName("调序只更新指定的planSchoolId, 不影响其他方案")
        void reorderOnlyAffectsSpecifiedSchools() {
            VoluntaryPlan plan = buildPlan(1, "userA", "本科一批");
            when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);
            when(planSchoolMapper.updateSortOrder(anyInt(), anyInt())).thenReturn(1);

            SchoolReorderDTO dto = new SchoolReorderDTO();
            dto.setPlanId(1);
            dto.setUserName("userA");
            dto.setOrderedSchools(List.of(
                    buildOrderItem(10, 2),
                    buildOrderItem(11, 0),
                    buildOrderItem(12, 1)
            ));

            Boolean result = voluntaryPlanService.reorderSchools(dto);
            assertTrue(result);

            verify(planSchoolMapper).updateSortOrder(10, 2);
            verify(planSchoolMapper).updateSortOrder(11, 0);
            verify(planSchoolMapper).updateSortOrder(12, 1);
            verifyNoMoreInteractions(planSchoolMapper);
        }

        @Test
        @DisplayName("调序不触发风险重算(只改sort_order)")
        void reorder_doesNotTriggerRiskRecalculation() {
            VoluntaryPlan plan = buildPlan(1, "userA", "本科一批");
            when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);
            when(planSchoolMapper.updateSortOrder(anyInt(), anyInt())).thenReturn(1);

            SchoolReorderDTO dto = new SchoolReorderDTO();
            dto.setPlanId(1);
            dto.setUserName("userA");
            dto.setOrderedSchools(List.of(buildOrderItem(10, 0)));

            voluntaryPlanService.reorderSchools(dto);

            // 不应触发任何风险计算相关的mapper调用
            verify(scLiScoreMapper, never()).selectOne(any());
            verify(planSchoolMapper, never()).selectMajorScoresForSchool(anyInt(), any());
            verify(schoolInfoMapper, never()).selectById(anyInt());
            verify(planVersionLogMapper, never()).insert(any());
            verify(voluntaryPlanMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("调序不创建新版本快照")
        void reorder_doesNotCreateVersionSnapshot() {
            VoluntaryPlan plan = buildPlan(1, "userA", "本科一批");
            when(voluntaryPlanMapper.selectById(1)).thenReturn(plan);
            when(planSchoolMapper.updateSortOrder(anyInt(), anyInt())).thenReturn(1);

            SchoolReorderDTO dto = new SchoolReorderDTO();
            dto.setPlanId(1);
            dto.setUserName("userA");
            dto.setOrderedSchools(List.of(buildOrderItem(10, 0)));

            voluntaryPlanService.reorderSchools(dto);

            verify(planVersionLogMapper, never()).insert(any());
        }

        @Test
        @DisplayName("方案不存在时调序 → 返回false")
        void reorder_nonExistentPlan_returnsFalse() {
            when(voluntaryPlanMapper.selectById(999)).thenReturn(null);

            SchoolReorderDTO dto = new SchoolReorderDTO();
            dto.setPlanId(999);
            dto.setUserName("userA");
            dto.setOrderedSchools(List.of(buildOrderItem(10, 0)));

            Boolean result = voluntaryPlanService.reorderSchools(dto);
            assertFalse(result);
        }
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  辅助方法                                                ║
    // ╚══════════════════════════════════════════════════════════╝

    private PlanGenerateRequestDTO buildGenerateRequest(int score, int userRank) {
        PlanGenerateRequestDTO req = new PlanGenerateRequestDTO();
        req.setUserName("testUser");
        req.setScore(score);
        req.setUserRank(userRank);
        req.setSubjectType("理科");
        req.setReachCount(3);
        req.setMatchCount(5);
        req.setSafetyCount(3);
        return req;
    }

    private PlanSaveRequestDTO buildSaveRequest(String userName, String batchName) {
        PlanSaveRequestDTO req = new PlanSaveRequestDTO();
        req.setUserName(userName);
        req.setPlanName("测试方案");
        req.setScore(580);
        req.setUserRank(20000);
        req.setSubjectType("理科");
        req.setBatchName(batchName);

        PlanSchoolDTO dto1 = new PlanSchoolDTO();
        dto1.setSchoolId(3);
        dto1.setSchoolName("四川大学");
        dto1.setCategory("稳");
        dto1.setSortOrder(0);

        PlanSchoolDTO dto2 = new PlanSchoolDTO();
        dto2.setSchoolId(5);
        dto2.setSchoolName("成都理工大学");
        dto2.setCategory("保");
        dto2.setSortOrder(1);

        req.setSchools(List.of(dto1, dto2));
        return req;
    }

    private VoluntaryPlan buildPlan(int id, String userName, String batchName) {
        VoluntaryPlan plan = new VoluntaryPlan();
        plan.setId(id);
        plan.setPlanName("测试方案");
        plan.setUserName(userName);
        plan.setScore(580);
        plan.setUserRank(20000);
        plan.setSubjectType("理科");
        plan.setBatchName(batchName);
        plan.setVersion(1);
        plan.setStatus(0);
        plan.setTotalRiskScore(BigDecimal.valueOf(35));
        return plan;
    }

    private PlanSchool buildPlanSchool(int id, int planId, int schoolId, String category) {
        PlanSchool ps = new PlanSchool();
        ps.setId(id);
        ps.setPlanId(planId);
        ps.setSchoolId(schoolId);
        ps.setSchoolName("TestSchool" + schoolId);
        ps.setCategory(category);
        ps.setSortOrder(0);
        ps.setAdmissionProb(BigDecimal.valueOf(55));
        ps.setMajorAdjustRisk(BigDecimal.valueOf(35));
        ps.setAvgRank3yr(BigDecimal.valueOf(18000));
        ps.setRankStdDev(BigDecimal.valueOf(1000));
        ps.setRankFluctuation("三年波动2000名");
        ps.setRankTrend("stable");
        return ps;
    }

    private ScLiScore buildScLiScore(int schoolId, int r2020, int r2021, int r2022) {
        ScLiScore sc = new ScLiScore();
        sc.setSchoolId(schoolId);
        sc.setSchoolName("TestSchool" + schoolId);
        sc.setRank2020(r2020);
        sc.setRank2021(r2021);
        sc.setRank2022(r2022);
        sc.setScore2020(590);
        sc.setScore2021(595);
        sc.setScore2022(600);
        return sc;
    }

    private ScoreRank buildScoreRank(String batchName) {
        ScoreRank sr = new ScoreRank();
        sr.setBatchName(batchName);
        sr.setScore("580");
        sr.setRank(20000);
        return sr;
    }

    private Map<String, Object> buildCandidateRow(int schoolId, String name,
                                                    int r2020, int r2021, int r2022) {
        Map<String, Object> row = new HashMap<>();
        row.put("schoolId", schoolId);
        row.put("schoolName", name);
        row.put("rank2020", r2020);
        row.put("rank2021", r2021);
        row.put("rank2022", r2022);
        row.put("monthView", 500);
        row.put("totalView", "6000");
        return row;
    }

    private SchoolReorderDTO.SchoolOrderItem buildOrderItem(int planSchoolId, int sortOrder) {
        SchoolReorderDTO.SchoolOrderItem item = new SchoolReorderDTO.SchoolOrderItem();
        item.setPlanSchoolId(planSchoolId);
        item.setSortOrder(sortOrder);
        return item;
    }
}
