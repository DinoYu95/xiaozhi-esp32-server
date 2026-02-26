package xiaozhi.modules.parent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "家长端登录/用户信息返回")
public class ParentLoginVO {
    @Schema(description = "token")
    private String token;
    @Schema(description = "过期时间戳")
    private Long expireAt;
    @Schema(description = "用户信息")
    private ParentUserVO user;
}
