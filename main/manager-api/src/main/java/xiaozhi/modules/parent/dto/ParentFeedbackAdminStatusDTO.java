package xiaozhi.modules.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "管理端更新反馈状态")
public class ParentFeedbackAdminStatusDTO {

    @NotBlank
    @Schema(description = "pending/processing/resolved/wont_fix", requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;

    @Schema(description = "内部备注（可选，会写入 admin_note）")
    private String adminNote;

    @Schema(description = "不修复原因（status=wont_fix 时建议填写）")
    private String wontFixReason;
}
