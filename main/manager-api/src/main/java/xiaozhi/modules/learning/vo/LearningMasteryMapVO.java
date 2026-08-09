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
    @Schema(description = "孩子档案 currentGrade，家长不可查看高于此年级的图谱")
    private Integer childMaxGrade;
    @Schema(description = "当前发布版本覆盖的最低年级")
    private Integer graphGradeMin;
    @Schema(description = "当前发布版本覆盖的最高年级")
    private Integer graphGradeMax;
    @Schema(description = "请求的 grade 是否有对应图谱节点；false 时 modules 为空，应展示「暂无该年级图谱」")
    private Boolean gradeSupported;
    private Long graphReleaseId;
    private String graphVersionLabel;
    private LearningMasterySummaryVO summary;
    private List<LearningMasteryModuleVO> modules;
    private String coverageNote;
    @Schema(description = "与 overview 一致的周一 yyyy-MM-dd")
    private String weekStart;
    @Schema(description = "weekStart + 6 天")
    private String weekEnd;
}
