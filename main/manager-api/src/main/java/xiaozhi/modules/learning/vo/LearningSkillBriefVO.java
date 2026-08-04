package xiaozhi.modules.learning.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "知识点简要（前后置关系）")
public class LearningSkillBriefVO {

    private String code;
    private String name;
    @Schema(description = "同 mastery-map 的 status")
    private String status;
}
