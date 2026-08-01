package xiaozhi.modules.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ParentLiveSessionIdDTO {

    @NotNull
    @Schema(description = "会话 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long sessionId;
}
