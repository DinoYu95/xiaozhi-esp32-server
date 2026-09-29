package xiaozhi.modules.parent.app.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "小程序审核/访问模式设置")
public class ParentAppAdminSettingsSaveDTO {

    @NotBlank
    @Schema(description = "open | internal_beta", requiredMode = Schema.RequiredMode.REQUIRED)
    private String accessMode;

    @Schema(description = "审核模式开关")
    private Boolean reviewMode;
}
