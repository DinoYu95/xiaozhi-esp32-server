package xiaozhi.modules.learning.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
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
import xiaozhi.modules.parent.dao.DeviceChildDao;
import xiaozhi.modules.parent.entity.DeviceChildEntity;
import xiaozhi.modules.learning.dao.KgEdgeDao;
import xiaozhi.modules.learning.dao.KgGraphReleaseDao;
import xiaozhi.modules.learning.dao.KgNodeDao;
import xiaozhi.modules.learning.dao.KgNodeRevisionDao;
import xiaozhi.modules.learning.dao.LearnerSkillStateDao;
import xiaozhi.modules.learning.entity.KgEdgeEntity;
import xiaozhi.modules.learning.entity.KgGraphReleaseEntity;
import xiaozhi.modules.learning.entity.KgNodeEntity;
import xiaozhi.modules.learning.entity.KgNodeRevisionEntity;
import xiaozhi.modules.learning.entity.LearnerSkillStateEntity;
import xiaozhi.modules.learning.service.LearningKgService;
import xiaozhi.modules.learning.service.LearningMasteryService;
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

    private final DeviceChildDao deviceChildDao;
    private final ParentDeviceBindingDao parentDeviceBindingDao;
    private final LearningKgService learningKgService;
    private final KgGraphReleaseDao kgGraphReleaseDao;
    private final KgNodeDao kgNodeDao;
    private final KgNodeRevisionDao kgNodeRevisionDao;
    private final KgEdgeDao kgEdgeDao;
    private final LearnerSkillStateDao learnerSkillStateDao;

    @Override
    public LearningMasteryMapVO masteryMap(Long parentUserId, Long childId, String subject, Integer grade) {
        ChildRelease ctx = loadContext(parentUserId, childId, subject, grade);
        List<SkillRow> rows = loadSkillRowsForGrade(ctx.releaseId, ctx.grade);
        Map<Long, LearnerSkillStateEntity> stateByNodeId = loadStatesForChild(childId, rows);

        Map<String, List<SkillRow>> byModule = new LinkedHashMap<>();
        for (SkillRow row : rows) {
            byModule.computeIfAbsent(row.moduleKey, k -> new ArrayList<>()).add(row);
        }

        List<LearningMasteryModuleVO> modules = byModule.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> LearningMasteryModuleLabels.sortIndex(e.getKey())))
                .map(e -> buildModuleVO(e.getKey(), e.getValue(), stateByNodeId))
                .collect(Collectors.toList());

        LearningMasterySummaryVO summary = aggregateSummary(modules);
        LearningMasteryMapVO vo = new LearningMasteryMapVO();
        vo.setChildId(childId);
        vo.setSubject(ctx.subject);
        vo.setSubjectLabel(LearningMasteryStatusUtil.subjectLabel(ctx.subject));
        vo.setGrade(ctx.grade);
        vo.setGradeConfigured(ctx.gradeConfigured);
        vo.setGraphReleaseId(ctx.releaseId);
        vo.setGraphVersionLabel(ctx.versionLabel);
        vo.setSummary(summary);
        vo.setModules(modules);
        vo.setCoverageNote(COVERAGE_NOTE);
        return vo;
    }

    @Override
    public LearningSkillDetailVO skillDetail(Long parentUserId, Long childId, String skillCode) {
        if (StringUtils.isBlank(skillCode)) {
            throw new RenException("skill code 必填");
        }
        ParentChildAccessHelper.ensureParentCanAccessChildById(
                deviceChildDao, parentDeviceBindingDao, parentUserId, childId);
        String code = skillCode.trim();
        KgNodeEntity node = kgNodeDao.selectOne(
                new LambdaQueryWrapper<KgNodeEntity>().eq(KgNodeEntity::getCode, code));
        if (node == null || !NODE_SKILL.equalsIgnoreCase(node.getNodeType())) {
            throw new RenException("知识点不存在或不是 SKILL");
        }
        Long releaseId = learningKgService.requireActiveReleaseId("math");
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
        vo.setSubject("math");
        vo.setMastery(toSkillVO(node, rev, st));
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
        String mk = moduleKey.trim().toUpperCase();
        List<SkillRow> rows = loadSkillRowsForGrade(ctx.releaseId, ctx.grade).stream()
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
                path.add(toSkillVO(r.node, r.revision, stateByNodeId.get(nid)));
            }
        }
        LearningModulePathVO vo = new LearningModulePathVO();
        vo.setModuleKey(mk);
        vo.setModuleLabel(LearningMasteryModuleLabels.labelFor(mk));
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
        String sub = StringUtils.defaultIfBlank(subject, "math").toLowerCase();
        if (!"math".equals(sub)) {
            throw new RenException("该学科图谱尚未开放，请稍后再试");
        }
        Long releaseId = learningKgService.requireActiveReleaseId(sub);
        KgGraphReleaseEntity release = kgGraphReleaseDao.selectById(releaseId);
        boolean gradeConfigured = child.getCurrentGrade() != null && child.getCurrentGrade() > 0;
        int grade = gradeParam != null && gradeParam > 0
                ? gradeParam
                : (gradeConfigured ? child.getCurrentGrade() : 1);
        if (release != null) {
            if (grade < release.getGradeMin()) {
                grade = release.getGradeMin();
            }
            if (grade > release.getGradeMax()) {
                grade = release.getGradeMax();
            }
        }
        ChildRelease ctx = new ChildRelease();
        ctx.subject = sub;
        ctx.releaseId = releaseId;
        ctx.versionLabel = release != null ? release.getVersionLabel() : null;
        ctx.grade = grade;
        ctx.gradeConfigured = gradeConfigured;
        return ctx;
    }

    private List<SkillRow> loadSkillRowsForGrade(Long releaseId, int grade) {
        List<KgNodeRevisionEntity> revs = kgNodeRevisionDao.selectList(
                new LambdaQueryWrapper<KgNodeRevisionEntity>()
                        .eq(KgNodeRevisionEntity::getGraphReleaseId, releaseId)
                        .eq(KgNodeRevisionEntity::getGrade, grade));
        List<SkillRow> out = new ArrayList<>();
        for (KgNodeRevisionEntity rev : revs) {
            KgNodeEntity node = kgNodeDao.selectById(rev.getNodeId());
            if (node == null || !NODE_SKILL.equalsIgnoreCase(node.getNodeType())) {
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
            Map<Long, LearnerSkillStateEntity> stateByNodeId) {
        List<LearningMasterySkillVO> skills = new ArrayList<>();
        int observed = 0;
        int need = 0;
        for (SkillRow row : rows) {
            LearnerSkillStateEntity st = stateByNodeId.get(row.nodeId);
            LearningMasterySkillVO sv = toSkillVO(row.node, row.revision, st);
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
        m.setModuleLabel(LearningMasteryModuleLabels.labelFor(moduleKey));
        m.setSkillTotal(skills.size());
        m.setObservedCount(observed);
        m.setNeedConsolidateCount(need);
        m.setSkills(skills);
        return m;
    }

    private LearningMasterySummaryVO aggregateSummary(List<LearningMasteryModuleVO> modules) {
        LearningMasterySummaryVO s = new LearningMasterySummaryVO();
        int total = 0;
        int observed = 0;
        int need = 0;
        int practicing = 0;
        int stable = 0;
        int unobserved = 0;
        for (LearningMasteryModuleVO m : modules) {
            total += m.getSkillTotal() != null ? m.getSkillTotal() : 0;
            observed += m.getObservedCount() != null ? m.getObservedCount() : 0;
            need += m.getNeedConsolidateCount() != null ? m.getNeedConsolidateCount() : 0;
            if (m.getSkills() == null) {
                continue;
            }
            for (LearningMasterySkillVO sk : m.getSkills()) {
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
        return s;
    }

    private LearningMasterySkillVO toSkillVO(
            KgNodeEntity node, KgNodeRevisionEntity rev, LearnerSkillStateEntity st) {
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
        return vo;
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
            if (n == null || !NODE_SKILL.equalsIgnoreCase(n.getNodeType())) {
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
        boolean gradeConfigured;
    }
}
