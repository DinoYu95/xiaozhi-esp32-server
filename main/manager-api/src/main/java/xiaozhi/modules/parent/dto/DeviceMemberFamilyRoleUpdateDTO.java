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

    @Schema(description = "家庭角色 code；传空字符串或 null 表示清除。可选：father/mother/paternal_grandfather/paternal_grandmother/maternal_grandfather/maternal_grandmother/other")
    private String familyRole;
}
