package xiaozhi.modules.parent.beta.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "内测任务步骤")
public class BetaMissionStepVO {

    private String stepKey;
    private String title;
    private String description;
    private Boolean required;
    private String verifyMode;
    private String status;
    private String actionUrl;
    private Boolean needsContextChild;
    private String navigateType;
}
