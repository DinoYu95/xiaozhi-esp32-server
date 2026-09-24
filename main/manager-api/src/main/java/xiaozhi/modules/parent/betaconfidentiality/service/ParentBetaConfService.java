package xiaozhi.modules.parent.betaconfidentiality.service;

import java.util.List;
import java.util.Map;

import xiaozhi.common.page.PageData;
import xiaozhi.modules.parent.betaconfidentiality.dto.ParentBetaConfAdminPublishDTO;
import xiaozhi.modules.parent.betaconfidentiality.dto.ParentBetaConfAdminSettingsDTO;
import xiaozhi.modules.parent.betaconfidentiality.dto.ParentBetaConfAgreeDTO;
import xiaozhi.modules.parent.betaconfidentiality.vo.ParentBetaConfAdminOverviewVO;
import xiaozhi.modules.parent.betaconfidentiality.vo.ParentBetaConfDocumentVO;
import xiaozhi.modules.parent.betaconfidentiality.vo.ParentBetaConfHistoryItemVO;
import xiaozhi.modules.parent.betaconfidentiality.vo.ParentBetaConfPendingUserVO;
import xiaozhi.modules.parent.betaconfidentiality.vo.ParentBetaConfStatusVO;

public interface ParentBetaConfService {

    ParentBetaConfDocumentVO getPublishedDocument();

    ParentBetaConfStatusVO getStatus(Long parentUserId);

    void agree(Long parentUserId, ParentBetaConfAgreeDTO dto, String clientIp, String userAgent);

    boolean isBetaConfEnabled();

    boolean isBetaConfRequired(Long parentUserId);

    ParentBetaConfAdminOverviewVO adminOverview();

    void adminSaveSettings(ParentBetaConfAdminSettingsDTO dto);

    void adminPublish(ParentBetaConfAdminPublishDTO dto);

    List<ParentBetaConfHistoryItemVO> adminHistory();

    PageData<ParentBetaConfPendingUserVO> adminPendingUsers(Map<String, Object> params);
}
