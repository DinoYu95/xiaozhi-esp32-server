package xiaozhi.modules.parent.vo;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "家长端已绑定设备项")
public class ParentDeviceItemVO {
    @Schema(description = "设备 id（mac）")
    private String deviceId;
    @Schema(description = "绑定时间")
    private Date bindTime;
}
