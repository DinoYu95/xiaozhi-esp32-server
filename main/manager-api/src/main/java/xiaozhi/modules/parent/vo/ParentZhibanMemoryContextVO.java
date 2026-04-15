package xiaozhi.modules.parent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 家长端小程序连 zhiban 时使用的记忆命名空间与设备标识（与设备端主孩子 user_id 对齐）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "家长询问孩子情况时 zhiban 所用上下文")
public class ParentZhibanMemoryContextVO {

    @Schema(description = "与设备端一致的长期记忆 user_id，格式 deviceId + '_' + device_child.id")
    private String zhibanUserId;

    @Schema(description = "智能体ID")
    private String agentId;

    @Schema(description = "设备 MAC（聊天记录查询用）")
    private String macAddress;

    @Schema(description = "设备主键 ai_device.id，与 shadow-mission/active 的 deviceId 一致")
    private String deviceId;

    @Schema(description = "孩子昵称/姓名")
    private String childName;

    @Schema(description = "智控台登记的孩子档案摘要（爱好、话题等），非对话抽取")
    private String deviceChildProfile;
}
