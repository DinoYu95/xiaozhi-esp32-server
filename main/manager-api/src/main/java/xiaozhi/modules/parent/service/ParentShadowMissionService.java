package xiaozhi.modules.parent.service;

import java.util.List;

import xiaozhi.modules.parent.dto.ParentShadowMissionCreateDTO;
import xiaozhi.modules.parent.dto.ParentShadowMissionUpdateDTO;
import xiaozhi.modules.parent.vo.ParentShadowMissionActiveVO;
import xiaozhi.modules.parent.vo.ParentShadowMissionDetailVO;
import xiaozhi.modules.parent.vo.ParentShadowMissionPageVO;
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

    // ---------- 小程序家长 Token 鉴权 ----------

    /**
     * 当前进行中任务列表（校验家长与该孩子设备绑定）。
     */
    List<ParentShadowMissionActiveVO> listActiveForParent(Long parentUserId, Long childId);

    /**
     * 分页查询某孩子的任务；status 为空表示全部状态。
     */
    ParentShadowMissionPageVO pageForParent(Long parentUserId, Long childId, String status, int page, int pageSize);

    /**
     * 任务详情（须为该家长有权管理的孩子下的任务）。
     */
    ParentShadowMissionDetailVO getDetailForParent(Long parentUserId, Long missionId);

    /**
     * 创建任务（同内部 upsert 规则：进行中最多 5 条）。
     */
    ParentShadowMissionUpsertResultVO createForParent(Long parentUserId, ParentShadowMissionCreateDTO dto);

    /**
     * 更新进行中任务：可改标题、说明；可选从当前时刻重算 endsAt。
     */
    void updateForParent(Long parentUserId, Long missionId, ParentShadowMissionUpdateDTO dto);

    /**
     * 取消单条进行中任务。
     */
    void cancelOneForParent(Long parentUserId, Long missionId);

    /**
     * 取消该孩子全部进行中任务。
     */
    void cancelAllForParent(Long parentUserId, Long childId);
}
