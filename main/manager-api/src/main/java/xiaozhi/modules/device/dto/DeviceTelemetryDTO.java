package xiaozhi.modules.device.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * 设备实时状态上报（电量、WiFi），供 xiaozhi-server 写入 Redis。
 */
@Setter
@Getter
@Schema(description = "设备实时状态上报")
public class DeviceTelemetryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "设备 ID（MAC，如 AA:BB:CC:DD:EE:FF）", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonProperty("deviceId")
    private String deviceId;

    @Schema(description = "电量百分比 0-100")
    @JsonProperty("batteryLevel")
    private Integer batteryLevel;

    @Schema(description = "当前 WiFi 名称")
    @JsonProperty("wifiName")
    private String wifiName;
}
