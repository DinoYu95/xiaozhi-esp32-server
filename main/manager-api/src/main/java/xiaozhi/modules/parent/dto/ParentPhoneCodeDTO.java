package xiaozhi.modules.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "家长端发送手机验证码请求")
public class ParentPhoneCodeDTO {
    @Schema(description = "手机号")
    private String phone;
}
