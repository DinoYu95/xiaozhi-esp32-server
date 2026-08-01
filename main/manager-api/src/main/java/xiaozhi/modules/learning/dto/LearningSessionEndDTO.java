package xiaozhi.modules.learning.dto;

import lombok.Data;

@Data
public class LearningSessionEndDTO {
    private String sessionUuid;
    private String endReason;
    private Long endedAtMs;
    private Integer userTurnCount;
    private Integer photoCount;
    private Integer longestSilenceSec;
}
