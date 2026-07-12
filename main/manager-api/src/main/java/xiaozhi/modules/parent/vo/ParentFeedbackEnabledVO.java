package xiaozhi.modules.parent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "内测反馈入口是否可用")
public class ParentFeedbackEnabledVO {

    @Schema(description = "参数字典总开关")
    private Boolean betaFeedbackEnabled;

    @Schema(description = "当前用户是否具备内测资格（含家庭共享继承）")
    private Boolean betaTester;

    @Schema(description = "是否通过家庭共享获得内测资格")
    private Boolean betaAccessViaSharing;

    @Schema(description = "是否应显示反馈入口（开关与内测资格均为 true）")
    private Boolean showEntry;
}
