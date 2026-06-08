package xiaozhi.modules.parent.beta.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "内测任务运行配置")
public class BetaMissionAdminConfigVO {

    private Boolean enabled;
    private String campaignCode;
    private String campaignTitle;
    private Integer stepCount;
    private Integer requiredCount;
}
