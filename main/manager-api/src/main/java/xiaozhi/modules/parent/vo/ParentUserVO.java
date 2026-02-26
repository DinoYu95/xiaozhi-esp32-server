package xiaozhi.modules.parent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "家长用户信息")
public class ParentUserVO {
    @Schema(description = "用户 id")
    private Long id;
    @Schema(description = "昵称")
    private String nickname;
    @Schema(description = "头像 URL")
    private String avatarUrl;
    @Schema(description = "手机号（脱敏）")
    private String phone;
}
