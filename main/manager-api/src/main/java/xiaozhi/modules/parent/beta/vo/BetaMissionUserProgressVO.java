package xiaozhi.modules.parent.beta.vo;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "内测任务用户进度")
public class BetaMissionUserProgressVO {

    private Long parentUserId;
    private String parentNickname;
    private Long contextChildId;
    private Integer requiredDone;
    private Integer requiredTotal;
    private Boolean packCompleted;
    private Date updateTime;
}
