package xiaozhi.modules.parent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "远程看娃快照状态")
public class ParentSnapshotStatusVO {

    @Schema(description = "waiting / uploaded / expired / not_found")
    private String status;

    @Schema(description = "OSS objectKey，uploaded 时有值")
    private String objectKey;

    @Schema(description = "可访问 URL，uploaded 时有值")
    private String accessUrl;

    @Schema(description = "图片宽")
    private Integer width;

    @Schema(description = "图片高")
    private Integer height;
}
