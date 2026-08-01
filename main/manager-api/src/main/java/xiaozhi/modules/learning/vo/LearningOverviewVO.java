package xiaozhi.modules.learning.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "学习洞察首页聚合")
public class LearningOverviewVO {

    @Schema(description = "是否已设置 currentGrade")
    private Boolean gradeConfigured;
    @Schema(description = "是否已发布数学图谱")
    private Boolean graphReady;
    private Integer currentGrade;
    private String textbookSeries;
    private String subjectsEnabled;
    private LearningWeeklyDigestVO weeklyDigest;
}
