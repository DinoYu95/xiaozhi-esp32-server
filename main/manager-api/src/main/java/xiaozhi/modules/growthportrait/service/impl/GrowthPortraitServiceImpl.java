package xiaozhi.modules.growthportrait.service.impl;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.growthportrait.dao.GpParentNotificationDao;
import xiaozhi.modules.growthportrait.dao.GpParentSettingsDao;
import xiaozhi.modules.growthportrait.dao.GpTemplateEdgeDao;
import xiaozhi.modules.growthportrait.dao.GpTemplateNodeDao;
import xiaozhi.modules.growthportrait.dao.GpTemplateReleaseDao;
import xiaozhi.modules.growthportrait.dao.LearnerGrowthEvidenceDao;
import xiaozhi.modules.growthportrait.dao.LearnerGrowthStateDao;
import xiaozhi.modules.growthportrait.dto.GrowthEvidenceIngestDTO;
import xiaozhi.modules.growthportrait.dto.GrowthEvidenceSessionDTO;
import xiaozhi.modules.growthportrait.dto.TeachingGpPublishDTO;
import xiaozhi.modules.growthportrait.entity.GpParentNotificationEntity;
import xiaozhi.modules.growthportrait.entity.GpParentSettingsEntity;
import xiaozhi.modules.growthportrait.entity.GpTemplateEdgeEntity;
import xiaozhi.modules.growthportrait.entity.GpTemplateNodeEntity;
import xiaozhi.modules.growthportrait.entity.GpTemplateReleaseEntity;
import xiaozhi.modules.growthportrait.entity.LearnerGrowthEvidenceEntity;
import xiaozhi.modules.growthportrait.entity.LearnerGrowthStateEntity;
import xiaozhi.modules.growthportrait.service.GrowthPortraitClassifyService;
import xiaozhi.modules.growthportrait.service.GrowthPortraitService;
import xiaozhi.modules.growthportrait.util.GrowthAgeBandUtil;
import xiaozhi.modules.growthportrait.util.GrowthStateEngine;
import xiaozhi.modules.growthportrait.vo.GrowthGraphVO;
import xiaozhi.modules.growthportrait.vo.GrowthLinkVO;
import xiaozhi.modules.growthportrait.vo.GrowthNodeVO;
import xiaozhi.modules.growthportrait.vo.GrowthNotificationPageVO;
import xiaozhi.modules.growthportrait.vo.GrowthWeeklyDigestVO;
import xiaozhi.modules.parent.dao.DeviceChildDao;
import xiaozhi.modules.parent.dao.ParentDeviceBindingDao;
import xiaozhi.modules.parent.entity.DeviceChildEntity;
import xiaozhi.modules.parent.entity.ParentDeviceBindingEntity;
import xiaozhi.modules.parent.util.ParentChildAccessHelper;

@Service
@RequiredArgsConstructor
public class GrowthPortraitServiceImpl implements GrowthPortraitService {

    private static final int DEFAULT_WEEKLY_INSTANT_CAP = 2;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final GpTemplateReleaseDao releaseDao;
    private final GpTemplateNodeDao nodeDao;
    private final GpTemplateEdgeDao edgeDao;
    private final LearnerGrowthEvidenceDao evidenceDao;
    private final LearnerGrowthStateDao stateDao;
    private final GpParentNotificationDao notificationDao;
    private final GpParentSettingsDao settingsDao;
    private final DeviceChildDao deviceChildDao;
    private final ParentDeviceBindingDao parentDeviceBindingDao;
    private final GrowthPortraitClassifyService classifyService;

    @Override
    @Transactional
    public Long publishFromTeaching(TeachingGpPublishDTO body) {
        if (body == null || body.getNodes() == null || body.getNodes().isEmpty()) {
            throw new RenException("nodes 不能为空");
        }
        String ageBand = normalizeAgeBand(body.getAgeBand());
        GpTemplateReleaseEntity release = new GpTemplateReleaseEntity();
        release.setAgeBand(ageBand);
        release.setVersionLabel(StringUtils.defaultIfBlank(body.getVersionLabel(), "gp-" + ageBand));
        release.setTeachingSubmissionId(body.getTeachingSubmissionId());
        release.setRulesJson(body.getRulesJson());
        release.setStatus(GpTemplateReleaseEntity.STATUS_DRAFT);
        release.setCreateTime(new Date());
        releaseDao.insert(release);
        Long releaseId = release.getId();

        for (TeachingGpPublishDTO.Node n : body.getNodes()) {
            GpTemplateNodeEntity en = new GpTemplateNodeEntity();
            en.setReleaseId(releaseId);
            en.setCode(n.getCode());
            en.setNodeType(n.getNodeType());
            en.setParentCode(n.getParentCode());
            en.setLabel(n.getLabel());
            en.setShortLabel(StringUtils.defaultIfBlank(n.getShortLabel(), n.getLabel()));
            en.setShortDesc(n.getShortDesc());
            en.setClusterCode(n.getClusterCode());
            en.setSortOrder(n.getSortOrder() != null ? n.getSortOrder() : 0);
            en.setRequiredEvidence(n.getRequiredEvidence() != null ? n.getRequiredEvidence() : 3);
            en.setVisibleThreshold(n.getVisibleThreshold() != null ? n.getVisibleThreshold() : 52);
            en.setStrongThreshold(n.getStrongThreshold() != null ? n.getStrongThreshold() : 72);
            if (n.getMatchHints() != null && !n.getMatchHints().isEmpty()) {
                try {
                    en.setMatchHints(JSON.writeValueAsString(n.getMatchHints()));
                } catch (Exception ignored) {
                    en.setMatchHints("[]");
                }
            }
            en.setPropertiesJson(n.getPropertiesJson());
            nodeDao.insert(en);
        }
        if (body.getEdges() != null) {
            for (TeachingGpPublishDTO.Edge e : body.getEdges()) {
                GpTemplateEdgeEntity ee = new GpTemplateEdgeEntity();
                ee.setReleaseId(releaseId);
                ee.setFromCode(e.getFromCode());
                ee.setToCode(e.getToCode());
                ee.setEdgeType(StringUtils.defaultIfBlank(e.getEdgeType(), "CONTAINS"));
                edgeDao.insert(ee);
            }
        }
        publishRelease(releaseId);
        return releaseId;
    }

    private void publishRelease(Long releaseId) {
        GpTemplateReleaseEntity release = releaseDao.selectById(releaseId);
        if (release == null) {
            throw new RenException("release 不存在");
        }
        List<GpTemplateReleaseEntity> old = releaseDao.selectList(
                new LambdaQueryWrapper<GpTemplateReleaseEntity>()
                        .eq(GpTemplateReleaseEntity::getAgeBand, release.getAgeBand())
                        .eq(GpTemplateReleaseEntity::getStatus, GpTemplateReleaseEntity.STATUS_PUBLISHED));
        Date now = new Date();
        for (GpTemplateReleaseEntity o : old) {
            o.setStatus(GpTemplateReleaseEntity.STATUS_ARCHIVED);
            releaseDao.updateById(o);
        }
        release.setStatus(GpTemplateReleaseEntity.STATUS_PUBLISHED);
        release.setPublishedAt(now);
        releaseDao.updateById(release);
    }

    @Override
    public GrowthGraphVO getGraph(Long parentUserId, Long childId) {
        ParentChildAccessHelper.ensureParentCanAccessChildById(
                deviceChildDao, parentDeviceBindingDao, parentUserId, childId);
        return getGraphByChildId(childId);
    }

    @Override
    public GrowthGraphVO getGraphByChildId(Long childId) {
        DeviceChildEntity child = ParentChildAccessHelper.requireChild(deviceChildDao, childId);
        String ageBand = GrowthAgeBandUtil.resolveAgeBand(child);
        GpTemplateReleaseEntity release = findPublishedRelease(ageBand);
        if (release == null) {
            throw new RenException("暂无已发布的成长星图模板：" + ageBand
                    + "（孩子档案解析结果；已发布 preschool 不等于所有孩子都能用，请发版对应年龄段或核对生日/年级/年龄段）");
        }
        List<GpTemplateNodeEntity> templateNodes = nodeDao.selectList(
                new LambdaQueryWrapper<GpTemplateNodeEntity>()
                        .eq(GpTemplateNodeEntity::getReleaseId, release.getId())
                        .orderByAsc(GpTemplateNodeEntity::getSortOrder));
        List<GpTemplateEdgeEntity> templateEdges = edgeDao.selectList(
                new LambdaQueryWrapper<GpTemplateEdgeEntity>()
                        .eq(GpTemplateEdgeEntity::getReleaseId, release.getId()));
        Map<String, LearnerGrowthStateEntity> stateByCode = loadStates(childId, release.getId());
        recomputeAllStates(childId, release.getId(), templateNodes, stateByCode);

        GrowthGraphVO vo = new GrowthGraphVO();
        vo.setReleaseId(release.getId());
        vo.setAgeBand(ageBand);
        GrowthGraphVO.CenterNode center = new GrowthGraphVO.CenterNode();
        center.setLabel(StringUtils.defaultIfBlank(child.getName(), "孩子"));
        center.setShortDesc("成长枢纽");
        center.setAvatarUrl(child.getAvatarUrl());
        vo.setCenter(center);

        Map<String, GpTemplateNodeEntity> byCode = templateNodes.stream()
                .collect(Collectors.toMap(GpTemplateNodeEntity::getCode, n -> n, (a, b) -> a));
        List<GrowthNodeVO> nodes = new ArrayList<>();
        GrowthNodeVO centerNode = buildCenterNode(center);
        nodes.add(centerNode);
        int strongCount = 0;
        for (GpTemplateNodeEntity tn : templateNodes) {
            LearnerGrowthStateEntity st = stateByCode.get(tn.getCode());
            GrowthNodeVO n = toNodeVo(tn, st, byCode);
            nodes.add(n);
            if ("strong".equals(n.getState())) {
                strongCount++;
            }
        }
        applySimpleLayout(nodes);
        vo.setNodes(nodes);
        vo.setStrongCount(strongCount);
        List<GrowthLinkVO> links = new ArrayList<>();
        for (GpTemplateNodeEntity tn : templateNodes) {
            if ("hub".equals(tn.getNodeType())) {
                GrowthLinkVO cl = new GrowthLinkVO();
                cl.setSource("center");
                cl.setTarget(tn.getCode());
                LearnerGrowthStateEntity hs = stateByCode.get(tn.getCode());
                cl.setStrength(hs != null ? hs.getStrength() : 30);
                links.add(cl);
            }
        }
        for (GpTemplateEdgeEntity e : templateEdges) {
            GrowthLinkVO l = new GrowthLinkVO();
            l.setSource(e.getFromCode());
            l.setTarget(e.getToCode());
            LearnerGrowthStateEntity tgt = stateByCode.get(e.getToCode());
            l.setStrength(tgt != null ? tgt.getStrength() : 0);
            links.add(l);
        }
        vo.setLinks(links);
        vo.setRules(parseRules(release.getRulesJson(), ageBand));
        return vo;
    }

    @Override
    @Transactional
    public void ingestEvidence(GrowthEvidenceIngestDTO body) {
        if (body == null || body.getChildId() == null) {
            throw new RenException("childId 必填");
        }
        DeviceChildEntity child = ParentChildAccessHelper.requireChild(deviceChildDao, body.getChildId());
        String ageBand = GrowthAgeBandUtil.resolveAgeBand(child);
        GpTemplateReleaseEntity release = findPublishedRelease(ageBand);
        if (release == null) {
            return;
        }
        List<GpTemplateNodeEntity> signals = nodeDao.selectList(
                new LambdaQueryWrapper<GpTemplateNodeEntity>()
                        .eq(GpTemplateNodeEntity::getReleaseId, release.getId())
                        .eq(GpTemplateNodeEntity::getNodeType, "signal"));
        String matchedCode = StringUtils.trimToNull(body.getNodeCode());
        if (matchedCode == null) {
            matchedCode = matchSignalCode(signals, body.getText());
        }
        if (matchedCode == null) {
            return;
        }
        final String nodeCode = matchedCode;
        GpTemplateNodeEntity signalNode = signals.stream()
                .filter(s -> nodeCode.equals(s.getCode())).findFirst().orElse(null);
        if (signalNode == null) {
            return;
        }
        int confidence = body.getConfidence() != null ? body.getConfidence() : 75;
        LearnerGrowthEvidenceEntity ev = new LearnerGrowthEvidenceEntity();
        ev.setChildId(body.getChildId());
        ev.setReleaseId(release.getId());
        ev.setNodeCode(nodeCode);
        ev.setSourceType(StringUtils.defaultIfBlank(body.getSourceType(), "conversation"));
        ev.setSourceRef(body.getSourceRef());
        ev.setConfidence(Math.max(1, Math.min(100, confidence)));
        ev.setSnippet(StringUtils.left(body.getText(), 500));
        ev.setCreateTime(new Date());
        evidenceDao.insert(ev);

        List<GpTemplateNodeEntity> allNodes = nodeDao.selectList(
                new LambdaQueryWrapper<GpTemplateNodeEntity>()
                        .eq(GpTemplateNodeEntity::getReleaseId, release.getId()));
        Map<String, LearnerGrowthStateEntity> stateByCode = loadStates(body.getChildId(), release.getId());
        String prevSignalState = stateByCode.containsKey(nodeCode)
                ? stateByCode.get(nodeCode).getState() : "locked";
        recomputeAllStates(body.getChildId(), release.getId(), allNodes, stateByCode);
        LearnerGrowthStateEntity after = stateByCode.get(nodeCode);
        if (after != null && "strong".equals(after.getState()) && !"strong".equals(prevSignalState)) {
            maybeNotifyInstant(body.getChildId(), after, signalNode);
        }
    }

    @Override
    public void ingestSession(GrowthEvidenceSessionDTO body) {
        if (body == null || body.getChildId() == null) {
            throw new RenException("childId 必填");
        }
        DeviceChildEntity child = ParentChildAccessHelper.requireChild(deviceChildDao, body.getChildId());
        String ageBand = GrowthAgeBandUtil.resolveAgeBand(child);
        GpTemplateReleaseEntity release = findPublishedRelease(ageBand);
        if (release == null) {
            return;
        }
        String transcript = buildSessionTranscript(body);
        if (StringUtils.isBlank(transcript)) {
            return;
        }
        List<GpTemplateNodeEntity> allNodes = nodeDao.selectList(
                new LambdaQueryWrapper<GpTemplateNodeEntity>()
                        .eq(GpTemplateNodeEntity::getReleaseId, release.getId()));
        List<GrowthPortraitClassifyService.ClassifiedSignal> hits =
                classifyService.classify(transcript, allNodes);
        if (hits.isEmpty()) {
            return;
        }
        String sourceRef = StringUtils.defaultIfBlank(body.getSourceRef(), "session");
        for (GrowthPortraitClassifyService.ClassifiedSignal hit : hits) {
            GrowthEvidenceIngestDTO ev = new GrowthEvidenceIngestDTO();
            ev.setChildId(body.getChildId());
            ev.setSourceType(StringUtils.defaultIfBlank(body.getSourceType(), "conversation_session"));
            ev.setSourceRef(sourceRef);
            ev.setNodeCode(hit.getSignalCode());
            ev.setText(StringUtils.defaultIfBlank(hit.getSnippet(), transcript));
            ev.setConfidence(hit.getConfidence());
            ingestEvidence(ev);
        }
    }

    private String buildSessionTranscript(GrowthEvidenceSessionDTO body) {
        if (StringUtils.isNotBlank(body.getTranscript())) {
            return body.getTranscript().trim();
        }
        if (body.getTurns() == null || body.getTurns().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (GrowthEvidenceSessionDTO.Turn t : body.getTurns()) {
            if (t == null || StringUtils.isBlank(t.getText())) {
                continue;
            }
            String role = StringUtils.defaultIfBlank(t.getRole(), "user").trim().toLowerCase();
            String label = "assistant".equals(role) ? "机器人" : "孩子";
            sb.append(label).append("：").append(t.getText().trim()).append('\n');
        }
        return sb.toString().trim();
    }

    @Override
    public GrowthNotificationPageVO listNotifications(Long parentUserId, Long childId, int page, int pageSize) {
        ParentChildAccessHelper.ensureParentCanAccessChildById(
                deviceChildDao, parentDeviceBindingDao, parentUserId, childId);
        int p = Math.max(1, page);
        int ps = Math.min(50, Math.max(1, pageSize));
        List<GpParentNotificationEntity> rows = notificationDao.selectList(
                new LambdaQueryWrapper<GpParentNotificationEntity>()
                        .eq(GpParentNotificationEntity::getParentUserId, parentUserId)
                        .eq(GpParentNotificationEntity::getChildId, childId)
                        .orderByDesc(GpParentNotificationEntity::getCreateTime)
                        .last("LIMIT " + ((p - 1) * ps) + "," + ps));
        long unread = notificationDao.selectCount(
                new LambdaQueryWrapper<GpParentNotificationEntity>()
                        .eq(GpParentNotificationEntity::getParentUserId, parentUserId)
                        .eq(GpParentNotificationEntity::getChildId, childId)
                        .eq(GpParentNotificationEntity::getIsRead, 0));
        GrowthNotificationPageVO vo = new GrowthNotificationPageVO();
        vo.setUnreadCount((int) unread);
        vo.setItems(rows.stream().map(r -> {
            GrowthNotificationPageVO.Item item = new GrowthNotificationPageVO.Item();
            item.setId(r.getId());
            item.setNotifyType(r.getNotifyType());
            item.setTitle(r.getTitle());
            item.setSummary(r.getSummary());
            item.setNodeCode(r.getNodeCode());
            item.setIsRead(r.getIsRead());
            item.setCreateTime(r.getCreateTime());
            return item;
        }).toList());
        return vo;
    }

    @Override
    @Transactional
    public void markNotificationRead(Long parentUserId, Long notificationId) {
        GpParentNotificationEntity n = notificationDao.selectById(notificationId);
        if (n == null || !Objects.equals(n.getParentUserId(), parentUserId)) {
            throw new RenException("通知不存在");
        }
        n.setIsRead(1);
        notificationDao.updateById(n);
    }

    @Override
    public GrowthWeeklyDigestVO weeklyDigest(Long parentUserId, Long childId, String weekStart) {
        ParentChildAccessHelper.ensureParentCanAccessChildById(
                deviceChildDao, parentDeviceBindingDao, parentUserId, childId);
        LocalDate start = weekStart != null
                ? LocalDate.parse(weekStart, DateTimeFormatter.ISO_LOCAL_DATE)
                : LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = start.plusDays(6);
        Date from = Date.from(start.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date to = Date.from(end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

        DeviceChildEntity child = ParentChildAccessHelper.requireChild(deviceChildDao, childId);
        GpTemplateReleaseEntity release = findPublishedRelease(GrowthAgeBandUtil.resolveAgeBand(child));
        GrowthWeeklyDigestVO vo = new GrowthWeeklyDigestVO();
        vo.setWeekStart(start.toString());
        vo.setWeekEnd(end.toString());
        if (release == null) {
            vo.setTopHighlights(List.of());
            vo.setParentTip("成长星图模板尚未发布，敬请期待。");
            vo.setNewStrongCount(0);
            return vo;
        }
        List<LearnerGrowthStateEntity> states = stateDao.selectList(
                new LambdaQueryWrapper<LearnerGrowthStateEntity>()
                        .eq(LearnerGrowthStateEntity::getChildId, childId)
                        .eq(LearnerGrowthStateEntity::getReleaseId, release.getId())
                        .eq(LearnerGrowthStateEntity::getState, "strong"));
        Map<String, GpTemplateNodeEntity> nodes = nodeDao.selectList(
                new LambdaQueryWrapper<GpTemplateNodeEntity>()
                        .eq(GpTemplateNodeEntity::getReleaseId, release.getId()))
                .stream().collect(Collectors.toMap(GpTemplateNodeEntity::getCode, n -> n, (a, b) -> a));

        int newStrong = (int) states.stream()
                .filter(s -> s.getFirstStrongAt() != null && !s.getFirstStrongAt().before(from) && s.getFirstStrongAt().before(to))
                .count();
        vo.setNewStrongCount(newStrong);
        List<GrowthWeeklyDigestVO.Highlight> top = states.stream()
                .sorted(Comparator.comparingInt(LearnerGrowthStateEntity::getStrength).reversed())
                .limit(3)
                .map(s -> {
                    GpTemplateNodeEntity tn = nodes.get(s.getNodeCode());
                    GrowthWeeklyDigestVO.Highlight h = new GrowthWeeklyDigestVO.Highlight();
                    h.setNodeCode(s.getNodeCode());
                    h.setLabel(tn != null ? tn.getLabel() : s.getNodeCode());
                    h.setShortDesc(tn != null ? tn.getShortDesc() : "");
                    h.setStrength(s.getStrength());
                    return h;
                }).toList();
        vo.setTopHighlights(top);
        vo.setParentTip(top.isEmpty()
                ? "本周继续观察孩子的自然表现，亮点会在证据积累后慢慢显现。"
                : "本周亮点「" + top.get(0).getLabel() + "」值得肯定，可以围绕它安排一次轻量亲子活动。");
        return vo;
    }

    private void recomputeAllStates(
            Long childId,
            Long releaseId,
            List<GpTemplateNodeEntity> templateNodes,
            Map<String, LearnerGrowthStateEntity> stateByCode) {
        Map<String, List<String>> childrenOf = new HashMap<>();
        for (GpTemplateNodeEntity n : templateNodes) {
            if (n.getParentCode() != null) {
                childrenOf.computeIfAbsent(n.getParentCode(), k -> new ArrayList<>()).add(n.getCode());
            }
        }
        List<GpTemplateNodeEntity> signals = templateNodes.stream()
                .filter(n -> "signal".equals(n.getNodeType())).toList();
        for (GpTemplateNodeEntity sig : signals) {
            upsertNodeState(childId, releaseId, sig, stateByCode);
        }
        List<GpTemplateNodeEntity> subs = templateNodes.stream()
                .filter(n -> "sub".equals(n.getNodeType())).toList();
        for (GpTemplateNodeEntity sub : subs) {
            rollupNode(childId, releaseId, sub, childrenOf, stateByCode);
        }
        List<GpTemplateNodeEntity> hubs = templateNodes.stream()
                .filter(n -> "hub".equals(n.getNodeType())).toList();
        for (GpTemplateNodeEntity hub : hubs) {
            rollupNode(childId, releaseId, hub, childrenOf, stateByCode);
        }
        GrowthStateEngine.applyVisualHierarchy(templateNodes, stateByCode);
        for (LearnerGrowthStateEntity st : stateByCode.values()) {
            if (st.getId() == null) {
                stateDao.insert(st);
            } else {
                stateDao.updateById(st);
            }
        }
    }

    private void rollupNode(
            Long childId,
            Long releaseId,
            GpTemplateNodeEntity node,
            Map<String, List<String>> childrenOf,
            Map<String, LearnerGrowthStateEntity> stateByCode) {
        List<String> childCodes = childrenOf.getOrDefault(node.getCode(), List.of());
        int evidenceCount = 0;
        int strengthSum = 0;
        for (String cc : childCodes) {
            LearnerGrowthStateEntity cs = stateByCode.get(cc);
            if (cs != null) {
                evidenceCount += cs.getEvidenceCount() != null ? cs.getEvidenceCount() : 0;
                strengthSum += cs.getStrength() != null ? cs.getStrength() : 0;
            }
        }
        int avgConf = 70;
        int strength = childCodes.isEmpty() ? 0
                : Math.min(100, strengthSum / Math.max(1, childCodes.size()));
        if (!childCodes.isEmpty() && evidenceCount > 0) {
            strength = Math.max(strength, GrowthStateEngine.computeStrength(
                    Math.max(1, evidenceCount / Math.max(1, childCodes.size())), avgConf));
        }
        LearnerGrowthStateEntity st = stateByCode.computeIfAbsent(node.getCode(), k -> newState(childId, releaseId, k));
        st.setEvidenceCount(evidenceCount);
        st.setStrength(strength);
        int required = node.getRequiredEvidence() != null ? node.getRequiredEvidence() : defaultRequired(node.getNodeType());
        String newState = "hub".equals(node.getNodeType())
                ? GrowthStateEngine.rollupHubState(childCodes, stateByCode, evidenceCount, required)
                : GrowthStateEngine.lightState(
                        strength, Math.max(evidenceCount, 0), required,
                        node.getStrongThreshold() != null ? node.getStrongThreshold() : 72);
        updateStateTransition(st, newState);
        st.setUpdateTime(new Date());
    }

    private void upsertNodeState(
            Long childId,
            Long releaseId,
            GpTemplateNodeEntity node,
            Map<String, LearnerGrowthStateEntity> stateByCode) {
        List<LearnerGrowthEvidenceEntity> evs = evidenceDao.selectList(
                new LambdaQueryWrapper<LearnerGrowthEvidenceEntity>()
                        .eq(LearnerGrowthEvidenceEntity::getChildId, childId)
                        .eq(LearnerGrowthEvidenceEntity::getReleaseId, releaseId)
                        .eq(LearnerGrowthEvidenceEntity::getNodeCode, node.getCode()));
        int count = evs.size();
        int avgConf = count == 0 ? 0
                : (int) Math.round(evs.stream().mapToInt(LearnerGrowthEvidenceEntity::getConfidence).average().orElse(0));
        int strength = GrowthStateEngine.computeStrength(count, avgConf);
        LearnerGrowthStateEntity st = stateByCode.computeIfAbsent(node.getCode(), k -> newState(childId, releaseId, k));
        st.setEvidenceCount(count);
        st.setStrength(strength);
        int required = node.getRequiredEvidence() != null ? node.getRequiredEvidence() : defaultRequired(node.getNodeType());
        String newState = GrowthStateEngine.lightState(
                strength, count, required,
                node.getStrongThreshold() != null ? node.getStrongThreshold() : 72);
        updateStateTransition(st, newState);
        st.setUpdateTime(new Date());
    }

    private static int defaultRequired(String nodeType) {
        return switch (String.valueOf(nodeType)) {
            case "hub" -> 6;
            case "sub" -> 5;
            default -> 3;
        };
    }

    private LearnerGrowthStateEntity newState(Long childId, Long releaseId, String code) {
        LearnerGrowthStateEntity st = new LearnerGrowthStateEntity();
        st.setChildId(childId);
        st.setReleaseId(releaseId);
        st.setNodeCode(code);
        st.setEvidenceCount(0);
        st.setStrength(0);
        st.setState("locked");
        st.setVisualIntensity(0.0);
        st.setVisualTier("none");
        st.setUpdateTime(new Date());
        return st;
    }

    private void updateStateTransition(LearnerGrowthStateEntity st, String newState) {
        String prev = st.getState();
        st.setState(newState);
        if ("strong".equals(newState) && st.getFirstStrongAt() == null) {
            st.setFirstStrongAt(new Date());
        }
        if (!"strong".equals(newState) && !"strong".equals(prev)) {
            // keep firstStrongAt
        }
    }

    private Map<String, LearnerGrowthStateEntity> loadStates(Long childId, Long releaseId) {
        List<LearnerGrowthStateEntity> list = stateDao.selectList(
                new LambdaQueryWrapper<LearnerGrowthStateEntity>()
                        .eq(LearnerGrowthStateEntity::getChildId, childId)
                        .eq(LearnerGrowthStateEntity::getReleaseId, releaseId));
        Map<String, LearnerGrowthStateEntity> map = new HashMap<>();
        list.forEach(s -> map.put(s.getNodeCode(), s));
        return map;
    }

    private GpTemplateReleaseEntity findPublishedRelease(String ageBand) {
        return releaseDao.selectOne(
                new LambdaQueryWrapper<GpTemplateReleaseEntity>()
                        .eq(GpTemplateReleaseEntity::getAgeBand, ageBand)
                        .eq(GpTemplateReleaseEntity::getStatus, GpTemplateReleaseEntity.STATUS_PUBLISHED)
                        .orderByDesc(GpTemplateReleaseEntity::getPublishedAt)
                        .last("LIMIT 1"));
    }

    private String matchSignalCode(List<GpTemplateNodeEntity> signals, String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        String t = text.toLowerCase();
        GpTemplateNodeEntity best = null;
        int bestScore = 0;
        for (GpTemplateNodeEntity s : signals) {
            int score = 0;
            if (StringUtils.contains(t, StringUtils.defaultString(s.getLabel()).toLowerCase())) {
                score += 3;
            }
            if (StringUtils.contains(t, StringUtils.defaultString(s.getShortLabel()).toLowerCase())) {
                score += 2;
            }
            List<String> hints = parseHints(s.getMatchHints());
            for (String h : hints) {
                if (StringUtils.isNotBlank(h) && t.contains(h.toLowerCase())) {
                    score += 2;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return bestScore >= 2 && best != null ? best.getCode() : null;
    }

    private List<String> parseHints(String json) {
        if (StringUtils.isBlank(json)) {
            return List.of();
        }
        try {
            return JSON.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private void maybeNotifyInstant(Long childId, LearnerGrowthStateEntity state, GpTemplateNodeEntity node) {
        List<Long> parentIds = findParentUserIdsForChild(childId);
        if (parentIds.isEmpty()) {
            return;
        }
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Date from = Date.from(weekStart.atStartOfDay(ZoneId.systemDefault()).toInstant());
        for (Long parentUserId : parentIds) {
            if (!isInstantEnabled(parentUserId, childId)) {
                continue;
            }
            long sentThisWeek = notificationDao.selectCount(
                    new LambdaQueryWrapper<GpParentNotificationEntity>()
                            .eq(GpParentNotificationEntity::getParentUserId, parentUserId)
                            .eq(GpParentNotificationEntity::getChildId, childId)
                            .eq(GpParentNotificationEntity::getNotifyType, "instant")
                            .ge(GpParentNotificationEntity::getCreateTime, from));
            if (sentThisWeek >= DEFAULT_WEEKLY_INSTANT_CAP) {
                continue;
            }
            long dup = notificationDao.selectCount(
                    new LambdaQueryWrapper<GpParentNotificationEntity>()
                            .eq(GpParentNotificationEntity::getParentUserId, parentUserId)
                            .eq(GpParentNotificationEntity::getChildId, childId)
                            .eq(GpParentNotificationEntity::getNodeCode, state.getNodeCode())
                            .eq(GpParentNotificationEntity::getNotifyType, "instant"));
            if (dup > 0) {
                continue;
            }
            GpParentNotificationEntity n = new GpParentNotificationEntity();
            n.setParentUserId(parentUserId);
            n.setChildId(childId);
            n.setNodeCode(state.getNodeCode());
            n.setNotifyType("instant");
            n.setTitle("成长亮点 · " + node.getLabel());
            n.setSummary("孩子在「" + node.getLabel() + "」上出现了强烈亮点，"
                    + StringUtils.defaultString(node.getShortDesc()) + "。点击查看成长星图。");
            n.setIsRead(0);
            n.setCreateTime(new Date());
            notificationDao.insert(n);
        }
    }

    private boolean isInstantEnabled(Long parentUserId, Long childId) {
        GpParentSettingsEntity s = settingsDao.selectOne(
                new LambdaQueryWrapper<GpParentSettingsEntity>()
                        .eq(GpParentSettingsEntity::getParentUserId, parentUserId)
                        .eq(GpParentSettingsEntity::getChildId, childId));
        return s == null || s.getInstantNotifyEnabled() == null || s.getInstantNotifyEnabled() == 1;
    }

    private List<Long> findParentUserIdsForChild(Long childId) {
        DeviceChildEntity child = deviceChildDao.selectById(childId);
        if (child == null || StringUtils.isBlank(child.getDeviceId())) {
            return List.of();
        }
        List<ParentDeviceBindingEntity> bindings = parentDeviceBindingDao.selectList(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getDeviceId, child.getDeviceId()));
        return bindings.stream().map(ParentDeviceBindingEntity::getParentUserId).distinct().toList();
    }

    private GrowthNodeVO buildCenterNode(GrowthGraphVO.CenterNode center) {
        GrowthNodeVO n = new GrowthNodeVO();
        n.setId("center");
        n.setType("center");
        n.setLabel(center.getLabel());
        n.setShortLabel(center.getLabel());
        n.setShortDesc(center.getShortDesc());
        n.setCluster("center");
        n.setLevel(0);
        n.setStrength(0);
        n.setEvidenceCount(0);
        n.setRequiredCount(0);
        n.setState("visible");
        n.setVisualIntensity(0.5);
        n.setVisualTier("mid");
        n.setEvidence("");
        n.setSuggest("成长中心");
        n.setX(0.5);
        n.setY(0.5);
        return n;
    }

    /** 星图预布局（归一化 0~1），供小程序 canvas 直接绘制，不依赖 D3 力导向 */
    private void applySimpleLayout(List<GrowthNodeVO> nodes) {
        List<GrowthNodeVO> hubs = nodes.stream().filter(n -> "hub".equals(n.getType())).toList();
        int hc = Math.max(1, hubs.size());
        for (int i = 0; i < hc; i++) {
            double angle = (Math.PI * 2 * i) / hc - Math.PI / 2;
            hubs.get(i).setX(0.5 + Math.cos(angle) * 0.28);
            hubs.get(i).setY(0.5 + Math.sin(angle) * 0.28);
        }
        Map<String, GrowthNodeVO> byId = nodes.stream().collect(Collectors.toMap(GrowthNodeVO::getId, n -> n, (a, b) -> a));
        for (GrowthNodeVO sub : nodes.stream().filter(n -> "sub".equals(n.getType())).toList()) {
            GrowthNodeVO hub = sub.getParentHub() != null ? byId.get(sub.getParentHub()) : null;
            double bx = hub != null && hub.getX() != null ? hub.getX() : 0.5;
            double by = hub != null && hub.getY() != null ? hub.getY() : 0.5;
            int idx = Math.abs(sub.getId().hashCode() % 6);
            double a = (Math.PI * 2 * idx) / 6;
            sub.setX(bx + Math.cos(a) * 0.09);
            sub.setY(by + Math.sin(a) * 0.09);
        }
        for (GrowthNodeVO sig : nodes.stream().filter(n -> "signal".equals(n.getType())).toList()) {
            GrowthNodeVO sub = sig.getParentSub() != null ? byId.get(sig.getParentSub()) : null;
            double bx = sub != null && sub.getX() != null ? sub.getX() : 0.5;
            double by = sub != null && sub.getY() != null ? sub.getY() : 0.5;
            int idx = Math.abs(sig.getId().hashCode() % 4);
            double a = (Math.PI * 2 * idx) / 4;
            sig.setX(bx + Math.cos(a) * 0.045);
            sig.setY(by + Math.sin(a) * 0.045);
        }
    }

    private GrowthNodeVO toNodeVo(
            GpTemplateNodeEntity tn,
            LearnerGrowthStateEntity st,
            Map<String, GpTemplateNodeEntity> byCode) {
        GrowthNodeVO n = new GrowthNodeVO();
        n.setId(tn.getCode());
        n.setType(tn.getNodeType());
        n.setLabel(tn.getLabel());
        n.setShortLabel(tn.getShortLabel());
        n.setShortDesc(tn.getShortDesc());
        n.setCluster(tn.getClusterCode());
        n.setLevel(switch (tn.getNodeType()) {
            case "hub" -> 1;
            case "sub" -> 2;
            case "signal" -> 3;
            default -> 0;
        });
        n.setRequiredCount(tn.getRequiredEvidence() != null ? tn.getRequiredEvidence() : 3);
        if (st != null) {
            n.setStrength(st.getStrength() != null ? st.getStrength() : 0);
            n.setEvidenceCount(st.getEvidenceCount() != null ? st.getEvidenceCount() : 0);
            n.setState(st.getState());
            n.setVisualIntensity(st.getVisualIntensity() != null ? st.getVisualIntensity() : 0);
            n.setVisualTier(st.getVisualTier());
        } else {
            n.setStrength(0);
            n.setEvidenceCount(0);
            n.setState("locked");
            n.setVisualIntensity(0);
            n.setVisualTier("none");
        }
        if (tn.getParentCode() != null) {
            GpTemplateNodeEntity parent = byCode.get(tn.getParentCode());
            if (parent != null) {
                if ("sub".equals(tn.getNodeType())) {
                    n.setParentHub("hub".equals(parent.getNodeType()) ? parent.getCode() : null);
                    n.setParentSub(null);
                } else if ("signal".equals(tn.getNodeType())) {
                    n.setParentSub("sub".equals(parent.getNodeType()) ? parent.getCode() : null);
                    if (parent.getParentCode() != null) {
                        n.setParentHub(parent.getParentCode());
                    }
                }
            }
        }
        n.setEvidence("已收集 " + n.getEvidenceCount() + " 条观测证据");
        n.setSuggest(GrowthStateEngine.buildSuggest(n.getState(), n.getEvidenceCount(), n.getRequiredCount()));
        return n;
    }

    private GrowthGraphVO.GrowthRulesVO parseRules(String rulesJson, String ageBand) {
        GrowthGraphVO.GrowthRulesVO r = new GrowthGraphVO.GrowthRulesVO();
        r.setWeeklyInstantCap(DEFAULT_WEEKLY_INSTANT_CAP);
        r.setObserveDays(switch (ageBand) {
            case "preschool" -> 21;
            case "lower" -> 14;
            default -> 10;
        });
        if (StringUtils.isBlank(rulesJson)) {
            return r;
        }
        try {
            JsonNode root = JSON.readTree(rulesJson);
            if (root.has("notifyPolicy")) {
                JsonNode np = root.get("notifyPolicy");
                if (np.has("weeklyInstantCap")) {
                    r.setWeeklyInstantCap(np.get("weeklyInstantCap").asInt(DEFAULT_WEEKLY_INSTANT_CAP));
                }
            }
        } catch (Exception ignored) {
            // defaults
        }
        return r;
    }

    private static String normalizeAgeBand(String ageBand) {
        String b = StringUtils.trimToEmpty(ageBand).toLowerCase();
        return List.of("preschool", "lower", "upper", "middle").contains(b) ? b : "upper";
    }

    @Override
    @Transactional
    public void updateSettings(
            Long parentUserId, Long childId, Boolean instantNotifyEnabled, Boolean weeklyDigestEnabled) {
        ParentChildAccessHelper.ensureParentCanAccessChildById(
                deviceChildDao, parentDeviceBindingDao, parentUserId, childId);
        GpParentSettingsEntity s = settingsDao.selectOne(
                new LambdaQueryWrapper<GpParentSettingsEntity>()
                        .eq(GpParentSettingsEntity::getParentUserId, parentUserId)
                        .eq(GpParentSettingsEntity::getChildId, childId));
        if (s == null) {
            s = new GpParentSettingsEntity();
            s.setParentUserId(parentUserId);
            s.setChildId(childId);
            s.setInstantNotifyEnabled(1);
            s.setWeeklyDigestEnabled(1);
            s.setUpdateTime(new Date());
        }
        if (instantNotifyEnabled != null) {
            s.setInstantNotifyEnabled(instantNotifyEnabled ? 1 : 0);
        }
        if (weeklyDigestEnabled != null) {
            s.setWeeklyDigestEnabled(weeklyDigestEnabled ? 1 : 0);
        }
        s.setUpdateTime(new Date());
        if (s.getId() == null) {
            settingsDao.insert(s);
        } else {
            settingsDao.updateById(s);
        }
    }
}
