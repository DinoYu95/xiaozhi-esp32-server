package xiaozhi.modules.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "家长端更新个人信息请求")
public class ParentProfileDTO {
    @Schema(description = "昵称")
    private String nickname;
    @Schema(description = "头像 URL")
    private String avatarUrl;
    @Schema(description = "手机号")
    private String phone;
}
