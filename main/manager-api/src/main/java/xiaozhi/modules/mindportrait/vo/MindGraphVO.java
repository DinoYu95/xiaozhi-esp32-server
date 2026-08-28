package xiaozhi.modules.mindportrait.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "心绪图谱渲染数据")
public class MindGraphVO {

    private Long releaseId;
    private String ageBand;
    private CenterNode center;
    private List<MindNodeVO> nodes;
    private List<MindLinkVO> links;
    private MindRulesVO rules;
    private int strongCount;

    @Data
    public static class CenterNode {
        private String label;
        private String shortDesc;
        private String avatarUrl;
    }

    @Data
    public static class MindRulesVO {
        private int observeDays;
        private int weeklyInstantCap;
    }
}
