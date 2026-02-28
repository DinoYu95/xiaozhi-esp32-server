package xiaozhi.modules.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "保存主孩子声纹")
public class ChildVoicePrintSaveDTO {

    @Schema(description = "设备ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String deviceId;

    @Schema(description = "孩子ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long childId;

    @Schema(description = "上传得到的音频ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String audioId;

    @Schema(description = "声纹来源姓名（如孩子昵称）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sourceName;

    @Schema(description = "描述（如与孩子关系、年龄等）")
    private String introduce;
}
