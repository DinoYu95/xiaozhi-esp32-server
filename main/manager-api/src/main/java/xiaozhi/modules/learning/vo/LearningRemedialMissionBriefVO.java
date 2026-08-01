package xiaozhi.modules.learning.vo;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "学习系统下发的回炉影子任务摘要")
public class LearningRemedialMissionBriefVO {

    private Long id;
    private String title;
    private String status;
    private String skillCode;
    private Date endsAt;
    private Date createTime;
}
