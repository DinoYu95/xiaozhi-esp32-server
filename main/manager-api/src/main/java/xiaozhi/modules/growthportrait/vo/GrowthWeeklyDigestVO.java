package xiaozhi.modules.growthportrait.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "成长星图周报")
public class GrowthWeeklyDigestVO {

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
