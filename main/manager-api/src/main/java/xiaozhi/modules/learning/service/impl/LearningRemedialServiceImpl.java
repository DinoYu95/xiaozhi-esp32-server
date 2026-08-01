package xiaozhi.modules.learning.service.impl;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.RequiredArgsConstructor;
import xiaozhi.common.utils.JsonUtils;
import xiaozhi.modules.learning.dao.KgEdgeDao;
import xiaozhi.modules.learning.dao.KgNodeDao;
import xiaozhi.modules.learning.dao.KgNodeRevisionDao;
import xiaozhi.modules.learning.dao.LearningEvidenceEventDao;
import xiaozhi.modules.learning.dao.LearningHomeworkSessionDao;
import xiaozhi.modules.learning.entity.KgEdgeEntity;
import xiaozhi.modules.learning.entity.KgNodeEntity;
import xiaozhi.modules.learning.entity.KgNodeRevisionEntity;
import xiaozhi.modules.learning.entity.LearningEvidenceEventEntity;
import xiaozhi.modules.learning.entity.LearningHomeworkSessionEntity;
import xiaozhi.modules.learning.service.LearningRemedialService;
import xiaozhi.modules.parent.dao.ParentDeviceBindingDao;
import xiaozhi.modules.parent.dao.ParentShadowMissionDao;
import xiaozhi.modules.parent.entity.ParentDeviceBindingEntity;
import xiaozhi.modules.parent.entity.ParentShadowMissionEntity;
import xiaozhi.modules.parent.service.ParentShadowMissionService;
import xiaozhi.modules.parent.util.ParentDeviceAccessHelper;
import xiaozhi.modules.parent.vo.ParentShadowMissionUpsertResultVO;

@Service
@RequiredArgsConstructor
public class LearningRemedialServiceImpl implements LearningRemedialService {

    private static final int LEARNING_DURATION_MIN = 120;
    private static final BigDecimal TRIGGER_CONF = new BigDecimal("0.70");

    private final ParentShadowMissionService parentShadowMissionService;
    private final ParentShadowMissionDao parentShadowMissionDao;
    private final ParentDeviceBindingDao parentDeviceBindingDao;
    private final KgNodeDao kgNodeDao;
    private final KgNodeRevisionDao kgNodeRevisionDao;
    private final KgEdgeDao kgEdgeDao;
    private final LearningHomeworkSessionDao learningHomeworkSessionDao;
    private final LearningEvidenceEventDao learningEvidenceEventDao;

    @Override
    public void maybeCreateRemedialShadow(
            Long childId,
            String deviceId,
            Long sessionId,
            String primarySkillCode,
            boolean visionWrong,
            BigDecimal confidence) {
        if (childId == null || StringUtils.isBlank(primarySkillCode)) {
            return;
        }
        if (visionWrong) {
            if (confidence != null && confidence.compareTo(TRIGGER_CONF) < 0) {
                return;
            }
        } else {
            return;
        }
        long activeLearning = parentShadowMissionDao.selectCount(
                new LambdaQueryWrapper<ParentShadowMissionEntity>()
                        .eq(ParentShadowMissionEntity::getChildId, childId)
                        .eq(ParentShadowMissionEntity::getStatus, ParentShadowMissionEntity.STATUS_ACTIVE)
                        .eq(ParentShadowMissionEntity::getSource, "learning")
                        .eq(ParentShadowMissionEntity::getSkillCode, primarySkillCode));
        if (activeLearning > 0) {
            return;
        }
        ParentDeviceBindingEntity owner = ParentDeviceAccessHelper.findPrimaryOwner(
                parentDeviceBindingDao, deviceId);
        if (owner == null || owner.getParentUserId() == null) {
            return;
        }
        LearningHomeworkSessionEntity session = sessionId != null
                ? learningHomeworkSessionDao.selectById(sessionId) : null;
        Long releaseId = session != null ? session.getGraphReleaseId() : null;
        InterventionPick pick = pickIntervention(releaseId, primarySkillCode);
        String title = pick.title;
        String instructions = pick.instructions;
        ParentShadowMissionUpsertResultVO vo = parentShadowMissionService.createLearningRemedial(
                deviceId,
                childId,
                owner.getParentUserId(),
                sessionId,
                primarySkillCode,
                title,
                instructions,
                LEARNING_DURATION_MIN);
        if (vo != null && vo.getId() != null && sessionId != null) {
            LearningEvidenceEventEntity ev = new LearningEvidenceEventEntity();
            ev.setSessionId(sessionId);
            ev.setChildId(childId);
            ev.setEventType("SHADOW_REMEDIAL_CREATED");
            ev.setOccurredAt(new Date());
            ev.setPayload(JsonUtils.toJsonString(vo));
            ev.setSkillCodes(JsonUtils.toJsonString(List.of(primarySkillCode)));
            ev.setIdempotencyKey("remedial:" + sessionId + ":" + primarySkillCode);
            ev.setCreateTime(new Date());
            try {
                learningEvidenceEventDao.insert(ev);
            } catch (Exception ignored) {
                // duplicate idempotency
            }
        }
    }

    private InterventionPick pickIntervention(Long releaseId, String skillCode) {
        KgNodeEntity skill = kgNodeDao.selectOne(
                new LambdaQueryWrapper<KgNodeEntity>().eq(KgNodeEntity::getCode, skillCode));
        if (skill == null || releaseId == null) {
            return defaultPick(skillCode);
        }
        List<KgEdgeEntity> misEdges = kgEdgeDao.selectList(
                new LambdaQueryWrapper<KgEdgeEntity>()
                        .eq(KgEdgeEntity::getGraphReleaseId, releaseId)
                        .eq(KgEdgeEntity::getFromNodeId, skill.getId())
                        .eq(KgEdgeEntity::getEdgeType, "HAS_MISCONCEPTION"));
        for (KgEdgeEntity me : misEdges) {
            List<KgEdgeEntity> rem = kgEdgeDao.selectList(
                    new LambdaQueryWrapper<KgEdgeEntity>()
                            .eq(KgEdgeEntity::getGraphReleaseId, releaseId)
                            .eq(KgEdgeEntity::getFromNodeId, me.getToNodeId())
                            .eq(KgEdgeEntity::getEdgeType, "REMEDIATED_BY")
                            .last("LIMIT 1"));
            if (!rem.isEmpty()) {
                KgNodeRevisionEntity rev = kgNodeRevisionDao.selectOne(
                        new LambdaQueryWrapper<KgNodeRevisionEntity>()
                                .eq(KgNodeRevisionEntity::getGraphReleaseId, releaseId)
                                .eq(KgNodeRevisionEntity::getNodeId, rem.get(0).getToNodeId()));
                if (rev != null) {
                    return new InterventionPick(
                            "回炉：" + rev.getName(),
                            rev.getDescription() != null ? rev.getDescription()
                                    : "请用轻松的方式带孩子练一练「" + rev.getName() + "」，完成后自然点到即可。");
                }
            }
        }
        List<KgEdgeEntity> direct = kgEdgeDao.selectList(
                new LambdaQueryWrapper<KgEdgeEntity>()
                        .eq(KgEdgeEntity::getGraphReleaseId, releaseId)
                        .eq(KgEdgeEntity::getFromNodeId, skill.getId())
                        .eq(KgEdgeEntity::getEdgeType, "REMEDIATED_BY")
                        .last("LIMIT 1"));
        if (!direct.isEmpty()) {
            KgNodeRevisionEntity rev = kgNodeRevisionDao.selectOne(
                    new LambdaQueryWrapper<KgNodeRevisionEntity>()
                            .eq(KgNodeRevisionEntity::getGraphReleaseId, releaseId)
                            .eq(KgNodeRevisionEntity::getNodeId, direct.get(0).getToNodeId()));
            if (rev != null) {
                return new InterventionPick("回炉：" + rev.getName(), StringUtils.defaultString(rev.getDescription()));
            }
        }
        KgNodeRevisionEntity skillRev = kgNodeRevisionDao.selectOne(
                new LambdaQueryWrapper<KgNodeRevisionEntity>()
                        .eq(KgNodeRevisionEntity::getGraphReleaseId, releaseId)
                        .eq(KgNodeRevisionEntity::getNodeId, skill.getId()));
        String name = skillRev != null ? skillRev.getName() : skillCode;
        return new InterventionPick("巩固：" + name, "请陪孩子用口头小练习巩固「" + name + "」，不要直接给作业答案。");
    }

    private InterventionPick defaultPick(String skillCode) {
        return new InterventionPick("巩固练习", "请陪孩子练一练相关知识点（" + skillCode + "）。");
    }

    private record InterventionPick(String title, String instructions) {
    }
}
