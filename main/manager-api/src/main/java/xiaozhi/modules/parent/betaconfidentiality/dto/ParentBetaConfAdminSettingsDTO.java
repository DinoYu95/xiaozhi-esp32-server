package xiaozhi.modules.parent.betaconfidentiality.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "内测保密协议开关")
public class ParentBetaConfAdminSettingsDTO {

    private Boolean enabled;
}
