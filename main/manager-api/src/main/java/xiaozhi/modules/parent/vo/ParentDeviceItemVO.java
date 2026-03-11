package xiaozhi.modules.parent.vo;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "家长端已绑定设备项")
public class ParentDeviceItemVO {
    @Schema(description = "设备 id（mac）")
    private String deviceId;
    @Schema(description = "设备展示名称，为主孩子名+「的机器人」，无主孩子时为「我的机器人」")
    private String deviceName;
    @Schema(description = "主孩子名字")
    private String ownerChildName;
    @Schema(description = "绑定时间")
    private Date bindTime;
    @Schema(description = "最后连接时间")
    private Date lastConnectedAt;
    @Schema(description = "是否在线：基于 lastConnectedAt 判断，5 分钟内有心跳视为在线")
    private Boolean isOnline;
    @Schema(description = "电量百分比，设备未上报时为占位值 0，后续设备上报后替换")
    private Integer batteryLevel;
    @Schema(description = "当前连接的 WiFi 名称，设备未上报时为占位值 \"--\"，后续设备上报后替换")
    private String wifiName;
}
