package xiaozhi.modules.parent.vo;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "管理端-家长登录身份")
public class AdminParentUserAuthVO {

    private Long id;
    private String authType;
    private String channel;
    @Schema(description = "脱敏 openId")
    private String openIdMasked;
    @Schema(description = "脱敏 unionId")
    private String unionIdMasked;
    @Schema(description = "脱敏手机号")
    private String phoneMasked;
    private Date createTime;
}
