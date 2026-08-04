package xiaozhi.modules.learning.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "掌握地图汇总")
public class LearningMasterySummaryVO {

    private Integer skillTotal;
    private Integer observedCount;
    private Integer needConsolidateCount;
    private Integer practicingCount;
    private Integer stableCount;
    private Integer unobservedCount;
}
