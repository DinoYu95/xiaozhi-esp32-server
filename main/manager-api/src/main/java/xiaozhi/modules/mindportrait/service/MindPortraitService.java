package xiaozhi.modules.mindportrait.service;

import xiaozhi.modules.mindportrait.dto.MindEvidenceIngestDTO;
import xiaozhi.modules.mindportrait.dto.MindEvidenceSessionDTO;
import xiaozhi.modules.mindportrait.dto.TeachingMpPublishDTO;
import xiaozhi.modules.mindportrait.vo.MindGraphVO;
import xiaozhi.modules.mindportrait.vo.MindNotificationPageVO;
import xiaozhi.modules.mindportrait.vo.MindWeeklyDigestVO;
import xiaozhi.modules.mindportrait.vo.MindWellnessSummaryVO;

public interface MindPortraitService {

    Long publishFromTeaching(TeachingMpPublishDTO body);

    MindGraphVO getGraph(Long parentUserId, Long childId);

    MindGraphVO getGraphByChildId(Long childId);

    MindWellnessSummaryVO getWellnessSummary(Long parentUserId, Long childId);

    void ingestEvidence(MindEvidenceIngestDTO body);

    void ingestSession(MindEvidenceSessionDTO body);

    MindNotificationPageVO listNotifications(Long parentUserId, Long childId, int page, int pageSize);

    void markNotificationRead(Long parentUserId, Long notificationId);

    MindWeeklyDigestVO weeklyDigest(Long parentUserId, Long childId, String weekStart);

    void updateSettings(Long parentUserId, Long childId, Boolean instantNotifyEnabled, Boolean weeklyDigestEnabled);
}
