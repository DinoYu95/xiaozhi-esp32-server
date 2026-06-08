package xiaozhi.modules.parent.beta.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "内测任务开关")
public class BetaMissionAdminConfigSaveDTO {

    private Boolean enabled;
}
