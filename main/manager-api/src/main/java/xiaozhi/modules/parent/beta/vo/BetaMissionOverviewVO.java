package xiaozhi.modules.parent.beta.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "内测任务概览")
public class BetaMissionOverviewVO {

    private String campaignCode;
    private String campaignTitle;
    private String campaignDescription;
    private Long contextChildId;
    private String contextChildName;
    private Boolean contextLocked;
    private Integer requiredTotal;
    private Integer requiredDone;
    private Boolean packCompleted;
    private Boolean popupDismissed;
    private List<BetaMissionSectionVO> sections;
}
