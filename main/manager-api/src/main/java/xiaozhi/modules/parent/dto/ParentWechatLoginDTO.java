package xiaozhi.modules.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "家长端微信登录请求")
public class ParentWechatLoginDTO {
    @Schema(description = "微信 code")
    private String code;
    @Schema(description = "渠道：wechat/mini_program/app")
    private String channel;
}
