package xiaozhi.modules.learning.vo;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "作业辅导 session 列表项")
public class LearningSessionItemVO {

    private Long id;
    private Date startedAt;
    private Date endedAt;
    private String observationLevel;
    private Integer userTurnCount;
    private Integer photoCount;
    private Long durationSec;
    private String endReason;
    @Schema(description = "来自 session 小结，可直接展示")
    private String parentHeadline;
}
