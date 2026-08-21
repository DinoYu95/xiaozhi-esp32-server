package xiaozhi.modules.growthportrait.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "成长星图渲染数据")
public class GrowthGraphVO {

    private Long releaseId;
    private String ageBand;
    private CenterNode center;
    private List<GrowthNodeVO> nodes;
    private List<GrowthLinkVO> links;
    private GrowthRulesVO rules;
    private int strongCount;

    @Data
    public static class CenterNode {
        private String label;
        private String shortDesc;
        private String avatarUrl;
    }

    @Data
    public static class GrowthRulesVO {
        private int observeDays;
        private int weeklyInstantCap;
    }
}
