package xiaozhi.modules.mindportrait.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "心绪陪伴周报（会话卡片）")
public class MindWeeklyDigestVO {

    private String weekStart;
    private String weekEnd;
    private List<Highlight> topHighlights;
    /** @deprecated 使用 parentSupport */
    private String parentTip;
    private int newStrongCount;

    /** 会话卡片标题，如「整体平稳，「面对压力时」值得多陪」 */
    private String title;
    /** 卡片正文摘要 */
    private String summary;
    /** 给家长的行动建议（会话卡片 bullet） */
    private List<String> parentActions;
    /** 共情短句（会话独立消息或卡片内） */
    private String parentSupport;
    /** 陪伴孩子的 3 个小动作（会话 parent_tips 消息） */
    private List<String> childTips;

    @Data
    public static class Highlight {
        private String nodeCode;
        private String label;
        private String shortDesc;
        private int strength;
    }
}
