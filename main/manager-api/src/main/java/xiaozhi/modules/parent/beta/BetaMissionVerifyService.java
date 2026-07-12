package xiaozhi.modules.parent.beta;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.RequiredArgsConstructor;
import xiaozhi.modules.agent.dao.AgentVoicePrintDao;
import xiaozhi.modules.agent.entity.AgentVoicePrintEntity;
import xiaozhi.modules.parent.beta.BetaMissionStepRegistry.StepDef;
import xiaozhi.modules.parent.beta.entity.BetaMissionUserStateEntity;
import xiaozhi.modules.parent.dao.DeviceChildDao;
import xiaozhi.modules.parent.dao.ParentDeviceBindingDao;
import xiaozhi.modules.parent.dao.ParentFeedbackDao;
import xiaozhi.modules.parent.dao.ParentRiskPreferenceDao;
import xiaozhi.modules.parent.dao.ParentRiskWatchDao;
import xiaozhi.modules.parent.dao.ParentUserSkillDao;
import xiaozhi.modules.parent.entity.DeviceChildEntity;
import xiaozhi.modules.parent.entity.ParentDeviceBindingEntity;
import xiaozhi.modules.parent.entity.ParentFeedbackEntity;
import xiaozhi.modules.parent.entity.ParentRiskPreferenceEntity;
import xiaozhi.modules.parent.entity.ParentRiskWatchEntity;
import xiaozhi.modules.parent.entity.ParentUserSkillEntity;
import xiaozhi.modules.parent.util.ParentBetaAccessHelper;

@Service
@RequiredArgsConstructor
public class BetaMissionVerifyService {

    private final ParentDeviceBindingDao parentDeviceBindingDao;
    private final DeviceChildDao deviceChildDao;
    private final AgentVoicePrintDao agentVoicePrintDao;
    private final ParentUserSkillDao parentUserSkillDao;
    private final ParentRiskPreferenceDao parentRiskPreferenceDao;
    private final ParentRiskWatchDao parentRiskWatchDao;
    private final ParentFeedbackDao parentFeedbackDao;

    public boolean isStepCompleted(Long parentUserId, StepDef step, BetaMissionUserStateEntity state) {
        return switch (step.getStepKey()) {
            case "bind_device" -> hasDeviceBinding(parentUserId);
            case "has_child" -> hasAnyDeviceChild(parentUserId);
            case "voiceprint_done" -> hasVoiceprintForContext(state);
            case "skill_created" -> hasUserSkill(parentUserId);
            case "risk_preference_set" -> hasRiskPreference(parentUserId, state);
            case "risk_watch_created" -> hasRiskWatch(parentUserId, state);
            case "risk_alert_viewed" -> hasRiskAlertVisited(state);
            case "feedback_submitted" -> hasFeedback(parentUserId);
            default -> false;
        };
    }

    private boolean hasDeviceBinding(Long parentUserId) {
        return parentDeviceBindingDao.selectCount(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getParentUserId, parentUserId)) > 0;
    }

    private boolean hasAnyDeviceChild(Long parentUserId) {
        var bindings = parentDeviceBindingDao.selectList(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getParentUserId, parentUserId));
        if (bindings.isEmpty()) {
            return false;
        }
        for (ParentDeviceBindingEntity b : bindings) {
            String deviceId = b.getDeviceId();
            if (deviceId == null) {
                continue;
            }
            long count = deviceChildDao.selectCount(
                    new LambdaQueryWrapper<DeviceChildEntity>()
                            .eq(DeviceChildEntity::getDeviceId, deviceId));
            if (count > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean hasVoiceprintForContext(BetaMissionUserStateEntity state) {
        Long childId = state.getContextChildId();
        if (childId == null) {
            return false;
        }
        return agentVoicePrintDao.selectCount(
                new LambdaQueryWrapper<AgentVoicePrintEntity>()
                        .eq(AgentVoicePrintEntity::getChildId, childId)) > 0;
    }

    private boolean hasUserSkill(Long parentUserId) {
        var cohort = ParentBetaAccessHelper.resolveDeviceCohortParentIds(parentDeviceBindingDao, parentUserId);
        if (cohort.isEmpty()) {
            return false;
        }
        return parentUserSkillDao.selectCount(
                new LambdaQueryWrapper<ParentUserSkillEntity>()
                        .in(ParentUserSkillEntity::getParentUserId, cohort)) > 0;
    }

    private boolean hasRiskPreference(Long parentUserId, BetaMissionUserStateEntity state) {
        Long childId = state.getContextChildId();
        if (childId == null) {
            return false;
        }
        return parentRiskPreferenceDao.selectCount(
                new LambdaQueryWrapper<ParentRiskPreferenceEntity>()
                        .eq(ParentRiskPreferenceEntity::getParentUserId, parentUserId)
                        .eq(ParentRiskPreferenceEntity::getChildId, childId)) > 0;
    }

    private boolean hasRiskWatch(Long parentUserId, BetaMissionUserStateEntity state) {
        Long childId = state.getContextChildId();
        if (childId == null) {
            return false;
        }
        return parentRiskWatchDao.selectCount(
                new LambdaQueryWrapper<ParentRiskWatchEntity>()
                        .eq(ParentRiskWatchEntity::getParentUserId, parentUserId)
                        .eq(ParentRiskWatchEntity::getChildId, childId)) > 0;
    }

    private boolean hasRiskAlertVisited(BetaMissionUserStateEntity state) {
        return state.getRiskAlertVisited() != null && state.getRiskAlertVisited() == 1;
    }

    private boolean hasFeedback(Long parentUserId) {
        return parentFeedbackDao.selectCount(
                new LambdaQueryWrapper<ParentFeedbackEntity>()
                        .eq(ParentFeedbackEntity::getParentUserId, parentUserId)) > 0;
    }
}
