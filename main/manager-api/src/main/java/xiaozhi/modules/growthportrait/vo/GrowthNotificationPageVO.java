package xiaozhi.modules.growthportrait.vo;

import java.util.Date;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "成长星图家长通知")
public class GrowthNotificationPageVO {

    private List<Item> items;
    private int unreadCount;

    @Data
    public static class Item {
        private Long id;
        private String notifyType;
        private String title;
        private String summary;
        private String nodeCode;
        private Integer isRead;
        private Date createTime;
    }
}
