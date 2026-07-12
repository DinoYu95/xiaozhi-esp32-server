package xiaozhi.modules.parent.consent.service;

import java.util.List;
import java.util.Map;

import xiaozhi.common.page.PageData;
import xiaozhi.modules.parent.consent.dto.ParentConsentAdminPublishDTO;
import xiaozhi.modules.parent.consent.dto.ParentConsentAdminSettingsDTO;
import xiaozhi.modules.parent.consent.dto.ParentConsentAgreeDTO;
import xiaozhi.modules.parent.consent.vo.ParentConsentAdminOverviewVO;
import xiaozhi.modules.parent.consent.vo.ParentConsentDocumentVO;
import xiaozhi.modules.parent.consent.vo.ParentConsentHistoryItemVO;
import xiaozhi.modules.parent.consent.vo.ParentConsentPendingUserVO;
import xiaozhi.modules.parent.consent.vo.ParentConsentStatusVO;

public interface ParentConsentService {

    ParentConsentDocumentVO getPublishedDocument();

    ParentConsentStatusVO getStatus(Long parentUserId);

    void agree(Long parentUserId, ParentConsentAgreeDTO dto, String clientIp, String userAgent);

    boolean isConsentEnabled();

    boolean isConsentRequired(Long parentUserId);

    boolean hasAgreedCurrentVersion(Long parentUserId);

    boolean isDeviceConsentOk(String deviceId, String macAddress);

    String getDeviceBlockedPrompt();

    ParentConsentAdminOverviewVO adminOverview();

    void adminSaveSettings(ParentConsentAdminSettingsDTO dto);

    void adminPublish(ParentConsentAdminPublishDTO dto);

    List<ParentConsentHistoryItemVO> adminHistory();

    PageData<ParentConsentPendingUserVO> adminPendingUsers(Map<String, Object> params);
}
