package xiaozhi.modules.parent.vo;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "邀请预览")
public class DeviceInvitePreviewVO {
    private Boolean valid;
    /** expired | revoked | exhausted | not_found */
    private String reason;
    private String deviceId;
    private String deviceName;
    private String inviterNickname;
    private Date expiresAt;
    private Boolean alreadyMember;
}
