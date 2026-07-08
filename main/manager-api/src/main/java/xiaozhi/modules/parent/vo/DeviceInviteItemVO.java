package xiaozhi.modules.parent.vo;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "邀请单列表项")
public class DeviceInviteItemVO {
    private Long inviteId;
    private String deviceId;
    private Date expiresAt;
    private Integer maxUses;
    private Integer usedCount;
    private String status;
    private Date createdAt;
}
