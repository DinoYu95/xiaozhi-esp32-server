package xiaozhi.modules.parent.app.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "微信登录内测未开放说明")
public class ParentWechatAccessDeniedVO {

    private String accessMode;
    private String supportHint;
}
