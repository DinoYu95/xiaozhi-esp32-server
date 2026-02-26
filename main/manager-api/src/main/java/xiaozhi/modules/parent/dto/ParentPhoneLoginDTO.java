package xiaozhi.modules.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "家长端手机号验证码登录请求")
public class ParentPhoneLoginDTO {
    @Schema(description = "手机号")
    private String phone;
    @Schema(description = "验证码")
    private String code;
}
