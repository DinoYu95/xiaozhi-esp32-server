package xiaozhi.modules.parent.vo;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "管理端-家长用户列表项")
public class AdminParentUserListItemVO {

    private Long id;
    @Schema(description = "原始昵称（可能为空）")
    private String nickname;
    @Schema(description = "展示昵称（含兜底）")
    private String displayNickname;
    @Schema(description = "头像可访问 URL，无头像时为 null")
    private String avatarUrl;
    @Schema(description = "脱敏手机号")
    private String phoneMasked;
    @Schema(description = "登录方式摘要，如 wechat/mini_program")
    private String loginMethods;
    private Boolean betaTester;
    private Integer deviceCount;
    private Date createTime;
    private Date updateTime;
}
