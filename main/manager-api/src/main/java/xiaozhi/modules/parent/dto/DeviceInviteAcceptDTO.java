package xiaozhi.modules.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "接受设备邀请")
public class DeviceInviteAcceptDTO {
    @Schema(description = "邀请 token（分享链接参数）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String inviteToken;
}
