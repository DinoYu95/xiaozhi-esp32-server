package xiaozhi.modules.parent.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "保存风险关注侧重")
public class ParentRiskPreferenceSaveDTO {

    @NotNull
    @Schema(description = "device_child.id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long childId;

    @Schema(description = "关注领域 code 列表，如 peer_relation")
    private List<String> focusDomains;
}
