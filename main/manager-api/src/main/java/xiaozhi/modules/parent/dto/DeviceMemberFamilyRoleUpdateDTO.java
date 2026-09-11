package xiaozhi.modules.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "更新设备成员家庭角色备注")
public class DeviceMemberFamilyRoleUpdateDTO {

    @NotBlank
    @Schema(description = "设备 ID（MAC）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String deviceId;

    @NotNull
    @Schema(description = "目标成员 parentId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long parentId;

    @Schema(description = "家庭角色 code；传空字符串或 null 表示清除。可选：father/mother/… 或中文：爸爸/妈妈/…")
    private String familyRole;

    @Schema(description = "家庭角色中文（与 familyRole 二选一，小程序 picker 可直接传 label）")
    private String familyRoleLabel;

    /** 优先 familyRole，否则 familyRoleLabel */
    public String resolveRoleInput() {
        if (familyRole != null && !familyRole.isBlank()) {
            return familyRole;
        }
        return familyRoleLabel;
    }
}
