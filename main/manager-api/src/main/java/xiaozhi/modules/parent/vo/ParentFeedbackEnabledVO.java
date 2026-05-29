package xiaozhi.modules.parent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "内测反馈入口是否可用")
public class ParentFeedbackEnabledVO {

    @Schema(description = "参数字典总开关")
    private Boolean betaFeedbackEnabled;

    @Schema(description = "当前用户是否内测用户")
    private Boolean betaTester;

    @Schema(description = "是否应显示反馈入口（两者均为 true）")
    private Boolean showEntry;
}
