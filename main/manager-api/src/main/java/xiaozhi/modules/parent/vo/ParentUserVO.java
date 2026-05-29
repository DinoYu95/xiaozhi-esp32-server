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
    @Schema(description = "是否内测用户（可显示反馈入口）")
    private Boolean betaTester;
    @Schema(description = "内测反馈功能是否全局开启（参数字典 server.beta_feedback_enabled）")
    private Boolean betaFeedbackEnabled;
}
