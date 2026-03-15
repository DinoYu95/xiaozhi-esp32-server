package xiaozhi.modules.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 家长端发送聊天消息 DTO
 */
@Data
@Schema(description = "家长端发送消息")
public class ParentChatSendDTO {

    @Schema(description = "孩子ID（device_child.id）", required = true)
    private Long childId;

    @Schema(description = "设备ID（可选，由 child 推导）")
    private String deviceId;

    @Schema(description = "文本内容（与 audioId 二选一或都有，有 content 时可直接用）")
    private String content;

    @Schema(description = "语音消息的 audioId（需先调用 upload-audio 获得）")
    private String audioId;
}
