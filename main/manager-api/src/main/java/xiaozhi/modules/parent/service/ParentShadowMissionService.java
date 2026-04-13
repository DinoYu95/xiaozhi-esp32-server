package xiaozhi.modules.parent.service;

import xiaozhi.modules.parent.vo.ParentShadowMissionActiveVO;
import xiaozhi.modules.parent.vo.ParentShadowMissionUpsertResultVO;

public interface ParentShadowMissionService {

    /**
     * 当前仍生效的一条任务（按 device + child）；过期会懒标记 expired并返回 null。
     */
    ParentShadowMissionActiveVO getActive(String deviceId, Long childId);

    /**
     * 家长创建或替换当前 active（旧 active 标记为 cancelled）。
     */
    ParentShadowMissionUpsertResultVO upsert(
            Long parentUserId,
            Long childId,
            String title,
            String instructions,
            int durationMinutes);

    void cancel(Long parentUserId, Long childId);
}
