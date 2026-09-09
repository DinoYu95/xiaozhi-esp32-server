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
    @Schema(description = "家庭角色 code：father/mother/paternal_grandfather/…/other；未设置时为 null")
    private String familyRole;
    @Schema(description = "家庭角色中文展示，如「爸爸」")
    private String familyRoleLabel;
    @Schema(description = "当前登录用户是否可编辑该成员的家庭角色（Owner 可改全员；Member 仅可改本人）")
    private Boolean canEditFamilyRole;
}
