package xiaozhi.modules.risk.service;

import java.util.List;

import xiaozhi.modules.risk.dto.ChildRiskConfigSaveDTO;
import xiaozhi.modules.risk.dto.ChildRiskRuleSaveDTO;
import xiaozhi.modules.risk.dto.ChildRiskSignalDTO;
import xiaozhi.common.page.PageData;
import xiaozhi.modules.risk.vo.ChildRiskAgentRuntimeVO;
import xiaozhi.modules.risk.vo.ChildRiskConfigVO;
import xiaozhi.modules.risk.vo.ChildRiskEventAdminVO;
import xiaozhi.modules.risk.vo.ChildRiskRulePublicVO;
import xiaozhi.modules.risk.vo.ChildRiskSignalResultVO;
import xiaozhi.modules.risk.vo.ParentRiskNotificationDetailVO;
import xiaozhi.modules.risk.vo.ParentRiskNotificationPageVO;

public interface ChildRiskService {

    ChildRiskSignalResultVO receiveSignal(ChildRiskSignalDTO dto);

    /** 定时调度：投递 outbox 到「家长小程序通知」。 */
    void processPendingOutboxBatch();

    /** 校验家长与该设备孩子有绑定（复用绑定表）。 */
    void verifyParentOwnsChild(Long parentUserId, Long childId);

    List<ChildRiskRulePublicVO> listEnabledRulesForAgent();

    ChildRiskAgentRuntimeVO getAgentRiskRuntime();

    PageData<ChildRiskEventAdminVO> pageEvents(int page, int limit);

    ChildRiskConfigVO getAdminChildRiskConfig();

    void saveAdminChildRiskConfig(ChildRiskConfigSaveDTO dto);

    void saveOrUpdateRule(ChildRiskRuleSaveDTO dto);

    void deleteRule(Long id);

    List<ChildRiskRulePublicVO> listAllRulesForAdmin();

    ParentRiskNotificationPageVO pageNotificationsForParent(Long parentUserId, Long childId, int page, int pageSize);

    long countUnreadForParent(Long parentUserId, Long childId);

    void markReadForParent(Long parentUserId, Long notificationId);

    /** 单条通知详情（校验归属当前家长） */
    ParentRiskNotificationDetailVO getRiskNotificationDetail(Long parentUserId, Long notificationId);
}
