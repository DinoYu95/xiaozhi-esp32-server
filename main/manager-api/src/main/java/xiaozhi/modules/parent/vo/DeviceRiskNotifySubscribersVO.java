package xiaozhi.modules.parent.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "设备风险通知订阅配置")
public class DeviceRiskNotifySubscribersVO {

    private String deviceId;
    private String deviceName;
    @Schema(description = "Owner 是否始终接收（恒为 true）")
    private Boolean ownerAlwaysReceive;
    private List<DeviceMemberItemVO> members;
}
