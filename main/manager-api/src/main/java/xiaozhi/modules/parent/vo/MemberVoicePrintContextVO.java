package xiaozhi.modules.parent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "家长成员声纹录入上下文（录入页进入时拉取）")
public class MemberVoicePrintContextVO {

    @Schema(description = "是否已设置家庭角色")
    private Boolean familyRoleSet;

    @Schema(description = "家庭角色 code")
    private String familyRole;

    @Schema(description = "家庭角色中文，如「爸爸」")
    private String familyRoleLabel;

    @Schema(description = "锁定的声纹身份名称（= familyRoleLabel），录入页只读展示")
    private String lockedSourceName;

    @Schema(description = "是否须先去家庭共享设置角色")
    private Boolean requireFamilyRoleFirst;

    @Schema(description = "当前用户在该设备是否已有成员声纹")
    private Boolean hasVoicePrint;

    @Schema(description = "已有成员声纹 ID，可用于重新录入/删除")
    private String voicePrintId;

    @Schema(description = "是否可录入/重新录入/删除本人成员声纹")
    private Boolean canManage;
}
