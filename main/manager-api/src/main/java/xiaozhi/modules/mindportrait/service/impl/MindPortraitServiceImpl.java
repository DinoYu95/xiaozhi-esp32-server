package xiaozhi.modules.mindportrait.service.impl;

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
import xiaozhi.modules.mindportrait.dao.MpParentNotificationDao;
import xiaozhi.modules.mindportrait.dao.MpParentSettingsDao;
import xiaozhi.modules.mindportrait.dao.MpTemplateEdgeDao;
import xiaozhi.modules.mindportrait.dao.MpTemplateNodeDao;
import xiaozhi.modules.mindportrait.dao.MpTemplateReleaseDao;
import xiaozhi.modules.mindportrait.dao.LearnerMindEvidenceDao;
import xiaozhi.modules.mindportrait.dao.LearnerMindStateDao;
import xiaozhi.modules.mindportrait.dto.MindEvidenceIngestDTO;
import xiaozhi.modules.mindportrait.dto.MindEvidenceSessionDTO;
import xiaozhi.modules.mindportrait.dto.TeachingMpPublishDTO;
import xiaozhi.modules.mindportrait.entity.MpParentNotificationEntity;
import xiaozhi.modules.mindportrait.entity.MpParentSettingsEntity;
import xiaozhi.modules.mindportrait.entity.MpTemplateEdgeEntity;
import xiaozhi.modules.mindportrait.entity.MpTemplateNodeEntity;
import xiaozhi.modules.mindportrait.entity.MpTemplateReleaseEntity;
import xiaozhi.modules.mindportrait.entity.LearnerMindEvidenceEntity;
import xiaozhi.modules.mindportrait.entity.LearnerMindStateEntity;
import xiaozhi.modules.mindportrait.service.MindPortraitClassifyService;
import xiaozhi.modules.mindportrait.service.MindPortraitService;
import xiaozhi.modules.growthportrait.util.GrowthAgeBandUtil;
import xiaozhi.modules.mindportrait.util.MindStateEngine;
import xiaozhi.modules.mindportrait.util.MindWellnessSupport;
import xiaozhi.modules.mindportrait.vo.MindGraphVO;
import xiaozhi.modules.mindportrait.vo.MindLinkVO;
import xiaozhi.modules.mindportrait.vo.MindNodeVO;
import xiaozhi.modules.mindportrait.vo.MindNotificationPageVO;
import xiaozhi.modules.mindportrait.vo.MindWeeklyDigestVO;
import xiaozhi.modules.mindportrait.vo.MindWellnessSummaryVO;
import xiaozhi.modules.parent.dao.DeviceChildDao;
import xiaozhi.modules.parent.dao.ParentDeviceBindingDao;
import xiaozhi.modules.parent.entity.DeviceChildEntity;
import xiaozhi.modules.parent.entity.ParentDeviceBindingEntity;
import xiaozhi.modules.parent.util.ParentChildAccessHelper;

@Service
@RequiredArgsConstructor
public class MindPortraitServiceImpl implements MindPortraitService {

    private static final int DEFAULT_WEEKLY_INSTANT_CAP = 2;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final MpTemplateReleaseDao releaseDao;
    private final MpTemplateNodeDao nodeDao;
    private final MpTemplateEdgeDao edgeDao;
    private final LearnerMindEvidenceDao evidenceDao;
    private final LearnerMindStateDao stateDao;
    private final MpParentNotificationDao notificationDao;
    private final MpParentSettingsDao settingsDao;
    private final DeviceChildDao deviceChildDao;
    private final ParentDeviceBindingDao parentDeviceBindingDao;
    private final MindPortraitClassifyService classifyService;

    @Override
    @Transactional
    public Long publishFromTeaching(TeachingMpPublishDTO body) {
        if (body == null || body.getNodes() == null || body.getNodes().isEmpty()) {
            throw new RenException("nodes 不能为空");
        }
        String ageBand = normalizeAgeBand(body.getAgeBand());
        MpTemplateReleaseEntity release = new MpTemplateReleaseEntity();
        release.setAgeBand(ageBand);
        release.setVersionLabel(StringUtils.defaultIfBlank(body.getVersionLabel(), "mp-" + ageBand));
        release.setTeachingSubmissionId(body.getTeachingSubmissionId());
        release.setRulesJson(body.getRulesJson());
        release.setStatus(MpTemplateReleaseEntity.STATUS_DRAFT);
        release.setCreateTime(new Date());
        releaseDao.insert(release);
        Long releaseId = release.getId();

        for (TeachingMpPublishDTO.Node n : body.getNodes()) {
            MpTemplateNodeEntity en = new MpTemplateNodeEntity();
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
            for (TeachingMpPublishDTO.Edge e : body.getEdges()) {
                MpTemplateEdgeEntity ee = new MpTemplateEdgeEntity();
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
        MpTemplateReleaseEntity release = releaseDao.selectById(releaseId);
        if (release == null) {
            throw new RenException("release 不存在");
        }
        List<MpTemplateReleaseEntity> old = releaseDao.selectList(
                new LambdaQueryWrapper<MpTemplateReleaseEntity>()
                        .eq(MpTemplateReleaseEntity::getAgeBand, release.getAgeBand())
                        .eq(MpTemplateReleaseEntity::getStatus, MpTemplateReleaseEntity.STATUS_PUBLISHED));
        Date now = new Date();
        for (MpTemplateReleaseEntity o : old) {
            o.setStatus(MpTemplateReleaseEntity.STATUS_ARCHIVED);
            releaseDao.updateById(o);
        }
        release.setStatus(MpTemplateReleaseEntity.STATUS_PUBLISHED);
        release.setPublishedAt(now);
        releaseDao.updateById(release);
    }

    @Override
    public MindGraphVO getGraph(Long parentUserId, Long childId) {
        ParentChildAccessHelper.ensureParentCanAccessChildById(
                deviceChildDao, parentDeviceBindingDao, parentUserId, childId);
        return getGraphByChildId(childId);
    }

    @Override
    public MindGraphVO getGraphByChildId(Long childId) {
        DeviceChildEntity child = ParentChildAccessHelper.requireChild(deviceChildDao, childId);
        String ageBand = GrowthAgeBandUtil.resolveAgeBand(child);
        MpTemplateReleaseEntity release = findPublishedRelease(ageBand);
        if (release == null) {
            List<String> publishedBands = releaseDao.selectList(
                    new LambdaQueryWrapper<MpTemplateReleaseEntity>()
                            .eq(MpTemplateReleaseEntity::getStatus, MpTemplateReleaseEntity.STATUS_PUBLISHED)
                            .orderByAsc(MpTemplateReleaseEntity::getAgeBand))
                    .stream()
                    .map(MpTemplateReleaseEntity::getAgeBand)
                    .distinct()
                    .collect(Collectors.toList());
            String publishedHint = publishedBands.isEmpty()
                    ? "manager-api 中 mp_template_release 尚无 published 记录（教研「通过」可能未同步到本环境）"
                    : "manager-api 已发布年龄段：" + String.join(", ", publishedBands);
            throw new RenException("暂无已发布的心绪图谱模板：" + ageBand
                    + "（孩子档案解析结果）。"
                    + publishedHint
                    + "。请发版对应年龄段或核对孩子生日/年级/年龄段。");
        }
        List<MpTemplateNodeEntity> templateNodes = nodeDao.selectList(
                new LambdaQueryWrapper<MpTemplateNodeEntity>()
                        .eq(MpTemplateNodeEntity::getReleaseId, release.getId())
                        .orderByAsc(MpTemplateNodeEntity::getSortOrder));
        List<MpTemplateEdgeEntity> templateEdges = edgeDao.selectList(
                new LambdaQueryWrapper<MpTemplateEdgeEntity>()
                        .eq(MpTemplateEdgeEntity::getReleaseId, release.getId()));
        Map<String, LearnerMindStateEntity> stateByCode = loadStates(childId, release.getId());
        recomputeAllStates(childId, release.getId(), templateNodes, stateByCode);

        MindGraphVO vo = new MindGraphVO();
        vo.setReleaseId(release.getId());
        vo.setAgeBand(ageBand);
        MindGraphVO.CenterNode center = new MindGraphVO.CenterNode();
        center.setLabel(StringUtils.defaultIfBlank(child.getName(), "孩子"));
        center.setShortDesc("观察枢纽");
        center.setAvatarUrl(child.getAvatarUrl());
        vo.setCenter(center);

        Map<String, MpTemplateNodeEntity> byCode = templateNodes.stream()
                .collect(Collectors.toMap(MpTemplateNodeEntity::getCode, n -> n, (a, b) -> a));
        List<MindNodeVO> nodes = new ArrayList<>();
        MindNodeVO centerNode = buildCenterNode(center);
        nodes.add(centerNode);
        int strongCount = 0;
        for (MpTemplateNodeEntity tn : templateNodes) {
            LearnerMindStateEntity st = stateByCode.get(tn.getCode());
            MindNodeVO n = toNodeVo(tn, st, byCode);
            nodes.add(n);
            if ("strong".equals(n.getState())) {
                strongCount++;
            }
        }
        applySimpleLayout(nodes);
        vo.setNodes(nodes);
        vo.setStrongCount(strongCount);
        List<MindLinkVO> links = new ArrayList<>();
        for (MpTemplateNodeEntity tn : templateNodes) {
            if ("hub".equals(tn.getNodeType())) {
                MindLinkVO cl = new MindLinkVO();
                cl.setSource("center");
                cl.setTarget(tn.getCode());
                LearnerMindStateEntity hs = stateByCode.get(tn.getCode());
                cl.setStrength(hs != null ? hs.getStrength() : 30);
                links.add(cl);
            }
        }
        for (MpTemplateEdgeEntity e : templateEdges) {
            MindLinkVO l = new MindLinkVO();
            l.setSource(e.getFromCode());
            l.setTarget(e.getToCode());
            LearnerMindStateEntity tgt = stateByCode.get(e.getToCode());
            l.setStrength(tgt != null ? tgt.getStrength() : 0);
            links.add(l);
        }
        vo.setLinks(links);
        vo.setRules(parseRules(release.getRulesJson(), ageBand));
        return vo;
    }

    @Override
    public MindWellnessSummaryVO getWellnessSummary(Long parentUserId, Long childId) {
        ParentChildAccessHelper.ensureParentCanAccessChildById(
                deviceChildDao, parentDeviceBindingDao, parentUserId, childId);
        return buildWellnessSummaryForChild(childId);
    }

    private MindWellnessSummaryVO buildWellnessSummaryForChild(Long childId) {
        DeviceChildEntity child = ParentChildAccessHelper.requireChild(deviceChildDao, childId);
        MindGraphVO graph;
        try {
            graph = getGraphByChildId(childId);
        } catch (RenException e) {
            if (StringUtils.contains(e.getMessage(), "暂无已发布的心绪图谱模板")) {
                return emptyWellnessSummary(child);
            }
            throw e;
        }
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Date from = MindWellnessSupport.weekStartDate(weekStart.minusDays(7));
        Date to = new Date();
        List<LearnerMindEvidenceEntity> recentEvidence = evidenceDao.selectList(
                new LambdaQueryWrapper<LearnerMindEvidenceEntity>()
                        .eq(LearnerMindEvidenceEntity::getChildId, childId)
                        .eq(LearnerMindEvidenceEntity::getReleaseId, graph.getReleaseId())
                        .ge(LearnerMindEvidenceEntity::getCreateTime, from)
                        .le(LearnerMindEvidenceEntity::getCreateTime, to)
                        .orderByAsc(LearnerMindEvidenceEntity::getCreateTime));
        Map<String, String> clusterByNodeCode = nodeDao.selectList(
                new LambdaQueryWrapper<MpTemplateNodeEntity>()
                        .eq(MpTemplateNodeEntity::getReleaseId, graph.getReleaseId()))
                .stream()
                .collect(Collectors.toMap(
                        MpTemplateNodeEntity::getCode,
                        n -> StringUtils.defaultString(n.getClusterCode()),
                        (a, b) -> a));
        return MindWellnessSupport.build(
                childId,
                child.getName(),
                graph,
                recentEvidence,
                clusterByNodeCode);
    }

    private MindWellnessSummaryVO emptyWellnessSummary(DeviceChildEntity child) {
        MindWellnessSummaryVO vo = new MindWellnessSummaryVO();
        vo.setChildId(child.getId());
        vo.setChildName(StringUtils.defaultIfBlank(child.getName(), "孩子"));
        vo.setObserveDays(14);
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        vo.setWeekStart(weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE));
        vo.setWeekEnd(weekStart.plusDays(6).format(DateTimeFormatter.ISO_LOCAL_DATE));
        vo.setOverallLevel("stable");
        vo.setOverallText("观察数据积累中");
        vo.setSummary("心绪陪伴模板尚未发布或观察数据不足，请继续让孩子与小智自然聊天。");
        vo.setChips(List.of());
        vo.setDimensions(List.of());
        vo.setWeekTrend(List.of());
        vo.setShowActions(false);
        return vo;
    }

    @Override
    @Transactional
    public void ingestEvidence(MindEvidenceIngestDTO body) {
        if (body == null || body.getChildId() == null) {
            throw new RenException("childId 必填");
        }
        DeviceChildEntity child = ParentChildAccessHelper.requireChild(deviceChildDao, body.getChildId());
        String ageBand = GrowthAgeBandUtil.resolveAgeBand(child);
        MpTemplateReleaseEntity release = findPublishedRelease(ageBand);
        if (release == null) {
            return;
        }
        List<MpTemplateNodeEntity> signals = nodeDao.selectList(
                new LambdaQueryWrapper<MpTemplateNodeEntity>()
                        .eq(MpTemplateNodeEntity::getReleaseId, release.getId())
                        .eq(MpTemplateNodeEntity::getNodeType, "signal"));
        String matchedCode = StringUtils.trimToNull(body.getNodeCode());
        if (matchedCode == null) {
            matchedCode = matchSignalCode(signals, body.getText());
        }
        if (matchedCode == null) {
            return;
        }
        final String nodeCode = matchedCode;
        MpTemplateNodeEntity signalNode = signals.stream()
                .filter(s -> nodeCode.equals(s.getCode())).findFirst().orElse(null);
        if (signalNode == null) {
            return;
        }
        int confidence = body.getConfidence() != null ? body.getConfidence() : 75;
        LearnerMindEvidenceEntity ev = new LearnerMindEvidenceEntity();
        ev.setChildId(body.getChildId());
        ev.setReleaseId(release.getId());
        ev.setNodeCode(nodeCode);
        ev.setSourceType(StringUtils.defaultIfBlank(body.getSourceType(), "conversation"));
        ev.setSourceRef(body.getSourceRef());
        ev.setConfidence(Math.max(1, Math.min(100, confidence)));
        ev.setSnippet(StringUtils.left(body.getText(), 500));
        ev.setCreateTime(new Date());
        evidenceDao.insert(ev);

        List<MpTemplateNodeEntity> allNodes = nodeDao.selectList(
                new LambdaQueryWrapper<MpTemplateNodeEntity>()
                        .eq(MpTemplateNodeEntity::getReleaseId, release.getId()));
        Map<String, LearnerMindStateEntity> stateByCode = loadStates(body.getChildId(), release.getId());
        String prevSignalState = stateByCode.containsKey(nodeCode)
                ? stateByCode.get(nodeCode).getState() : "locked";
        recomputeAllStates(body.getChildId(), release.getId(), allNodes, stateByCode);
        LearnerMindStateEntity after = stateByCode.get(nodeCode);
        if (after != null && "strong".equals(after.getState()) && !"strong".equals(prevSignalState)) {
            maybeNotifyInstant(body.getChildId(), after, signalNode);
        }
    }

    @Override
    public void ingestSession(MindEvidenceSessionDTO body) {
        if (body == null || body.getChildId() == null) {
            throw new RenException("childId 必填");
        }
        DeviceChildEntity child = ParentChildAccessHelper.requireChild(deviceChildDao, body.getChildId());
        String ageBand = GrowthAgeBandUtil.resolveAgeBand(child);
        MpTemplateReleaseEntity release = findPublishedRelease(ageBand);
        if (release == null) {
            return;
        }
        String transcript = buildSessionTranscript(body);
        if (StringUtils.isBlank(transcript)) {
            return;
        }
        List<MpTemplateNodeEntity> allNodes = nodeDao.selectList(
                new LambdaQueryWrapper<MpTemplateNodeEntity>()
                        .eq(MpTemplateNodeEntity::getReleaseId, release.getId()));
        List<MindPortraitClassifyService.ClassifiedSignal> hits =
                classifyService.classify(transcript, allNodes);
        if (hits.isEmpty()) {
            return;
        }
        String sourceRef = StringUtils.defaultIfBlank(body.getSourceRef(), "session");
        for (MindPortraitClassifyService.ClassifiedSignal hit : hits) {
            MindEvidenceIngestDTO ev = new MindEvidenceIngestDTO();
            ev.setChildId(body.getChildId());
            ev.setSourceType(StringUtils.defaultIfBlank(body.getSourceType(), "conversation_session"));
            ev.setSourceRef(sourceRef);
            ev.setNodeCode(hit.getSignalCode());
            ev.setText(StringUtils.defaultIfBlank(hit.getSnippet(), transcript));
            ev.setConfidence(hit.getConfidence());
            ingestEvidence(ev);
        }
    }

    private String buildSessionTranscript(MindEvidenceSessionDTO body) {
        if (StringUtils.isNotBlank(body.getTranscript())) {
            return body.getTranscript().trim();
        }
        if (body.getTurns() == null || body.getTurns().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (MindEvidenceSessionDTO.Turn t : body.getTurns()) {
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
    public MindNotificationPageVO listNotifications(Long parentUserId, Long childId, int page, int pageSize) {
        ParentChildAccessHelper.ensureParentCanAccessChildById(
                deviceChildDao, parentDeviceBindingDao, parentUserId, childId);
        int p = Math.max(1, page);
        int ps = Math.min(50, Math.max(1, pageSize));
        List<MpParentNotificationEntity> rows = notificationDao.selectList(
                new LambdaQueryWrapper<MpParentNotificationEntity>()
                        .eq(MpParentNotificationEntity::getParentUserId, parentUserId)
                        .eq(MpParentNotificationEntity::getChildId, childId)
                        .orderByDesc(MpParentNotificationEntity::getCreateTime)
                        .last("LIMIT " + ((p - 1) * ps) + "," + ps));
        long unread = notificationDao.selectCount(
                new LambdaQueryWrapper<MpParentNotificationEntity>()
                        .eq(MpParentNotificationEntity::getParentUserId, parentUserId)
                        .eq(MpParentNotificationEntity::getChildId, childId)
                        .eq(MpParentNotificationEntity::getIsRead, 0));
        MindNotificationPageVO vo = new MindNotificationPageVO();
        vo.setUnreadCount((int) unread);
        vo.setItems(rows.stream().map(r -> {
            MindNotificationPageVO.Item item = new MindNotificationPageVO.Item();
            item.setId(r.getId());
            item.setNotifyType(r.getNotifyType());
            item.setCardType("instant".equals(r.getNotifyType()) ? "mind_instant_card" : "mind_weekly_card");
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
        MpParentNotificationEntity n = notificationDao.selectById(notificationId);
        if (n == null || !Objects.equals(n.getParentUserId(), parentUserId)) {
            throw new RenException("通知不存在");
        }
        n.setIsRead(1);
        notificationDao.updateById(n);
    }

    @Override
    public MindWeeklyDigestVO weeklyDigest(Long parentUserId, Long childId, String weekStart) {
        ParentChildAccessHelper.ensureParentCanAccessChildById(
                deviceChildDao, parentDeviceBindingDao, parentUserId, childId);
        LocalDate start = weekStart != null
                ? LocalDate.parse(weekStart, DateTimeFormatter.ISO_LOCAL_DATE)
                : LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = start.plusDays(6);
        Date from = Date.from(start.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date to = Date.from(end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

        DeviceChildEntity child = ParentChildAccessHelper.requireChild(deviceChildDao, childId);
        MpTemplateReleaseEntity release = findPublishedRelease(GrowthAgeBandUtil.resolveAgeBand(child));
        MindWeeklyDigestVO vo = new MindWeeklyDigestVO();
        vo.setWeekStart(start.toString());
        vo.setWeekEnd(end.toString());
        if (release == null) {
            vo.setTopHighlights(List.of());
            vo.setParentTip("心绪陪伴模板尚未发布，敬请期待。");
            vo.setTitle("心绪陪伴");
            vo.setSummary("模板尚未发布，请继续让孩子与小智自然聊天。");
            vo.setParentActions(List.of());
            vo.setParentSupport("给家长的你：观察数据积累中，不必焦虑，持续陪伴即可。");
            vo.setChildTips(List.of());
            vo.setNewStrongCount(0);
            return vo;
        }
        List<LearnerMindStateEntity> states = stateDao.selectList(
                new LambdaQueryWrapper<LearnerMindStateEntity>()
                        .eq(LearnerMindStateEntity::getChildId, childId)
                        .eq(LearnerMindStateEntity::getReleaseId, release.getId())
                        .eq(LearnerMindStateEntity::getState, "strong"));
        Map<String, MpTemplateNodeEntity> nodes = nodeDao.selectList(
                new LambdaQueryWrapper<MpTemplateNodeEntity>()
                        .eq(MpTemplateNodeEntity::getReleaseId, release.getId()))
                .stream().collect(Collectors.toMap(MpTemplateNodeEntity::getCode, n -> n, (a, b) -> a));

        int newStrong = (int) states.stream()
                .filter(s -> s.getFirstStrongAt() != null && !s.getFirstStrongAt().before(from) && s.getFirstStrongAt().before(to))
                .count();
        vo.setNewStrongCount(newStrong);
        List<MindWeeklyDigestVO.Highlight> top = states.stream()
                .sorted(Comparator.comparingInt(LearnerMindStateEntity::getStrength).reversed())
                .limit(3)
                .map(s -> {
                    MpTemplateNodeEntity tn = nodes.get(s.getNodeCode());
                    MindWeeklyDigestVO.Highlight h = new MindWeeklyDigestVO.Highlight();
                    h.setNodeCode(s.getNodeCode());
                    h.setLabel(tn != null ? tn.getLabel() : s.getNodeCode());
                    h.setShortDesc(tn != null ? tn.getShortDesc() : "");
                    h.setStrength(s.getStrength());
                    return h;
                }).toList();
        vo.setTopHighlights(top);
        enrichWeeklyDigestForChat(vo, child);
        return vo;
    }

    private void enrichWeeklyDigestForChat(MindWeeklyDigestVO vo, DeviceChildEntity child) {
        MindWellnessSummaryVO wellness = buildWellnessSummaryForChild(child.getId());
        String childName = StringUtils.defaultIfBlank(child.getName(), "孩子");
        List<MindWeeklyDigestVO.Highlight> top = vo.getTopHighlights() != null ? vo.getTopHighlights() : List.of();
        if (wellness.getDimensions() != null && !wellness.getDimensions().isEmpty()) {
            vo.setTitle(buildWeeklyTitle(wellness));
            vo.setSummary(StringUtils.defaultIfBlank(wellness.getSummary(),
                    childName + "本周心绪观察已更新。"));
            vo.setParentActions(buildParentActions(wellness));
            vo.setParentSupport(buildParentSupport(wellness, childName));
            vo.setChildTips(buildChildTips(wellness));
            vo.setParentTip(vo.getParentSupport());
            return;
        }
        vo.setTitle(top.isEmpty()
                ? childName + " · 本周心绪观察"
                : "本周亮点：「" + top.get(0).getLabel() + "」");
        vo.setSummary(top.isEmpty()
                ? "本周继续观察孩子的自然表现，系统会在证据积累后给出趋势参考。"
                : "本周在「" + top.get(0).getLabel() + "」方向有积极信号，值得温柔肯定。");
        vo.setParentActions(List.of(
                "今晚可先问「今天有没有开心的事」再聊学习",
                "孩子紧张时，先复述感受，不急着给方案"));
        vo.setParentSupport("给家长的你：孩子情绪波动不等于你的教育出了问题，持续观察本身就是在陪伴。");
        vo.setChildTips(List.of(
                "保留每天 15 分钟「只聊兴趣、不纠正」的专属时间",
                "当他提到压力话题，先听完整再回应",
                "用「需要我陪你开始吗」替代「你怎么又不…」"));
        vo.setParentTip(vo.getParentSupport());
    }

    private String buildWeeklyTitle(MindWellnessSummaryVO wellness) {
        MindWellnessSummaryVO.Dimension watch = wellness.getDimensions().stream()
                .filter(d -> "watch".equals(d.getStatus()))
                .findFirst()
                .orElse(null);
        if (watch != null) {
            return "整体平稳，「" + watch.getName() + "」值得多陪";
        }
        return StringUtils.defaultIfBlank(wellness.getOverallText(), "本周心绪观察");
    }

    private List<String> buildParentActions(MindWellnessSummaryVO wellness) {
        List<String> actions = new ArrayList<>();
        MindWellnessSummaryVO.Dimension stress = wellness.getDimensions().stream()
                .filter(d -> "stress".equals(d.getCode()))
                .findFirst()
                .orElse(null);
        if (stress != null && "watch".equals(stress.getStatus())) {
            actions.add("今晚可先问「今天有没有开心的事」再问作业");
            actions.add("他紧张时，先复述感受，不急着给方案");
        } else {
            actions.add("保持日常闲聊，不必刻意「检查」情绪");
            actions.add("肯定孩子主动表达感受的时刻");
        }
        actions.add("详细状态可在机器人 Tab 查看");
        return actions.stream().limit(3).toList();
    }

    private String buildParentSupport(MindWellnessSummaryVO wellness, String childName) {
        if ("concern".equals(wellness.getOverallLevel())) {
            return "给家长的你：" + childName + "近期压力相关话题增多，不等于你的教育出了问题。"
                    + "你已经在认真观察，先安顿好自己的情绪，孩子才更容易感到安全。";
        }
        if ("watch".equals(wellness.getOverallLevel())) {
            return "给家长的你：孩子提到压力会紧张，不等于你的教育出了问题。"
                    + "你已经在持续观察，这本身就是在陪伴。";
        }
        return "给家长的你：本周整体平稳。你不需要立刻「解决」每一种情绪，陪伴倾听本身就是支持。";
    }

    private List<String> buildChildTips(MindWellnessSummaryVO wellness) {
        boolean watchStress = wellness.getDimensions().stream()
                .anyMatch(d -> "stress".equals(d.getCode()) && "watch".equals(d.getStatus()));
        if (watchStress) {
            return List.of(
                    "当他提到考试时，先复述感受：「听起来你有点紧张」",
                    "避免「你怎么又不写」——改成「需要我陪你开始吗」",
                    "若连续 3 天都提到，可在机器人 Tab 看「面对压力时」详情");
        }
        return List.of(
                "保留每天 15 分钟「只聊兴趣、不纠正」的专属时间",
                "多问开放式问题：「今天有什么有意思的事？」",
                "孩子表达不满时，先确认你听懂了，再讨论怎么办");
    }

    private void recomputeAllStates(
            Long childId,
            Long releaseId,
            List<MpTemplateNodeEntity> templateNodes,
            Map<String, LearnerMindStateEntity> stateByCode) {
        Map<String, List<String>> childrenOf = new HashMap<>();
        for (MpTemplateNodeEntity n : templateNodes) {
            if (n.getParentCode() != null) {
                childrenOf.computeIfAbsent(n.getParentCode(), k -> new ArrayList<>()).add(n.getCode());
            }
        }
        List<MpTemplateNodeEntity> signals = templateNodes.stream()
                .filter(n -> "signal".equals(n.getNodeType())).toList();
        for (MpTemplateNodeEntity sig : signals) {
            upsertNodeState(childId, releaseId, sig, stateByCode);
        }
        List<MpTemplateNodeEntity> subs = templateNodes.stream()
                .filter(n -> "sub".equals(n.getNodeType())).toList();
        for (MpTemplateNodeEntity sub : subs) {
            rollupNode(childId, releaseId, sub, childrenOf, stateByCode);
        }
        List<MpTemplateNodeEntity> hubs = templateNodes.stream()
                .filter(n -> "hub".equals(n.getNodeType())).toList();
        for (MpTemplateNodeEntity hub : hubs) {
            rollupNode(childId, releaseId, hub, childrenOf, stateByCode);
        }
        MindStateEngine.applyVisualHierarchy(templateNodes, stateByCode);
        for (LearnerMindStateEntity st : stateByCode.values()) {
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
            MpTemplateNodeEntity node,
            Map<String, List<String>> childrenOf,
            Map<String, LearnerMindStateEntity> stateByCode) {
        List<String> childCodes = childrenOf.getOrDefault(node.getCode(), List.of());
        int evidenceCount = 0;
        int strengthSum = 0;
        for (String cc : childCodes) {
            LearnerMindStateEntity cs = stateByCode.get(cc);
            if (cs != null) {
                evidenceCount += cs.getEvidenceCount() != null ? cs.getEvidenceCount() : 0;
                strengthSum += cs.getStrength() != null ? cs.getStrength() : 0;
            }
        }
        int avgConf = 70;
        int strength = childCodes.isEmpty() ? 0
                : Math.min(100, strengthSum / Math.max(1, childCodes.size()));
        if (!childCodes.isEmpty() && evidenceCount > 0) {
            strength = Math.max(strength, MindStateEngine.computeStrength(
                    Math.max(1, evidenceCount / Math.max(1, childCodes.size())), avgConf));
        }
        LearnerMindStateEntity st = stateByCode.computeIfAbsent(node.getCode(), k -> newState(childId, releaseId, k));
        st.setEvidenceCount(evidenceCount);
        st.setStrength(strength);
        int required = node.getRequiredEvidence() != null ? node.getRequiredEvidence() : defaultRequired(node.getNodeType());
        String newState = "hub".equals(node.getNodeType())
                ? MindStateEngine.rollupHubState(childCodes, stateByCode, evidenceCount, required)
                : MindStateEngine.lightState(
                        strength, Math.max(evidenceCount, 0), required,
                        node.getStrongThreshold() != null ? node.getStrongThreshold() : 72);
        updateStateTransition(st, newState);
        st.setUpdateTime(new Date());
    }

    private void upsertNodeState(
            Long childId,
            Long releaseId,
            MpTemplateNodeEntity node,
            Map<String, LearnerMindStateEntity> stateByCode) {
        List<LearnerMindEvidenceEntity> evs = evidenceDao.selectList(
                new LambdaQueryWrapper<LearnerMindEvidenceEntity>()
                        .eq(LearnerMindEvidenceEntity::getChildId, childId)
                        .eq(LearnerMindEvidenceEntity::getReleaseId, releaseId)
                        .eq(LearnerMindEvidenceEntity::getNodeCode, node.getCode()));
        int count = evs.size();
        int avgConf = count == 0 ? 0
                : (int) Math.round(evs.stream().mapToInt(LearnerMindEvidenceEntity::getConfidence).average().orElse(0));
        int strength = MindStateEngine.computeStrength(count, avgConf);
        LearnerMindStateEntity st = stateByCode.computeIfAbsent(node.getCode(), k -> newState(childId, releaseId, k));
        st.setEvidenceCount(count);
        st.setStrength(strength);
        int required = node.getRequiredEvidence() != null ? node.getRequiredEvidence() : defaultRequired(node.getNodeType());
        String newState = MindStateEngine.lightState(
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

    private LearnerMindStateEntity newState(Long childId, Long releaseId, String code) {
        LearnerMindStateEntity st = new LearnerMindStateEntity();
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

    private void updateStateTransition(LearnerMindStateEntity st, String newState) {
        String prev = st.getState();
        st.setState(newState);
        if ("strong".equals(newState) && st.getFirstStrongAt() == null) {
            st.setFirstStrongAt(new Date());
        }
        if (!"strong".equals(newState) && !"strong".equals(prev)) {
            // keep firstStrongAt
        }
    }

    private Map<String, LearnerMindStateEntity> loadStates(Long childId, Long releaseId) {
        List<LearnerMindStateEntity> list = stateDao.selectList(
                new LambdaQueryWrapper<LearnerMindStateEntity>()
                        .eq(LearnerMindStateEntity::getChildId, childId)
                        .eq(LearnerMindStateEntity::getReleaseId, releaseId));
        Map<String, LearnerMindStateEntity> map = new HashMap<>();
        list.forEach(s -> map.put(s.getNodeCode(), s));
        return map;
    }

    private MpTemplateReleaseEntity findPublishedRelease(String ageBand) {
        return releaseDao.selectOne(
                new LambdaQueryWrapper<MpTemplateReleaseEntity>()
                        .eq(MpTemplateReleaseEntity::getAgeBand, ageBand)
                        .eq(MpTemplateReleaseEntity::getStatus, MpTemplateReleaseEntity.STATUS_PUBLISHED)
                        .orderByDesc(MpTemplateReleaseEntity::getPublishedAt)
                        .last("LIMIT 1"));
    }

    private String matchSignalCode(List<MpTemplateNodeEntity> signals, String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        String t = text.toLowerCase();
        MpTemplateNodeEntity best = null;
        int bestScore = 0;
        for (MpTemplateNodeEntity s : signals) {
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

    private void maybeNotifyInstant(Long childId, LearnerMindStateEntity state, MpTemplateNodeEntity node) {
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
                    new LambdaQueryWrapper<MpParentNotificationEntity>()
                            .eq(MpParentNotificationEntity::getParentUserId, parentUserId)
                            .eq(MpParentNotificationEntity::getChildId, childId)
                            .eq(MpParentNotificationEntity::getNotifyType, "instant")
                            .ge(MpParentNotificationEntity::getCreateTime, from));
            if (sentThisWeek >= DEFAULT_WEEKLY_INSTANT_CAP) {
                continue;
            }
            long dup = notificationDao.selectCount(
                    new LambdaQueryWrapper<MpParentNotificationEntity>()
                            .eq(MpParentNotificationEntity::getParentUserId, parentUserId)
                            .eq(MpParentNotificationEntity::getChildId, childId)
                            .eq(MpParentNotificationEntity::getNodeCode, state.getNodeCode())
                            .eq(MpParentNotificationEntity::getNotifyType, "instant"));
            if (dup > 0) {
                continue;
            }
            MpParentNotificationEntity n = new MpParentNotificationEntity();
            n.setParentUserId(parentUserId);
            n.setChildId(childId);
            n.setNodeCode(state.getNodeCode());
            n.setNotifyType("instant");
            n.setTitle("成长亮点 · " + node.getLabel());
            n.setSummary("孩子在「" + node.getLabel() + "」上出现了积极信号，"
                    + StringUtils.defaultString(node.getShortDesc()) + "。点击查看心绪图谱。");
            n.setIsRead(0);
            n.setCreateTime(new Date());
            notificationDao.insert(n);
        }
    }

    private boolean isInstantEnabled(Long parentUserId, Long childId) {
        MpParentSettingsEntity s = settingsDao.selectOne(
                new LambdaQueryWrapper<MpParentSettingsEntity>()
                        .eq(MpParentSettingsEntity::getParentUserId, parentUserId)
                        .eq(MpParentSettingsEntity::getChildId, childId));
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

    private MindNodeVO buildCenterNode(MindGraphVO.CenterNode center) {
        MindNodeVO n = new MindNodeVO();
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
    private void applySimpleLayout(List<MindNodeVO> nodes) {
        List<MindNodeVO> hubs = nodes.stream().filter(n -> "hub".equals(n.getType())).toList();
        int hc = Math.max(1, hubs.size());
        for (int i = 0; i < hc; i++) {
            double angle = (Math.PI * 2 * i) / hc - Math.PI / 2;
            hubs.get(i).setX(0.5 + Math.cos(angle) * 0.28);
            hubs.get(i).setY(0.5 + Math.sin(angle) * 0.28);
        }
        Map<String, MindNodeVO> byId = nodes.stream().collect(Collectors.toMap(MindNodeVO::getId, n -> n, (a, b) -> a));
        for (MindNodeVO sub : nodes.stream().filter(n -> "sub".equals(n.getType())).toList()) {
            MindNodeVO hub = sub.getParentHub() != null ? byId.get(sub.getParentHub()) : null;
            double bx = hub != null && hub.getX() != null ? hub.getX() : 0.5;
            double by = hub != null && hub.getY() != null ? hub.getY() : 0.5;
            int idx = Math.abs(sub.getId().hashCode() % 6);
            double a = (Math.PI * 2 * idx) / 6;
            sub.setX(bx + Math.cos(a) * 0.09);
            sub.setY(by + Math.sin(a) * 0.09);
        }
        for (MindNodeVO sig : nodes.stream().filter(n -> "signal".equals(n.getType())).toList()) {
            MindNodeVO sub = sig.getParentSub() != null ? byId.get(sig.getParentSub()) : null;
            double bx = sub != null && sub.getX() != null ? sub.getX() : 0.5;
            double by = sub != null && sub.getY() != null ? sub.getY() : 0.5;
            int idx = Math.abs(sig.getId().hashCode() % 4);
            double a = (Math.PI * 2 * idx) / 4;
            sig.setX(bx + Math.cos(a) * 0.045);
            sig.setY(by + Math.sin(a) * 0.045);
        }
    }

    private MindNodeVO toNodeVo(
            MpTemplateNodeEntity tn,
            LearnerMindStateEntity st,
            Map<String, MpTemplateNodeEntity> byCode) {
        MindNodeVO n = new MindNodeVO();
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
            MpTemplateNodeEntity parent = byCode.get(tn.getParentCode());
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
        n.setSuggest(MindStateEngine.buildSuggest(n.getState(), n.getEvidenceCount(), n.getRequiredCount()));
        return n;
    }

    private MindGraphVO.MindRulesVO parseRules(String rulesJson, String ageBand) {
        MindGraphVO.MindRulesVO r = new MindGraphVO.MindRulesVO();
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
        MpParentSettingsEntity s = settingsDao.selectOne(
                new LambdaQueryWrapper<MpParentSettingsEntity>()
                        .eq(MpParentSettingsEntity::getParentUserId, parentUserId)
                        .eq(MpParentSettingsEntity::getChildId, childId));
        if (s == null) {
            s = new MpParentSettingsEntity();
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
