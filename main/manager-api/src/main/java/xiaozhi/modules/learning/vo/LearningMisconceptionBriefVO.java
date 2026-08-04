package xiaozhi.modules.learning.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "易错点简要")
public class LearningMisconceptionBriefVO {

    private String code;
    private String name;
    private String description;
}
