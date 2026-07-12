package xiaozhi.modules.parent.beta.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "内测任务入口状态")
public class BetaMissionEntryStatusVO {

    private Boolean betaMissionEnabled;
    @Schema(description = "当前用户是否具备内测资格（含家庭共享继承）")
    private Boolean betaTester;

    @Schema(description = "是否通过家庭共享获得内测资格（本人无 is_beta_tester 标记时为 true）")
    private Boolean betaAccessViaSharing;
    private Boolean showEntry;
    private Boolean packCompleted;
    private Boolean contextLocked;
    private Boolean popupDismissed;
    private Integer requiredTotal;
    private Integer requiredDone;
}
