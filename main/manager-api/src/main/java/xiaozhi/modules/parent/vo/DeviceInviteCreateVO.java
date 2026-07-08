package xiaozhi.modules.parent.vo;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "生成邀请响应")
public class DeviceInviteCreateVO {
    private String inviteToken;
    private Date expiresAt;
    private String shareTitle;
    private String sharePath;
    private Integer maxUses;
}
