package xiaozhi.modules.learning.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "家长端掌握地图")
public class LearningMasteryMapVO {

    private Long childId;
    private String subject;
    @Schema(description = "math 等")
    private String subjectLabel;
    private Integer grade;
    private Boolean gradeConfigured;
    private Long graphReleaseId;
    private String graphVersionLabel;
    private LearningMasterySummaryVO summary;
    private List<LearningMasteryModuleVO> modules;
    private String coverageNote;
}
