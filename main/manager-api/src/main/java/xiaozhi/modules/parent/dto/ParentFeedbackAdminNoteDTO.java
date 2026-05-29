package xiaozhi.modules.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "管理端仅更新内部备注")
public class ParentFeedbackAdminNoteDTO {

    @NotBlank
    @Schema(description = "内部备注", requiredMode = Schema.RequiredMode.REQUIRED)
    private String adminNote;
}
