package xiaozhi.modules.learning.dto;

import lombok.Data;

@Data
public class LearningSessionTurnDTO {
    private String sessionUuid;
    private String text;
    private Long occurredAtMs;
    private String idempotencyKey;
}
