package xiaozhi.modules.learning.vo;

import java.util.Date;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "作业辅导 session 详情")
public class LearningSessionDetailVO {

    private Long id;
    private Long childId;
    private Date startedAt;
    private Date endedAt;
    private String observationLevel;
    private Integer userTurnCount;
    private Integer photoCount;
    private Long durationSec;
    private String endReason;
    private String parentHeadline;
    private String parentSuggestion;
    @Schema(description = "原始小结 JSON 解析结果")
    private Map<String, Object> summary;
    @Schema(description = "本会话触达的知识点 code")
    private List<String> skillCodes;
}
