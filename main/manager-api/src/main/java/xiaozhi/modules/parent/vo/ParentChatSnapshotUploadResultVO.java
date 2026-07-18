package xiaozhi.modules.parent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "家长聊天远程看娃快照上传结果")
public class ParentChatSnapshotUploadResultVO {

    @Schema(description = "助手消息 id")
    private Long messageId;

    @Schema(description = "OSS objectKey")
    private String objectKey;

    @Schema(description = "可访问 URL")
    private String accessUrl;
}
