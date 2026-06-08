package xiaozhi.modules.parent.beta.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "内测任务漏斗")
public class BetaMissionFunnelVO {

    private Integer betaTesterTotal;
    private Integer packCompletedTotal;
    private List<BetaMissionFunnelStepVO> steps;
}
