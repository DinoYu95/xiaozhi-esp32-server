package xiaozhi.modules.parent.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 主孩子今日简报
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "主孩子今日简报")
public class DailyBriefVO {

    @Schema(description = "孩子姓名")
    private String childName;

    @Schema(description = "日期，yyyy-MM-dd")
    private String date;

    @Schema(description = "今日对话消息总数（孩子+助手）")
    private Integer messageCount;

    @Schema(description = "今日最早对话时间，HH:mm")
    private String firstChatAt;

    @Schema(description = "今日最晚对话时间，HH:mm")
    private String lastChatAt;

    @Schema(description = "今日亮点（从孩子发言中按信息量打分并参考时段分布选取，最多5条，每条约42字内）")
    private List<String> highlights;
}
