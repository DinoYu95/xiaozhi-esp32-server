package xiaozhi.modules.learning.service.impl;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.JsonUtils;
import xiaozhi.modules.learning.dao.KgNodeDao;
import xiaozhi.modules.learning.dao.KgNodeRevisionDao;
import xiaozhi.modules.learning.dao.LearnerSkillStateDao;
import xiaozhi.modules.learning.dao.LearningEvidenceEventDao;
import xiaozhi.modules.learning.dao.LearningHomeworkSessionDao;
import xiaozhi.modules.learning.dto.LearningSessionEndDTO;
import xiaozhi.modules.learning.dto.LearningSessionPhotoDTO;
import xiaozhi.modules.learning.dto.LearningSessionStartDTO;
import xiaozhi.modules.learning.dto.LearningSessionTurnDTO;
import xiaozhi.modules.learning.entity.KgNodeEntity;
import xiaozhi.modules.learning.entity.KgNodeRevisionEntity;
import xiaozhi.modules.learning.entity.LearnerSkillStateEntity;
import xiaozhi.modules.learning.entity.LearningEvidenceEventEntity;
import xiaozhi.modules.learning.entity.LearningHomeworkSessionEntity;
import xiaozhi.modules.learning.service.LearningKgService;
import xiaozhi.modules.learning.service.LearningRemedialService;
import xiaozhi.modules.learning.service.LearningSessionService;
import xiaozhi.modules.learning.entity.KgGraphReleaseEntity;
import xiaozhi.modules.learning.util.ChildGradeOptionsUtil;
import xiaozhi.modules.learning.util.LearningChildProfileUtil;
import xiaozhi.modules.learning.util.LearningKgNodeTypeUtil;
import xiaozhi.modules.learning.vo.LearningOverviewVO;
import xiaozhi.modules.learning.vo.LearningRemedialMissionBriefVO;
import xiaozhi.modules.learning.vo.LearningSessionDetailVO;
import xiaozhi.modules.learning.vo.LearningSessionItemVO;
import xiaozhi.modules.learning.vo.LearningSessionPageVO;
import xiaozhi.modules.learning.vo.LearningWeeklyDigestVO;
import xiaozhi.modules.parent.dao.DeviceChildDao;
import xiaozhi.modules.parent.dao.ParentDeviceBindingDao;
import xiaozhi.modules.parent.dao.ParentShadowMissionDao;
import xiaozhi.modules.parent.entity.DeviceChildEntity;
import xiaozhi.modules.parent.entity.ParentShadowMissionEntity;
import xiaozhi.modules.parent.util.ParentChildAccessHelper;

@Service
@RequiredArgsConstructor
public class LearningSessionServiceImpl implements LearningSessionService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern SUB_EXPR = Pattern.compile("\\d\\s*[-−－]\\s*\\d");

    private final LearningHomeworkSessionDao sessionDao;
    private final LearningEvidenceEventDao evidenceDao;
    private final LearnerSkillStateDao learnerSkillStateDao;
    private final DeviceChildDao deviceChildDao;
    private final ParentDeviceBindingDao parentDeviceBindingDao;
    private final ParentShadowMissionDao parentShadowMissionDao;
    private final LearningKgService learningKgService;
    private final KgNodeDao kgNodeDao;
    private final KgNodeRevisionDao kgNodeRevisionDao;
    private final LearningRemedialService learningRemedialService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> startSession(LearningSessionStartDTO dto) {
        if (dto == null || StringUtils.isBlank(dto.getSessionUuid()) || dto.getChildId() == null) {
            throw new RenException("sessionUuid、childId 必填");
        }
        LearningHomeworkSessionEntity existing = sessionDao.selectOne(
                new LambdaQueryWrapper<LearningHomeworkSessionEntity>()
                        .eq(LearningHomeworkSessionEntity::getSessionUuid, dto.getSessionUuid()));
        if (existing != null) {
            return Map.of("sessionId", existing.getId(), "graphReleaseId", existing.getGraphReleaseId());
        }
        DeviceChildEntity child = deviceChildDao.selectById(dto.getChildId());
        if (child == null) {
            throw new RenException("孩子不存在");
        }
        Long releaseId = null;
        try {
            releaseId = learningKgService.requireActiveReleaseId("math");
        } catch (RenException ignored) {
            // 未发布图谱时仍允许 session，仅不做诊断
        }
        Date now = new Date();
        LearningHomeworkSessionEntity s = new LearningHomeworkSessionEntity();
        s.setSessionUuid(dto.getSessionUuid().trim());
        s.setDeviceId(StringUtils.defaultString(dto.getDeviceId(), child.getDeviceId()));
        s.setChildId(dto.getChildId());
        s.setGraphReleaseId(releaseId);
        s.setStartedAt(dto.getStartedAtMs() != null ? new Date(dto.getStartedAtMs()) : now);
        s.setUserTurnCount(0);
        s.setPhotoCount(0);
        s.setLongestSilenceSec(0);
        s.setCreateTime(now);
        s.setUpdateTime(now);
        sessionDao.insert(s);
        appendEvidence(s, "SESSION_START", "{}", null, null, null, "start:" + s.getSessionUuid());
        Map<String, Object> out = new HashMap<>();
        out.put("sessionId", s.getId());
        out.put("graphReleaseId", releaseId);
        out.put("currentGrade", child.getCurrentGrade());
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordTurn(LearningSessionTurnDTO dto) {
        LearningHomeworkSessionEntity s = requireOpenSession(dto.getSessionUuid());
        s.setUserTurnCount(s.getUserTurnCount() + 1);
        s.setUpdateTime(new Date());
        sessionDao.updateById(s);
        DeviceChildEntity child = deviceChildDao.selectById(s.getChildId());
        Diagnosis d = diagnoseVerbal(child, s.getGraphReleaseId(), dto.getText());
        appendEvidence(
                s,
                "DIAGNOSIS_VERBAL",
                JsonUtils.toJsonString(d.payload),
                d.skillCodes,
                d.misconceptionCodes,
                d.confidence,
                StringUtils.defaultIfBlank(dto.getIdempotencyKey(), "turn:" + s.getId() + ":" + s.getUserTurnCount()));
        if (!d.skillCodes.isEmpty()) {
            applySkillState(s, d.skillCodes.get(0), d.correctHint, d.confidence, false);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordPhoto(LearningSessionPhotoDTO dto) {
        LearningHomeworkSessionEntity s = requireOpenSession(dto.getSessionUuid());
        s.setPhotoCount(s.getPhotoCount() + 1);
        s.setUpdateTime(new Date());
        sessionDao.updateById(s);
        DeviceChildEntity child = deviceChildDao.selectById(s.getChildId());
        String blob = StringUtils.defaultString(dto.getVisionText()) + "\n" + StringUtils.defaultString(dto.getAssistantReply());
        Diagnosis d = diagnoseVision(child, s.getGraphReleaseId(), blob);
        appendEvidence(s, "PHOTO_CAPTURE", JsonUtils.toJsonString(Map.of("hasPhoto", true)), null, null, null,
                "photo:" + s.getId() + ":" + s.getPhotoCount());
        appendEvidence(
                s,
                "DIAGNOSIS_VISION",
                JsonUtils.toJsonString(d.payload),
                d.skillCodes,
                d.misconceptionCodes,
                d.confidence,
                StringUtils.defaultIfBlank(dto.getIdempotencyKey(), "vision:" + s.getId() + ":" + s.getPhotoCount()));
        if (!d.skillCodes.isEmpty()) {
            applySkillState(s, d.skillCodes.get(0), d.correctHint, d.confidence, true);
            if (!d.correctHint) {
                learningRemedialService.maybeCreateRemedialShadow(
                        s.getChildId(),
                        s.getDeviceId(),
                        s.getId(),
                        d.skillCodes.get(0),
                        true,
                        d.confidence);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> endSession(LearningSessionEndDTO dto) {
        LearningHomeworkSessionEntity s = sessionDao.selectOne(
                new LambdaQueryWrapper<LearningHomeworkSessionEntity>()
                        .eq(LearningHomeworkSessionEntity::getSessionUuid, dto.getSessionUuid()));
        if (s == null) {
            throw new RenException("session 不存在");
        }
        if (s.getEndedAt() != null) {
            return JsonUtils.parseObject(s.getSummaryJson(), Map.class);
        }
        if (dto.getUserTurnCount() != null) {
            s.setUserTurnCount(dto.getUserTurnCount());
        }
        if (dto.getPhotoCount() != null) {
            s.setPhotoCount(dto.getPhotoCount());
        }
        if (dto.getLongestSilenceSec() != null) {
            s.setLongestSilenceSec(dto.getLongestSilenceSec());
        }
        String level = observationLevel(s);
        s.setObservationLevel(level);
        s.setEndReason(StringUtils.defaultIfBlank(dto.getEndReason(), "manual"));
        s.setEndedAt(dto.getEndedAtMs() != null ? new Date(dto.getEndedAtMs()) : new Date());
        Map<String, Object> summary = buildSummary(s, level);
        s.setSummaryJson(JsonUtils.toJsonString(summary));
        s.setUpdateTime(new Date());
        sessionDao.updateById(s);
        appendEvidence(s, "SESSION_END", s.getSummaryJson(), null, null, null, "end:" + s.getSessionUuid());
        return summary;
    }

    @Override
    public Map<String, Object> getChildLearningContext(Long childId) {
        DeviceChildEntity child = deviceChildDao.selectById(childId);
        if (child == null) {
            throw new RenException("孩子不存在");
        }
        Map<String, Object> m = new HashMap<>();
        m.put("childId", childId);
        m.put("currentGrade", child.getCurrentGrade());
        m.put("textbookSeries", child.getTextbookSeries());
        m.put("subjectsEnabled", child.getSubjectsEnabled());
        try {
            m.put("graphReleaseId", learningKgService.requireActiveReleaseId("math"));
        } catch (RenException e) {
            m.put("graphReleaseId", null);
        }
        return m;
    }

    @Override
    public LearningWeeklyDigestVO weeklyDigest(Long parentUserId, Long childId, String weekStart) {
        ParentChildAccessHelper.ensureParentCanAccessChildById(
                deviceChildDao, parentDeviceBindingDao, parentUserId, childId);
        DeviceChildEntity child = deviceChildDao.selectById(childId);
        if (child == null) {
            throw new RenException("孩子不存在");
        }
        LocalDate start = StringUtils.isNotBlank(weekStart)
                ? LocalDate.parse(weekStart)
                : LocalDate.now(ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = start.plusDays(6);
        Date from = Date.from(start.atStartOfDay(ZONE).toInstant());
        Date to = Date.from(end.plusDays(1).atStartOfDay(ZONE).toInstant());
        List<LearningHomeworkSessionEntity> sessions = sessionDao.selectList(
                new LambdaQueryWrapper<LearningHomeworkSessionEntity>()
                        .eq(LearningHomeworkSessionEntity::getChildId, childId)
                        .ge(LearningHomeworkSessionEntity::getStartedAt, from)
                        .lt(LearningHomeworkSessionEntity::getStartedAt, to));
        LearningWeeklyDigestVO vo = new LearningWeeklyDigestVO();
        vo.setWeekStart(start.toString());
        vo.setWeekEnd(end.toString());
        vo.setSessionCount(sessions.size());
        int strong = 0, medium = 0, weak = 0;
        for (LearningHomeworkSessionEntity s : sessions) {
            if ("strong".equals(s.getObservationLevel())) {
                strong++;
            } else if ("medium".equals(s.getObservationLevel())) {
                medium++;
            } else {
                weak++;
            }
        }
        vo.setStrongSessionCount(strong);
        vo.setMediumSessionCount(medium);
        vo.setWeakSessionCount(weak);
        List<LearnerSkillStateEntity> states = learnerSkillStateDao.selectList(
                new LambdaQueryWrapper<LearnerSkillStateEntity>()
                        .eq(LearnerSkillStateEntity::getChildId, childId)
                        .orderByAsc(LearnerSkillStateEntity::getPMastery)
                        .last("LIMIT 5"));
        List<Map<String, Object>> weakSkills = new ArrayList<>();
        for (LearnerSkillStateEntity st : states) {
            KgNodeEntity node = kgNodeDao.selectById(st.getSkillNodeId());
            if (node == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", node.getCode());
            row.put("pMastery", st.getPMastery());
            row.put("evidenceCount", st.getEvidenceCount());
            if (st.getGraphReleaseId() != null) {
                KgNodeRevisionEntity rev = kgNodeRevisionDao.selectOne(
                        new LambdaQueryWrapper<KgNodeRevisionEntity>()
                                .eq(KgNodeRevisionEntity::getGraphReleaseId, st.getGraphReleaseId())
                                .eq(KgNodeRevisionEntity::getNodeId, st.getSkillNodeId()));
                if (rev != null) {
                    row.put("name", rev.getName());
                }
            }
            weakSkills.add(row);
        }
        vo.setTopWeakSkills(weakSkills);
        vo.setErrorClassDistribution(aggregateErrorClassDistribution(childId, from, to));
        vo.setRemedialShadowMissions(loadRemedialMissionsForWeek(childId, from, to));
        vo.setCoverageNote("当前精准学情支持小学1-3年级数学；语/英作业可陪伴，知识点报告陆续开放。");
        if (sessions.isEmpty()) {
            vo.setParentHeadline("本周暂无作业辅导记录。");
            vo.setParentSuggestion("孩子说「进入作业辅导」后可记录学情。");
        } else if (strong == 0 && medium == 0) {
            vo.setParentHeadline("本周有陪伴记录，但缺少问答/拍题，无法判断掌握情况。");
            vo.setParentSuggestion("鼓励孩子卡住时问一题或拍一道典型错题。");
        } else {
            vo.setParentHeadline("本周作业辅导 " + sessions.size() + " 次，其中拍题/问答有效 " + (strong + medium) + " 次。");
            vo.setParentSuggestion("优先巩固掌握度较低的知识点，可按智伴回炉任务陪孩子练一练。");
        }
        return vo;
    }

    @Override
    public LearningOverviewVO overview(Long parentUserId, Long childId, String weekStart) {
        ParentChildAccessHelper.ensureParentCanAccessChildById(
                deviceChildDao, parentDeviceBindingDao, parentUserId, childId);
        DeviceChildEntity child = deviceChildDao.selectById(childId);
        if (child == null) {
            throw new RenException("孩子不存在");
        }
        LearningOverviewVO ov = new LearningOverviewVO();
        ov.setCurrentGrade(child.getCurrentGrade());
        ov.setGradeLabel(ChildGradeOptionsUtil.resolveGradeLabel(child));
        ov.setGrowthAgeBand(ChildGradeOptionsUtil.resolveGrowthAgeBand(child));
        ov.setPreschoolProfile(ChildGradeOptionsUtil.isPreschoolProfile(child));
        ov.setProvinceCode(LearningChildProfileUtil.resolveProvince(child));
        ov.setTextbookEdition(LearningChildProfileUtil.resolveTextbook(child));
        ov.setGradeConfigured(ChildGradeOptionsUtil.isGradeConfigured(child));
        ov.setTextbookSeries(child.getTextbookSeries());
        ov.setSubjectsEnabled(child.getSubjectsEnabled());
        ov.setProfileProvinceRaw(child.getProvinceCode());
        if (ChildGradeOptionsUtil.isPreschoolProfile(child)) {
            ov.setGraphReady(false);
            ov.setGraphReleaseId(null);
            ov.setGraphSkillCountAtGrade(0);
            ov.setWeeklyDigest(weeklyDigest(parentUserId, childId, weekStart));
            return ov;
        }
        int g = LearningChildProfileUtil.clampGraphGrade(child, child.getCurrentGrade());
        KgGraphReleaseEntity release = learningKgService.findActivePublishedRelease(
                "math",
                LearningChildProfileUtil.resolveProvince(child),
                LearningChildProfileUtil.resolveCity(child),
                LearningChildProfileUtil.resolveTextbook(child),
                LearningChildProfileUtil.resolveSemester(child),
                g);
        if (release == null) {
            ov.setGraphReady(false);
            ov.setGraphReleaseId(null);
            ov.setGraphSkillCountAtGrade(0);
        } else {
            long skillN = learningKgService.countSkillNodesAtGrade(release.getId(), g);
            ov.setGraphReleaseId(release.getId());
            ov.setGraphSkillCountAtGrade((int) skillN);
            ov.setGraphReady(skillN > 0);
        }
        ov.setWeeklyDigest(weeklyDigest(parentUserId, childId, weekStart));
        return ov;
    }

    @Override
    public LearningSessionPageVO pageSessions(
            Long parentUserId, Long childId, String weekStart, int page, int pageSize) {
        ParentChildAccessHelper.ensureParentCanAccessChildById(
                deviceChildDao, parentDeviceBindingDao, parentUserId, childId);
        WeekRange range = resolveWeekRange(weekStart);
        int p = Math.max(1, page);
        int size = pageSize <= 0 ? 20 : Math.min(pageSize, 100);
        Page<LearningHomeworkSessionEntity> pageReq = new Page<>(p, size);
        Page<LearningHomeworkSessionEntity> result = sessionDao.selectPage(
                pageReq,
                new LambdaQueryWrapper<LearningHomeworkSessionEntity>()
                        .eq(LearningHomeworkSessionEntity::getChildId, childId)
                        .ge(LearningHomeworkSessionEntity::getStartedAt, range.from)
                        .lt(LearningHomeworkSessionEntity::getStartedAt, range.toExclusive)
                        .orderByDesc(LearningHomeworkSessionEntity::getStartedAt));
        List<LearningSessionItemVO> list = new ArrayList<>();
        for (LearningHomeworkSessionEntity s : result.getRecords()) {
            list.add(toSessionItem(s));
        }
        boolean hasMore = (long) p * size < result.getTotal();
        return new LearningSessionPageVO(list, result.getTotal(), p, size, hasMore);
    }

    @Override
    public LearningSessionDetailVO getSessionDetail(Long parentUserId, Long sessionId) {
        if (sessionId == null) {
            throw new RenException("sessionId 必填");
        }
        LearningHomeworkSessionEntity s = sessionDao.selectById(sessionId);
        if (s == null) {
            throw new RenException("session 不存在");
        }
        ParentChildAccessHelper.ensureParentCanAccessChildById(
                deviceChildDao, parentDeviceBindingDao, parentUserId, s.getChildId());
        LearningSessionDetailVO vo = new LearningSessionDetailVO();
        vo.setId(s.getId());
        vo.setChildId(s.getChildId());
        vo.setStartedAt(s.getStartedAt());
        vo.setEndedAt(s.getEndedAt());
        vo.setObservationLevel(s.getObservationLevel());
        vo.setUserTurnCount(s.getUserTurnCount());
        vo.setPhotoCount(s.getPhotoCount());
        vo.setDurationSec(durationSec(s));
        vo.setEndReason(s.getEndReason());
        Map<String, Object> summary = parseSummaryMap(s.getSummaryJson());
        vo.setSummary(summary);
        vo.setParentHeadline(stringField(summary, "parent_headline"));
        vo.setParentSuggestion(stringField(summary, "parent_suggestion"));
        if (StringUtils.isBlank(vo.getParentHeadline())) {
            vo.setParentHeadline(headlineFromSummary(s));
        }
        vo.setSkillCodes(collectSessionSkillCodes(s.getId()));
        return vo;
    }

    private LearningSessionItemVO toSessionItem(LearningHomeworkSessionEntity s) {
        LearningSessionItemVO item = new LearningSessionItemVO();
        item.setId(s.getId());
        item.setStartedAt(s.getStartedAt());
        item.setEndedAt(s.getEndedAt());
        item.setObservationLevel(s.getObservationLevel());
        item.setUserTurnCount(s.getUserTurnCount());
        item.setPhotoCount(s.getPhotoCount());
        item.setDurationSec(durationSec(s));
        item.setEndReason(s.getEndReason());
        item.setParentHeadline(headlineFromSummary(s));
        return item;
    }

    private String headlineFromSummary(LearningHomeworkSessionEntity s) {
        Map<String, Object> m = parseSummaryMap(s.getSummaryJson());
        String h = stringField(m, "parent_headline");
        if (StringUtils.isNotBlank(h)) {
            return h;
        }
        String level = s.getObservationLevel();
        if ("strong".equals(level)) {
            return "本次有拍题记录";
        }
        if ("medium".equals(level)) {
            return "本次以口头提问为主";
        }
        if ("weak".equals(level)) {
            return "本次缺少有效问答";
        }
        return "作业辅导记录";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSummaryMap(String json) {
        if (StringUtils.isBlank(json)) {
            return Map.of();
        }
        try {
            return JsonUtils.parseObject(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String stringField(Map<String, Object> m, String key) {
        if (m == null || !m.containsKey(key)) {
            return null;
        }
        Object v = m.get(key);
        return v != null ? String.valueOf(v) : null;
    }

    private List<String> collectSessionSkillCodes(Long sessionId) {
        List<LearningEvidenceEventEntity> events = evidenceDao.selectList(
                new LambdaQueryWrapper<LearningEvidenceEventEntity>()
                        .eq(LearningEvidenceEventEntity::getSessionId, sessionId)
                        .in(LearningEvidenceEventEntity::getEventType, "DIAGNOSIS_VISION", "DIAGNOSIS_VERBAL"));
        List<String> codes = new ArrayList<>();
        for (LearningEvidenceEventEntity ev : events) {
            if (StringUtils.isBlank(ev.getSkillCodes())) {
                continue;
            }
            try {
                List<String> part = JsonUtils.parseArray(ev.getSkillCodes(), String.class);
                if (part != null) {
                    for (String c : part) {
                        if (StringUtils.isNotBlank(c) && !codes.contains(c)) {
                            codes.add(c);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return codes;
    }

    private Map<String, Integer> aggregateErrorClassDistribution(Long childId, Date from, Date toExclusive) {
        List<LearningEvidenceEventEntity> events = evidenceDao.selectList(
                new LambdaQueryWrapper<LearningEvidenceEventEntity>()
                        .eq(LearningEvidenceEventEntity::getChildId, childId)
                        .ge(LearningEvidenceEventEntity::getOccurredAt, from)
                        .lt(LearningEvidenceEventEntity::getOccurredAt, toExclusive)
                        .in(LearningEvidenceEventEntity::getEventType, "DIAGNOSIS_VISION", "DIAGNOSIS_VERBAL"));
        Map<String, Integer> dist = new HashMap<>();
        for (LearningEvidenceEventEntity ev : events) {
            if (StringUtils.isBlank(ev.getPayload())) {
                continue;
            }
            try {
                Map<?, ?> payload = JsonUtils.parseObject(ev.getPayload(), Map.class);
                Object ec = payload != null ? payload.get("error_class") : null;
                if (ec == null) {
                    continue;
                }
                String key = String.valueOf(ec);
                dist.put(key, dist.getOrDefault(key, 0) + 1);
            } catch (Exception ignored) {
            }
        }
        return dist;
    }

    private List<LearningRemedialMissionBriefVO> loadRemedialMissionsForWeek(
            Long childId, Date from, Date toExclusive) {
        List<ParentShadowMissionEntity> rows = parentShadowMissionDao.selectList(
                new LambdaQueryWrapper<ParentShadowMissionEntity>()
                        .eq(ParentShadowMissionEntity::getChildId, childId)
                        .eq(ParentShadowMissionEntity::getSource, "learning")
                        .ge(ParentShadowMissionEntity::getCreateTime, from)
                        .lt(ParentShadowMissionEntity::getCreateTime, toExclusive)
                        .orderByDesc(ParentShadowMissionEntity::getId)
                        .last("LIMIT 10"));
        List<LearningRemedialMissionBriefVO> out = new ArrayList<>();
        for (ParentShadowMissionEntity e : rows) {
            LearningRemedialMissionBriefVO b = new LearningRemedialMissionBriefVO();
            b.setId(e.getId());
            b.setTitle(e.getTitle());
            b.setStatus(e.getStatus());
            b.setSkillCode(e.getSkillCode());
            b.setEndsAt(e.getEndsAt());
            b.setCreateTime(e.getCreateTime());
            out.add(b);
        }
        return out;
    }

    private WeekRange resolveWeekRange(String weekStart) {
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

    private LearningHomeworkSessionEntity requireOpenSession(String sessionUuid) {
        LearningHomeworkSessionEntity s = sessionDao.selectOne(
                new LambdaQueryWrapper<LearningHomeworkSessionEntity>()
                        .eq(LearningHomeworkSessionEntity::getSessionUuid, sessionUuid));
        if (s == null) {
            throw new RenException("session 不存在");
        }
        if (s.getEndedAt() != null) {
            throw new RenException("session 已结束");
        }
        return s;
    }

    private void appendEvidence(
            LearningHomeworkSessionEntity s,
            String type,
            String payload,
            List<String> skills,
            List<String> misconceptions,
            BigDecimal confidence,
            String idempotencyKey) {
        LearningEvidenceEventEntity e = new LearningEvidenceEventEntity();
        e.setSessionId(s.getId());
        e.setChildId(s.getChildId());
        e.setEventType(type);
        e.setOccurredAt(new Date());
        e.setPayload(payload);
        e.setSkillCodes(skills != null ? JsonUtils.toJsonString(skills) : null);
        e.setMisconceptionCodes(misconceptions != null ? JsonUtils.toJsonString(misconceptions) : null);
        e.setConfidence(confidence);
        e.setIdempotencyKey(idempotencyKey);
        e.setCreateTime(new Date());
        try {
            evidenceDao.insert(e);
        } catch (DuplicateKeyException ignored) {
            // idempotent
        }
    }

    private String observationLevel(LearningHomeworkSessionEntity s) {
        if (s.getPhotoCount() != null && s.getPhotoCount() > 0) {
            return "strong";
        }
        if (s.getUserTurnCount() != null && s.getUserTurnCount() > 0) {
            return "medium";
        }
        return "weak";
    }

    private Map<String, Object> buildSummary(LearningHomeworkSessionEntity s, String level) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("observation_level", level);
        m.put("duration_sec", durationSec(s));
        m.put("user_turn_count", s.getUserTurnCount());
        m.put("photo_count", s.getPhotoCount());
        if ("weak".equals(level)) {
            m.put("parent_headline", "本次作业辅导缺少问答或拍题，无法判断掌握情况。");
        } else if ("strong".equals(level)) {
            m.put("parent_headline", "本次有拍题记录，已写入学情。");
        } else {
            m.put("parent_headline", "本次以口头提问为主，学情证据偏弱。");
        }
        m.put("parent_suggestion", "weak".equals(level)
                ? "下次可鼓励孩子拍一道典型错题。"
                : "可在周报查看薄弱知识点。");
        return m;
    }

    private long durationSec(LearningHomeworkSessionEntity s) {
        if (s.getStartedAt() == null || s.getEndedAt() == null) {
            return 0;
        }
        return Math.max(0, (s.getEndedAt().getTime() - s.getStartedAt().getTime()) / 1000);
    }

    private void applySkillState(
            LearningHomeworkSessionEntity s,
            String skillCode,
            boolean positive,
            BigDecimal confidence,
            boolean vision) {
        KgNodeEntity node = kgNodeDao.selectOne(
                new LambdaQueryWrapper<KgNodeEntity>().eq(KgNodeEntity::getCode, skillCode));
        if (node == null) {
            return;
        }
        LearnerSkillStateEntity st = learnerSkillStateDao.selectOne(
                new LambdaQueryWrapper<LearnerSkillStateEntity>()
                        .eq(LearnerSkillStateEntity::getChildId, s.getChildId())
                        .eq(LearnerSkillStateEntity::getSkillNodeId, node.getId()));
        Date now = new Date();
        if (st == null) {
            st = new LearnerSkillStateEntity();
            st.setChildId(s.getChildId());
            st.setSkillNodeId(node.getId());
            st.setGraphReleaseId(s.getGraphReleaseId());
            st.setEvidenceStage("SCAFFOLDED");
            st.setPMastery(new BigDecimal("0.50"));
            st.setEvidenceCount(0);
        }
        BigDecimal delta = vision
                ? (positive ? new BigDecimal("0.10") : new BigDecimal("-0.15"))
                : (positive ? new BigDecimal("0.05") : new BigDecimal("-0.05"));
        BigDecimal next = st.getPMastery().add(delta);
        if (next.compareTo(BigDecimal.ZERO) < 0) {
            next = BigDecimal.ZERO;
        }
        if (next.compareTo(BigDecimal.ONE) > 0) {
            next = BigDecimal.ONE;
        }
        st.setPMastery(next);
        st.setEvidenceCount(st.getEvidenceCount() + 1);
        st.setLastEvidenceAt(now);
        st.setUpdateTime(now);
        if (st.getId() == null) {
            learnerSkillStateDao.insert(st);
        } else {
            learnerSkillStateDao.updateById(st);
        }
    }

    private Diagnosis diagnoseVerbal(DeviceChildEntity child, Long releaseId, String text) {
        Diagnosis d = new Diagnosis();
        d.payload = new HashMap<>();
        d.payload.put("source", "verbal");
        d.payload.put("text", StringUtils.abbreviate(text, 500));
        if (releaseId == null || child == null || StringUtils.isBlank(text)) {
            d.confidence = new BigDecimal("0.30");
            return d;
        }
        Integer grade = child.getCurrentGrade();
        List<KgNodeRevisionEntity> revs = kgNodeRevisionDao.selectList(
                new LambdaQueryWrapper<KgNodeRevisionEntity>().eq(KgNodeRevisionEntity::getGraphReleaseId, releaseId));
        String t = text.toLowerCase();
        for (KgNodeRevisionEntity rev : revs) {
            KgNodeEntity node = kgNodeDao.selectById(rev.getNodeId());
            if (node == null || !LearningKgNodeTypeUtil.isMasterySkill(node)) {
                continue;
            }
            if (grade != null && rev.getGrade() != null && !grade.equals(rev.getGrade())) {
                continue;
            }
            if (t.contains(rev.getName()) || t.contains(node.getCode().toLowerCase())) {
                d.skillCodes.add(node.getCode());
                d.confidence = new BigDecimal("0.65");
                break;
            }
            if (t.matches(".*[乘×x].*") && node.getCode().contains("MUL")) {
                d.skillCodes.add(node.getCode());
                d.confidence = new BigDecimal("0.55");
                break;
            }
            if ((t.contains("减") || t.contains("-")) && node.getCode().contains("SUB")) {
                d.skillCodes.add(node.getCode());
                d.confidence = new BigDecimal("0.55");
                break;
            }
        }
        if (d.skillCodes.isEmpty() && (t.contains("乘") || t.contains("×"))) {
            d.skillCodes.add("MATH.G2.MUL.TWO_DIGIT");
            d.confidence = new BigDecimal("0.50");
        }
        return d;
    }

    private Diagnosis diagnoseVision(DeviceChildEntity child, Long releaseId, String blob) {
        Diagnosis d = new Diagnosis();
        d.payload = new HashMap<>();
        d.payload.put("source", "vision");
        d.correctHint = true;
        if (StringUtils.isBlank(blob)) {
            d.confidence = new BigDecimal("0.30");
            return d;
        }
        String b = blob;
        if (SUB_EXPR.matcher(b).find() || b.contains("减")) {
            d.skillCodes.add("MATH.G1.SUB.TAKE_AWAY_MEANING");
            d.confidence = new BigDecimal("0.75");
        }
        if (b.matches(".*[=＝]\\s*7.*") && b.contains("5") && b.contains("2") && (b.contains("-") || b.contains("减"))) {
            d.correctHint = false;
            d.misconceptionCodes.add("MATH.G1.MIS.ADD_INSTEAD_OF_SUB");
            d.payload.put("error_class", "concept");
            d.payload.put("note", "5-2 写成 7，疑似加法");
        }
        if (b.contains("乘") || b.toLowerCase().contains("x")) {
            d.skillCodes.clear();
            d.skillCodes.add("MATH.G2.MUL.TWO_DIGIT");
        }
        return d;
    }

    private static class Diagnosis {
        List<String> skillCodes = new ArrayList<>();
        List<String> misconceptionCodes = new ArrayList<>();
        BigDecimal confidence = new BigDecimal("0.50");
        boolean correctHint = true;
        Map<String, Object> payload = new HashMap<>();
    }
}
