package xiaozhi.modules.learning.dto;

import lombok.Data;

@Data
public class LearningSessionPhotoDTO {
    private String sessionUuid;
    private String visionText;
    private String userQuestion;
    private String assistantReply;
    private Long occurredAtMs;
    private String idempotencyKey;
}
