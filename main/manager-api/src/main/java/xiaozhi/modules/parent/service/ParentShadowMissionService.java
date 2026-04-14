package xiaozhi.modules.parent.service;

import java.util.List;

import xiaozhi.modules.parent.vo.ParentShadowMissionActiveVO;
import xiaozhi.modules.parent.vo.ParentShadowMissionUpsertResultVO;

public interface ParentShadowMissionService {

    /**
     * 当前仍生效的任务列表（按 priority、id 升序）；过期会懒标记 expired。
     */
    List<ParentShadowMissionActiveVO> listActive(String deviceId, Long childId);

    /**
     * 兼容：仅返回列表第一条，无则 null。
     */
    ParentShadowMissionActiveVO getActive(String deviceId, Long childId);

    /**
     * 家长新增一条影子任务（不自动取消其他进行中的任务，有数量上限）。
     */
    ParentShadowMissionUpsertResultVO upsert(
            Long parentUserId,
            Long childId,
            String title,
            String instructions,
            int durationMinutes);

    void cancel(Long parentUserId, Long childId);

    /**
     * 孩子侧对话工具：将指定任务标为已完成（须属于该 child且为 active）。
     */
    void completeByChild(Long childId, Long missionId);
}
