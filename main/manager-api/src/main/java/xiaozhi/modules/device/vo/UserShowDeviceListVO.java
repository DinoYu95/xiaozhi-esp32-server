package xiaozhi.modules.device.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户显示设备列表VO")
public class UserShowDeviceListVO {

    @Schema(description = "应用 SWU 版本")
    private String appVersion;

    @Schema(description = "系统 SWU 版本")
    private String systemVersion;

    @Schema(description = "绑定用户名称")
    private String bindUserName;

    @Schema(description = "业务设备类型")
    private String deviceType;

    @Schema(description = "硬件板型 board")
    private String board;

    @Schema(description = "设备唯一标识符")
    private String id;

    @Schema(description = "mac地址")
    private String macAddress;

    @Schema(description = "开启OTA")
    private Integer otaUpgrade;

    @Schema(description = "最近对话时间")
    private String recentChatTime;

}