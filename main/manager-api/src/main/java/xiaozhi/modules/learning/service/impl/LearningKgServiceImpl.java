package xiaozhi.modules.learning.service.impl;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.learning.dao.KgClosureDao;
import xiaozhi.modules.learning.dao.KgEdgeDao;
import xiaozhi.modules.learning.dao.KgGraphReleaseDao;
import xiaozhi.modules.learning.dao.KgNodeDao;
import xiaozhi.modules.learning.dao.KgNodeRevisionDao;
import xiaozhi.modules.learning.entity.KgClosureEntity;
import xiaozhi.modules.learning.entity.KgEdgeEntity;
import xiaozhi.modules.learning.entity.KgGraphReleaseEntity;
import xiaozhi.modules.learning.entity.KgNodeEntity;
import xiaozhi.modules.learning.entity.KgNodeRevisionEntity;
import cn.hutool.json.JSONUtil;
import xiaozhi.modules.learning.dto.TeachingKgPublishDTO;
import xiaozhi.modules.learning.service.LearningKgService;
import xiaozhi.modules.learning.util.LearningKgGraphMatchUtil;
import xiaozhi.modules.learning.util.LearningProfileConstants;
import xiaozhi.modules.learning.vo.KgReleaseVO;

@Service
@RequiredArgsConstructor
public class LearningKgServiceImpl implements LearningKgService {

    private static final Set<String> CLOSURE_EDGE_TYPES = Set.of(
            "PREREQUISITE_OF", "SUBSKILL_OF", "PART_OF");

    private final KgGraphReleaseDao kgGraphReleaseDao;
    private final KgNodeDao kgNodeDao;
    private final KgNodeRevisionDao kgNodeRevisionDao;
    private final KgEdgeDao kgEdgeDao;
    private final KgClosureDao kgClosureDao;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDraftRelease(String versionLabel, String subject, int gradeMin, int gradeMax) {
        if (StringUtils.isBlank(versionLabel)) {
            throw new RenException("versionLabel 必填");
        }
        Date now = new Date();
        KgGraphReleaseEntity e = new KgGraphReleaseEntity();
        e.setVersionLabel(versionLabel.trim());
        e.setStatus(KgGraphReleaseEntity.STATUS_DRAFT);
        e.setSubject(LearningKgGraphMatchUtil.normalizeSubject(subject));
        e.setGradeMin(gradeMin);
        e.setGradeMax(gradeMax);
        e.setProvinceCode("CN");
        e.setTextbookEdition("generic");
        e.setCreateTime(now);
        e.setUpdateTime(now);
        kgGraphReleaseDao.insert(e);
        return e.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void importNodesCsv(Long releaseId, InputStream csv) {
        KgGraphReleaseEntity release = requireDraft(releaseId);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(csv, StandardCharsets.UTF_8))) {
            String header = br.readLine();
            if (header == null) {
                throw new RenException("nodes.csv 为空");
            }
            String line;
            while ((line = br.readLine()) != null) {
                if (StringUtils.isBlank(line) || line.startsWith("#")) {
                    continue;
                }
                String[] p = line.split(",", -1);
                if (p.length < 3) {
                    continue;
                }
                String code = p[0].trim();
                String nodeType = p[1].trim();
                String name = p[2].trim();
                String desc = p.length > 3 ? p[3].trim() : "";
                Integer grade = null;
                if (p.length > 4 && StringUtils.isNotBlank(p[4])) {
                    grade = Integer.parseInt(p[4].trim());
                }
                if (StringUtils.isAnyBlank(code, nodeType, name)) {
                    continue;
                }
                KgNodeEntity node = kgNodeDao.selectOne(
                        new LambdaQueryWrapper<KgNodeEntity>().eq(KgNodeEntity::getCode, code));
                Date now = new Date();
                if (node == null) {
                    node = new KgNodeEntity();
                    node.setCode(code);
                    node.setNodeType(nodeType);
                    node.setCreateTime(now);
                    kgNodeDao.insert(node);
                }
                KgNodeRevisionEntity rev = kgNodeRevisionDao.selectOne(
                        new LambdaQueryWrapper<KgNodeRevisionEntity>()
                                .eq(KgNodeRevisionEntity::getGraphReleaseId, release.getId())
                                .eq(KgNodeRevisionEntity::getNodeId, node.getId()));
                if (rev == null) {
                    rev = new KgNodeRevisionEntity();
                    rev.setGraphReleaseId(release.getId());
                    rev.setNodeId(node.getId());
                }
                rev.setName(name);
                rev.setDescription(desc);
                rev.setGrade(grade);
                if (rev.getId() == null) {
                    kgNodeRevisionDao.insert(rev);
                } else {
                    kgNodeRevisionDao.updateById(rev);
                }
            }
        } catch (RenException e) {
            throw e;
        } catch (Exception e) {
            throw new RenException("导入 nodes.csv 失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void importEdgesCsv(Long releaseId, InputStream csv) {
        KgGraphReleaseEntity release = requireDraft(releaseId);
        kgEdgeDao.delete(new LambdaQueryWrapper<KgEdgeEntity>()
                .eq(KgEdgeEntity::getGraphReleaseId, release.getId()));
        try (BufferedReader br = new BufferedReader(new InputStreamReader(csv, StandardCharsets.UTF_8))) {
            String header = br.readLine();
            if (header == null) {
                throw new RenException("edges.csv 为空");
            }
            String line;
            while ((line = br.readLine()) != null) {
                if (StringUtils.isBlank(line) || line.startsWith("#")) {
                    continue;
                }
                String[] p = line.split(",", -1);
                if (p.length < 3) {
                    continue;
                }
                String fromCode = p[0].trim();
                String edgeType = p[1].trim();
                String toCode = p[2].trim();
                boolean required = p.length <= 3 || !"false".equalsIgnoreCase(p[3].trim());
                BigDecimal strength = null;
                if (p.length > 4 && StringUtils.isNotBlank(p[4])) {
                    strength = new BigDecimal(p[4].trim());
                }
                Long fromId = nodeIdByCode(fromCode);
                Long toId = nodeIdByCode(toCode);
                if (fromId == null || toId == null) {
                    throw new RenException("边引用了未知节点: " + fromCode + " -> " + toCode);
                }
                KgEdgeEntity edge = new KgEdgeEntity();
                edge.setGraphReleaseId(release.getId());
                edge.setFromNodeId(fromId);
                edge.setToNodeId(toId);
                edge.setEdgeType(edgeType);
                edge.setRequired(required);
                edge.setStrength(strength);
                kgEdgeDao.insert(edge);
            }
        } catch (RenException e) {
            throw e;
        } catch (Exception e) {
            throw new RenException("导入 edges.csv 失败: " + e.getMessage());
        }
    }

    @Override
    public void validateRelease(Long releaseId) {
        requireDraft(releaseId);
        long skillCount = kgNodeRevisionDao.selectCount(
                new LambdaQueryWrapper<KgNodeRevisionEntity>()
                        .eq(KgNodeRevisionEntity::getGraphReleaseId, releaseId));
        if (skillCount == 0) {
            throw new RenException("该版本无节点，请先导入 nodes.csv");
        }
        detectCycle(releaseId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishRelease(Long releaseId) {
        validateRelease(releaseId);
        KgGraphReleaseEntity release = kgGraphReleaseDao.selectById(releaseId);
        if (release == null || !KgGraphReleaseEntity.STATUS_DRAFT.equals(release.getStatus())) {
            throw new RenException("仅 draft 可发布");
        }
        List<KgGraphReleaseEntity> oldPublished = kgGraphReleaseDao.selectList(
                new LambdaQueryWrapper<KgGraphReleaseEntity>()
                        .eq(KgGraphReleaseEntity::getSubject, release.getSubject())
                        .eq(KgGraphReleaseEntity::getProvinceCode,
                                StringUtils.defaultIfBlank(release.getProvinceCode(), "CN"))
                        .eq(KgGraphReleaseEntity::getTextbookEdition,
                                StringUtils.defaultIfBlank(release.getTextbookEdition(), "generic"))
                        .eq(KgGraphReleaseEntity::getGradeMin, release.getGradeMin())
                        .eq(KgGraphReleaseEntity::getGradeMax, release.getGradeMax())
                        .eq(KgGraphReleaseEntity::getStatus, KgGraphReleaseEntity.STATUS_PUBLISHED));
        Date now = new Date();
        for (KgGraphReleaseEntity old : oldPublished) {
            KgGraphReleaseEntity patch = new KgGraphReleaseEntity();
            patch.setId(old.getId());
            patch.setStatus(KgGraphReleaseEntity.STATUS_ARCHIVED);
            patch.setUpdateTime(now);
            kgGraphReleaseDao.updateById(patch);
        }
        rebuildClosure(releaseId);
        KgGraphReleaseEntity patch = new KgGraphReleaseEntity();
        patch.setId(releaseId);
        patch.setStatus(KgGraphReleaseEntity.STATUS_PUBLISHED);
        patch.setPublishedAt(now);
        patch.setUpdateTime(now);
        patch.setChecksum(String.valueOf(kgEdgeDao.selectCount(
                new LambdaQueryWrapper<KgEdgeEntity>().eq(KgEdgeEntity::getGraphReleaseId, releaseId))));
        kgGraphReleaseDao.updateById(patch);
    }

    @Override
    public KgReleaseVO getActivePublishedRelease(String subject) {
        KgGraphReleaseEntity e = findActivePublished(subject);
        if (e == null) {
            return null;
        }
        KgReleaseVO vo = new KgReleaseVO();
        vo.setId(e.getId());
        vo.setVersionLabel(e.getVersionLabel());
        vo.setStatus(e.getStatus());
        vo.setSubject(e.getSubject());
        vo.setGradeMin(e.getGradeMin());
        vo.setGradeMax(e.getGradeMax());
        vo.setPublishedAt(e.getPublishedAt());
        return vo;
    }

    @Override
    public Long requireActiveReleaseId(String subject) {
        KgGraphReleaseEntity e = findActivePublished(subject);
        if (e == null) {
            throw new RenException("尚未发布学科图谱: " + subject);
        }
        return e.getId();
    }

    @Override
    public Long requireActiveReleaseId(
            String subject, String provinceCode, String textbookEdition, int graphGrade) {
        String sub = LearningKgGraphMatchUtil.normalizeSubject(subject);
        String province = LearningProfileConstants.normalizeProvince(provinceCode);
        String textbook = LearningProfileConstants.normalizeTextbook(textbookEdition);
        KgGraphReleaseEntity e = findActivePublishedRelease(sub, province, textbook, graphGrade);
        if (e == null) {
            throw new RenException(
                    "尚未发布匹配的图谱: "
                            + sub
                            + " / "
                            + province
                            + " / "
                            + textbook
                            + " / "
                            + graphGrade
                            + "年级");
        }
        return e.getId();
    }

    @Override
    public KgGraphReleaseEntity findActivePublishedRelease(
            String subject, String provinceCode, String textbookEdition, int graphGrade) {
        if (graphGrade <= 0) {
            return null;
        }
        String sub = LearningKgGraphMatchUtil.normalizeSubject(subject);
        String province = LearningProfileConstants.normalizeProvince(provinceCode);
        String textbook = LearningProfileConstants.normalizeTextbook(textbookEdition);
        String[][] attempts = {
            {province, textbook},
            {province, LearningProfileConstants.DEFAULT_TEXTBOOK},
            {LearningProfileConstants.DEFAULT_PROVINCE, textbook},
            {LearningProfileConstants.DEFAULT_PROVINCE, LearningProfileConstants.DEFAULT_TEXTBOOK},
        };
        for (String[] pair : attempts) {
            KgGraphReleaseEntity hit = queryPublishedRelease(sub, pair[0], pair[1], graphGrade);
            if (hit != null) {
                return hit;
            }
        }
        KgGraphReleaseEntity byGrade = queryPublishedReleaseIgnoringRegion(sub, graphGrade);
        if (byGrade != null) {
            return byGrade;
        }
        return findActivePublished(sub);
    }

    /**
     * 省/教材四维未命中时：仍要求 graphGrade 落在 release 区间内（不按 min=max 精确相等）。
     */
    private KgGraphReleaseEntity queryPublishedReleaseIgnoringRegion(String subject, int graphGrade) {
        LambdaQueryWrapper<KgGraphReleaseEntity> w =
                new LambdaQueryWrapper<KgGraphReleaseEntity>()
                        .eq(KgGraphReleaseEntity::getSubject, subject)
                        .eq(KgGraphReleaseEntity::getStatus, KgGraphReleaseEntity.STATUS_PUBLISHED);
        LearningKgGraphMatchUtil.applyGraphGradeWithinRelease(w, graphGrade);
        w.orderByDesc(KgGraphReleaseEntity::getPublishedAt).last("LIMIT 1");
        return kgGraphReleaseDao.selectOne(w);
    }

    private KgGraphReleaseEntity queryPublishedRelease(
            String subject, String provinceCode, String textbookEdition, int graphGrade) {
        LambdaQueryWrapper<KgGraphReleaseEntity> w =
                new LambdaQueryWrapper<KgGraphReleaseEntity>()
                        .eq(KgGraphReleaseEntity::getSubject, subject)
                        .eq(KgGraphReleaseEntity::getProvinceCode, provinceCode)
                        .eq(KgGraphReleaseEntity::getTextbookEdition, textbookEdition)
                        .eq(KgGraphReleaseEntity::getStatus, KgGraphReleaseEntity.STATUS_PUBLISHED);
        LearningKgGraphMatchUtil.applyGraphGradeWithinRelease(w, graphGrade);
        w.orderByDesc(KgGraphReleaseEntity::getPublishedAt).last("LIMIT 1");
        return kgGraphReleaseDao.selectOne(w);
    }

    private KgGraphReleaseEntity findActivePublished(String subject) {
        String sub = LearningKgGraphMatchUtil.normalizeSubject(subject);
        return kgGraphReleaseDao.selectOne(
                new LambdaQueryWrapper<KgGraphReleaseEntity>()
                        .eq(KgGraphReleaseEntity::getSubject, sub)
                        .eq(KgGraphReleaseEntity::getStatus, KgGraphReleaseEntity.STATUS_PUBLISHED)
                        .orderByDesc(KgGraphReleaseEntity::getPublishedAt)
                        .last("LIMIT 1"));
    }

    @Override
    public long countSkillNodesAtGrade(Long releaseId, int grade) {
        if (releaseId == null || grade <= 0) {
            return 0;
        }
        KgGraphReleaseEntity release = kgGraphReleaseDao.selectById(releaseId);
        List<KgNodeRevisionEntity> revs = kgNodeRevisionDao.selectList(
                revisionGradeWrapper(releaseId, release, grade));
        long count = 0;
        for (KgNodeRevisionEntity rev : revs) {
            KgNodeEntity node = kgNodeDao.selectById(rev.getNodeId());
            if (node != null && "SKILL".equalsIgnoreCase(node.getNodeType())) {
                count++;
            }
        }
        return count;
    }

    public static LambdaQueryWrapper<KgNodeRevisionEntity> revisionGradeWrapper(
            Long releaseId, KgGraphReleaseEntity release, int grade) {
        LambdaQueryWrapper<KgNodeRevisionEntity> w = new LambdaQueryWrapper<KgNodeRevisionEntity>()
                .eq(KgNodeRevisionEntity::getGraphReleaseId, releaseId);
        w.and(q -> {
            q.eq(KgNodeRevisionEntity::getGrade, grade);
            if (singleGradeRelease(release, grade)) {
                q.or().isNull(KgNodeRevisionEntity::getGrade);
            }
        });
        return w;
    }

    static boolean singleGradeRelease(KgGraphReleaseEntity release, int grade) {
        return release != null
                && release.getGradeMin() != null
                && release.getGradeMax() != null
                && release.getGradeMin().equals(release.getGradeMax())
                && release.getGradeMin() == grade;
    }

    private KgGraphReleaseEntity requireDraft(Long releaseId) {
        KgGraphReleaseEntity release = kgGraphReleaseDao.selectById(releaseId);
        if (release == null) {
            throw new RenException("图谱版本不存在");
        }
        if (!KgGraphReleaseEntity.STATUS_DRAFT.equals(release.getStatus())) {
            throw new RenException("仅 draft 版本可编辑");
        }
        return release;
    }

    private Long nodeIdByCode(String code) {
        KgNodeEntity n = kgNodeDao.selectOne(
                new LambdaQueryWrapper<KgNodeEntity>().eq(KgNodeEntity::getCode, code));
        return n != null ? n.getId() : null;
    }

    private void detectCycle(Long releaseId) {
        for (String type : CLOSURE_EDGE_TYPES) {
            List<KgEdgeEntity> edges = kgEdgeDao.selectList(
                    new LambdaQueryWrapper<KgEdgeEntity>()
                            .eq(KgEdgeEntity::getGraphReleaseId, releaseId)
                            .eq(KgEdgeEntity::getEdgeType, type));
            Map<Long, List<Long>> adj = new HashMap<>();
            for (KgEdgeEntity e : edges) {
                adj.computeIfAbsent(e.getFromNodeId(), k -> new ArrayList<>()).add(e.getToNodeId());
            }
            Set<Long> visited = new HashSet<>();
            Set<Long> stack = new HashSet<>();
            for (Long node : adj.keySet()) {
                if (dfsCycle(node, adj, visited, stack)) {
                    throw new RenException("检测到循环关系: " + type);
                }
            }
        }
    }

    private boolean dfsCycle(Long u, Map<Long, List<Long>> adj, Set<Long> visited, Set<Long> stack) {
        if (stack.contains(u)) {
            return true;
        }
        if (visited.contains(u)) {
            return false;
        }
        visited.add(u);
        stack.add(u);
        for (Long v : adj.getOrDefault(u, List.of())) {
            if (dfsCycle(v, adj, visited, stack)) {
                return true;
            }
        }
        stack.remove(u);
        return false;
    }

    private void rebuildClosure(Long releaseId) {
        kgClosureDao.delete(new LambdaQueryWrapper<KgClosureEntity>()
                .eq(KgClosureEntity::getGraphReleaseId, releaseId));
        for (String type : CLOSURE_EDGE_TYPES) {
            List<KgEdgeEntity> edges = kgEdgeDao.selectList(
                    new LambdaQueryWrapper<KgEdgeEntity>()
                            .eq(KgEdgeEntity::getGraphReleaseId, releaseId)
                            .eq(KgEdgeEntity::getEdgeType, type));
            Map<Long, List<Long>> direct = new HashMap<>();
            for (KgEdgeEntity e : edges) {
                direct.computeIfAbsent(e.getFromNodeId(), k -> new ArrayList<>()).add(e.getToNodeId());
            }
            for (Long start : direct.keySet()) {
                Map<Long, Integer> depths = new HashMap<>();
                Queue<long[]> q = new ArrayDeque<>();
                for (Long d : direct.get(start)) {
                    q.add(new long[] { d, 1L });
                }
                while (!q.isEmpty()) {
                    long[] cur = q.poll();
                    long node = cur[0];
                    int depth = (int) cur[1];
                    if (depth > 12) {
                        continue;
                    }
                    Integer prev = depths.get(node);
                    if (prev != null && prev <= depth) {
                        continue;
                    }
                    depths.put(node, depth);
                    for (Long next : direct.getOrDefault(node, List.of())) {
                        q.add(new long[] { next, depth + 1L });
                    }
                }
                for (Map.Entry<Long, Integer> en : depths.entrySet()) {
                    KgClosureEntity c = new KgClosureEntity();
                    c.setGraphReleaseId(releaseId);
                    c.setRelationType(type);
                    c.setAncestorNodeId(start);
                    c.setDescendantNodeId(en.getKey());
                    c.setMinDepth(en.getValue());
                    kgClosureDao.insert(c);
                }
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long publishFromTeaching(TeachingKgPublishDTO dto) {
        if (dto == null || dto.getNodes() == null || dto.getNodes().isEmpty()) {
            throw new RenException("图谱节点为空");
        }
        int grade = dto.getGrade();
        if (grade < 1 || grade > 12) {
            throw new RenException("年级无效");
        }
        String subject = LearningKgGraphMatchUtil.normalizeSubject(dto.getSubject());
        String province = xiaozhi.modules.learning.util.LearningProfileConstants.normalizeProvince(
                dto.getProvinceCode());
        String textbook = xiaozhi.modules.learning.util.LearningProfileConstants.normalizeTextbook(
                dto.getTextbookEdition());
        String versionLabel = StringUtils.defaultIfBlank(dto.getVersionLabel(),
                "teaching-" + subject + "-G" + grade + "-" + System.currentTimeMillis());

        Date now = new Date();
        KgGraphReleaseEntity release = new KgGraphReleaseEntity();
        release.setVersionLabel(versionLabel.trim());
        release.setStatus(KgGraphReleaseEntity.STATUS_DRAFT);
        release.setSubject(subject);
        release.setProvinceCode(province);
        release.setTextbookEdition(textbook);
        release.setGradeMin(grade);
        release.setGradeMax(grade);
        release.setCreateTime(now);
        release.setUpdateTime(now);
        kgGraphReleaseDao.insert(release);
        Long releaseId = release.getId();

        for (TeachingKgPublishDTO.TeachingKgNodeDTO n : dto.getNodes()) {
            if (n == null || StringUtils.isAnyBlank(n.getCode(), n.getName())) {
                continue;
            }
            KgNodeEntity node = kgNodeDao.selectOne(
                    new LambdaQueryWrapper<KgNodeEntity>().eq(KgNodeEntity::getCode, n.getCode().trim()));
            String nodeType = normalizeTeachingNodeType(n.getNodeType());
            if (node == null) {
                node = new KgNodeEntity();
                node.setCode(n.getCode().trim());
                node.setNodeType(nodeType);
                node.setCreateTime(now);
                kgNodeDao.insert(node);
            } else if (!nodeType.equalsIgnoreCase(node.getNodeType())) {
                node.setNodeType(nodeType);
                kgNodeDao.updateById(node);
            }
            KgNodeRevisionEntity rev = new KgNodeRevisionEntity();
            rev.setGraphReleaseId(releaseId);
            rev.setNodeId(node.getId());
            rev.setName(n.getName().trim());
            rev.setDescription(StringUtils.defaultString(n.getDescription()));
            rev.setGrade(resolveRevisionGrade(n.getGrade(), grade));
            Map<String, Object> props = new HashMap<>();
            if (StringUtils.isNotBlank(n.getModuleCode())) {
                props.put("module_code", n.getModuleCode().trim());
            }
            if (StringUtils.isNotBlank(n.getModuleName())) {
                props.put("module_name", n.getModuleName().trim());
            }
            if (n.getModuleSortOrder() != null) {
                props.put("module_sort_order", n.getModuleSortOrder());
            }
            if (StringUtils.isNotBlank(n.getTeachingContent())) {
                props.put("teaching_content", n.getTeachingContent());
            }
            if (n.getMasteryRubric() != null && !n.getMasteryRubric().isEmpty()) {
                props.put("mastery_rubric", n.getMasteryRubric());
            }
            if (!props.isEmpty()) {
                rev.setProperties(JSONUtil.toJsonStr(props));
            }
            kgNodeRevisionDao.insert(rev);
        }

        if (dto.getEdges() != null) {
            for (TeachingKgPublishDTO.TeachingKgEdgeDTO e : dto.getEdges()) {
                if (e == null || StringUtils.isAnyBlank(e.getFromCode(), e.getToCode(), e.getEdgeType())) {
                    continue;
                }
                Long fromId = nodeIdByCode(e.getFromCode().trim());
                Long toId = nodeIdByCode(e.getToCode().trim());
                if (fromId == null || toId == null) {
                    throw new RenException("边引用了未知节点: " + e.getFromCode() + " -> " + e.getToCode());
                }
                KgEdgeEntity edge = new KgEdgeEntity();
                edge.setGraphReleaseId(releaseId);
                edge.setFromNodeId(fromId);
                edge.setToNodeId(toId);
                edge.setEdgeType(e.getEdgeType().trim());
                edge.setRequired(true);
                kgEdgeDao.insert(edge);
            }
        }

        if (countSkillNodesAtGrade(releaseId, grade) == 0) {
            throw new RenException(
                    "发布失败："
                            + grade
                            + " 年级无 SKILL 知识点（请检查教研节点 nodeType 是否为 SKILL/知识点，且 grade 正确）");
        }
        publishRelease(releaseId);
        return releaseId;
    }

    private static int resolveRevisionGrade(Integer nodeGrade, int releaseGrade) {
        if (nodeGrade == null || nodeGrade <= 0) {
            return releaseGrade;
        }
        return nodeGrade;
    }

    /** 教研侧常见别名 → 运行时 SKILL / MISCONCEPTION 等 */
    private static String normalizeTeachingNodeType(String raw) {
        if (StringUtils.isBlank(raw)) {
            return "SKILL";
        }
        String t = raw.trim();
        if ("知识点".equals(t) || "知识节点".equals(t)) {
            return "SKILL";
        }
        String upper = t.toUpperCase(java.util.Locale.ROOT);
        return switch (upper) {
            case "SKILL", "KNOWLEDGE", "KNOWLEDGE_POINT", "KP", "LEAF" -> "SKILL";
            case "MISCONCEPTION", "MIS" -> "MISCONCEPTION";
            default -> upper;
        };
    }
}
