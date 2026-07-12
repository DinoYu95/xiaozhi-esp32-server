package xiaozhi.modules.parent.vo;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "设备成员项")
public class DeviceMemberItemVO {
    private Long parentId;
    private String nickname;
    private String role;
    private Boolean isPrimary;
    private Long invitedBy;
    private Date joinedAt;
    @Schema(description = "头像 URL（被邀请人主动更换头像时返回；无则 null，前端不展示）")
    private String avatarUrl;
    @Schema(description = "是否接收该设备儿童风险提示（Owner 恒为 true）")
    private Boolean receiveRiskNotify;
    @Schema(description = "Owner 是否可在设置页编辑该成员开关")
    private Boolean canEdit;
}
