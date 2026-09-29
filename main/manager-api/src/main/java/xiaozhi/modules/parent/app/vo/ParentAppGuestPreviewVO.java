package xiaozhi.modules.parent.app.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "游客技能预览（脱敏示例）")
public class ParentAppGuestPreviewVO {

    private List<PreviewSkill> recommendedSkills;
    private String disclaimer;

    @Data
    public static class PreviewSkill {
        private String id;
        private String name;
        private String description;
        private Boolean isPreview;
    }
}
