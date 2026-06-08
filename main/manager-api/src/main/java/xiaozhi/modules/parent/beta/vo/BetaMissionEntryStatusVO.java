package xiaozhi.modules.parent.beta.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "内测任务入口状态")
public class BetaMissionEntryStatusVO {

    private Boolean betaMissionEnabled;
    private Boolean betaTester;
    private Boolean showEntry;
    private Boolean packCompleted;
    private Boolean contextLocked;
    private Boolean popupDismissed;
    private Integer requiredTotal;
    private Integer requiredDone;
}
