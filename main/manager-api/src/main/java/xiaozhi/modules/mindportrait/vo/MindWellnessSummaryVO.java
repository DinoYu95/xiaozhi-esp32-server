package xiaozhi.modules.mindportrait.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "心绪陪伴概览（机器人 Tab + 详情页）")
public class MindWellnessSummaryVO {

    private Long childId;
    private String childName;
    private int observeDays;
    private String weekStart;
    private String weekEnd;

    /** stable | watch | concern */
    private String overallLevel;
    private String overallText;
    private String summary;

    private List<Chip> chips;
    private List<Dimension> dimensions;
    private List<DayTrend> weekTrend;

    /** 图一底部按钮：stable 且无 watch 面向时为 false */
    private boolean showActions;
    private Actions actions;

    @Data
    public static class Chip {
        private String text;
        /** ok | watch | neutral */
        private String type;
    }

    @Data
    public static class Dimension {
        private String code;
        private String name;
        private String icon;
        /** ok | watch | observe */
        private String status;
        private String statusText;
        private String hint;
        private String detail;
    }

    @Data
    public static class DayTrend {
        private String date;
        private String dayLabel;
        /** ok | watch | neutral */
        private String level;
    }

    @Data
    public static class Actions {
        private ActionItem chat;
        private ActionItem detail;
    }

    @Data
    public static class ActionItem {
        private String label;
    }
}
