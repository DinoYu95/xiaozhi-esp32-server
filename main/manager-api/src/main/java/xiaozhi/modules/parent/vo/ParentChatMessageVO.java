package xiaozhi.modules.parent.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 家长端聊天消息 VO
 */
@Data
@Schema(description = "家长端聊天消息")
public class ParentChatMessageVO {

    @Schema(description = "消息ID")
    private Long id;

    @Schema(description = "会话ID")
    private String sessionId;

    @Schema(description = "消息类型：1=家长 2=助手")
    private Byte chatType;

    @Schema(description = "文本内容")
    private String content;

    @Schema(description = "语音消息的 audioId，非空表示可播放")
    private String audioId;

    @Schema(description = "消息形态：text/snapshot/text_with_snapshot")
    private String messageKind;

    @Schema(description = "聊天图片可访问 URL（远程看娃快照等）")
    private String imageUrl;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
