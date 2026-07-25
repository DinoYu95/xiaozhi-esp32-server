package xiaozhi.modules.parent.vo;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "管理端-家长设备绑定")
public class AdminParentUserDeviceVO {

    private Long bindingId;
    private String deviceId;
    private String role;
    private String status;
    private Date bindTime;
}
