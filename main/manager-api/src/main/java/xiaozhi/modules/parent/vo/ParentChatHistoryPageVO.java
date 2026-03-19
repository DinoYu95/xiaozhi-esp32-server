package xiaozhi.modules.parent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 家长端聊天记录分页响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "家长端聊天记录分页")
public class ParentChatHistoryPageVO {

    @Schema(description = "消息列表，按 createTime 降序（最新在前）")
    private List<ParentChatMessageVO> list;

    @Schema(description = "是否有更早的消息可加载")
    private Boolean hasMore;
}
