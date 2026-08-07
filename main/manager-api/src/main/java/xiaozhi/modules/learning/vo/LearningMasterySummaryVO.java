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
    @Schema(description = "观察覆盖口径，如 grade_cumulative")
    private String coverageScope;
    @Schema(description = "展示用学期/范围文案")
    private String termLabel;
    @Schema(description = "本周（weekStart 起 7 天）有观察的知识点数")
    private Integer observedThisWeekCount;
    @Schema(description = "本次建议巩固（consolidateThisPeriod=true）数量")
    private Integer suggestedConsolidateCount;
}
