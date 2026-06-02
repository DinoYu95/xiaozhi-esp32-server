package xiaozhi.modules.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ParentRiskWatchAuditDTO {

    @NotBlank
    @Schema(description = "approve 或 reject")
    private String action;

    private String auditNote;

    @Schema(description = "reject 时建议填写")
    private String rejectReason;
}
