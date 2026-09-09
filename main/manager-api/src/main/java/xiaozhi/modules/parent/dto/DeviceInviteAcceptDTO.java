package xiaozhi.modules.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "接受设备邀请")
public class DeviceInviteAcceptDTO {
    @Schema(description = "邀请 token（分享链接参数）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String inviteToken;

    @Schema(description = "可选：加入时设置本人在该设备上的家庭角色（father/mother/…/other）")
    private String familyRole;
}
