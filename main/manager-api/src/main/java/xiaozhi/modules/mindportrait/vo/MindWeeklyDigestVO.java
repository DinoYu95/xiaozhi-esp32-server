package xiaozhi.modules.mindportrait.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "心绪图谱周报")
public class MindWeeklyDigestVO {

    private String weekStart;
    private String weekEnd;
    private List<Highlight> topHighlights;
    private String parentTip;
    private int newStrongCount;

    @Data
    public static class Highlight {
        private String nodeCode;
        private String label;
        private String shortDesc;
        private int strength;
    }
}
