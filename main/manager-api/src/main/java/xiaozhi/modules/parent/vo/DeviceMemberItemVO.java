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
}
