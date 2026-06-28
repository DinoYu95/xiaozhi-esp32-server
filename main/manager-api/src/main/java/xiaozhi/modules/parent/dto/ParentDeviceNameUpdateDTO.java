package xiaozhi.modules.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "家长端修改设备名称请求")
public class ParentDeviceNameUpdateDTO {
    @Schema(description = "设备 id（mac），path 传 deviceId 时可省略")
    private String deviceId;

    @NotBlank(message = "设备名称不能为空")
    @Size(max = 64, message = "设备名称最多 64 个字符")
    @Schema(description = "机器人名称（对话自称与小程序展示均使用此名称）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String deviceName;
}
