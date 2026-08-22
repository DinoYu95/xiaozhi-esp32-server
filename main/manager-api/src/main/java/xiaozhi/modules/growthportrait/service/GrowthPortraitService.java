package xiaozhi.modules.growthportrait.service;

import xiaozhi.modules.growthportrait.dto.GrowthEvidenceIngestDTO;
import xiaozhi.modules.growthportrait.dto.GrowthEvidenceSessionDTO;
import xiaozhi.modules.growthportrait.dto.TeachingGpPublishDTO;
import xiaozhi.modules.growthportrait.vo.GrowthGraphVO;
import xiaozhi.modules.growthportrait.vo.GrowthNotificationPageVO;
import xiaozhi.modules.growthportrait.vo.GrowthWeeklyDigestVO;

public interface GrowthPortraitService {

    Long publishFromTeaching(TeachingGpPublishDTO body);

    GrowthGraphVO getGraph(Long parentUserId, Long childId);

    GrowthGraphVO getGraphByChildId(Long childId);

    void ingestEvidence(GrowthEvidenceIngestDTO body);

    void ingestSession(GrowthEvidenceSessionDTO body);

    GrowthNotificationPageVO listNotifications(Long parentUserId, Long childId, int page, int pageSize);

    void markNotificationRead(Long parentUserId, Long notificationId);

    GrowthWeeklyDigestVO weeklyDigest(Long parentUserId, Long childId, String weekStart);

    void updateSettings(Long parentUserId, Long childId, Boolean instantNotifyEnabled, Boolean weeklyDigestEnabled);
}
