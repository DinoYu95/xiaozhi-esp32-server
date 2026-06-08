package xiaozhi.modules.parent.beta.vo;

import java.util.Date;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "内测任务用户详情")
public class BetaMissionUserDetailVO {

    private Long parentUserId;
    private String parentNickname;
    private Long contextChildId;
    private String contextChildName;
    private Boolean packCompleted;
    private Date packCompletedAt;
    private Boolean popupDismissed;
    private List<BetaMissionStepVO> steps;
}
