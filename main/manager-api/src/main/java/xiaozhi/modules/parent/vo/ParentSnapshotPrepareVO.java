package xiaozhi.modules.parent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "设备异步任务 prepare 结果（家长看娃等）")
public class ParentSnapshotPrepareVO {

    @Schema(description = "请求 id")
    private String requestId;

    @Schema(description = "设备 MQTT clientId")
    private String clientId;

    @Schema(description = "一次性上传 token")
    private String uploadToken;

    @Schema(description = "设备 HTTP 上传地址")
    private String uploadUrl;

    @Schema(description = "业务 taskType，如 parent_snapshot")
    private String taskType;
}
