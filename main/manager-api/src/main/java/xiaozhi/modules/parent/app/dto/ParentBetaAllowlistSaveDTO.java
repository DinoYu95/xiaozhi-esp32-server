package xiaozhi.modules.parent.app.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "内测登录白名单")
public class ParentBetaAllowlistSaveDTO {

    private Long id;

    @NotBlank
    private String wechatOpenid;

    private String channel;
    private String phone;
    private String source;
    private Boolean enabled;
    private Boolean reviewerTest;
    private String remark;
}
