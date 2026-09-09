package xiaozhi.modules.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "保存家长成员声纹（sourceName 由服务端按家庭角色锁定，客户端勿传）")
public class MemberVoicePrintSaveDTO {

    @Schema(description = "设备 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String deviceId;

    @Schema(description = "上传得到的 audioId", requiredMode = Schema.RequiredMode.REQUIRED)
    private String audioId;

    @Schema(description = "描述（可选）")
    private String introduce;
}
