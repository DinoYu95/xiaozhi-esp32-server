package xiaozhi.modules.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ParentRiskWatchFromIntentDTO {

    @NotNull
    private Long childId;

    @NotBlank
    @Schema(description = "KEYWORD 或 EVALUATOR")
    private String watchType;

    @NotBlank
    private String userIntent;

    private String refinement;

    private ParentRiskWatchDraftFields previousDraft;
}
