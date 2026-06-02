package xiaozhi.modules.parent.service;

import xiaozhi.modules.parent.dto.ParentRiskWatchDraftFields;
import xiaozhi.modules.parent.vo.ParentRiskWatchDraftVO;

public interface ParentRiskWatchAssistService {

    ParentRiskWatchDraftVO generateDraft(
            String watchType, String userIntent, String refinement, ParentRiskWatchDraftFields previousDraft);
}
