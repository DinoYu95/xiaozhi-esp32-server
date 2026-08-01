package xiaozhi.modules.learning.vo;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "学习周报")
public class LearningWeeklyDigestVO {

    private String weekStart;
    private String weekEnd;
    private int sessionCount;
    private int strongSessionCount;
    private int mediumSessionCount;
    private int weakSessionCount;
    private List<Map<String, Object>> topWeakSkills;
    private Map<String, Integer> errorClassDistribution;
    private String coverageNote;
    private String parentHeadline;
    private String parentSuggestion;
    private List<LearningRemedialMissionBriefVO> remedialShadowMissions;
}
