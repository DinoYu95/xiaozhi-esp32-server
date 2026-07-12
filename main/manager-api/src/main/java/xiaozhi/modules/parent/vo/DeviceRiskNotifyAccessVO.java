package xiaozhi.modules.parent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "当前家长在某设备上的风险通知权限")
public class DeviceRiskNotifyAccessVO {

    private String deviceId;
    @Schema(description = "owner | member")
    private String role;
    @Schema(description = "是否会收到新产生的风险提示")
    private Boolean receiveRiskNotify;
    @Schema(description = "是否可进入「风险通知设置」")
    private Boolean canManageSubscribers;
}
