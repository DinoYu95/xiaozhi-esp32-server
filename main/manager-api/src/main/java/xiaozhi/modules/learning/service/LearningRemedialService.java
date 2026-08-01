package xiaozhi.modules.learning.service;

import xiaozhi.modules.parent.vo.ParentShadowMissionUpsertResultVO;

public interface LearningRemedialService {

    void maybeCreateRemedialShadow(
            Long childId,
            String deviceId,
            Long sessionId,
            String primarySkillCode,
            boolean visionWrong,
            java.math.BigDecimal confidence);
}
