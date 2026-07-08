package xiaozhi.modules.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "撤销设备邀请")
public class DeviceInviteRevokeDTO {
    private String inviteToken;
    private Long inviteId;
}
