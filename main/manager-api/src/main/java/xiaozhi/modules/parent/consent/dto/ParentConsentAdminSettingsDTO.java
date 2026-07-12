package xiaozhi.modules.parent.consent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "智控台保存协议设置")
public class ParentConsentAdminSettingsDTO {

    @Schema(description = "总开关")
    private Boolean enabled;

    @Schema(description = "owner_only | all_members")
    private String deviceBlockMode;

    @Schema(description = "设备未同意 TTS 文案")
    private String deviceBlockedPrompt;

    @Schema(description = "展示用保留天数")
    private Integer retentionDaysDisplay;
}
