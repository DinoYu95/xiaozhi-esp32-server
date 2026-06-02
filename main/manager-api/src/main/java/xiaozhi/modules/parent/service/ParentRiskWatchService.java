package xiaozhi.modules.parent.service;

import xiaozhi.common.page.PageData;
import xiaozhi.modules.parent.dto.ParentRiskPreferenceSaveDTO;
import xiaozhi.modules.parent.dto.ParentRiskWatchAuditDTO;
import xiaozhi.modules.parent.dto.ParentRiskWatchCreateDTO;
import xiaozhi.modules.parent.dto.ParentRiskWatchDraftFields;
import xiaozhi.modules.parent.dto.ParentRiskWatchFromIntentDTO;
import xiaozhi.modules.parent.vo.ParentRiskPreferenceVO;
import xiaozhi.modules.parent.vo.ParentRiskWatchDraftVO;
import xiaozhi.modules.parent.vo.ParentRiskWatchOverviewVO;
import xiaozhi.modules.parent.vo.ParentRiskWatchVO;

public interface ParentRiskWatchService {

    ParentRiskWatchOverviewVO getOverview(Long parentUserId, Long childId);

    ParentRiskPreferenceVO savePreference(Long parentUserId, ParentRiskPreferenceSaveDTO dto);

    ParentRiskWatchDraftVO draftFromIntent(Long parentUserId, ParentRiskWatchFromIntentDTO dto);

    ParentRiskWatchVO create(Long parentUserId, ParentRiskWatchCreateDTO dto);

    ParentRiskWatchVO getDetail(Long parentUserId, Long id);

    void disable(Long parentUserId, Long id);

    PageData<ParentRiskWatchVO> adminPage(String status, int page, int limit);

    ParentRiskWatchVO adminGetDetail(Long id);

    void adminAudit(Long id, ParentRiskWatchAuditDTO dto);
}
