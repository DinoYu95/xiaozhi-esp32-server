package xiaozhi.modules.parent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "家长聊天记录保存结果")
public class ParentChatSaveResultVO {

    @Schema(description = "家长消息 id")
    private Long userMessageId;

    @Schema(description = "助手消息 id")
    private Long assistantMessageId;
}
