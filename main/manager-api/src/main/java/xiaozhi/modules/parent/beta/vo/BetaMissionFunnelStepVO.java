package xiaozhi.modules.parent.beta.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "漏斗单步统计")
public class BetaMissionFunnelStepVO {

    private String stepKey;
    private String title;
    private Boolean required;
    private Integer completedCount;
    private Integer skippedCount;
    private Double completionRate;
}
