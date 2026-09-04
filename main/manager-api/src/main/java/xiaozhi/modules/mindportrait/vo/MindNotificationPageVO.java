package xiaozhi.modules.mindportrait.vo;

import java.util.Date;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "心绪图谱家长通知")
public class MindNotificationPageVO {

    private List<Item> items;
    private int unreadCount;

    @Data
    public static class Item {
        private Long id;
        private String notifyType;
        /** 会话渲染类型：mind_instant_card | mind_weekly_card */
        private String cardType;
        private String title;
        private String summary;
        private String nodeCode;
        private Integer isRead;
        private Date createTime;
    }
}
