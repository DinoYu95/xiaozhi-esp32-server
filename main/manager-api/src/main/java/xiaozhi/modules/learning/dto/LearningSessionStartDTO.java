package xiaozhi.modules.learning.dto;

import lombok.Data;

@Data
public class LearningSessionStartDTO {
    private String deviceId;
    private Long childId;
    private String sessionUuid;
    private Long startedAtMs;
}
