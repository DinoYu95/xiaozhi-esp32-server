package xiaozhi.modules.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "家长端微信登录请求")
public class ParentWechatLoginDTO {
    @Schema(description = "微信 code（必填，用于后端调微信 code2session 换 openid）")
    private String code;
    @Schema(description = "渠道：wechat/mini_program/app")
    private String channel;
    @Schema(description = "昵称（可选，前端微信授权 getUserProfile 后传入，用于写入/更新资料）")
    private String nickname;
    @Schema(description = "头像 URL（可选，前端微信授权后传入）")
    private String avatarUrl;
}
