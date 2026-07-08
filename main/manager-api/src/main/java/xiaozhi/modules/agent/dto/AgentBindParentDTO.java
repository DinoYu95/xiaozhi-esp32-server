package xiaozhi.modules.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "智控台为智能体绑定家长")
public class AgentBindParentDTO {
    @Schema(description = "家长用户 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long parentUserId;

    @Schema(description = "设备 ID（可选；多设备时指定，默认取该 Agent 下第一台设备）")
    private String deviceId;

    @Schema(description = "是否替换已有 Owner（已激活时更新家长须为 true）")
    private Boolean replaceExisting;
}
