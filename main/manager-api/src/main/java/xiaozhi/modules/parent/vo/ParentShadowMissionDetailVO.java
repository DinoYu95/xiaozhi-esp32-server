package xiaozhi.modules.parent.vo;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "家长影子任务详情（小程序列表/详情）")
public class ParentShadowMissionDetailVO {

    @Schema(description = "任务ID")
    private Long id;
    @Schema(description = "设备ID")
    private String deviceId;
    @Schema(description = "孩子主键 device_child.id")
    private Long childId;
    @Schema(description = "短标题")
    private String title;
    @Schema(description = "详细说明/引导话术偏好")
    private String instructions;
    @Schema(description = "失效时间")
    private Date endsAt;
    @Schema(description = "优先级，越小越优先")
    private Integer priority;
    @Schema(description = "状态：active/cancelled/expired/completed")
    private String status;
    @Schema(description = "创建时间")
    private Date createTime;
    @Schema(description = "更新时间")
    private Date updateTime;
    @Schema(description = "来源：parent|learning")
    private String source;
    @Schema(description = "学习回炉关联 skill code")
    private String skillCode;
    @Schema(description = "关联 learning_homework_session.id")
    private Long learningSessionId;
}
