package xiaozhi.modules.parent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "接受邀请响应")
public class DeviceInviteAcceptVO {
    private String deviceId;
    private String message;
    private String role;
    private Boolean alreadyMember;
}
