package xiaozhi.modules.learning.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "掌握地图模块（如加法、减法）")
public class LearningMasteryModuleVO {

    @Schema(description = "模块键，如 ADD、SUB")
    private String moduleKey;
    @Schema(description = "展示名，如 加法")
    private String moduleLabel;
    private Integer skillTotal;
    private Integer observedCount;
    private Integer needConsolidateCount;
    private List<LearningMasterySkillVO> skills;
}
