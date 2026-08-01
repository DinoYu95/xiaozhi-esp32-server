package xiaozhi.modules.learning.service;

import java.util.Map;

import xiaozhi.modules.learning.dto.LearningSessionEndDTO;
import xiaozhi.modules.learning.dto.LearningSessionPhotoDTO;
import xiaozhi.modules.learning.dto.LearningSessionStartDTO;
import xiaozhi.modules.learning.dto.LearningSessionTurnDTO;
import xiaozhi.modules.learning.vo.LearningOverviewVO;
import xiaozhi.modules.learning.vo.LearningSessionDetailVO;
import xiaozhi.modules.learning.vo.LearningSessionPageVO;
import xiaozhi.modules.learning.vo.LearningWeeklyDigestVO;

public interface LearningSessionService {

    Map<String, Object> startSession(LearningSessionStartDTO dto);

    void recordTurn(LearningSessionTurnDTO dto);

    void recordPhoto(LearningSessionPhotoDTO dto);

    Map<String, Object> endSession(LearningSessionEndDTO dto);

    Map<String, Object> getChildLearningContext(Long childId);

    LearningWeeklyDigestVO weeklyDigest(Long parentUserId, Long childId, String weekStart);

    LearningOverviewVO overview(Long parentUserId, Long childId, String weekStart);

    LearningSessionPageVO pageSessions(Long parentUserId, Long childId, String weekStart, int page, int pageSize);

    LearningSessionDetailVO getSessionDetail(Long parentUserId, Long sessionId);
}
