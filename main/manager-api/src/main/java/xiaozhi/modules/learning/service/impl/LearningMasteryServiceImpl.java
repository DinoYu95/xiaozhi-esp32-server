package xiaozhi.modules.learning.service.impl;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.JsonUtils;
import xiaozhi.modules.parent.dao.DeviceChildDao;
import xiaozhi.modules.parent.entity.DeviceChildEntity;
import xiaozhi.modules.learning.dao.KgEdgeDao;
import xiaozhi.modules.learning.dao.KgGraphReleaseDao;
import xiaozhi.modules.learning.dao.KgNodeDao;
import xiaozhi.modules.learning.dao.KgNodeRevisionDao;
import xiaozhi.modules.learning.dao.LearnerSkillStateDao;
import xiaozhi.modules.learning.dao.LearningEvidenceEventDao;
import xiaozhi.modules.learning.entity.KgEdgeEntity;
import xiaozhi.modules.learning.entity.KgGraphReleaseEntity;
import xiaozhi.modules.learning.entity.KgNodeEntity;
import xiaozhi.modules.learning.entity.KgNodeRevisionEntity;
import xiaozhi.modules.learning.entity.LearnerSkillStateEntity;
import xiaozhi.modules.learning.entity.LearningEvidenceEventEntity;
import xiaozhi.modules.learning.service.LearningKgService;
import xiaozhi.modules.learning.service.LearningMasteryService;
import xiaozhi.modules.learning.util.ChildGradeOptionsUtil;
import xiaozhi.modules.learning.util.LearningChildProfileUtil;
import xiaozhi.modules.learning.util.LearningKgNodeTypeUtil;
import xiaozhi.modules.learning.util.LearningMasteryModuleLabels;
import xiaozhi.modules.learning.util.LearningMasteryStatusUtil;
import xiaozhi.modules.learning.vo.LearningMasteryMapVO;
import xiaozhi.modules.learning.vo.LearningMasteryModuleVO;
import xiaozhi.modules.learning.vo.LearningMasterySkillVO;
import xiaozhi.modules.learning.vo.LearningMasterySummaryVO;
import xiaozhi.modules.learning.vo.LearningMisconceptionBriefVO;
import xiaozhi.modules.learning.vo.LearningModulePathVO;
import xiaozhi.modules.learning.vo.LearningSkillBriefVO;
import xiaozhi.modules.learning.vo.LearningSkillDetailVO;
import xiaozhi.modules.parent.dao.ParentDeviceBindingDao;
import xiaozhi.modules.parent.util.ParentChildAccessHelper;

@Service
@RequiredArgsConstructor
public class LearningMasteryServiceImpl implements LearningMasteryService {

    private static final String NODE_SKILL = "SKILL";
    private static final String NODE_MISCONCEPTION = "MISCONCEPTION";
    private static final String EDGE_PREREQ = "PREREQUISITE_OF";
    private static final String EDGE_MIS = "HAS_MISCONCEPTION";
    private static final String COVERAGE_NOTE =
            "掌握度来自作业辅导中的问答/拍题观察，不是学校考试成绩。当前精准图谱：小学1-3年级数学。";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int SUGGESTED_CONSOLIDATE_TOP_N = 5;

    private final DeviceChildDao deviceChildDao;
    private final ParentDeviceBindingDao parentDeviceBindingDao;
    private final LearningKgService learningKgService;
    private final KgGraphReleaseDao kgGraphReleaseDao;
    private final KgNodeDao kgNodeDao;
    private final KgNodeRevisionDao kgNodeRevisionDao;
    private final KgEdgeDao kgEdgeDao;
    private final LearnerSkillStateDao learnerSkillStateDao;
    private final LearningEvidenceEventDao learningEvidenceEventDao;

    @Override
    public LearningMasteryMapVO masteryMap(
            Long parentUserId, Long childId, String subject, Integer grade, String weekStart) {
        ChildRelease ctx = loadContext(parentUserId, childId, subject, grade);
        WeekRange week = resolveWeekRange(weekStart);
        Set<String> observedThisWeekCodes = loadObservedSkillCodesInRange(childId, week.from, week.toExclusive);
        Set<String> consolidatePeriodCodes = loadSuggestedConsolidateSkillCodes(childId);

        List<SkillRow> rows = ctx.releaseId != null
                ? loadSkillRowsForGrade(ctx.releaseId, ctx.grade)
                : List.of();
        boolean gradeSupported = isGradeSupported(ctx, rows);

        Map<Long, LearnerSkillStateEntity> stateByNodeId = gradeSupported
                ? loadStatesForChild(childId, rows)
                : Map.of();

        Map<String, List<SkillRow>> byModule = new LinkedHashMap<>();
        if (gradeSupported) {
            for (SkillRow row : rows) {
                byModule.computeIfAbsent(row.moduleKey, k -> new ArrayList<>()).add(row);
            }
        }

        List<LearningMasteryModuleVO> modules = gradeSupported
                ? byModule.entrySet().stream()
                        .sorted(Comparator.comparingInt(e -> LearningMasteryModuleLabels.sortIndex(e.getKey())))
                        .map(e -> buildModuleVO(
                                e.getKey(),
                                e.getValue(),
                                stateByNodeId,
                                observedThisWeekCodes,
                                consolidatePeriodCodes))
                        .collect(Collectors.toList())
                : List.of();

        LearningMasterySummaryVO summary = aggregateSummary(modules, ctx.grade);
        LearningMasteryMapVO vo = new LearningMasteryMapVO();
        vo.setChildId(childId);
        vo.setSubject(ctx.subject);
        vo.setSubjectLabel(LearningMasteryStatusUtil.subjectLabel(ctx.subject));
        vo.setGrade(ctx.grade);
        vo.setGradeConfigured(ctx.gradeConfigured);
        vo.setChildMaxGrade(ctx.childMaxGrade);
        vo.setGraphGradeMin(ctx.graphGradeMin);
        vo.setGraphGradeMax(ctx.graphGradeMax);
        vo.setGradeSupported(gradeSupported);
        vo.setGraphReleaseId(ctx.releaseId);
        vo.setGraphVersionLabel(ctx.versionLabel);
        vo.setSummary(summary);
        vo.setModules(modules);
        vo.setCoverageNote(COVERAGE_NOTE);
        vo.setWeekStart(week.start.toString());
        vo.setWeekEnd(week.end.toString());
        return vo;
    }

    @Override
    public LearningSkillDetailVO skillDetail(Long parentUserId, Long childId, String skillCode) {
        if (StringUtils.isBlank(skillCode)) {
            throw new RenException("skill code 必填");
        }
        ParentChildAccessHelper.ensureParentCanAccessChildById(
                deviceChildDao, parentDeviceBindingDao, parentUserId, childId);
        DeviceChildEntity child = deviceChildDao.selectById(childId);
        if (child == null) {
            throw new RenException("孩子不存在");
        }
        String code = skillCode.trim();
        KgNodeEntity node = kgNodeDao.selectOne(
                new LambdaQueryWrapper<KgNodeEntity>().eq(KgNodeEntity::getCode, code));
        if (node == null || !LearningKgNodeTypeUtil.isMasterySkill(node)) {
            throw new RenException("知识点不存在或不是 SKILL");
        }
        int skillGrade = LearningMasteryStatusUtil.gradeFromSkillCode(code);
        if (skillGrade <= 0) {
            skillGrade = LearningChildProfileUtil.resolveChildMaxGrade(child);
        }
        LearningChildProfileUtil.validateGraphGradeVisible(child, skillGrade);
        Long releaseId = learningKgService.requireActiveReleaseId(
                LearningMasteryStatusUtil.subjectFromSkillCode(code),
                LearningChildProfileUtil.resolveProvince(child),
                LearningChildProfileUtil.resolveCity(child),
                LearningChildProfileUtil.resolveTextbook(child),
                LearningChildProfileUtil.resolveSemester(child),
                skillGrade);
        KgNodeRevisionEntity rev = kgNodeRevisionDao.selectOne(
                new LambdaQueryWrapper<KgNodeRevisionEntity>()
                        .eq(KgNodeRevisionEntity::getGraphReleaseId, releaseId)
                        .eq(KgNodeRevisionEntity::getNodeId, node.getId()));
        if (rev == null) {
            throw new RenException("当前图谱版本中无该知识点");
        }
        LearnerSkillStateEntity st = learnerSkillStateDao.selectOne(
                new LambdaQueryWrapper<LearnerSkillStateEntity>()
                        .eq(LearnerSkillStateEntity::getChildId, childId)
                        .eq(LearnerSkillStateEntity::getSkillNodeId, node.getId()));

        LearningSkillDetailVO vo = new LearningSkillDetailVO();
        vo.setChildId(childId);
        vo.setCode(code);
        vo.setName(rev.getName());
        vo.setDescription(rev.getDescription());
        vo.setGrade(rev.getGrade());
        vo.setSubject(LearningMasteryStatusUtil.subjectFromSkillCode(code));
        vo.setMastery(toSkillVO(node, rev, st, false, false));
        vo.setPrerequisites(loadLinkedSkills(releaseId, childId, node.getId(), true));
        vo.setNextSkills(loadLinkedSkills(releaseId, childId, node.getId(), false));
        vo.setMisconceptions(loadMisconceptions(releaseId, node.getId()));
        vo.setParentTip(buildParentTip(vo.getMastery()));
        return vo;
    }

    @Override
    public LearningModulePathVO modulePath(
            Long parentUserId, Long childId, String subject, Integer grade, String moduleKey) {
        if (StringUtils.isBlank(moduleKey)) {
            throw new RenException("moduleKey 必填");
        }
        ChildRelease ctx = loadContext(parentUserId, childId, subject, grade);
        if (!isGradeWithinRelease(ctx)) {
            throw new RenException("该年级暂无图谱");
        }
        String mk = moduleKey.trim().toUpperCase();
        List<SkillRow> allGradeRows = loadSkillRowsForGrade(ctx.releaseId, ctx.grade);
        if (allGradeRows.isEmpty()) {
            throw new RenException("该年级暂无图谱");
        }
        List<SkillRow> rows = allGradeRows.stream()
                .filter(r -> mk.equals(r.moduleKey))
                .collect(Collectors.toList());
        if (rows.isEmpty()) {
            throw new RenException("该年级下无此模块");
        }
        Map<Long, SkillRow> rowByNodeId = new HashMap<>();
        for (SkillRow r : rows) {
            rowByNodeId.put(r.nodeId, r);
        }
        Set<Long> nodeIds = rowByNodeId.keySet();
        List<KgEdgeEntity> edges = kgEdgeDao.selectList(
                new LambdaQueryWrapper<KgEdgeEntity>()
                        .eq(KgEdgeEntity::getGraphReleaseId, ctx.releaseId)
                        .eq(KgEdgeEntity::getEdgeType, EDGE_PREREQ)
                        .in(KgEdgeEntity::getFromNodeId, nodeIds)
                        .in(KgEdgeEntity::getToNodeId, nodeIds));
        Map<Long, List<Long>> adj = new HashMap<>();
        Map<Long, Integer> indegree = new HashMap<>();
        for (Long id : nodeIds) {
            indegree.put(id, 0);
        }
        for (KgEdgeEntity e : edges) {
            adj.computeIfAbsent(e.getFromNodeId(), k -> new ArrayList<>()).add(e.getToNodeId());
            indegree.merge(e.getToNodeId(), 1, Integer::sum);
        }
        List<Long> sorted = topologicalSort(nodeIds, adj, indegree);
        Map<Long, LearnerSkillStateEntity> stateByNodeId = loadStatesForChild(childId, rows);

        List<LearningMasterySkillVO> path = new ArrayList<>();
        for (Long nid : sorted) {
            SkillRow r = rowByNodeId.get(nid);
            if (r != null) {
                path.add(toSkillVO(r.node, r.revision, stateByNodeId.get(nid), false, false));
            }
        }
        LearningModulePathVO vo = new LearningModulePathVO();
        vo.setModuleKey(mk);
        vo.setModuleLabel(resolveModuleLabel(mk, rows));
        vo.setGrade(ctx.grade);
        vo.setPath(path);
        return vo;
    }

    private ChildRelease loadContext(Long parentUserId, Long childId, String subject, Integer gradeParam) {
        ParentChildAccessHelper.ensureParentCanAccessChildById(
                deviceChildDao, parentDeviceBindingDao, parentUserId, childId);
        DeviceChildEntity child = deviceChildDao.selectById(childId);
        if (child == null) {
            throw new RenException("孩子不存在");
        }
        String sub = LearningChildProfileUtil.resolveSubject(subject);
        if (ChildGradeOptionsUtil.isPreschoolProfile(child)) {
            ChildRelease ctx = new ChildRelease();
            ctx.subject = sub;
            ctx.grade = 0;
            ctx.childMaxGrade = 0;
            ctx.gradeConfigured = true;
            ctx.releaseId = null;
            ctx.versionLabel = null;
            ctx.graphGradeMin = null;
            ctx.graphGradeMax = null;
            return ctx;
        }
        String province = LearningChildProfileUtil.resolveProvince(child);
        String city = LearningChildProfileUtil.resolveCity(child);
        String semester = LearningChildProfileUtil.resolveSemester(child);
        String textbook = LearningChildProfileUtil.resolveTextbook(child);
        int childMax = LearningChildProfileUtil.resolveChildMaxGrade(child);
        int grade = LearningChildProfileUtil.clampGraphGrade(child, gradeParam);
        LearningChildProfileUtil.validateGraphGradeVisible(child, grade);
        Long releaseId = learningKgService.requireActiveReleaseId(sub, province, city, textbook, semester, grade);
        KgGraphReleaseEntity release = kgGraphReleaseDao.selectById(releaseId);
        boolean gradeConfigured = ChildGradeOptionsUtil.isGradeConfigured(child);
        ChildRelease ctx = new ChildRelease();
        ctx.subject = sub;
        ctx.releaseId = releaseId;
        ctx.versionLabel = release != null ? release.getVersionLabel() : null;
        ctx.grade = grade;
        ctx.childMaxGrade = childMax;
        ctx.gradeConfigured = gradeConfigured;
        if (release != null) {
            ctx.graphGradeMin = release.getGradeMin();
            ctx.graphGradeMax = release.getGradeMax();
        }
        return ctx;
    }

    private static boolean isGradeWithinRelease(ChildRelease ctx) {
        if (ctx.graphGradeMin == null || ctx.graphGradeMax == null) {
            return true;
        }
        return ctx.grade >= ctx.graphGradeMin && ctx.grade <= ctx.graphGradeMax;
    }

    private static boolean isGradeSupported(ChildRelease ctx, List<SkillRow> rows) {
        if (!isGradeWithinRelease(ctx)) {
            return false;
        }
        return rows != null && !rows.isEmpty();
    }

    private List<SkillRow> loadSkillRowsForGrade(Long releaseId, int grade) {
        KgGraphReleaseEntity release = kgGraphReleaseDao.selectById(releaseId);
        List<KgNodeRevisionEntity> revs = kgNodeRevisionDao.selectList(
                LearningKgServiceImpl.revisionGradeWrapper(releaseId, release, grade));
        List<SkillRow> out = new ArrayList<>();
        for (KgNodeRevisionEntity rev : revs) {
            KgNodeEntity node = kgNodeDao.selectById(rev.getNodeId());
            if (node == null || !LearningKgNodeTypeUtil.isMasterySkill(node)) {
                continue;
            }
            SkillRow row = new SkillRow();
            row.node = node;
            row.revision = rev;
            row.nodeId = node.getId();
            row.moduleKey = LearningMasteryStatusUtil.moduleKeyFromSkillCode(node.getCode());
            out.add(row);
        }
        out.sort(Comparator.comparing(r -> r.node.getCode()));
        return out;
    }

    private Map<Long, LearnerSkillStateEntity> loadStatesForChild(Long childId, List<SkillRow> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = rows.stream().map(r -> r.nodeId).collect(Collectors.toList());
        List<LearnerSkillStateEntity> list = learnerSkillStateDao.selectList(
                new LambdaQueryWrapper<LearnerSkillStateEntity>()
                        .eq(LearnerSkillStateEntity::getChildId, childId)
                        .in(LearnerSkillStateEntity::getSkillNodeId, ids));
        Map<Long, LearnerSkillStateEntity> map = new HashMap<>();
        for (LearnerSkillStateEntity st : list) {
            map.put(st.getSkillNodeId(), st);
        }
        return map;
    }

    private LearningMasteryModuleVO buildModuleVO(
            String moduleKey,
            List<SkillRow> rows,
            Map<Long, LearnerSkillStateEntity> stateByNodeId,
            Set<String> observedThisWeekCodes,
            Set<String> consolidatePeriodCodes) {
        List<LearningMasterySkillVO> skills = new ArrayList<>();
        int observed = 0;
        int need = 0;
        for (SkillRow row : rows) {
            LearnerSkillStateEntity st = stateByNodeId.get(row.nodeId);
            LearningMasterySkillVO sv = toSkillVO(
                    row.node,
                    row.revision,
                    st,
                    observedThisWeekCodes.contains(row.node.getCode()),
                    consolidatePeriodCodes.contains(row.node.getCode()));
            skills.add(sv);
            if (st != null && st.getEvidenceCount() != null && st.getEvidenceCount() > 0) {
                observed++;
            }
            if (LearningMasteryStatusUtil.NEED_CONSOLIDATE.equals(sv.getStatus())) {
                need++;
            }
        }
        LearningMasteryModuleVO m = new LearningMasteryModuleVO();
        m.setModuleKey(moduleKey);
        m.setModuleLabel(resolveModuleLabel(moduleKey, rows));
        m.setSkillTotal(skills.size());
        m.setObservedCount(observed);
        m.setNeedConsolidateCount(need);
        m.setSkills(skills);
        return m;
    }

    private static String resolveModuleLabel(String moduleKey, List<SkillRow> rows) {
        if (rows != null) {
            for (SkillRow row : rows) {
                String name =
                        LearningMasteryModuleLabels.moduleNameFromProperties(
                                row.revision != null ? row.revision.getProperties() : null);
                if (StringUtils.isNotBlank(name)) {
                    return name.trim();
                }
            }
        }
        return LearningMasteryModuleLabels.labelFor(moduleKey);
    }

    private LearningMasterySummaryVO aggregateSummary(List<LearningMasteryModuleVO> modules, int grade) {
        LearningMasterySummaryVO s = new LearningMasterySummaryVO();
        int total = 0;
        int observed = 0;
        int need = 0;
        int practicing = 0;
        int stable = 0;
        int unobserved = 0;
        int observedThisWeek = 0;
        int suggestedConsolidate = 0;
        for (LearningMasteryModuleVO m : modules) {
            total += m.getSkillTotal() != null ? m.getSkillTotal() : 0;
            observed += m.getObservedCount() != null ? m.getObservedCount() : 0;
            need += m.getNeedConsolidateCount() != null ? m.getNeedConsolidateCount() : 0;
            if (m.getSkills() == null) {
                continue;
            }
            for (LearningMasterySkillVO sk : m.getSkills()) {
                if (Boolean.TRUE.equals(sk.getObservedThisWeek())) {
                    observedThisWeek++;
                }
                if (Boolean.TRUE.equals(sk.getConsolidateThisPeriod())) {
                    suggestedConsolidate++;
                }
                switch (StringUtils.defaultString(sk.getStatus())) {
                    case LearningMasteryStatusUtil.PRACTICING -> practicing++;
                    case LearningMasteryStatusUtil.STABLE -> stable++;
                    case LearningMasteryStatusUtil.UNOBSERVED -> unobserved++;
                    default -> {
                    }
                }
            }
        }
        s.setSkillTotal(total);
        s.setObservedCount(observed);
        s.setNeedConsolidateCount(need);
        s.setPracticingCount(practicing);
        s.setStableCount(stable);
        s.setUnobservedCount(unobserved);
        s.setCoverageScope("grade_cumulative");
        s.setTermLabel("小学" + grade + "年级图谱累计");
        s.setObservedThisWeekCount(observedThisWeek);
        s.setSuggestedConsolidateCount(suggestedConsolidate);
        return s;
    }

    private LearningMasterySkillVO toSkillVO(
            KgNodeEntity node,
            KgNodeRevisionEntity rev,
            LearnerSkillStateEntity st,
            boolean observedThisWeek,
            boolean consolidateThisPeriod) {
        LearningMasterySkillVO vo = new LearningMasterySkillVO();
        vo.setCode(node.getCode());
        vo.setName(rev.getName());
        vo.setDescription(rev.getDescription());
        if (st != null && st.getEvidenceCount() != null && st.getEvidenceCount() > 0) {
            vo.setPMastery(st.getPMastery());
            vo.setEvidenceCount(st.getEvidenceCount());
            vo.setLastEvidenceAt(st.getLastEvidenceAt());
        } else {
            vo.setEvidenceCount(0);
        }
        vo.setStatus(LearningMasteryStatusUtil.resolveStatus(vo.getEvidenceCount(), vo.getPMastery()));
        vo.setObservedThisWeek(observedThisWeek);
        vo.setConsolidateThisPeriod(consolidateThisPeriod);
        return vo;
    }

    private Set<String> loadObservedSkillCodesInRange(Long childId, Date from, Date toExclusive) {
        List<LearningEvidenceEventEntity> events = learningEvidenceEventDao.selectList(
                new LambdaQueryWrapper<LearningEvidenceEventEntity>()
                        .eq(LearningEvidenceEventEntity::getChildId, childId)
                        .ge(LearningEvidenceEventEntity::getOccurredAt, from)
                        .lt(LearningEvidenceEventEntity::getOccurredAt, toExclusive)
                        .in(
                                LearningEvidenceEventEntity::getEventType,
                                "DIAGNOSIS_VISION",
                                "DIAGNOSIS_VERBAL"));
        Set<String> codes = new HashSet<>();
        for (LearningEvidenceEventEntity ev : events) {
            appendSkillCodesFromEvent(ev, codes);
        }
        return codes;
    }

    /** 与 {@link LearningSessionServiceImpl#weeklyDigest} 的 topWeakSkills 同源（全局最低掌握度 Top5）。 */
    private Set<String> loadSuggestedConsolidateSkillCodes(Long childId) {
        List<LearnerSkillStateEntity> states = learnerSkillStateDao.selectList(
                new LambdaQueryWrapper<LearnerSkillStateEntity>()
                        .eq(LearnerSkillStateEntity::getChildId, childId)
                        .orderByAsc(LearnerSkillStateEntity::getPMastery)
                        .last("LIMIT " + SUGGESTED_CONSOLIDATE_TOP_N));
        Set<String> codes = new HashSet<>();
        for (LearnerSkillStateEntity st : states) {
            KgNodeEntity node = kgNodeDao.selectById(st.getSkillNodeId());
            if (node != null && StringUtils.isNotBlank(node.getCode())) {
                codes.add(node.getCode());
            }
        }
        return codes;
    }

    private static void appendSkillCodesFromEvent(LearningEvidenceEventEntity ev, Set<String> codes) {
        if (StringUtils.isBlank(ev.getSkillCodes())) {
            return;
        }
        try {
            List<String> part = JsonUtils.parseArray(ev.getSkillCodes(), String.class);
            if (part == null) {
                return;
            }
            for (String c : part) {
                if (StringUtils.isNotBlank(c)) {
                    codes.add(c.trim());
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static WeekRange resolveWeekRange(String weekStart) {
        LocalDate start = StringUtils.isNotBlank(weekStart)
                ? LocalDate.parse(weekStart)
                : LocalDate.now(ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = start.plusDays(6);
        Date from = Date.from(start.atStartOfDay(ZONE).toInstant());
        Date toExclusive = Date.from(end.plusDays(1).atStartOfDay(ZONE).toInstant());
        return new WeekRange(start, end, from, toExclusive);
    }

    private record WeekRange(LocalDate start, LocalDate end, Date from, Date toExclusive) {
    }

    private List<LearningSkillBriefVO> loadLinkedSkills(
            Long releaseId, Long childId, Long skillNodeId, boolean prerequisites) {
        List<KgEdgeEntity> edges;
        if (prerequisites) {
            edges = kgEdgeDao.selectList(
                    new LambdaQueryWrapper<KgEdgeEntity>()
                            .eq(KgEdgeEntity::getGraphReleaseId, releaseId)
                            .eq(KgEdgeEntity::getEdgeType, EDGE_PREREQ)
                            .eq(KgEdgeEntity::getToNodeId, skillNodeId));
        } else {
            edges = kgEdgeDao.selectList(
                    new LambdaQueryWrapper<KgEdgeEntity>()
                            .eq(KgEdgeEntity::getGraphReleaseId, releaseId)
                            .eq(KgEdgeEntity::getEdgeType, EDGE_PREREQ)
                            .eq(KgEdgeEntity::getFromNodeId, skillNodeId));
        }
        List<LearningSkillBriefVO> out = new ArrayList<>();
        for (KgEdgeEntity e : edges) {
            Long linkedId = prerequisites ? e.getFromNodeId() : e.getToNodeId();
            KgNodeEntity n = kgNodeDao.selectById(linkedId);
            if (n == null || !LearningKgNodeTypeUtil.isMasterySkill(n)) {
                continue;
            }
            KgNodeRevisionEntity rev = kgNodeRevisionDao.selectOne(
                    new LambdaQueryWrapper<KgNodeRevisionEntity>()
                            .eq(KgNodeRevisionEntity::getGraphReleaseId, releaseId)
                            .eq(KgNodeRevisionEntity::getNodeId, linkedId));
            LearnerSkillStateEntity st = learnerSkillStateDao.selectOne(
                    new LambdaQueryWrapper<LearnerSkillStateEntity>()
                            .eq(LearnerSkillStateEntity::getChildId, childId)
                            .eq(LearnerSkillStateEntity::getSkillNodeId, linkedId));
            LearningSkillBriefVO b = new LearningSkillBriefVO();
            b.setCode(n.getCode());
            b.setName(rev != null ? rev.getName() : n.getCode());
            b.setStatus(LearningMasteryStatusUtil.resolveStatus(
                    st != null ? st.getEvidenceCount() : 0, st != null ? st.getPMastery() : null));
            out.add(b);
        }
        out.sort(Comparator.comparing(LearningSkillBriefVO::getCode));
        return out;
    }

    private List<LearningMisconceptionBriefVO> loadMisconceptions(Long releaseId, Long skillNodeId) {
        List<KgEdgeEntity> edges = kgEdgeDao.selectList(
                new LambdaQueryWrapper<KgEdgeEntity>()
                        .eq(KgEdgeEntity::getGraphReleaseId, releaseId)
                        .eq(KgEdgeEntity::getEdgeType, EDGE_MIS)
                        .eq(KgEdgeEntity::getFromNodeId, skillNodeId));
        List<LearningMisconceptionBriefVO> out = new ArrayList<>();
        for (KgEdgeEntity e : edges) {
            KgNodeEntity n = kgNodeDao.selectById(e.getToNodeId());
            if (n == null || !NODE_MISCONCEPTION.equalsIgnoreCase(n.getNodeType())) {
                continue;
            }
            KgNodeRevisionEntity rev = kgNodeRevisionDao.selectOne(
                    new LambdaQueryWrapper<KgNodeRevisionEntity>()
                            .eq(KgNodeRevisionEntity::getGraphReleaseId, releaseId)
                            .eq(KgNodeRevisionEntity::getNodeId, n.getId()));
            LearningMisconceptionBriefVO m = new LearningMisconceptionBriefVO();
            m.setCode(n.getCode());
            m.setName(rev != null ? rev.getName() : n.getCode());
            m.setDescription(rev != null ? rev.getDescription() : null);
            out.add(m);
        }
        return out;
    }

    private String buildParentTip(LearningMasterySkillVO mastery) {
        if (mastery == null) {
            return "让孩子对设备说「进入作业辅导」，卡住时问一题或拍一道典型题。";
        }
        return switch (mastery.getStatus()) {
            case LearningMasteryStatusUtil.UNOBSERVED ->
                    "尚未在辅导中观察到练习，可让孩子在该知识点上问一题或拍一道题。";
            case LearningMasteryStatusUtil.NEED_CONSOLIDATE ->
                    "建议优先巩固；若有进行中的回炉任务，可陪孩子练一练。";
            case LearningMasteryStatusUtil.PRACTICING -> "继续用提问式辅导，不必一次讲完全部步骤。";
            case LearningMasteryStatusUtil.STABLE -> "观察上较稳，遇到变式题仍可在辅导模式里练一练。";
            default -> "让孩子对设备说「进入作业辅导」即可记录学情。";
        };
    }

    private static List<Long> topologicalSort(
            Set<Long> nodeIds, Map<Long, List<Long>> adj, Map<Long, Integer> indegree) {
        List<Long> order = new ArrayList<>();
        List<Long> queue = indegree.entrySet().stream()
                .filter(e -> e.getValue() == 0)
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));
        Map<Long, Integer> indeg = new HashMap<>(indegree);
        while (!queue.isEmpty()) {
            Long u = queue.remove(0);
            order.add(u);
            for (Long v : adj.getOrDefault(u, List.of())) {
                indeg.merge(v, -1, Integer::sum);
                if (indeg.get(v) == 0) {
                    queue.add(v);
                }
            }
            queue.sort(Long::compareTo);
        }
        if (order.size() < nodeIds.size()) {
            for (Long id : nodeIds.stream().sorted().collect(Collectors.toList())) {
                if (!order.contains(id)) {
                    order.add(id);
                }
            }
        }
        return order;
    }

    private static class SkillRow {
        Long nodeId;
        KgNodeEntity node;
        KgNodeRevisionEntity revision;
        String moduleKey;
    }

    private static class ChildRelease {
        String subject;
        Long releaseId;
        String versionLabel;
        int grade;
        int childMaxGrade;
        boolean gradeConfigured;
        Integer graphGradeMin;
        Integer graphGradeMax;
    }
}
